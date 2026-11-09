import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../api.dart';
import '../models.dart';
import '../theme.dart';
import '../widgets/common.dart';

class EnrollScreen extends StatefulWidget {
  final Customer customer;
  const EnrollScreen({super.key, required this.customer});
  @override
  State<EnrollScreen> createState() => _EnrollScreenState();
}

class _EnrollScreenState extends State<EnrollScreen> {
  int _step = 0; // 0=install, 1=consent, 2=provisioning QR
  bool _consent = false;
  final List<Offset?> _points = [];
  bool _saving = false;
  String? _provisioningQr; // JSON payload once enrollment started

  bool get _hasSignature => _points.any((p) => p != null);

  Future<void> _addDevice() async {
    if (!_consent) {
      showSnack(context, 'Please confirm consent', error: true);
      return;
    }
    if (!_hasSignature) {
      showSnack(context, 'Please capture the signature', error: true);
      return;
    }
    setState(() => _saving = true);
    try {
      final res = await Api.instance.startEnrollment(customerId: widget.customer.id);
      final enrollmentId = res['enrollmentId'] as String;
      await Api.instance.submitConsent(enrollmentId);
      await Api.instance.completeEnrollment(enrollmentId);
      final payload = res['qrPayload'];
      if (!mounted) return;
      setState(() {
        _provisioningQr = jsonEncode(payload);
        _step = 2;
      });
      showSnack(context, 'Device added · 1 key used');
    } catch (e) {
      if (mounted) showSnack(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Enroll device')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                _dot(0, 'Install'),
                _bar(_step >= 1),
                _dot(1, 'Consent'),
                _bar(_step >= 2),
                _dot(2, 'Set up'),
              ],
            ),
          ),
          Expanded(
            child: switch (_step) {
              0 => _installStep(),
              1 => _consentStep(),
              _ => _provisioningStep(),
            },
          ),
          if (_step < 2) _footer(),
        ],
      ),
    );
  }

  Widget _footer() => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              if (_step == 1)
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => setState(() => _step = 0),
                    style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(50)),
                    child: const Text('Back'),
                  ),
                ),
              if (_step == 1) const SizedBox(width: 12),
              Expanded(
                flex: 2,
                child: FilledButton(
                  onPressed: _saving
                      ? null
                      : () {
                          if (_step == 0) {
                            setState(() => _step = 1);
                          } else {
                            _addDevice();
                          }
                        },
                  child: _saving
                      ? const SizedBox(
                          width: 20, height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                      : Text(_step == 0 ? 'Next' : 'Add device'),
                ),
              ),
            ],
          ),
        ),
      );

  Widget _dot(int i, String label) {
    final done = _step >= i;
    return Column(children: [
      Container(
        width: 26, height: 26,
        decoration: BoxDecoration(color: done ? Brand.navy : Brand.line, shape: BoxShape.circle),
        child: Center(child: Text('${i + 1}',
            style: TextStyle(color: done ? Colors.white : Brand.muted, fontWeight: FontWeight.w700, fontSize: 13))),
      ),
      const SizedBox(height: 4),
      Text(label, style: const TextStyle(fontSize: 11, color: Brand.muted)),
    ]);
  }

  Widget _bar(bool on) => Expanded(child: Container(height: 2, color: on ? Brand.good : Brand.line));

  // ---- step 0: install customer app ----
  Widget _installStep() {
    final url = Api.instance.apkUrl;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _customerChip(),
        const SizedBox(height: 18),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(color: Brand.surface, borderRadius: BorderRadius.circular(16), border: Border.all(color: Brand.line)),
          child: Column(children: [
            const Text('Install Finance Key on the customer\'s phone',
                textAlign: TextAlign.center,
                style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: Brand.ink)),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
              child: QrImageView(data: url, size: 180, backgroundColor: Colors.white),
            ),
            const SizedBox(height: 12),
            const Text('Ask the customer to scan this with their phone camera to download the app.',
                textAlign: TextAlign.center, style: TextStyle(fontSize: 12.5, color: Brand.muted, height: 1.4)),
            const SizedBox(height: 8),
            SelectableText(url, textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 11, color: Brand.faint)),
          ]),
        ),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(13),
          decoration: BoxDecoration(color: Brand.navy.withOpacity(0.06), borderRadius: BorderRadius.circular(11)),
          child: const Text(
            'IMEI and device name are captured automatically from the customer\'s phone after setup — no manual typing.',
            style: TextStyle(fontSize: 11.5, color: Brand.muted, height: 1.5)),
        ),
      ],
    );
  }

  // ---- step 1: consent + signature ----
  Widget _consentStep() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _customerChip(),
        const SizedBox(height: 16),
        CheckboxListTile(
          value: _consent,
          onChanged: (v) => setState(() => _consent = v ?? false),
          controlAffinity: ListTileControlAffinity.leading,
          contentPadding: EdgeInsets.zero,
          activeColor: Brand.navy,
          title: const Text(
            'The customer has been informed about EMI device management and possible restriction, and consents voluntarily. Emergency calls always remain available.',
            style: TextStyle(fontSize: 13, height: 1.45)),
        ),
        const SizedBox(height: 10),
        const Text('Customer signature *',
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Brand.muted)),
        const SizedBox(height: 6),
        _SignaturePad(points: _points, onChanged: () => setState(() {})),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton(onPressed: () => setState(() => _points.clear()), child: const Text('Clear')),
        ),
        const SizedBox(height: 4),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(children: [
              _reviewRow('Customer', widget.customer.fullName),
              const Divider(height: 18),
              _reviewRow('Device info', 'Auto from phone'),
              const Divider(height: 18),
              _reviewRow('License key', '1 will be used'),
            ]),
          ),
        ),
      ],
    );
  }

  // ---- step 2: provisioning QR ----
  Widget _provisioningStep() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(color: Brand.surface, borderRadius: BorderRadius.circular(16), border: Border.all(color: Brand.good)),
          child: Column(children: [
            const Icon(Icons.check_circle, color: Brand.good, size: 40),
            const SizedBox(height: 8),
            Text('${widget.customer.fullName} added',
                style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: Brand.ink)),
            const SizedBox(height: 4),
            const Text('Now provision the device as Device Owner.',
                style: TextStyle(fontSize: 12.5, color: Brand.muted)),
          ]),
        ),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(color: Brand.surface, borderRadius: BorderRadius.circular(16), border: Border.all(color: Brand.line)),
          child: Column(children: [
            const Text('Device Owner provisioning QR',
                style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: Brand.ink)),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
              child: QrImageView(data: _provisioningQr ?? '', size: 210, backgroundColor: Colors.white),
            ),
          ]),
        ),
        const SizedBox(height: 16),
        _stepsCard(),
        const SizedBox(height: 16),
        FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Done')),
      ],
    );
  }

  Widget _stepsCard() => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(color: Brand.navy.withOpacity(0.06), borderRadius: BorderRadius.circular(12)),
        child: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('On the customer\'s phone:',
                style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5, color: Brand.ink)),
            SizedBox(height: 8),
            Text('1. Factory reset the phone (or on a brand-new phone).\n'
                '2. On the first "Hello / Welcome" screen, tap 6 times.\n'
                '3. The QR scanner opens — scan the QR above.\n'
                '4. The phone sets up Finance Key as Device Owner.\n'
                '5. IMEI & device name fill in automatically here.',
                style: TextStyle(fontSize: 12.5, color: Brand.muted, height: 1.6)),
          ],
        ),
      );

  Widget _customerChip() => Card(
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(children: [
            const Icon(Icons.person, color: Brand.navyInk),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(widget.customer.fullName,
                    style: const TextStyle(fontWeight: FontWeight.w700, color: Brand.ink)),
                Text(widget.customer.phone, style: const TextStyle(color: Brand.muted, fontSize: 12.5)),
              ]),
            ),
          ]),
        ),
      );

  Widget _reviewRow(String k, String v) => Row(children: [
        Text(k, style: const TextStyle(color: Brand.muted, fontSize: 13)),
        const Spacer(),
        Text(v, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: Brand.ink)),
      ]);
}

class _SignaturePad extends StatelessWidget {
  final List<Offset?> points;
  final VoidCallback onChanged;
  const _SignaturePad({required this.points, required this.onChanged});
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 160,
      decoration: BoxDecoration(
        color: Brand.surface, borderRadius: BorderRadius.circular(12), border: Border.all(color: Brand.line)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: GestureDetector(
          onPanStart: (d) { points.add(d.localPosition); onChanged(); },
          onPanUpdate: (d) { points.add(d.localPosition); onChanged(); },
          onPanEnd: (_) { points.add(null); onChanged(); },
          child: CustomPaint(
            painter: _SignaturePainter(points),
            child: points.any((p) => p != null)
                ? const SizedBox.expand()
                : const Center(child: Text('Sign here', style: TextStyle(color: Brand.faint, fontSize: 13))),
          ),
        ),
      ),
    );
  }
}

class _SignaturePainter extends CustomPainter {
  final List<Offset?> points;
  _SignaturePainter(this.points);
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Brand.ink
      ..strokeWidth = 2.4
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;
    for (var i = 0; i < points.length - 1; i++) {
      final a = points[i];
      final b = points[i + 1];
      if (a != null && b != null) canvas.drawLine(a, b, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _SignaturePainter old) => true;
}
