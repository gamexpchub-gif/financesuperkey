import 'package:flutter/material.dart';
import '../api.dart';
import '../models.dart';
import '../theme.dart';
import '../widgets/common.dart';

class DeviceDetailScreen extends StatefulWidget {
  final Device device;
  const DeviceDetailScreen({super.key, required this.device});
  @override
  State<DeviceDetailScreen> createState() => _DeviceDetailScreenState();
}

class _DeviceDetailScreenState extends State<DeviceDetailScreen> {
  Future<Map<String, dynamic>>? _timeline;
  late String _status;

  @override
  void initState() {
    super.initState();
    _status = widget.device.status;
    _timeline = Api.instance.deviceTimeline(widget.device.id);
  }

  List<TimelineEntry> _entries(Map<String, dynamic> t) {
    final out = <TimelineEntry>[];
    final enr = t['enrollment'];
    if (enr != null) {
      out.add(TimelineEntry('Enrolled', 'QR Device Owner provisioning',
          fmtDate(enr['createdAt'] ?? ''), 'good'));
      if (enr['consentAt'] != null) {
        out.add(TimelineEntry(
            'Consent & signature captured',
            'Version ${enr['consentTextVersion'] ?? '—'}',
            fmtDate(enr['consentAt']),
            'good'));
      }
    }
    final reqs = (t['requests'] as List?) ?? [];
    for (final r in reqs) {
      final type = r['type'] == 'restrict' ? 'Restriction' : 'Release';
      final st = r['status'];
      out.add(TimelineEntry('$type $st', r['reason'] ?? '',
          fmtDate(r['reviewedAt'] ?? r['createdAt'] ?? ''),
          st == 'rejected' ? 'warn' : (r['type'] == 'restrict' ? 'crit' : 'good')));
    }
    return out.reversed.toList();
  }

  Future<void> _requestRestriction(String type) async {
    final reason = await showDialog<String>(
      context: context,
      builder: (_) => _ReasonDialog(
        title: type == 'restrict' ? 'Request restriction' : 'Request release',
      ),
    );
    if (reason == null || reason.trim().length < 3) return;
    try {
      await Api.instance.requestRestriction(widget.device.id, type, reason.trim());
      if (!mounted) return;
      showSnack(context, 'Request submitted to admin for review');
      setState(() {
        _timeline = Api.instance.deviceTimeline(widget.device.id);
      });
    } catch (e) {
      if (mounted) showSnack(context, '$e', error: true);
    }
  }

  Color _toneColor(String tone) => switch (tone) {
        'good' => Brand.good,
        'warn' => Brand.warn,
        'crit' => Brand.crit,
        _ => Brand.navyInk,
      };

  @override
  Widget build(BuildContext context) {
    final d = widget.device;
    final restricted = _status == 'restricted';
    return Scaffold(
      appBar: AppBar(
        title: Text(d.customer?.fullName ?? 'Device'),
        actions: [Padding(padding: const EdgeInsets.only(right: 12), child: Center(child: StatusPill.device(_status)))],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: Column(
                children: [
                  _kvRow('Model', d.model),
                  const Divider(height: 1),
                  _kvRow('IMEI', d.imeiMasked),
                  const Divider(height: 1),
                  _kvRow('Phone', d.customer?.phone ?? '—'),
                  const Divider(height: 1),
                  _kvRow('Enrolled', fmtDate(d.createdAt)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          const ComingSoonBanner(
              text: 'Installment & payment data will appear here.'),
          const SizedBox(height: 20),
          const SectionHeader('Activity timeline'),
          FutureBuilder<Map<String, dynamic>>(
            future: _timeline,
            builder: (context, snap) {
              if (!snap.hasData) {
                return const Padding(
                  padding: EdgeInsets.all(20),
                  child: Center(child: CircularProgressIndicator()),
                );
              }
              final entries = _entries(snap.data!);
              return Column(
                children: [
                  for (var i = 0; i < entries.length; i++)
                    _TimelineRow(
                      entry: entries[i],
                      color: _toneColor(entries[i].tone),
                      last: i == entries.length - 1,
                    ),
                ],
              );
            },
          ),
          const SizedBox(height: 20),
          if (restricted)
            OutlinedButton.icon(
              onPressed: () => _requestRestriction('release'),
              icon: const Icon(Icons.lock_open, size: 18),
              label: const Text('Request release'),
              style: OutlinedButton.styleFrom(
                foregroundColor: Brand.good,
                minimumSize: const Size.fromHeight(48),
                side: const BorderSide(color: Brand.good),
              ),
            )
          else
            OutlinedButton.icon(
              onPressed: () => _requestRestriction('restrict'),
              icon: const Icon(Icons.lock_outline, size: 18),
              label: const Text('Request restriction'),
              style: OutlinedButton.styleFrom(
                foregroundColor: Brand.crit,
                minimumSize: const Size.fromHeight(48),
                side: const BorderSide(color: Brand.crit),
              ),
            ),
          const SizedBox(height: 10),
          const Text(
            'Merchants submit a request; the final decision and policy are applied by the admin on the server. Emergency calls stay on when restricted.',
            style: TextStyle(fontSize: 11.5, color: Brand.faint, height: 1.5),
          ),
        ],
      ),
    );
  }

  Widget _kvRow(String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        child: Row(
          children: [
            Text(k, style: const TextStyle(color: Brand.muted, fontSize: 13)),
            const Spacer(),
            Text(v,
                style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 13,
                    color: Brand.ink)),
          ],
        ),
      );
}

class _TimelineRow extends StatelessWidget {
  final TimelineEntry entry;
  final Color color;
  final bool last;
  const _TimelineRow(
      {required this.entry, required this.color, required this.last});
  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Column(
            children: [
              Container(
                width: 16,
                height: 16,
                decoration: BoxDecoration(
                  color: Brand.surface,
                  shape: BoxShape.circle,
                  border: Border.all(color: color, width: 2),
                ),
                child: Center(
                  child: Container(
                      width: 7,
                      height: 7,
                      decoration:
                          BoxDecoration(color: color, shape: BoxShape.circle)),
                ),
              ),
              if (!last)
                Expanded(child: Container(width: 2, color: Brand.line)),
            ],
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: last ? 0 : 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(entry.title,
                      style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 13,
                          color: Brand.ink)),
                  if (entry.subtitle.isNotEmpty)
                    Text(entry.subtitle,
                        style:
                            const TextStyle(color: Brand.muted, fontSize: 12)),
                  Text(entry.time,
                      style: const TextStyle(color: Brand.faint, fontSize: 11)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ReasonDialog extends StatefulWidget {
  final String title;
  const _ReasonDialog({required this.title});
  @override
  State<_ReasonDialog> createState() => _ReasonDialogState();
}

class _ReasonDialogState extends State<_ReasonDialog> {
  final _c = TextEditingController();
  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        controller: _c,
        autofocus: true,
        maxLines: 3,
        decoration: const InputDecoration(hintText: 'Reason…'),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel')),
        FilledButton(
          style: FilledButton.styleFrom(minimumSize: const Size(88, 42)),
          onPressed: () => Navigator.pop(context, _c.text),
          child: const Text('Submit'),
        ),
      ],
    );
  }
}
