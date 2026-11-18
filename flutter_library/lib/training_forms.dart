import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'glass.dart';
import 'main.dart' show Json, bridge, objects;

Future<Map<String, num>?> editBodyMetrics(
  BuildContext context,
  Map<String, String> labels,
  Map<Object?, Object?> profile, {
  required bool recordWeight,
}) async {
  final age = profile['age'] as num? ?? 0;
  final height = profile['height'] as num? ?? 0;
  final showProfile = !recordWeight || height <= 0 || age < 22;
  final values = <String, String>{
    if (age > 0) 'age': age.toString(),
    if (height > 0) 'height': height.toString(),
  };
  var gender = (profile['gender'] as num?)?.toInt() ?? 0;
  final form = GlobalKey<FormState>();
  num? number(String field) =>
      num.tryParse((values[field] ?? '').trim().replaceAll(',', '.'));
  Widget field(String key, String caption) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: TextFormField(
      initialValue: values[key],
      keyboardType: TextInputType.numberWithOptions(decimal: key != 'age'),
      decoration: InputDecoration(labelText: labels[caption]),
      onChanged: (value) => values[key] = value,
      validator: (value) {
        if ((value ?? '').trim().isEmpty && key != 'weight') return null;
        final parsed = number(key);
        return parsed != null &&
                parsed.isFinite &&
                parsed > 0 &&
                (key != 'age' || parsed == parsed.toInt())
            ? null
            : labels['flutter_error'];
      },
    ),
  );
  return showDialog<Map<String, num>>(
    context: context,
    builder: (dialog) => StatefulBuilder(
      builder: (context, setState) => GlassAlertDialog(
        wide: true,
        title: Text(
          labels[showProfile
                  ? 'dialog_profile_title'
                  : 'dialog_weight_title'] ??
              '',
        ),
        content: SingleChildScrollView(
          child: Form(
            key: form,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (recordWeight) field('weight', 'label_weight_kg'),
                if (showProfile) ...[
                  field('age', 'label_age'),
                  field('height', 'label_height'),
                  Text(labels['label_gender'] ?? ''),
                  Wrap(
                    spacing: 8,
                    children: [
                      for (final entry in const [
                        'gender_male',
                        'gender_female',
                      ].asMap().entries)
                        ChoiceChip(
                          label: Text(labels[entry.value] ?? ''),
                          selected: gender == entry.key,
                          onSelected: (_) => setState(() => gender = entry.key),
                        ),
                    ],
                  ),
                ],
                if (recordWeight) ...[
                  const SizedBox(height: 12),
                  Text(labels['weekly_metrics_subtitle'] ?? ''),
                  field('fat', 'label_body_fat_kg'),
                  field('muscle', 'label_skeletal_muscle_kg'),
                  field('water', 'label_body_water'),
                  field('waist', 'label_waist_circumference'),
                  field('hip', 'label_hip_circumference'),
                ],
              ],
            ),
          ),
        ),
        actions: [
          GlassAction(
            label: labels['btn_cancel'] ?? '',
            onPressed: () => Navigator.pop(dialog),
          ),
          GlassAction(
            label: labels['btn_save'] ?? '',
            onPressed: () {
              if (form.currentState?.validate() != true) return;
              Navigator.pop(dialog, <String, num>{
                'gender': gender,
                for (final key in values.keys)
                  if (number(key) case final value?) key: value,
              });
            },
          ),
        ],
      ),
    ),
  );
}

Future<String?> editImportCsv(
  BuildContext context,
  Map<String, String> labels,
) {
  var text = labels['import_csv_template'] ?? '';
  return showDialog<String>(
    context: context,
    builder: (dialog) => GlassAlertDialog(
      wide: true,
      title: Text(labels['import_dialog_title'] ?? ''),
      content: TextFormField(
        initialValue: text,
        onChanged: (value) => text = value,
        minLines: 3,
        maxLines: 8,
        keyboardType: TextInputType.multiline,
      ),
      actions: [
        GlassAction(
          label: labels['btn_cancel'] ?? '',
          onPressed: () => Navigator.pop(dialog),
        ),
        GlassAction(
          label: labels['import_btn'] ?? '',
          onPressed: () {
            if (text.trim().isNotEmpty) Navigator.pop(dialog, text);
          },
        ),
      ],
    ),
  );
}

Future<void> showKeyReference(
  BuildContext context,
  Map<String, String> labels,
) {
  const groups = {
    'section_body_part': [
      'part_chest',
      'part_back',
      'part_shoulders',
      'part_arms',
      'part_abs',
      'part_cardio',
      'part_hips',
      'part_thighs',
      'part_calves',
      'part_other',
    ],
    'section_equipment': [
      'equip_barbell',
      'equip_dumbbell',
      'equip_machine',
      'equip_cable',
      'equip_bodyweight',
      'equip_cardio_machine',
      'equip_kettlebell',
      'equip_smith_machine',
      'equip_resistance_band',
      'equip_medicine_ball',
      'equip_trx',
      'equip_bench',
      'equip_other',
    ],
  };
  final selected = <String>{};
  return showDialog<void>(
    context: context,
    builder: (dialog) => StatefulBuilder(
      builder: (context, setState) => GlassAlertDialog(
        wide: true,
        title: Text(labels['help_dialog_title'] ?? ''),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Wrap(
              children: [
                TextButton(
                  onPressed: () => setState(
                    () =>
                        selected.addAll(groups.values.expand((group) => group)),
                  ),
                  child: Text(labels['btn_select_all'] ?? ''),
                ),
                TextButton(
                  onPressed: () => setState(selected.clear),
                  child: Text(labels['btn_clear'] ?? ''),
                ),
              ],
            ),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                children: [
                  for (final group in groups.entries) ...[
                    Text(
                      labels[group.key] ?? '',
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    for (final key in group.value)
                      CheckboxListTile(
                        title: Text(labels[key] ?? key),
                        subtitle: Text(key),
                        value: selected.contains(key),
                        onChanged: (checked) => setState(() {
                          if (checked == true) {
                            selected.add(key);
                          } else {
                            selected.remove(key);
                          }
                        }),
                      ),
                  ],
                ],
              ),
            ),
          ],
        ),
        actions: [
          GlassAction(
            label: labels['btn_done'] ?? '',
            onPressed: () async {
              if (selected.isNotEmpty)
                await Clipboard.setData(
                  ClipboardData(text: selected.join(', ')),
                );
              if (dialog.mounted) Navigator.pop(dialog);
            },
          ),
        ],
      ),
    ),
  );
}

Future<String?> pickPersonalExercise(
  BuildContext context,
  List<Json> rows,
  Map<String, String> labels,
) => showExercisePicker(
  context,
  options: {for (final row in rows) '${row['id']}': row['name'] as String},
  groups: {
    for (final row in rows)
      '${row['id']}': (row['category'] as String).toUpperCase(),
  },
  groupLabels: {
    for (final key in ['STRENGTH', 'CARDIO', 'CORE'])
      key: labels['category_${key.toLowerCase()}'] ?? key,
  },
  details: {
    for (final row in rows)
      '${row['id']}': [
        row['bodyLabel'],
        row['equipment'],
      ].whereType<String>().where((part) => part.isNotEmpty).join(' · '),
  },
  label: labels['title_select_exercise'] ?? '',
  cancelLabel: labels['btn_cancel'] ?? '',
  search: (query) async {
    final ids = await bridge.invokeListMethod<int>('personalSearch', {
      'query': query,
    });
    return {for (final id in ids ?? <int>[]) '$id'};
  },
);

Future<void> showWeeklyRoutine(
  BuildContext context,
  Map<String, String> labels,
  List<Json> templates,
) => showDialog<void>(
  context: context,
  builder: (_) => _WeeklyRoutine(labels: labels, templates: templates),
);

class _WeeklyRoutine extends StatefulWidget {
  const _WeeklyRoutine({required this.labels, required this.templates});
  final Map<String, String> labels;
  final List<Json> templates;
  @override
  State<_WeeklyRoutine> createState() => _WeeklyRoutineState();
}

class _WeeklyRoutineState extends State<_WeeklyRoutine> {
  static const days = [1, 2, 3, 4, 5, 6, 7];
  int day = 1;
  List<Json> rows = [];
  bool loading = true, failed = false;

  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh([String action = 'list', int? id]) async {
    setState(() {
      loading = true;
      failed = false;
    });
    try {
      final outcome = await bridge.invokeMethod('weeklyRoutine', {
        'action': action,
        'day': day,
        'id': id,
      });
      if (mounted)
        setState(() {
          rows = objects(outcome);
          loading = false;
        });
    } catch (_) {
      if (mounted)
        setState(() {
          loading = false;
          failed = true;
        });
    }
  }

  Future<void> add() async {
    final selected = await pickPersonalExercise(
      context,
      widget.templates,
      widget.labels,
    );
    if (selected != null && mounted) await refresh('add', int.parse(selected));
  }

  @override
  Widget build(BuildContext context) {
    final l = widget.labels;
    return GlassAlertDialog(
      wide: true,
      title: Text(l['weekly_plan'] ?? ''),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          GlassScrollableSegments<int>(
            values: days,
            labels: [for (final number in days) l['week_$number'] ?? '$number'],
            selected: day,
            onChanged: (selected) {
              if (loading) return;
              setState(() => day = selected);
              refresh();
            },
          ),
          if (loading)
            const Padding(
              padding: EdgeInsets.all(16),
              child: CircularProgressIndicator(),
            )
          else if (failed)
            TextButton(
              onPressed: refresh,
              child: Text(l['flutter_retry'] ?? ''),
            )
          else if (rows.isEmpty)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(l['no_plan'] ?? ''),
            )
          else
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: rows.length,
                itemBuilder: (_, index) {
                  final item = rows[index];
                  return Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Glass(
                      definedEdge: true,
                      child: ListTile(
                        title: Text(item['name'] as String),
                        subtitle: Text(item['target'] as String? ?? ''),
                        trailing: IconButton(
                          icon: const Icon(Icons.delete_outline),
                          tooltip: l['btn_delete'],
                          onPressed: () => refresh('delete', item['id'] as int),
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
        ],
      ),
      actions: [
        GlassAction(
          label: l['btn_close'] ?? '',
          onPressed: () => Navigator.pop(context),
        ),
        if (!loading)
          GlassAction(
            label: l['btn_add'] ?? '',
            icon: Icons.add,
            onPressed: add,
          ),
      ],
    );
  }
}
