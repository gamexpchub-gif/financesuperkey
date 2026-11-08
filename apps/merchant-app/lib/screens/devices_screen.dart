import 'package:flutter/material.dart';
import '../api.dart';
import '../models.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'device_detail_screen.dart';

class DevicesScreen extends StatefulWidget {
  const DevicesScreen({super.key});
  @override
  State<DevicesScreen> createState() => _DevicesScreenState();
}

class _DevicesScreenState extends State<DevicesScreen> {
  Future<Paged<Device>>? _future;
  String _status = '';
  String _q = '';

  static const _filters = [
    ['', 'All'],
    ['active', 'Active'],
    ['review', 'Review'],
    ['restricted', 'Restricted'],
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    setState(() => _future = Api.instance.devices(status: _status, q: _q));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Devices')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 6),
            child: TextField(
              decoration: const InputDecoration(
                hintText: 'Model, IMEI or customer',
                prefixIcon: Icon(Icons.search, size: 20),
              ),
              onChanged: (v) {
                _q = v;
                _load();
              },
            ),
          ),
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              children: _filters.map((f) {
                final on = _status == f[0];
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(f[1]),
                    selected: on,
                    onSelected: (_) {
                      _status = f[0];
                      _load();
                    },
                    selectedColor: Brand.ink,
                    labelStyle: TextStyle(
                        color: on ? Colors.white : Brand.muted,
                        fontWeight: FontWeight.w600,
                        fontSize: 12.5),
                    backgroundColor: Brand.surface,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(20),
                      side: const BorderSide(color: Brand.line),
                    ),
                    showCheckmark: false,
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: FutureBuilder<Paged<Device>>(
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
                final rows = snap.data!.data;
                if (rows.isEmpty) {
                  return const Center(
                      child: Text('No devices found',
                          style: TextStyle(color: Brand.faint)));
                }
                return ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 20),
                  itemCount: rows.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) {
                    final d = rows[i];
                    return Card(
                      child: InkWell(
                        borderRadius: BorderRadius.circular(14),
                        onTap: () async {
                          await Navigator.of(context).push(MaterialPageRoute(
                              builder: (_) => DeviceDetailScreen(device: d)));
                          _load();
                        },
                        child: Padding(
                          padding: const EdgeInsets.all(13),
                          child: Row(
                            children: [
                              Container(
                                width: 42,
                                height: 42,
                                decoration: BoxDecoration(
                                  color: Brand.bg,
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: const Icon(Icons.smartphone,
                                    color: Brand.muted, size: 22),
                              ),
                              const SizedBox(width: 13),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(d.customer?.fullName ?? '—',
                                        style: const TextStyle(
                                            fontWeight: FontWeight.w700,
                                            fontSize: 14,
                                            color: Brand.ink)),
                                    const SizedBox(height: 2),
                                    Text('${d.model} · ${d.imeiMasked}',
                                        style: const TextStyle(
                                            color: Brand.muted,
                                            fontSize: 12)),
                                  ],
                                ),
                              ),
                              StatusPill.device(d.status),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
