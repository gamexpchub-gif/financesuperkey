import 'package:flutter/material.dart';
import '../api.dart';
import '../models.dart';
import '../theme.dart';
import '../widgets/common.dart';

class KeysScreen extends StatefulWidget {
  const KeysScreen({super.key});
  @override
  State<KeysScreen> createState() => _KeysScreenState();
}

class _KeysScreenState extends State<KeysScreen> {
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

  Future<void> _requestPackage() async {
    final qty = await showModalBottomSheet<int>(
      context: context,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => const _PackageSheet(),
    );
    if (qty == null) return;
    try {
      await Api.instance.requestKeys(qty);
      if (!mounted) return;
      showSnack(context, 'Purchase request sent to Super Admin');
    } catch (e) {
      if (mounted) showSnack(context, '$e', error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('License Keys')),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: FutureBuilder<Overview>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return Center(
                  child: Text('${snap.error}',
                      style: const TextStyle(color: Brand.muted)));
            }
            final o = snap.data!;
            final k = o.keys;
            final pct = k.total == 0 ? 0 : ((k.consumed / k.total) * 100).round();
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 22),
                    child: Column(
                      children: [
                        RingChart(
                          value: k.consumed,
                          total: k.total,
                          caption: '$pct% used',
                        ),
                        const SizedBox(height: 10),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          child: Row(
                            children: [
                              _kq('Total', k.total, Brand.navyInk),
                              _kq('Available', k.available, Brand.good),
                              _kq('In use', k.consumed, Brand.goldInk),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                const SectionHeader('Devices'),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Row(
                      children: [
                        _dstat('${o.devicesActive}', 'Open', Brand.good),
                        _vline(),
                        _dstat('${o.devicesReview}', 'Review', Brand.warn),
                        _vline(),
                        _dstat('${o.devicesRestricted}', 'Locked', Brand.crit),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                const SectionHeader('Buy more keys'),
                _PackageCard(
                  title: '100 Keys',
                  subtitle: 'Starter pack',
                  price: '৳5,000',
                  onTap: _requestPackage,
                ),
                const SizedBox(height: 10),
                _PackageCard(
                  title: '500 Keys',
                  subtitle: 'Business pack · 10% off',
                  price: '৳22,500',
                  featured: true,
                  onTap: _requestPackage,
                ),
                const SizedBox(height: 18),
                FilledButton.icon(
                  onPressed: _requestPackage,
                  icon: const Icon(Icons.add),
                  label: const Text('Request key package'),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Key purchases are approved from the Super Admin panel.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 11.5, color: Brand.faint),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _dstat(String value, String label, Color c) => Expanded(
        child: Column(
          children: [
            Text(value, style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: c)),
            const SizedBox(height: 2),
            Text(label, style: const TextStyle(fontSize: 12, color: Brand.muted)),
          ],
        ),
      );

  Widget _vline() => Container(width: 1, height: 36, color: Brand.line);

  Widget _kq(String label, int value, Color c) => Expanded(
        child: Column(
          children: [
            Text('$value',
                style: TextStyle(
                    fontSize: 20, fontWeight: FontWeight.w800, color: c)),
            Text(label,
                style: const TextStyle(fontSize: 11.5, color: Brand.muted)),
          ],
        ),
      );
}

class _PackageCard extends StatelessWidget {
  final String title, subtitle, price;
  final bool featured;
  final VoidCallback onTap;
  const _PackageCard({
    required this.title,
    required this.subtitle,
    required this.price,
    required this.onTap,
    this.featured = false,
  });
  @override
  Widget build(BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: featured ? Brand.gold : Brand.line),
      ),
      child: ListTile(
        onTap: onTap,
        leading: Container(
          width: 46,
          height: 46,
          decoration: BoxDecoration(
            color: Brand.gold.withOpacity(0.12),
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(Icons.vpn_key, color: Brand.goldInk),
        ),
        title: Text(title,
            style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text(subtitle),
        trailing: Text(price,
            style: const TextStyle(
                fontWeight: FontWeight.w800, color: Brand.ink)),
      ),
    );
  }
}

class _PackageSheet extends StatelessWidget {
  const _PackageSheet();
  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Choose a package',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            const SizedBox(height: 16),
            for (final p in const [
              [100, '100 Keys — ৳5,000'],
              [500, '500 Keys — ৳22,500'],
              [1000, '1000 Keys — ৳40,000'],
            ])
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: OutlinedButton(
                  style: OutlinedButton.styleFrom(
                      minimumSize: const Size.fromHeight(50)),
                  onPressed: () => Navigator.pop(context, p[0] as int),
                  child: Text(p[1] as String),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
