import 'package:flutter/material.dart';
import '../api.dart';
import '../models.dart';
import '../theme.dart';
import 'add_customer_screen.dart';
import 'enroll_screen.dart';

class CustomersScreen extends StatefulWidget {
  const CustomersScreen({super.key});
  @override
  State<CustomersScreen> createState() => _CustomersScreenState();
}

class _CustomersScreenState extends State<CustomersScreen> {
  Future<Paged<Customer>>? _future;
  String _q = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    setState(() => _future = Api.instance.customers(q: _q));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Customers')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final added = await Navigator.of(context).push<bool>(
              MaterialPageRoute(builder: (_) => const AddCustomerScreen()));
          if (added == true) _load();
        },
        backgroundColor: Brand.navy,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('Add'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              decoration: const InputDecoration(
                hintText: 'Search by name or phone',
                prefixIcon: Icon(Icons.search, size: 20),
              ),
              onChanged: (v) {
                _q = v;
                _load();
              },
            ),
          ),
          Expanded(
            child: FutureBuilder<Paged<Customer>>(
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
                      child: Text('No customers found',
                          style: TextStyle(color: Brand.faint)));
                }
                return ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 90),
                  itemCount: rows.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) => _CustomerCard(customer: rows[i]),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _CustomerCard extends StatelessWidget {
  final Customer customer;
  const _CustomerCard({required this.customer});

  String get _initials {
    final parts = customer.fullName.trim().split(RegExp(r'\s+'));
    final a = parts.isNotEmpty && parts[0].isNotEmpty ? parts[0][0] : '';
    final b = parts.length > 1 && parts[1].isNotEmpty ? parts[1][0] : '';
    return (a + b).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(13),
        child: Row(
          children: [
            CircleAvatar(
              radius: 22,
              backgroundColor: Brand.navy,
              child: Text(_initials,
                  style: const TextStyle(
                      color: Colors.white, fontWeight: FontWeight.w700)),
            ),
            const SizedBox(width: 13),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(customer.fullName,
                      style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 14,
                          color: Brand.ink)),
                  const SizedBox(height: 2),
                  Text(customer.phone,
                      style: const TextStyle(
                          color: Brand.muted, fontSize: 12.5)),
                ],
              ),
            ),
            TextButton.icon(
              onPressed: () {
                Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => EnrollScreen(customer: customer)));
              },
              icon: const Icon(Icons.add_circle_outline, size: 18),
              label: const Text('Enroll'),
              style: TextButton.styleFrom(foregroundColor: Brand.goldInk),
            ),
          ],
        ),
      ),
    );
  }
}
