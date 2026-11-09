import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'models.dart';

/// Default base URL of the backend API, baked in at build time.
/// Override at run time in the app's Settings screen, or at build time:
///   flutter run --dart-define=API_BASE=http://10.0.2.2:3000/v1
const String kDefaultApiBase = String.fromEnvironment(
  'API_BASE',
  defaultValue: 'https://admin.deencircle.online/financekey/api/v1',
);

/// URL where the Customer app (Finance Key) APK is hosted. The merchant shows
/// this as a QR so the customer can scan and download it. Overridable in Settings.
const String kDefaultApkUrl = String.fromEnvironment(
  'APK_URL',
  defaultValue: 'https://admin.deencircle.online/financekey/apk/finance-key.apk',
);

class ApiException implements Exception {
  final int status;
  final String message;
  ApiException(this.status, this.message);
  @override
  String toString() => message;
}

class Api {
  Api._();
  static final Api instance = Api._();

  String? _token;
  String? _customBase;
  String? _customApkUrl;
  SessionUser? _user;
  SessionUser? get user => _user;

  static const _kToken = 'fsk_token';
  static const _kRefresh = 'fsk_refresh';
  static const _kUser = 'fsk_user';
  static const _kBase = 'fsk_base_url';
  static const _kApk = 'fsk_apk_url';

  String get apkUrl => (_customApkUrl != null && _customApkUrl!.isNotEmpty)
      ? _customApkUrl!
      : kDefaultApkUrl;

  Future<void> setApkUrl(String? url) async {
    final p = await SharedPreferences.getInstance();
    final t = url?.trim();
    if (t == null || t.isEmpty) {
      _customApkUrl = null;
      await p.remove(_kApk);
    } else {
      _customApkUrl = t;
      await p.setString(_kApk, t);
    }
  }

  /// Effective base URL — a saved override, else the compile-time default.
  String get baseUrl => (_customBase != null && _customBase!.isNotEmpty)
      ? _customBase!
      : kDefaultApiBase;

  Future<void> loadSession() async {
    final p = await SharedPreferences.getInstance();
    _token = p.getString(_kToken);
    _customBase = p.getString(_kBase);
    _customApkUrl = p.getString(_kApk);
    final u = p.getString(_kUser);
    if (u != null) {
      _user = SessionUser.fromJson(jsonDecode(u) as Map<String, dynamic>);
    }
  }

  Future<void> setBaseUrl(String? url) async {
    final p = await SharedPreferences.getInstance();
    final trimmed = url?.trim();
    if (trimmed == null || trimmed.isEmpty) {
      _customBase = null;
      await p.remove(_kBase);
    } else {
      // Normalise: drop a trailing slash.
      _customBase = trimmed.endsWith('/')
          ? trimmed.substring(0, trimmed.length - 1)
          : trimmed;
      await p.setString(_kBase, _customBase!);
    }
  }

  bool get isLoggedIn => _token != null;

  Future<void> logout() async {
    final p = await SharedPreferences.getInstance();
    await p.remove(_kToken);
    await p.remove(_kRefresh);
    await p.remove(_kUser);
    _token = null;
    _user = null;
  }

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (_token != null) 'Authorization': 'Bearer $_token',
      };

  Uri _uri(String path) => Uri.parse('$baseUrl$path');

  /// Wraps an http call so connection/DNS failures become a clear message
  /// instead of a raw SocketException.
  Future<http.Response> _send(Future<http.Response> Function() call) async {
    try {
      return await call().timeout(const Duration(seconds: 20));
    } on SocketException catch (e) {
      final host = Uri.tryParse(baseUrl)?.host ?? 'the server';
      if (e.osError?.errorCode == 7 ||
          e.message.toLowerCase().contains('failed host lookup')) {
        throw ApiException(
            0, 'Can\'t find "$host". Check the server address in Settings and your internet connection.');
      }
      throw ApiException(0, 'Can\'t reach the server. Check your internet connection and try again.');
    } on http.ClientException {
      throw ApiException(0, 'Network error. Please try again.');
    } catch (_) {
      throw ApiException(0, 'Connection timed out. Please try again.');
    }
  }

  dynamic _decode(http.Response res) {
    final body = res.body.isEmpty ? null : jsonDecode(res.body);
    if (res.statusCode == 401) {
      logout();
      throw ApiException(401, 'Session expired — please sign in again');
    }
    if (res.statusCode >= 400) {
      // Handles both NestJS default errors ({message, error, statusCode})
      // and a nested {error: {message}} shape, without indexing a String.
      dynamic msg = 'Request failed (${res.statusCode})';
      if (body is Map) {
        final err = body['error'];
        if (err is Map && err['message'] != null) {
          msg = err['message'];
        } else if (body['message'] != null) {
          msg = body['message'];
        }
      }
      throw ApiException(res.statusCode, msg is List ? msg.join(', ') : '$msg');
    }
    return body;
  }

  Future<dynamic> get(String path) async {
    final res = await _send(() => http.get(_uri(path), headers: _headers));
    return _decode(res);
  }

  Future<dynamic> post(String path, [Map<String, dynamic>? body]) async {
    final res = await _send(() => http.post(_uri(path),
        headers: _headers, body: body == null ? null : jsonEncode(body)));
    return _decode(res);
  }

  /// Lightweight connectivity probe for the Settings screen.
  Future<bool> ping() async {
    try {
      final res = await _send(() => http.get(_uri('/health')));
      return res.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  // ---------- auth ----------
  Future<SessionUser> login(String identifier, String password) async {
    final res = await _send(() => http.post(_uri('/auth/login'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'identifier': identifier, 'password': password})));
    final body = _decode(res) as Map<String, dynamic>;
    _token = body['access'] as String;
    _user = SessionUser.fromJson(body['user'] as Map<String, dynamic>);
    final p = await SharedPreferences.getInstance();
    await p.setString(_kToken, _token!);
    await p.setString(_kRefresh, body['refresh'] as String);
    await p.setString(_kUser, jsonEncode(_user!.toJson()));
    return _user!;
  }

  // ---------- data ----------
  Future<Overview> overview() async =>
      Overview.fromJson(await get('/reports/overview') as Map<String, dynamic>);

  Future<KeySummary> keySummary() async =>
      KeySummary.fromJson(await get('/keys/summary') as Map<String, dynamic>);

  Future<Paged<Customer>> customers({String? q}) async {
    final res = await get('/customers${q != null && q.isNotEmpty ? '?q=$q' : ''}')
        as Map<String, dynamic>;
    final data = (res['data'] as List)
        .map((e) => Customer.fromJson(e as Map<String, dynamic>))
        .toList();
    return Paged(data, res['total'] ?? data.length);
  }

  Future<Customer> createCustomer(Map<String, dynamic> body) async =>
      Customer.fromJson(await post('/customers', body) as Map<String, dynamic>);

  Future<Paged<Device>> devices({String? status, String? q}) async {
    final params = <String>[];
    if (status != null && status.isNotEmpty) params.add('status=$status');
    if (q != null && q.isNotEmpty) params.add('q=$q');
    params.add('limit=50');
    final res =
        await get('/devices?${params.join('&')}') as Map<String, dynamic>;
    final data = (res['data'] as List)
        .map((e) => Device.fromJson(e as Map<String, dynamic>))
        .toList();
    return Paged(data, res['total'] ?? data.length);
  }

  Future<Map<String, dynamic>> deviceTimeline(String id) async =>
      await get('/devices/$id/timeline') as Map<String, dynamic>;

  /// Starts enrollment. IMEI and model are filled automatically by the customer's
  /// device after provisioning, so they are not required here.
  Future<Map<String, dynamic>> startEnrollment({
    required String customerId,
    String? model,
  }) async =>
      await post('/enrollment/start', {
        'customerId': customerId,
        if (model != null && model.isNotEmpty) 'model': model,
        'method': 'qr_device_owner',
      }) as Map<String, dynamic>;

  Future<void> submitConsent(String enrollmentId) async {
    await post('/enrollment/$enrollmentId/consent', {
      'consentTextVersion': 'v1.0',
      'signatureUrl': 'pending-upload://signature',
      'doc1Url': 'pending-upload://nid',
      'doc2Url': 'pending-upload://photo',
    });
  }

  Future<void> completeEnrollment(String enrollmentId) async {
    await post('/enrollment/$enrollmentId/complete');
  }

  Future<void> requestRestriction(String deviceId, String type, String reason) async {
    await post('/devices/$deviceId/restriction-requests', {
      'type': type,
      'reason': reason,
    });
  }

  Future<void> requestKeys(int quantity) async {
    await post('/keys/purchase-request', {'quantity': quantity});
  }
}
