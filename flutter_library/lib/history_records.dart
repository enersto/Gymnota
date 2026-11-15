import 'package:flutter/material.dart';

import 'glass.dart';

String setValue(
  Map<String, dynamic> task,
  Map<String, dynamic> set,
  Map<String, String> labels, {
  bool right = false,
}) {
  final value = '${set[right ? 'rightWeight' : 'weightOrDuration'] ?? '—'}';
  final reps = '${set[right ? 'rightReps' : 'reps'] ?? '—'}';
  switch (task['logType']) {
    case 1:
      return double.tryParse(value) == null
          ? value
          : '$value ${labels['flutter_minutes'] ?? 'min'}';
    case 2:
      return '$reps ${labels['label_reps'] ?? ''}';
    default:
      return '$value kg × $reps';
  }
}

String formatWeight(num value) =>
    value.toStringAsFixed(2).replaceFirst(RegExp(r'\.?0+$'), '');

typedef HistoryRecord = Map<String, dynamic>;

/// Flat, lazy date/card rows: no per-row blur or nested scrollables.
class HistoryRecordsView extends StatelessWidget {
  final List<HistoryRecord> tasks, weights;
  final Map<String, String> labels;
  final String query;
  const HistoryRecordsView({
    super.key,
    required this.tasks,
    required this.weights,
    required this.labels,
    this.query = '',
  });
  @override
  Widget build(BuildContext context) {
    final grouped = <String, List<HistoryRecord>>{};
    final dailyWeights = <String, HistoryRecord>{};
    for (final task in tasks) {
      grouped.putIfAbsent('${task['date']}', () => []).add(task);
    }
    for (final weight in weights) {
      dailyWeights.putIfAbsent('${weight['date']}', () => weight);
    }
    final dates = {...grouped.keys, ...dailyWeights.keys}.toList()
      ..sort((a, b) => b.compareTo(a));
    final rows =
        <({String date, HistoryRecord? task, bool header, bool empty})>[];
    final needle = query.trim().toLowerCase();
    for (final date in dates) {
      final day = grouped[date] ?? [];
      if (needle.isNotEmpty &&
          !date.contains(needle) &&
          !day.any((t) => '${t['name']}'.toLowerCase().contains(needle)))
        continue;
      rows.add((date: date, task: null, header: true, empty: false));
      if (day.isEmpty)
        rows.add((date: date, task: null, header: false, empty: true));
      for (final task in day) {
        rows.add((date: date, task: task, header: false, empty: false));
      }
    }
    if (rows.isEmpty)
      return LayoutBuilder(
        builder: (_, constraints) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(
              height: constraints.maxHeight,
              child: Center(child: Text(labels['no_history'] ?? '')),
            ),
          ],
        ),
      );
    return ListView.builder(
      key: const PageStorageKey('history-record-list'),
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
      itemCount: rows.length,
      itemBuilder: (_, index) {
        final row = rows[index];
        if (row.header)
          return Padding(
            padding: EdgeInsets.only(top: index == 0 ? 8 : 16, bottom: 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    row.date,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurface
                          .withValues(alpha: .8),
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                if (dailyWeights[row.date] case final weight?)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.orange.withValues(alpha: .1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.monitor_weight_outlined,
                          size: 14,
                          color: Colors.orange,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${formatWeight(weight['weight'] as num)} kg',
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(
                                color: Colors.orange,
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          );
        if (row.empty)
          return Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              labels['history_no_train'] ?? '',
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: Theme.of(context).colorScheme.onSurface),
            ),
          );
        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: HistoryRecordCard(
            key: ValueKey('history-task-${row.task!['id']}'),
            task: row.task!,
            labels: labels,
          ),
        );
      },
    );
  }
}

class HistoryRecordCard extends StatelessWidget {
  final HistoryRecord task;
  final Map<String, String> labels;
  const HistoryRecordCard({
    super.key,
    required this.task,
    required this.labels,
  });
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context), l = labels;
    final sets = (task['sets'] as List? ?? [])
        .map((s) => Map<String, dynamic>.from(s as Map))
        .toList();
    final unilateral = task['unilateral'] == true;
    final valueStyle = theme.textTheme.bodyMedium?.copyWith(
      color: theme.colorScheme.onSurface,
    );
    return Glass(
      radius: 16,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            LayoutBuilder(
              builder: (_, constraints) {
                final name = Text(
                  '${task['name'] ?? ''}',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                );
                final tag = Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 4,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.tertiaryContainer.withValues(
                      alpha: .7,
                    ),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    l['tag_uni'] ?? '',
                    style: theme.textTheme.labelSmall?.copyWith(
                      fontSize: 10,
                      color: theme.colorScheme.onSurface,
                    ),
                  ),
                );
                final done = ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 110),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.check_circle_outline,
                        size: 14,
                        color: theme.colorScheme.onSurface,
                      ),
                      const SizedBox(width: 4),
                      Flexible(
                        child: Text(
                          l['btn_done'] ?? '',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurface,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ],
                  ),
                );
                if (constraints.maxWidth < 300 &&
                    MediaQuery.textScalerOf(context).scale(1) > 1.3) {
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      name,
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 4,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [if (unilateral) tag, done],
                      ),
                    ],
                  );
                }
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (unilateral)
                      Padding(
                        padding: const EdgeInsets.only(right: 8, top: 3),
                        child: tag,
                      ),
                    Expanded(child: name),
                    const SizedBox(width: 8),
                    done,
                  ],
                );
              },
            ),
            Divider(
              height: 24,
              color: theme.colorScheme.outlineVariant.withValues(alpha: .3),
            ),
            if (sets.isEmpty)
              Text(
                '${task['target'] ?? ''}${'${task['actualWeight'] ?? ''}'.isEmpty ? '' : ' @ ${task['actualWeight']}'}',
                style: valueStyle,
              )
            else if (unilateral)
              Table(
                columnWidths: const {
                  0: FixedColumnWidth(32),
                  1: FlexColumnWidth(),
                  2: FlexColumnWidth(),
                },
                defaultVerticalAlignment: TableCellVerticalAlignment.middle,
                children: [
                  for (final (index, set) in sets.indexed)
                    TableRow(
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 6),
                          child: Text(
                            '#${set['setNumber'] ?? index + 1}',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onSurface.withValues(
                                alpha: .6,
                              ),
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(0, 6, 8, 6),
                          child: Text(
                            '${l['label_side_left'] ?? ''}: ${setValue(task, set, l)}',
                            style: valueStyle,
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 6),
                          child: Text(
                            '${l['label_side_right'] ?? ''}: ${setValue(task, set, l, right: true)}',
                            style: valueStyle,
                          ),
                        ),
                      ],
                    ),
                ],
              )
            else if (task['category'] == 'STRENGTH' && task['logType'] == 0)
              Text(
                sets.map((s) => setValue(task, s, l)).join('  |  '),
                style: valueStyle,
              )
            else
              for (final set in sets)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.check,
                        size: 16,
                        color: theme.colorScheme.onSurface,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(setValue(task, set, l), style: valueStyle),
                      ),
                    ],
                  ),
                ),
          ],
        ),
      ),
    );
  }
}
