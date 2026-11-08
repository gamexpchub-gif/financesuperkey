import 'package:flutter/material.dart';
import 'api.dart';
import 'theme.dart';
import 'screens/login_screen.dart';
import 'screens/home_shell.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Api.instance.loadSession();
  runApp(const MerchantApp());
}

class MerchantApp extends StatelessWidget {
  const MerchantApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Finance Super Key',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      home: Api.instance.isLoggedIn ? const HomeShell() : const LoginScreen(),
    );
  }
}
