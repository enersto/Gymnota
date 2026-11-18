import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'glass.dart';
import 'main.dart' show Json, bridge, object, objects, ExerciseImage;

Future<bool?> showExerciseEditor(
  BuildContext context,
  Json config,
  Map<String, String> labels, {
  Json? initial,
}) {
  FocusManager.instance.primaryFocus?.unfocus();
  return showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (_) =>
        ExerciseEditor(config: config, labels: labels, initial: initial),
  );
}

class ExerciseEditor extends StatefulWidget {
  const ExerciseEditor({
    super.key,
    required this.config,
    required this.labels,
    this.initial,
  });
  final Json config;
  final Map<String, String> labels;
  final Json? initial;
  @override
  State<ExerciseEditor> createState() => _ExerciseEditorState();
}

class _ExerciseEditorState extends State<ExerciseEditor> {
  final form = GlobalKey<FormState>();
  late final name = TextEditingController(
    text: widget.initial?['name'] as String? ?? '',
  );
  late final target = TextEditingController(
    text: widget.initial?['target'] as String? ?? '',
  );
  late final instruction = TextEditingController(
    text: widget.initial?['instruction'] as String? ?? '',
  );
  late String category = widget.initial?['category'] as String? ?? 'STRENGTH';
  late String bodyPart = widget.initial?['bodyPart'] as String? ?? 'part_chest';
  late String equipment =
      widget.initial?['equipmentKey'] as String? ?? 'equip_barbell';
  late int logType = (widget.initial?['logType'] as num?)?.toInt() ?? 0;
  late bool unilateral = widget.initial?['unilateral'] == true;
  late String? photo = widget.initial?['imageUri'] as String?;
  late String? animation = widget.initial?['animationUri'] as String?;
  final draftPhotos = <String>{};
  bool busy = false;
  String? error;
  String l(String key) => widget.labels[key] ?? '';

  @override
  void dispose() {
    name.dispose();
    target.dispose();
    instruction.dispose();
    for (final uri in draftPhotos) {
      unawaited(
        bridge
            .invokeMethod('exerciseEditor', {
              'operation': 'discardPhoto',
              'uri': uri,
            })
            .catchError((_) => null),
      );
    }
    super.dispose();
  }

  Future<void> pickPhoto(String source) async {
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final uri = await bridge.invokeMethod<String>('exerciseEditor', {
        'operation': 'photo',
        'source': source,
      });
      if (uri != null) {
        if (!mounted) {
          await bridge.invokeMethod('exerciseEditor', {
            'operation': 'discardPhoto',
            'uri': uri,
          });
          return;
        }
        draftPhotos.add(uri);
        setState(() => photo = uri);
      }
    } catch (_) {
      if (mounted) setState(() => error = l('flutter_error'));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> chooseAnimation() async {
    FocusManager.instance.primaryFocus?.unfocus();
    final row = await showDialog<Json>(
      context: context,
      builder: (_) =>
          _AnimationPicker(config: widget.config, labels: widget.labels),
    );
    if (row == null || !mounted) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final draft = object(
        await bridge.invokeMethod('exerciseEditor', {
          'operation': 'catalogDraft',
          'sourceId': row['id'],
          'language': widget.config['language'] ?? 'zh',
        }),
      );
      if (mounted) setState(() => animation = draft['animationUri'] as String?);
    } catch (_) {
      if (mounted) setState(() => error = l('flutter_error'));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> save() async {
    if (!form.currentState!.validate()) return;
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await bridge.invokeMethod('exerciseEditor', {
        'operation': 'save',
        'id': widget.initial?['id'] ?? 0,
        'name': name.text,
        'target': target.text,
        'category': category,
        'bodyPart': bodyPart,
        'equipmentKey': equipment,
        'logType': logType,
        'unilateral': unilateral,
        'instruction': instruction.text,
        'imageUri': photo,
        'animationUri': animation,
      });
      if (photo != null) draftPhotos.remove(photo);
      if (mounted) Navigator.pop(context, true);
    } catch (_) {
      if (mounted) setState(() => error = l('flutter_error'));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> selectKey(
    String caption,
    String selected,
    List<Json> options,
    ValueChanged<String> changed,
  ) async {
    final value = await showDialog<String>(
      context: context,
      builder: (dialog) => GlassAlertDialog(
        title: Text(caption),
        wide: true,
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final row in options)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: GlassCommand(
                    label: row['label'] as String,
                    fullWidth: true,
                    primary: row['key'] == selected,
                    onPressed: () =>
                        Navigator.pop(dialog, row['key'] as String),
                  ),
                ),
            ],
          ),
        ),
        actions: [
          GlassCommand(
            label: l('btn_cancel'),
            onPressed: () => Navigator.pop(dialog),
          ),
        ],
      ),
    );
    if (mounted && value != null) setState(() => changed(value));
  }

  Widget section(String title) => Padding(
    padding: const EdgeInsets.only(top: 18, bottom: 8),
    child: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
  );
  Widget input(
    TextEditingController controller,
    String key, {
    int lines = 1,
    bool required = false,
  }) => Padding(
    padding: const EdgeInsets.only(top: 8, bottom: 10),
    child: TextFormField(
      key: ValueKey('exercise-editor-$key'),
      controller: controller,
      enabled: !busy,
      minLines: lines,
      maxLines: lines == 1 ? 1 : 6,
      textCapitalization: TextCapitalization.sentences,
      decoration: InputDecoration(
        labelText: l(key),
        filled: true,
        fillColor: Theme.of(context).colorScheme.onSurface
            .withValues(alpha: .045),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
      ),
      validator: required
          ? (value) => (value ?? '').trim().isEmpty
                ? l('exercise_name_required')
                : null
          : null,
    ),
  );
  Widget media(String uri) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: EditorMedia(uri: uri, name: name.text),
  );

  @override
  Widget build(BuildContext context) {
    final parts = objects(widget.config['bodyPartOptions'] ?? const []);
    final equipments = objects(widget.config['equipmentOptions'] ?? const []);
    String optionName(List<Json> values, String key) =>
        values
                .where(
                  (row) =>
                      row['key'] == (key == 'part_legs' ? 'part_thighs' : key),
                )
                .firstOrNull?['label']
            as String? ??
        key;
    final recordTypes = category == 'CARDIO'
        ? [1]
        : category == 'CORE'
        ? [0, 2, 1]
        : [0, 2];
    if (!recordTypes.contains(logType)) recordTypes.add(logType);
    String typeLabel(int type) => l(
      type == 1
          ? 'log_type_duration'
          : type == 2
          ? 'log_type_reps_only'
          : 'log_type_weight_reps',
    );
    return PopScope(
      canPop: !busy,
      child: GlassAlertDialog(
        wide: true,
        title: Text(
          l(
            (widget.initial?['id'] as num? ?? 0) > 0
                ? 'title_edit_exercise'
                : 'title_new_exercise',
          ),
          style: rootPageTitleStyle(context).copyWith(fontSize: 22),
        ),
        content: SingleChildScrollView(
          child: Form(
            key: form,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                input(name, 'label_name', required: true),
                input(target, 'label_target'),
                section(l('label_category')),
                GlassSegments<String>(
                  values: const ['STRENGTH', 'CARDIO', 'CORE'],
                  labels: [
                    l('category_strength'),
                    l('category_cardio'),
                    l('category_core'),
                  ],
                  selected: category,
                  onChanged: (value) {
                    if (!busy)
                      setState(() {
                        category = value;
                        logType = value == 'STRENGTH' ? 0 : 1;
                      });
                  },
                ),
                if (category == 'STRENGTH')
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(l('label_is_unilateral')),
                    value: unilateral,
                    onChanged: busy
                        ? null
                        : (value) =>
                              setState(() => unilateral = value ?? false),
                  ),
                section(l('label_body_part')),
                GlassCommand(
                  label: optionName(parts, bodyPart),
                  icon: Icons.expand_more,
                  fullWidth: true,
                  onPressed: busy
                      ? null
                      : () => selectKey(
                          l('label_body_part'),
                          bodyPart,
                          parts,
                          (value) => bodyPart = value,
                        ),
                ),
                section(l('label_equipment')),
                GlassCommand(
                  label: optionName(equipments, equipment),
                  icon: Icons.expand_more,
                  fullWidth: true,
                  onPressed: busy
                      ? null
                      : () => selectKey(
                          l('label_equipment'),
                          equipment,
                          equipments,
                          (value) => equipment = value,
                        ),
                ),
                section(l('label_log_type')),
                if (recordTypes.length == 1)
                  Semantics(
                    selected: true,
                    child: GlassCommand(
                      label: typeLabel(recordTypes.single),
                      primary: true,
                      fullWidth: true,
                      icon: Icons.timer_outlined,
                      onPressed: busy ? null : () {},
                    ),
                  )
                else
                  GlassSegments<int>(
                    values: recordTypes,
                    labels: recordTypes.map(typeLabel).toList(),
                    selected: logType,
                    onChanged: (value) {
                      if (!busy) setState(() => logType = value);
                    },
                  ),
                const SizedBox(height: 16),
                input(instruction, 'label_instruction', lines: 3),
                section(l('exercise_photo_title')),
                Text(
                  l('exercise_media_separate'),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 10),
                if (photo != null) media(photo!),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    GlassCommand(
                      label: l('source_gallery'),
                      icon: Icons.photo_library_outlined,
                      onPressed: busy ? null : () => pickPhoto('gallery'),
                    ),
                    GlassCommand(
                      label: l('source_camera'),
                      icon: Icons.photo_camera_outlined,
                      onPressed: busy ? null : () => pickPhoto('camera'),
                    ),
                    if (photo != null)
                      GlassCommand(
                        label: l('remove_exercise_photo'),
                        icon: Icons.delete_outline,
                        onPressed: busy
                            ? null
                            : () => setState(() => photo = null),
                      ),
                  ],
                ),
                section(l('catalog_choose_animation')),
                if (animation != null) media(animation!),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    GlassCommand(
                      label: l('catalog_choose_animation'),
                      icon: Icons.movie_outlined,
                      onPressed: busy ? null : chooseAnimation,
                    ),
                    if (animation != null)
                      GlassCommand(
                        label: l('exercise_remove_animation'),
                        icon: Icons.link_off,
                        onPressed: busy
                            ? null
                            : () => setState(() => animation = null),
                      ),
                  ],
                ),
                if (animation != null)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: Text('© Gym visual — https://gymvisual.com/'),
                  ),
                if (error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Text(
                      error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
        actions: [
          Row(
            children: [
              Expanded(
                child: GlassCommand(
                  label: l('btn_cancel'),
                  onPressed: busy ? null : () => Navigator.pop(context, false),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: GlassCommand(
                  label: l('btn_save'),
                  primary: true,
                  onPressed: busy ? null : save,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class EditorMedia extends StatefulWidget {
  const EditorMedia({super.key, required this.uri, required this.name});
  final String uri, name;
  @override
  State<EditorMedia> createState() => _EditorMediaState();
}

class _EditorMediaState extends State<EditorMedia> {
  late Future<Uint8List?> bytes;
  void load() {
    bytes = bridge.invokeMethod<Uint8List>('exerciseEditor', {
      'operation': 'media',
      'uri': widget.uri,
    });
  }

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void didUpdateWidget(EditorMedia old) {
    super.didUpdateWidget(old);
    if (old.uri != widget.uri) load();
  }

  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(14),
    child: SizedBox(
      height: 168,
      child: FutureBuilder<Uint8List?>(
        future: bytes,
        builder: (context, snapshot) {
          final data = snapshot.data;
          if (data == null)
            return const Center(child: Icon(Icons.image_outlined, size: 40));
          return Image.memory(
            data,
            fit: BoxFit.contain,
            gaplessPlayback: true,
            semanticLabel: widget.name,
            errorBuilder: (_, __, ___) =>
                const Center(child: Icon(Icons.broken_image_outlined)),
          );
        },
      ),
    ),
  );
}

class _AnimationPicker extends StatefulWidget {
  const _AnimationPicker({required this.config, required this.labels});
  final Json config;
  final Map<String, String> labels;
  @override
  State<_AnimationPicker> createState() => _AnimationPickerState();
}

class _AnimationPickerState extends State<_AnimationPicker> {
  String query = '', part = '';
  List<Json> rows = [];
  Timer? debounce;
  int generation = 0;
  bool failed = false;
  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    debounce?.cancel();
    super.dispose();
  }

  Future<void> load() async {
    final request = ++generation;
    try {
      final result = object(
        await bridge.invokeMethod('catalog', {
          'language': widget.config['language'] ?? 'zh',
          'query': query,
          'part': part.isEmpty ? null : part,
        }),
      );
      if (mounted && request == generation)
        setState(() {
          rows = objects(result['entries']);
          failed = false;
        });
    } catch (_) {
      if (mounted && request == generation) setState(() => failed = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = widget.labels;
    final parts = objects(widget.config['bodyPartOptions'] ?? const []);
    return GlassAlertDialog(
      wide: true,
      title: Text(l['catalog_choose_animation'] ?? ''),
      content: SizedBox(
        height: 470,
        child: Column(
          children: [
            TextField(
              decoration: InputDecoration(
                labelText: l['catalog_search'],
                prefixIcon: const Icon(Icons.search),
              ),
              onChanged: (value) {
                query = value;
                debounce?.cancel();
                debounce = Timer(const Duration(milliseconds: 220), load);
              },
            ),
            const SizedBox(height: 10),
            GlassScrollableSegments<String>(
              values: ['', for (final row in parts) row['key'] as String],
              labels: [
                l['catalog_all_parts'] ?? '',
                for (final row in parts) row['label'] as String,
              ],
              selected: part,
              onChanged: (value) {
                setState(() => part = value);
                debounce?.cancel();
                load();
              },
            ),
            const SizedBox(height: 8),
            Expanded(
              child: failed
                  ? Center(
                      child: GlassCommand(
                        label: l['flutter_retry'] ?? '',
                        onPressed: load,
                      ),
                    )
                  : ListView.builder(
                      itemCount: rows.length,
                      itemBuilder: (context, index) {
                        final row = rows[index];
                        return ListTile(
                          leading: ExerciseImage(
                            id: row['id'],
                            thumbnail: true,
                            size: 48,
                            retryLabel: l['flutter_retry'] ?? '',
                          ),
                          title: Text(
                            row['name'],
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          subtitle: Text(
                            '${row['bodyLabel']} · ${row['equipment']}',
                          ),
                          onTap: () => Navigator.pop(context, row),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
      actions: [
        GlassCommand(
          label: l['btn_cancel'] ?? '',
          onPressed: () => Navigator.pop(context),
        ),
      ],
    );
  }
}
