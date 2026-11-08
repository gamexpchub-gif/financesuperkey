import 'package:flutter/material.dart';
import '../api.dart';
import '../models.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'login_screen.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});
  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  Future<Overview>? _future;

  @override
  void initState() {
    super.initState();
    _future = Api.instance.overview();
  }

  Future<void> _refresh() async {
    setState(() => _future = Api.instance.overview());
    await _future;
  }

  Future<void> _logout() async {
    await Api.instance.logout();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Dashboard'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout_rounded, size: 20),
            onPressed: _logout,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: FutureBuilder<Overview>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return _ErrorView(message: '${snap.error}', onRetry: _refresh);
            }
            final o = snap.data!;
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _KeyCard(keys: o.keys),
                const SizedBox(height: 18),
                const SectionHeader('Device status'),
                Row(
                  children: [
                    Expanded(
                        child: StatTile(
                            icon: Icons.check_circle_outline,
                            tone: Brand.good,
                            value: '${o.devicesActive}',
                            label: 'Active')),
                    const SizedBox(width: 12),
                    Expanded(
                        child: StatTile(
                            icon: Icons.schedule,
                            tone: Brand.warn,
                            value: '${o.devicesReview}',
                            label: 'Review')),
                    const SizedBox(width: 12),
                    Expanded(
                        child: StatTile(
                            icon: Icons.lock_outline,
                            tone: Brand.crit,
                            value: '${o.devicesRestricted}',
                            label: 'Restricted')),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                        child: StatTile(
                            icon: Icons.people_outline,
                            tone: Brand.navyInk,
                            value: '${o.customersTotal}',
                            label: 'Customers')),
                    const SizedBox(width: 12),
                    Expanded(
                        child: StatTile(
                            icon: Icons.smartphone_outlined,
                            tone: Brand.navyInk,
                            value: '${o.devicesTotal}',
                            label: 'Total Devices')),
                  ],
                ),
                const SizedBox(height: 18),
                const ComingSoonBanner(
                    text:
                        'Installment calc, collection & ledger arrive in the finance module.'),
                const SizedBox(height: 18),
                const Text(
                  'All restrictions require lawful ownership, customer consent and admin approval. Emergency calls always stay available.',
                  style: TextStyle(fontSize: 11.5, color: Brand.faint, height: 1.5),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _KeyCard extends StatelessWidget {
  final KeySummary keys;
  const _KeyCard({required this.keys});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Brand.navy, Color(0xFF1A2C5C)],
        ),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          _MiniRing(value: keys.available, total: keys.total),
          const SizedBox(width: 18),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('License keys',
                    style: TextStyle(
                        color: Colors.white70,
                        fontSize: 12,
                        fontWeight: FontWeight.w600)),
                const SizedBox(height: 10),
                _kv('Total', keys.total, Colors.white),
                _kv('Available', keys.available, const Color(0xFF45C47C)),
                _kv('In use', keys.consumed, Brand.gold),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _kv(String label, int value, Color c) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(
          children: [
            Container(
                width: 9,
                height: 9,
                decoration: BoxDecoration(
                    color: c, borderRadius: BorderRadius.circular(2))),
            const SizedBox(width: 9),
            Text(label,
                style: const TextStyle(color: Colors.white70, fontSize: 13)),
            const Spacer(),
            Text('$value',
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.w700)),
          ],
        ),
      );
}

class _MiniRing extends StatelessWidget {
  final int value, total;
  const _MiniRing({required this.value, required this.total});
  @override
  Widget build(BuildContext context) {
    final frac = total == 0 ? 0.0 : value / total;
    return SizedBox(
      width: 96,
      height: 96,
      child: Stack(
        alignment: Alignment.center,
        children: [
          SizedBox(
            width: 96,
            height: 96,
            child: CircularProgressIndicator(
              value: frac.clamp(0.0, 1.0),
              strokeWidth: 10,
              backgroundColor: Colors.white24,
              valueColor: const AlwaysStoppedAnimation(Brand.gold),
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('$value',
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 22,
                      fontWeight: FontWeight.w800)),
              const Text('left',
                  style: TextStyle(color: Colors.white60, fontSize: 10)),
            ],
          ),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  final Future<void> Function() onRetry;
  const _ErrorView({required this.message, required this.onRetry});
  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        const SizedBox(height: 120),
        const Icon(Icons.cloud_off_rounded, size: 40, color: Brand.faint),
        const SizedBox(height: 14),
        Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Text(message,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Brand.muted, fontSize: 13)),
          ),
        ),
        const SizedBox(height: 16),
        Center(
          child: OutlinedButton(
              onPressed: onRetry, child: const Text('Retry')),
        ),
      ],
    );
  }
}
