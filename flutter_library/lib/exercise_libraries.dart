import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'embedded_catalog.dart';
import 'exercise_editor.dart';
import 'glass.dart';
import 'main.dart' show Json, bridge, objects;

class ExerciseLibraries extends StatefulWidget {
  const ExerciseLibraries({
    super.key,
    required this.config,
    required this.labels,
  });
  final Json config;
  final Map<String, String> labels;

  @override
  State<ExerciseLibraries> createState() => _ExerciseLibrariesState();
}

class _ExerciseLibrariesState extends State<ExerciseLibraries> {
  static const categories = ['STRENGTH', 'CARDIO', 'CORE'];
  String category = categories.first;
  bool catalogVisited = false;
  Set<int>? matchingIds;
  int searchRequest = 0;

  bool get readOnly => widget.config['previewReadOnly'] == true;
  String get query => (widget.config['query'] as String? ?? '').trim();
  List<Json> get rows => objects(widget.config['personalRows']);
  String label(String key) => widget.labels[key] ?? '';

  @override
  void initState() {
    super.initState();
    if (query.isNotEmpty) search();
  }

  @override
  void didUpdateWidget(ExerciseLibraries oldWidget) {
    super.didUpdateWidget(oldWidget);
    if ((oldWidget.config['query'] as String? ?? '').trim() != query ||
        (query.isNotEmpty &&
            oldWidget.config['personalSearchRevision'] !=
                widget.config['personalSearchRevision'])) {
      search();
    }
  }

  Future<void> search() async {
    final generation = ++searchRequest;
    matchingIds = null;
    if (query.isEmpty) return;
    try {
      final matches = await bridge.invokeListMethod<int>('personalSearch', {
        'query': query,
      });
      if (mounted && generation == searchRequest) {
        setState(() => matchingIds = matches?.toSet());
      }
    } catch (_) {
      // The list still offers local matching if the platform channel is unavailable.
    }
  }

  Future<void> action(String action, [int? id]) async {
    try {
      if (action == 'add' || action == 'edit') {
        final initial = action == 'edit'
            ? rows.where((row) => row['id'] == id).firstOrNull
            : null;
        if (action == 'edit' && initial == null) return;
        await showExerciseEditor(
          context,
          widget.config,
          widget.labels,
          initial: initial,
        );
        return;
      }
      await bridge.invokeMethod('exerciseManagerAction', {
        'action': action,
        'id': id,
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(label('flutter_error'))));
      }
    }
  }

  Widget personalList() {
    final searchText = query.toLowerCase();
    final visible = rows.where((row) {
      return row['category'] == category &&
          (searchText.isEmpty ||
              (matchingIds?.contains(row['id']) ??
                  (row['name'] as String).toLowerCase().contains(searchText)));
    }).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, glassMenuGap),
          child: GlassSegments<String>(
            values: categories,
            labels: [
              for (final key in categories)
                label('category_' + key.toLowerCase()),
            ],
            selected: category,
            onChanged: (value) => setState(() => category = value),
          ),
        ),
        Expanded(
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (visible.isEmpty)
                Center(child: Text(label('catalog_empty')))
              else
                ListView.builder(
                  key: PageStorageKey(category),
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 72),
                  itemCount: visible.length,
                  itemBuilder: (context, index) => exerciseCard(visible[index]),
                ),
              if (!readOnly)
                PositionedDirectional(
                  end: 16,
                  bottom: 12,
                  child: GlassIconAction(
                    label: label('btn_add'),
                    icon: Icons.add,
                    onPressed: () => action('add'),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }

  Future<void> showPersonalDetail(Json row) async {
    FocusManager.instance.primaryFocus?.unfocus();
    final route = DialogRoute<bool>(context: context,
      builder: (_) => PersonalExerciseDetail(row: row, labels: widget.labels, editable: !readOnly));
    final edit = await Navigator.of(context, rootNavigator: true).push(route);
    await route.completed;
    if (edit == true && mounted) await action('edit', row['id'] as int);
  }

  Widget exerciseCard(Json row) {
    final id = row['id'] as int;
    final metadata = [
      row['bodyLabel'],
      row['equipment'],
    ].whereType<String>().where((value) => value.isNotEmpty).join(' · ');
    final theme = Theme.of(context);
    return Padding(
      key: ValueKey(id),
      padding: const EdgeInsets.only(bottom: 12),
      child: Glass(
        radius: 16,
        definedEdge: true,
        child: Row(
          children: [
            Expanded(
              child: InkWell(
                borderRadius: BorderRadius.circular(16),
                onTap: readOnly
                    ? () => showDialog<void>(
                        context: context,
                        builder: (dialog) => GlassAlertDialog(
                          title: Text(row['name'] as String),
                          content: Text(
                            [metadata, row['target'], row['instruction']]
                                .whereType<String>()
                                .where((text) => text.isNotEmpty)
                                .join('\n\n'),
                          ),
                          actions: [
                            GlassCommand(
                              label: label('btn_close'),
                              onPressed: () => Navigator.pop(dialog),
                            ),
                          ],
                        ),
                      )
                    : widget.config['primaryHost'] == true
                    ? () => showPersonalDetail(row)
                    : () => action('view', id),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      if (row['hasMedia'] == true) ...[
                        PersonalExerciseThumbnail(
                          key: ValueKey((id, row['mediaRevision'])),
                          id: id,
                        ),
                        const SizedBox(width: 12),
                      ],
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              row['name'] as String,
                              style: theme.textTheme.titleSmall?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            if (metadata.isNotEmpty)
                              Text(metadata, style: theme.textTheme.bodySmall),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            if (!readOnly)
              IconButton(
                icon: const Icon(Icons.delete_outline),
                tooltip: label('btn_delete'),
                color: theme.colorScheme.error,
                onPressed: () => action('delete', id),
              ),
            const SizedBox(width: 4),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final personal = widget.config['personalSelected'] != false;
    if (!personal) catalogVisited = true;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, glassMenuGap),
          child: GlassSegments<bool>(
            values: const [true, false],
            labels: [label('catalog_personal_title'), label('catalog_title')],
            selected: personal,
            primary: true,
            onChanged: (value) =>
                action(value ? 'showPersonal' : 'showCatalog'),
          ),
        ),
        Expanded(
          child: IndexedStack(
            index: personal ? 0 : 1,
            sizing: StackFit.expand,
            children: [
              personalList(),
              if (catalogVisited)
                EmbeddedCatalog(config: widget.config, labels: widget.labels)
              else
                const SizedBox.shrink(),
            ],
          ),
        ),
      ],
    );
  }
}

class PersonalExerciseDetail extends StatelessWidget {
  const PersonalExerciseDetail({
    super.key,
    required this.row,
    required this.labels,
    this.editable = true,
  });

  final Json row;
  final Map<String, String> labels;
  final bool editable;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final name = row['name'] as String;
    final category = (row['category'] as String).toLowerCase();
    final instruction = row['instruction'] as String? ?? '';
    return GlassAlertDialog(
      compact: true,
      wide: true,
      title: Text(name, style: const TextStyle(fontWeight: FontWeight.bold)),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (row['hasAnimation'] == true)
              _PersonalDetailMedia(
                id: row['id'] as int,
                name: name,
                animation: true,
                uri: row['animationUri'] as String?,
              ),
            if (row['hasAnimation'] == true)
              const Text('© Gym visual — https://gymvisual.com/'),
            if (row['hasPhoto'] == true)
              _PersonalDetailMedia(
                id: row['id'] as int,
                name: name,
                animation: false,
                uri: row['imageUri'] as String?,
              ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 4,
              children: [
                for (final text in [
                  labels['category_$category'],
                  row['bodyLabel'],
                  row['equipment'],
                  if (row['unilateral'] == true) labels['tag_uni'],
                ].whereType<String>().where((text) => text.isNotEmpty))
                  Chip(visualDensity: VisualDensity.compact, label: Text(text)),
              ],
            ),
            const SizedBox(height: 12),
            for (final (label, value) in [
              ('label_target', row['target']),
              ('label_log_type', row['logTypeLabel']),
            ])
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(
                        text: labels[label] ?? '',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      TextSpan(text: '  ${value ?? ''}'),
                    ],
                  ),
                ),
              ),
            Text(
              labels['label_instruction'] ?? '',
              style: theme.textTheme.titleSmall,
            ),
            const SizedBox(height: 4),
            Text(
              instruction.isEmpty
                  ? labels['label_instruction_empty'] ?? ''
                  : instruction,
            ),
          ],
        ),
      ),
      actions: [
        GlassAction(
          label: labels['btn_close'] ?? '',
          onPressed: () => Navigator.pop(context),
        ),
        if (editable)
          GlassAction(
            label: labels['btn_edit'] ?? '',
            icon: Icons.edit_outlined,
            onPressed: () => Navigator.pop(context, true),
          ),
      ],
    );
  }
}

class _PersonalDetailMedia extends StatefulWidget {
  const _PersonalDetailMedia({
    required this.id,
    required this.name,
    required this.animation,
    this.uri,
  });

  final int id;
  final String name;
  final bool animation;
  final String? uri;

  @override
  State<_PersonalDetailMedia> createState() => _PersonalDetailMediaState();
}

class _PersonalDetailMediaState extends State<_PersonalDetailMedia> {
  Animation<double>? transition;
  Future<Uint8List?>? bytes;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (bytes != null) return;
    final routeAnimation = ModalRoute.of(context)?.animation;
    if (routeAnimation == null || routeAnimation.isCompleted) {
      load();
    } else if (transition != routeAnimation) {
      transition?.removeStatusListener(settled);
      transition = routeAnimation..addStatusListener(settled);
    }
  }

  void load() {
    if (widget.uri != null) {
      bytes = bridge.invokeMethod<Uint8List>('exerciseEditor', {
        'operation': 'media',
        'uri': widget.uri,
      });
      return;
    }
    bytes = bridge.invokeMethod<Uint8List>('personalDetailMedia', {
      'id': widget.id,
      'animation': widget.animation,
    });
  }

  void settled(AnimationStatus status) {
    if (status != AnimationStatus.completed) return;
    transition?.removeStatusListener(settled);
    transition = null;
    setState(load);
  }

  @override
  void dispose() {
    transition?.removeStatusListener(settled);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 180,
    child: FutureBuilder<Uint8List?>(
      future: bytes,
      builder: (context, snapshot) {
        final image = snapshot.data;
        if (image == null) {
          return Center(
            child: snapshot.connectionState == ConnectionState.done
                ? const Icon(Icons.broken_image_outlined)
                : const SizedBox.square(
                    dimension: 22,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
          );
        }
        return InkWell(
          onTap: () => showDialog<void>(
            context: context,
            builder: (dialog) => Dialog(
              child: InteractiveViewer(
                child: Image.memory(
                  image,
                  fit: BoxFit.contain,
                  semanticLabel: widget.name,
                ),
              ),
            ),
          ),
          child: Image.memory(
            image,
            fit: BoxFit.contain,
            semanticLabel: widget.name,
          ),
        );
      },
    ),
  );
}

class PersonalExerciseThumbnail extends StatefulWidget {
  const PersonalExerciseThumbnail({super.key, required this.id});
  final int id;

  @override
  State<PersonalExerciseThumbnail> createState() =>
      PersonalExerciseThumbnailState();
}

class PersonalExerciseThumbnailState extends State<PersonalExerciseThumbnail> {
  late final bytes = bridge.invokeMethod<Uint8List>('personalThumbnail', {
    'id': widget.id,
  });

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: 52,
    child: ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: FutureBuilder<Uint8List?>(
        future: bytes,
        builder: (context, snapshot) => snapshot.hasData
            ? Image.memory(snapshot.data!, fit: BoxFit.cover)
            : ColoredBox(
                color: Theme.of(context).colorScheme.primary
                    .withValues(alpha: .06),
                child: const Icon(Icons.fitness_center, size: 18),
              ),
      ),
    ),
  );
}
