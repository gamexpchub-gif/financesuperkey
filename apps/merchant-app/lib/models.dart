// Data models mirroring the backend JSON shapes.

class SessionUser {
  final String id;
  final String userType;
  final String? merchantId;
  SessionUser({required this.id, required this.userType, this.merchantId});

  factory SessionUser.fromJson(Map<String, dynamic> j) => SessionUser(
        id: j['id'] as String,
        userType: j['userType'] as String,
        merchantId: j['merchantId'] as String?,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'userType': userType,
        'merchantId': merchantId,
      };
}

class KeySummary {
  final int total, available, consumed, released;
  KeySummary({
    required this.total,
    required this.available,
    required this.consumed,
    required this.released,
  });
  factory KeySummary.fromJson(Map<String, dynamic> j) => KeySummary(
        total: j['total'] ?? 0,
        available: j['available'] ?? 0,
        consumed: j['consumed'] ?? 0,
        released: j['released'] ?? 0,
      );
}

class Overview {
  final int devicesTotal, devicesActive, devicesReview, devicesRestricted;
  final int customersTotal;
  final int pendingRequests;
  final KeySummary keys;
  Overview({
    required this.devicesTotal,
    required this.devicesActive,
    required this.devicesReview,
    required this.devicesRestricted,
    required this.customersTotal,
    required this.pendingRequests,
    required this.keys,
  });
  factory Overview.fromJson(Map<String, dynamic> j) {
    final d = j['devices'] ?? {};
    return Overview(
      devicesTotal: d['total'] ?? 0,
      devicesActive: d['active'] ?? 0,
      devicesReview: d['review'] ?? 0,
      devicesRestricted: d['restricted'] ?? 0,
      customersTotal: (j['customers'] ?? {})['total'] ?? 0,
      pendingRequests: (j['restrictionRequests'] ?? {})['pending'] ?? 0,
      keys: KeySummary.fromJson(j['keys'] ?? {}),
    );
  }
}

class Customer {
  final String id, fullName, phone;
  final String? nidNo, address, dealerNo;
  final String createdAt;
  Customer({
    required this.id,
    required this.fullName,
    required this.phone,
    this.nidNo,
    this.address,
    this.dealerNo,
    required this.createdAt,
  });
  factory Customer.fromJson(Map<String, dynamic> j) => Customer(
        id: j['id'] as String,
        fullName: j['fullName'] ?? '',
        phone: j['phone'] ?? '',
        nidNo: j['nidNo'] as String?,
        address: j['address'] as String?,
        dealerNo: j['dealerNo'] as String?,
        createdAt: j['createdAt'] ?? '',
      );
}

class DeviceCustomer {
  final String id, fullName, phone;
  DeviceCustomer({required this.id, required this.fullName, required this.phone});
  factory DeviceCustomer.fromJson(Map<String, dynamic> j) => DeviceCustomer(
        id: j['id'] ?? '',
        fullName: j['fullName'] ?? '',
        phone: j['phone'] ?? '',
      );
}

class Device {
  final String id, model, imeiMasked, status, createdAt;
  final DeviceCustomer? customer;
  Device({
    required this.id,
    required this.model,
    required this.imeiMasked,
    required this.status,
    required this.createdAt,
    this.customer,
  });
  factory Device.fromJson(Map<String, dynamic> j) => Device(
        id: j['id'] as String,
        model: j['model'] ?? '',
        imeiMasked: j['imeiMasked'] ?? '',
        status: j['status'] ?? 'active',
        createdAt: j['createdAt'] ?? '',
        customer: j['customer'] != null
            ? DeviceCustomer.fromJson(j['customer'] as Map<String, dynamic>)
            : null,
      );
}

class TimelineEntry {
  final String title, subtitle, time, tone;
  TimelineEntry(this.title, this.subtitle, this.time, this.tone);
}

class Paged<T> {
  final List<T> data;
  final int total;
  Paged(this.data, this.total);
}
