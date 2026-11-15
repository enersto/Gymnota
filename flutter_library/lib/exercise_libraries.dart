import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'embedded_catalog.dart';
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
                onTap: () => action('view', id),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      if (row['hasMedia'] == true) ...[
                        _PersonalThumbnail(
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

class _PersonalThumbnail extends StatefulWidget {
  const _PersonalThumbnail({super.key, required this.id});
  final int id;

  @override
  State<_PersonalThumbnail> createState() => _PersonalThumbnailState();
}

class _PersonalThumbnailState extends State<_PersonalThumbnail> {
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
