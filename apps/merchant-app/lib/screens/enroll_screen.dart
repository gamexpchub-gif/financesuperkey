import 'package:flutter/material.dart';
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
  int _step = 0;
  final _model = TextEditingController();
  final _imei = TextEditingController();
  bool _consent = false;
  final List<Offset?> _points = [];
  bool _saving = false;

  @override
  void dispose() {
    _model.dispose();
    _imei.dispose();
    super.dispose();
  }

  bool get _hasSignature => _points.any((p) => p != null);

  Future<void> _submit() async {
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
      final res = await Api.instance.startEnrollment(
        customerId: widget.customer.id,
        model: _model.text.trim(),
        imei: _imei.text.trim(),
      );
      final enrollmentId = res['enrollmentId'] as String;
      await Api.instance.submitConsent(enrollmentId);
      await Api.instance.completeEnrollment(enrollmentId);
      if (!mounted) return;
      showSnack(context, 'Device enrolled · 1 key used');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) showSnack(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _next() {
    if (_step == 0) {
      if (_model.text.trim().length < 2 || _imei.text.trim().length < 6) {
        showSnack(context, 'Enter model and IMEI', error: true);
        return;
      }
      setState(() => _step = 1);
    } else {
      _submit();
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
                _dot(0, 'Device'),
                Expanded(child: Container(height: 2, color: _step >= 1 ? Brand.good : Brand.line)),
                _dot(1, 'Consent'),
              ],
            ),
          ),
          Expanded(
            child: _step == 0 ? _deviceStep() : _consentStep(),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  if (_step == 1)
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => setState(() => _step = 0),
                        style: OutlinedButton.styleFrom(
                            minimumSize: const Size.fromHeight(50)),
                        child: const Text('Back'),
                      ),
                    ),
                  if (_step == 1) const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton(
                      onPressed: _saving ? null : _next,
                      child: _saving
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2.5, color: Colors.white))
                          : Text(_step == 0 ? 'Next' : 'Add device'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _dot(int i, String label) {
    final done = _step >= i;
    return Column(
      children: [
        Container(
          width: 26,
          height: 26,
          decoration: BoxDecoration(
            color: done ? Brand.navy : Brand.line,
            shape: BoxShape.circle,
          ),
          child: Center(
            child: Text('${i + 1}',
                style: TextStyle(
                    color: done ? Colors.white : Brand.muted,
                    fontWeight: FontWeight.w700,
                    fontSize: 13)),
          ),
        ),
        const SizedBox(height: 4),
        Text(label,
            style: const TextStyle(fontSize: 11, color: Brand.muted)),
      ],
    );
  }

  Widget _deviceStep() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                const Icon(Icons.person, color: Brand.navyInk),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(widget.customer.fullName,
                          style: const TextStyle(
                              fontWeight: FontWeight.w700, color: Brand.ink)),
                      Text(widget.customer.phone,
                          style: const TextStyle(
                              color: Brand.muted, fontSize: 12.5)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 18),
        _label('Device model *'),
        TextField(
            controller: _model,
            decoration: const InputDecoration(hintText: 'e.g. Redmi 13C')),
        const SizedBox(height: 14),
        _label('IMEI / Serial *'),
        TextField(
          controller: _imei,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(
              hintText: 'Scan or type IMEI',
              prefixIcon: Icon(Icons.qr_code_scanner, size: 20)),
        ),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(13),
          decoration: BoxDecoration(
            color: Brand.navy.withOpacity(0.06),
            borderRadius: BorderRadius.circular(11),
          ),
          child: const Text(
            'Device enrolls via the official Android Device Owner QR flow during setup, with the customer\'s consent. No Play Protect bypass or hidden control.',
            style: TextStyle(fontSize: 11.5, color: Brand.muted, height: 1.5),
          ),
        ),
      ],
    );
  }

  Widget _consentStep() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        CheckboxListTile(
          value: _consent,
          onChanged: (v) => setState(() => _consent = v ?? false),
          controlAffinity: ListTileControlAffinity.leading,
          contentPadding: EdgeInsets.zero,
          activeColor: Brand.navy,
          title: const Text(
            'The customer has been informed about EMI device management and possible restriction, and consents voluntarily. Emergency calls always remain available.',
            style: TextStyle(fontSize: 13, height: 1.45),
          ),
        ),
        const SizedBox(height: 10),
        _label('Customer signature *'),
        _SignaturePad(points: _points, onChanged: () => setState(() {})),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            onPressed: () => setState(() => _points.clear()),
            child: const Text('Clear'),
          ),
        ),
        const SizedBox(height: 8),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              children: [
                _reviewRow('Customer', widget.customer.fullName),
                const Divider(height: 18),
                _reviewRow('Device', _model.text.trim().isEmpty ? '—' : _model.text.trim()),
                const Divider(height: 18),
                _reviewRow('License key', '1 will be used'),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _reviewRow(String k, String v) => Row(
        children: [
          Text(k, style: const TextStyle(color: Brand.muted, fontSize: 13)),
          const Spacer(),
          Text(v,
              style: const TextStyle(
                  fontWeight: FontWeight.w600, fontSize: 13, color: Brand.ink)),
        ],
      );

  Widget _label(String t) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(t,
            style: const TextStyle(
                fontSize: 12, fontWeight: FontWeight.w600, color: Brand.muted)),
      );
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
        color: Brand.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Brand.line),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: GestureDetector(
          onPanStart: (d) {
            points.add(d.localPosition);
            onChanged();
          },
          onPanUpdate: (d) {
            points.add(d.localPosition);
            onChanged();
          },
          onPanEnd: (_) {
            points.add(null);
            onChanged();
          },
          child: CustomPaint(
            painter: _SignaturePainter(points),
            child: points.any((p) => p != null)
                ? const SizedBox.expand()
                : const Center(
                    child: Text('Sign here',
                        style: TextStyle(color: Brand.faint, fontSize: 13)),
                  ),
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
      if (a != null && b != null) {
        canvas.drawLine(a, b, paint);
      }
    }
  }

  @override
  bool shouldRepaint(covariant _SignaturePainter old) => true;
}
