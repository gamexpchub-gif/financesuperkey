import 'package:flutter/material.dart';
import '../api.dart';
import '../theme.dart';
import 'home_shell.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _id = TextEditingController();
  final _pw = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _id.dispose();
    _pw.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await Api.instance.login(_id.text.trim(), _pw.text);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => const HomeShell()),
      );
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Container(
                      width: 46,
                      height: 46,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                            colors: [Brand.navy, Brand.navyInk]),
                        borderRadius: BorderRadius.circular(13),
                      ),
                      child: const Icon(Icons.vpn_key_rounded,
                          color: Colors.white, size: 24),
                    ),
                    const SizedBox(width: 12),
                    const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Finance Super Key',
                            style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.w800,
                                color: Brand.ink)),
                        Text('Merchant App',
                            style:
                                TextStyle(fontSize: 12.5, color: Brand.muted)),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 30),
                if (_error != null)
                  Container(
                    margin: const EdgeInsets.only(bottom: 14),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Brand.crit.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Row(children: [
                      const Icon(Icons.error_outline,
                          size: 18, color: Brand.crit),
                      const SizedBox(width: 8),
                      Expanded(
                          child: Text(_error!,
                              style: const TextStyle(
                                  color: Brand.crit, fontSize: 12.5))),
                    ]),
                  ),
                const Text('Phone or email',
                    style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: Brand.muted)),
                const SizedBox(height: 6),
                TextField(
                  controller: _id,
                  keyboardType: TextInputType.text,
                  decoration: const InputDecoration(hintText: '01811111111'),
                ),
                const SizedBox(height: 14),
                const Text('Password',
                    style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: Brand.muted)),
                const SizedBox(height: 6),
                TextField(
                  controller: _pw,
                  obscureText: true,
                  onSubmitted: (_) => _submit(),
                  decoration: const InputDecoration(hintText: '••••••••'),
                ),
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _loading ? null : _submit,
                  child: _loading
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                              strokeWidth: 2.5, color: Colors.white))
                      : const Text('Sign in'),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Brand.bg,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: Brand.line),
                  ),
                  child: const Text(
                    'Dev login:  01811111111 / Owner@12345',
                    style: TextStyle(fontSize: 11.5, color: Brand.muted),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
