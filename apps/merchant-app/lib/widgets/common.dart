import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../theme.dart';

/// Status pill with a colored dot.
class StatusPill extends StatelessWidget {
  final String label;
  final Color color;
  const StatusPill({super.key, required this.label, required this.color});

  factory StatusPill.device(String status) {
    switch (status) {
      case 'restricted':
        return const StatusPill(label: 'Restricted', color: Brand.crit);
      case 'review':
        return const StatusPill(label: 'Under Review', color: Brand.warn);
      default:
        return const StatusPill(label: 'Active', color: Brand.good);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(8, 4, 10, 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
          Text(label,
              style: TextStyle(
                  color: color, fontSize: 12, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/// Small metric tile.
class StatTile extends StatelessWidget {
  final IconData icon;
  final Color tone;
  final String value;
  final String label;
  final String? sub;
  const StatTile({
    super.key,
    required this.icon,
    required this.tone,
    required this.value,
    required this.label,
    this.sub,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: tone.withOpacity(0.13),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(icon, size: 18, color: tone),
            ),
            const SizedBox(height: 12),
            Text(value,
                style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    color: Brand.ink,
                    height: 1)),
            const SizedBox(height: 6),
            Text(label,
                style: const TextStyle(
                    fontSize: 12.5,
                    color: Brand.muted,
                    fontWeight: FontWeight.w600)),
            if (sub != null) ...[
              const SizedBox(height: 2),
              Text(sub!,
                  style: const TextStyle(fontSize: 11.5, color: Brand.faint)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Donut ring showing a value out of a total (used for key quota).
class RingChart extends StatelessWidget {
  final int value;
  final int total;
  final String caption;
  final Color color;
  const RingChart({
    super.key,
    required this.value,
    required this.total,
    required this.caption,
    this.color = Brand.gold,
  });

  @override
  Widget build(BuildContext context) {
    final frac = total == 0 ? 0.0 : value / total;
    return SizedBox(
      width: 150,
      height: 150,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(
            size: const Size(150, 150),
            painter: _RingPainter(frac, color),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('$value',
                  style: const TextStyle(
                      fontSize: 34,
                      fontWeight: FontWeight.w800,
                      color: Brand.ink,
                      height: 1)),
              const SizedBox(height: 4),
              Text(caption,
                  style: const TextStyle(fontSize: 11.5, color: Brand.faint)),
            ],
          ),
        ],
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  final double frac;
  final Color color;
  _RingPainter(this.frac, this.color);

  @override
  void paint(Canvas canvas, Size size) {
    const stroke = 14.0;
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (size.width - stroke) / 2;
    final bg = Paint()
      ..color = Brand.line
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke;
    final fg = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;
    canvas.drawCircle(center, radius, bg);
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -math.pi / 2,
      2 * math.pi * frac.clamp(0.0, 1.0),
      false,
      fg,
    );
  }

  @override
  bool shouldRepaint(covariant _RingPainter old) =>
      old.frac != frac || old.color != color;
}

/// Section header row.
class SectionHeader extends StatelessWidget {
  final String title;
  final Widget? trailing;
  const SectionHeader(this.title, {super.key, this.trailing});
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(2, 4, 2, 10),
      child: Row(
        children: [
          Text(title,
              style: const TextStyle(
                  fontSize: 15, fontWeight: FontWeight.w700, color: Brand.ink)),
          const Spacer(),
          if (trailing != null) trailing!,
        ],
      ),
    );
  }
}

String fmtDate(String iso) {
  try {
    return DateFormat('dd MMM yyyy, HH:mm').format(DateTime.parse(iso).toLocal());
  } catch (_) {
    return iso;
  }
}

void showSnack(BuildContext context, String message, {bool error = false}) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Text(message),
      backgroundColor: error ? Brand.crit : Brand.ink,
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
    ));
}

/// "Coming soon" banner for EMI features.
class ComingSoonBanner extends StatelessWidget {
  final String text;
  const ComingSoonBanner({super.key, required this.text});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(13),
      decoration: BoxDecoration(
        color: Brand.gold.withOpacity(0.1),
        borderRadius: BorderRadius.circular(11),
        border: Border.all(color: Brand.gold.withOpacity(0.25)),
      ),
      child: Row(
        children: [
          const Icon(Icons.schedule, size: 18, color: Brand.goldInk),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('EMI finance module — coming soon',
                    style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w700,
                        color: Brand.goldInk)),
                Text(text,
                    style: const TextStyle(fontSize: 11.5, color: Brand.muted)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
