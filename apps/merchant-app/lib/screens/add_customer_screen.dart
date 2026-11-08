import 'package:flutter/material.dart';
import '../api.dart';
import '../theme.dart';
import '../widgets/common.dart';

class AddCustomerScreen extends StatefulWidget {
  const AddCustomerScreen({super.key});
  @override
  State<AddCustomerScreen> createState() => _AddCustomerScreenState();
}

class _AddCustomerScreenState extends State<AddCustomerScreen> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _nid = TextEditingController();
  final _dealer = TextEditingController();
  final _address = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    _nid.dispose();
    _dealer.dispose();
    _address.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      await Api.instance.createCustomer({
        'fullName': _name.text.trim(),
        'phone': _phone.text.trim(),
        if (_nid.text.trim().isNotEmpty) 'nidNo': _nid.text.trim(),
        if (_dealer.text.trim().isNotEmpty) 'dealerNo': _dealer.text.trim(),
        if (_address.text.trim().isNotEmpty) 'address': _address.text.trim(),
      });
      if (!mounted) return;
      showSnack(context, 'Customer added');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) showSnack(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Add customer')),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _label('Full name *'),
            TextFormField(
              controller: _name,
              validator: (v) =>
                  (v == null || v.trim().length < 2) ? 'Enter a name' : null,
              decoration: const InputDecoration(hintText: 'Customer name'),
            ),
            const SizedBox(height: 14),
            _label('Phone number *'),
            TextFormField(
              controller: _phone,
              keyboardType: TextInputType.phone,
              validator: (v) =>
                  (v == null || v.trim().length < 6) ? 'Enter a phone' : null,
              decoration: const InputDecoration(hintText: '01XXXXXXXXX'),
            ),
            const SizedBox(height: 14),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _label('NID number'),
                      TextFormField(
                          controller: _nid,
                          decoration:
                              const InputDecoration(hintText: 'XXXX XXXX')),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _label('Dealer no.'),
                      TextFormField(
                          controller: _dealer,
                          decoration:
                              const InputDecoration(hintText: 'D-1042')),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            _label('Address'),
            TextFormField(
              controller: _address,
              decoration: const InputDecoration(hintText: 'Area, Thana, District'),
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _saving ? null : _save,
              child: _saving
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Text('Save customer'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _label(String t) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(t,
            style: const TextStyle(
                fontSize: 12, fontWeight: FontWeight.w600, color: Brand.muted)),
      );
}
