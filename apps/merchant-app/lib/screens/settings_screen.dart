import 'package:flutter/material.dart';
import '../api.dart';
import '../theme.dart';
import '../widgets/common.dart';

/// Lets the operator point the app at a different backend without rebuilding —
/// useful for staging, a backup domain, or a direct IP while DNS propagates.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});
  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late final TextEditingController _url;
  bool _testing = false;
  bool? _ok;

  @override
  void initState() {
    super.initState();
    _url = TextEditingController(text: Api.instance.baseUrl);
  }

  @override
  void dispose() {
    _url.dispose();
    super.dispose();
  }

  Future<void> _test() async {
    setState(() {
      _testing = true;
      _ok = null;
    });
    await Api.instance.setBaseUrl(_url.text);
    final ok = await Api.instance.ping();
    if (!mounted) return;
    setState(() {
      _testing = false;
      _ok = ok;
    });
  }

  Future<void> _save() async {
    await Api.instance.setBaseUrl(_url.text);
    if (!mounted) return;
    showSnack(context, 'Server address saved');
    Navigator.of(context).pop();
  }

  Future<void> _reset() async {
    await Api.instance.setBaseUrl(null);
    setState(() {
      _url.text = Api.instance.baseUrl;
      _ok = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Server settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Backend API address',
              style: TextStyle(
                  fontSize: 12, fontWeight: FontWeight.w600, color: Brand.muted)),
          const SizedBox(height: 6),
          TextField(
            controller: _url,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: const InputDecoration(
              hintText: 'https://host/financekey/api/v1',
              prefixIcon: Icon(Icons.dns_outlined, size: 20),
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _testing ? null : _test,
                  icon: _testing
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.wifi_tethering, size: 18),
                  label: const Text('Test connection'),
                  style: OutlinedButton.styleFrom(
                      minimumSize: const Size.fromHeight(48)),
                ),
              ),
            ],
          ),
          if (_ok != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: (_ok! ? Brand.good : Brand.crit).withOpacity(0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                children: [
                  Icon(_ok! ? Icons.check_circle : Icons.error_outline,
                      size: 18, color: _ok! ? Brand.good : Brand.crit),
                  const SizedBox(width: 9),
                  Expanded(
                    child: Text(
                      _ok!
                          ? 'Connected — the server is reachable.'
                          : 'Could not reach this address. Check it and your internet.',
                      style: TextStyle(
                          fontSize: 12.5,
                          color: _ok! ? Brand.good : Brand.crit),
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 20),
          FilledButton(onPressed: _save, child: const Text('Save')),
          const SizedBox(height: 10),
          TextButton(
              onPressed: _reset,
              child: const Text('Reset to default')),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(13),
            decoration: BoxDecoration(
              color: Brand.bg,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: Brand.line),
            ),
            child: const Text(
              'Default: $kDefaultApiBase\n\nMost merchants never change this. Use it only if your provider gave you a different server address.',
              style: TextStyle(
                  fontSize: 11.5, color: Brand.muted, height: 1.5),
            ),
          ),
        ],
      ),
    );
  }
}
