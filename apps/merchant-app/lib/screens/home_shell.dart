import 'package:flutter/material.dart';
import '../theme.dart';
import 'dashboard_screen.dart';
import 'customers_screen.dart';
import 'devices_screen.dart';
import 'keys_screen.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});
  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  final _pages = const [
    DashboardScreen(),
    CustomersScreen(),
    DevicesScreen(),
    KeysScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _index, children: _pages),
      bottomNavigationBar: NavigationBarTheme(
        data: NavigationBarThemeData(
          backgroundColor: Brand.surface,
          indicatorColor: Brand.navy.withOpacity(0.1),
          labelTextStyle: MaterialStateProperty.all(
            const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600),
          ),
        ),
        child: NavigationBar(
          selectedIndex: _index,
          height: 66,
          onDestinationSelected: (i) => setState(() => _index = i),
          destinations: const [
            NavigationDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home_rounded),
                label: 'Home'),
            NavigationDestination(
                icon: Icon(Icons.people_outline),
                selectedIcon: Icon(Icons.people_rounded),
                label: 'Customers'),
            NavigationDestination(
                icon: Icon(Icons.smartphone_outlined),
                selectedIcon: Icon(Icons.smartphone_rounded),
                label: 'Devices'),
            NavigationDestination(
                icon: Icon(Icons.vpn_key_outlined),
                selectedIcon: Icon(Icons.vpn_key_rounded),
                label: 'Keys'),
          ],
        ),
      ),
    );
  }
}
