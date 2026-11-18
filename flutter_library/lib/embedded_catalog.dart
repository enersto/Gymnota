import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'main.dart'
    show Json, bridge, object, objects, ExerciseImage, Failure, MediaCache;
import 'glass.dart';
import 'exercise_editor.dart';
import 'glass_frame_probe.dart';

import 'dart:async';

const catalogParts = [
  'part_chest',
  'part_back',
  'part_shoulders',
  'part_hips',
  'part_thighs',
  'part_calves',
  'part_arms',
  'part_abs',
  'part_cardio',
  'part_other',
];

/// The original catalogue content: its surrounding navigation remains Android-owned.
class EmbeddedCatalog extends StatefulWidget {
  final Json config;
  final Map<String, String> labels;
  const EmbeddedCatalog({
    super.key,
    required this.config,
    required this.labels,
  });
  @override
  State<EmbeddedCatalog> createState() => _EmbeddedCatalogState();
}

class _EmbeddedCatalogState extends State<EmbeddedCatalog> {
  List<Json> rows = [];
  String? part;
  String localQuery = '';
  bool loading = false, failed = false;
  int generation = 0;
  Timer? debounce;
  bool inOverlay = false;
  bool previewing = false;
  int backdropGeneration = 0;
  final scroll = ScrollController();
  String get language => widget.config['language'] as String;
  bool get showSearch => widget.config['showSearch'] == true;
  String get query =>
      showSearch ? localQuery : '${widget.config['query'] ?? ''}';

  @override
  void initState() {
    super.initState();
    final initial = widget.config['initialCatalog'];
    if (initial is Map &&
        widget.config['catalogLanguage'] == language &&
        query.isEmpty) {
      rows = objects(object(initial)['entries']);
    } else {
      loading = true;
      load();
    }
  }

  @override
  void didUpdateWidget(EmbeddedCatalog old) {
    super.didUpdateWidget(old);
    if (old.config['language'] != language ||
        old.config['query'] != widget.config['query']) {
      generation++;
      debounce?.cancel();
      if (old.config['language'] != language) {
        load();
      } else {
        debounce = Timer(const Duration(milliseconds: 180), load);
      }
    }
  }

  Future<void> load() async {
    final request = ++generation;
    if (mounted)
      setState(() {
        loading = rows.isEmpty;
        failed = false;
      });
    try {
      final result = object(
        await bridge.invokeMethod('catalog', {
          'language': language,
          'query': query,
          'part': part,
        }),
      );
      if (mounted && request == generation)
        setState(() {
          rows = objects(result['entries']);
          loading = false;
        });
    } catch (_) {
      if (mounted && request == generation)
        setState(() {
          failed = true;
          loading = false;
        });
    }
  }

  @override
  void dispose() {
    debounce?.cancel();
    scroll.dispose();
    super.dispose();
  }

  Future<void> preview(Json entry) async {
    if (previewing) return;
    previewing = true;
    final opening = Stopwatch()..start();
    GlassPopupProbe.active = true;
    GlassPopupProbe.firstPaintMs = null;
    GlassPopupProbe.fullyVisibleMs = null;
    unawaited(
      MediaCache.load(entry['id'], false)
          .then<void>((bytes) async {
            if (mounted)
              await precacheImage(
                MemoryImage(bytes),
                context,
                onError: (_, __) {},
              );
          })
          .catchError((Object _) {}),
    );
    var overlay = false;
    var overlayClosed = false;
    try {
      FocusManager.instance.primaryFocus?.unfocus();
      // Unfocus already requests keyboard dismissal; do not serialize a platform
      // round trip before opening the route.
      unawaited(SystemChannels.textInput.invokeMethod<void>('TextInput.hide'));
      if (!mounted) return;
      final offset = scroll.hasClients ? scroll.offset : 0.0;
      overlay = await bridge.invokeMethod<bool>('openCatalogOverlay') == true;
      if (!mounted) {
        if (overlay) {
          await bridge.invokeMethod('closeCatalogOverlay');
          overlayClosed = true;
        }
        return;
      }
      ++backdropGeneration;
      if (overlay) {
        final cached = catalogCachedBackdrop;
        final size = MediaQuery.sizeOf(context);
        final fits =
            cached?.viewport == null ||
            ((cached!.viewport!.width - size.width).abs() < 1 &&
                (cached.viewport!.height - size.height).abs() < 1);
        glassOverlay.value = fits && cached != null
            ? cached
            : const GlassOverlayBackdrop(null);
      }
      setState(() => inOverlay = overlay);
      // The viewport is already full-window and never changes for a route.
      final route = DialogRoute<String>(
        context: context,
        animationStyle: const AnimationStyle(
          duration: Duration(milliseconds: 140),
          reverseDuration: Duration(milliseconds: 110),
        ),
        barrierColor: const Color(0x66000000),
        builder: (dialogContext) {
          final l = widget.labels;
          return GlassAlertDialog(
            title: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  entry['name'],
                  softWrap: true,
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                Chip(
                  label: Text(
                    '${entry['categoryLabel'] ?? ''}',
                    style: const TextStyle(fontSize: 10),
                  ),
                ),
              ],
            ),
            content: SizedBox(
              width: 360,
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '© Gym visual — https://gymvisual.com/',
                      style: TextStyle(fontSize: 11),
                    ),
                    const SizedBox(height: 8),
                    GestureDetector(
                      onTap: () => showDialog<void>(
                        context: dialogContext,
                        builder: (imageContext) => Dialog(
                          backgroundColor: Colors.transparent,
                          insetPadding: const EdgeInsets.all(16),
                          child: AspectRatio(
                            aspectRatio: 1,
                            child: Stack(
                              fit: StackFit.expand,
                              children: [
                                InteractiveViewer(
                                  child: ExerciseImage(
                                    id: entry['id'],
                                    thumbnail: false,
                                    size: double.infinity,
                                  ),
                                ),
                                Positioned(
                                  top: 0,
                                  right: 0,
                                  child: IconButton(
                                    tooltip: l['btn_cancel'],
                                    onPressed: () =>
                                        Navigator.pop(imageContext),
                                    icon: const Icon(Icons.close),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                      child: Center(
                        child: ExerciseImage(
                          id: entry['id'],
                          thumbnail: false,
                          size: 180,
                          retryLabel: l['flutter_retry'] ?? '',
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      children: [
                        Chip(label: Text('${entry['bodyLabel']}')),
                        Chip(label: Text('${entry['equipment']}')),
                        if (entry['unilateral'] == true)
                          Chip(label: Text(l['tag_uni'] ?? '')),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Text('${l['label_target']}: ${entry['target'] ?? ''}'),
                    Text(
                      '${l['label_log_type']}: ${entry['logTypeLabel'] ?? ''}',
                    ),
                    const SizedBox(height: 16),
                    const Divider(),
                    const SizedBox(height: 16),
                    Text(
                      l['label_instruction'] ?? '',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        color: Theme.of(context).colorScheme.primary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${entry['instruction']}'.isEmpty
                          ? l['label_instruction_empty'] ?? ''
                          : entry['instruction'],
                    ),
                  ],
                ),
              ),
            ),
            actions: [
              glassAction(
                l['btn_cancel'] ?? '',
                () => Navigator.pop(dialogContext),
              ),
              if (widget.config['previewReadOnly'] != true)
                glassAction(
                  l['catalog_use_exercise'] ?? '',
                  () => Navigator.pop(dialogContext, 'use'),
                  icon: Icons.add,
                ),
            ],
          );
        },
      );
      final dismissed = Navigator.of(context, rootNavigator: true).push(route);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && previewing) {
          GlassPopupProbe.firstPaintMs = opening.elapsedMicroseconds / 1000;
        }
      });
      void settled(AnimationStatus status) {
        if (status == AnimationStatus.completed)
          GlassPopupProbe.fullyVisibleMs = opening.elapsedMicroseconds / 1000;
      }

      route.animation?.addStatusListener(settled);
      final action = await dismissed;
      route.animation?.removeStatusListener(settled);
      await route.completed;
      if (overlay) {
        await bridge.invokeMethod('closeCatalogOverlay');
        overlayClosed = true;
      }
      if (!mounted) return;
      glassOverlay.value = null;
      setState(() => inOverlay = false);
      await WidgetsBinding.instance.endOfFrame;
      if (!mounted) return;
      if (scroll.hasClients)
        scroll.jumpTo(
          offset.clamp(
            scroll.position.minScrollExtent,
            scroll.position.maxScrollExtent,
          ),
        );
      if (action == 'use') {
        try {
          if (widget.config['primaryHost'] == true) {
            final draft = object(
              await bridge.invokeMethod('exerciseEditor', {
                'operation': 'catalogDraft',
                'sourceId': entry['id'],
                'language': language,
              }),
            );
            if (mounted)
              await showExerciseEditor(
                context,
                widget.config,
                widget.labels,
                initial: draft,
              );
          } else {
            await bridge.invokeMethod('useExercise', {
              'id': entry['id'],
              'language': language,
            });
          }
        } catch (_) {
          if (mounted)
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(widget.labels['flutter_error'] ?? '')),
            );
        }
      }
    } finally {
      if (overlay && !overlayClosed) {
        try {
          await bridge.invokeMethod('closeCatalogOverlay');
        } catch (_) {}
      }
      GlassPopupProbe.active = false;
      backdropGeneration++;
      glassOverlay.value = null;
      previewing = false;
    }
  }

  Widget glassAction(String text, VoidCallback action, {IconData? icon}) =>
      GlassAction(label: text, onPressed: action, icon: icon);

  @override
  Widget build(BuildContext context) {
    final l = widget.labels;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (showSearch) ...[
                Glass(
                  blur: true,
                  child: TextField(
                    onChanged: (value) {
                      localQuery = value;
                      generation++;
                      debounce?.cancel();
                      debounce = Timer(const Duration(milliseconds: 180), load);
                    },
                    decoration: InputDecoration(
                      labelText: l['catalog_search'],
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.all(16),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
              ],
              GlassScrollableSegments<String?>(
                values: const [null, ...catalogParts],
                labels: [
                  l['catalog_all_parts'] ?? '',
                  for (final key in catalogParts) l[key] ?? key,
                ],
                selected: part,
                onChanged: (value) {
                  setState(() => part = value);
                  debounce?.cancel();
                  load();
                },
              ),
              if (widget.config['previewReadOnly'] != true) ...[
                const SizedBox(height: 10),
                Text(
                  l['catalog_setup_hint'] ?? '',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
              const SizedBox(height: 12),
            ],
          ),
        ),
        Expanded(
          child: loading
              ? const Center(child: CircularProgressIndicator())
              : failed
              ? Failure(labels: l, retry: load)
              : rows.isEmpty
              ? Center(child: Text(l['catalog_empty'] ?? ''))
              : ListView.builder(
                  controller: scroll,
                  padding: EdgeInsets.fromLTRB(
                    16,
                    0,
                    16,
                    widget.config['fixedViewport'] == true ? 24 : 120,
                  ),
                  itemCount: rows.length,
                  itemBuilder: (context, index) {
                    final entry = rows[index];
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Glass(
                        radius: 16,
                        definedEdge: true,
                        child: InkWell(
                          onTapDown: (_) {
                            unawaited(
                              MediaCache.load(entry['id'], false)
                                  .then<void>((bytes) {
                                    if (mounted)
                                      unawaited(
                                        precacheImage(
                                          MemoryImage(bytes),
                                          context,
                                          onError: (_, __) {},
                                        ),
                                      );
                                  })
                                  .catchError((Object _) {}),
                            );
                          },
                          onTap: () => preview(entry),
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.center,
                              children: [
                                ExerciseImage(
                                  id: entry['id'],
                                  thumbnail: true,
                                  size: 52,
                                  retryLabel: l['flutter_retry'] ?? '',
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        entry['name'],
                                        style: Theme.of(context)
                                            .textTheme
                                            .titleSmall
                                            ?.copyWith(
                                              fontWeight: FontWeight.bold,
                                            ),
                                      ),
                                      Text(
                                        [entry['bodyLabel'], entry['equipment']]
                                            .whereType<String>()
                                            .where((value) => value.isNotEmpty)
                                            .join(' · '),
                                        style: Theme.of(context)
                                            .textTheme
                                            .bodySmall,
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }
}
