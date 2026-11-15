import 'coach_preview.dart';

import 'dart:async';

import 'glass.dart';
import 'glass_frame_probe.dart';
import 'embedded_catalog.dart';
import 'exercise_libraries.dart';
import 'settings_page.dart';
import 'history_charts.dart';
import 'history_records.dart';
export 'history_records.dart' show setValue, formatWeight;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

const bridge = MethodChannel('gymnota/library');
typedef Json = Map<String, dynamic>;
Json object(dynamic value) => Map<String, dynamic>.from(value as Map);
List<Json> objects(dynamic value) => (value as List).map(object).toList();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await initializeGlass();
  } catch (_) {
    debugPrint('Glass preload unavailable; adaptive fallback remains enabled.');
  }
  runApp(const LibraryApp());
}

final historySnapshots = ValueNotifier<Json?>(null);
final historySnapshotFailed = ValueNotifier<bool>(false);
bool historyRasterWarmup = false;
bool historyRasterPrimed = false;
@pragma('vm:entry-point')
Future<void> warmHistory() async {
  WidgetsFlutterBinding.ensureInitialized();
  historyRasterWarmup = true;
  const MethodChannel('gymnota/historyWarmup')
      .setMethodCallHandler((call) async {
        if (call.method == 'activate') {
          historyRasterWarmup = false;
          return true;
        }
        throw MissingPluginException();
      });
  try {
    await initializeGlass();
  } catch (_) {}
  runApp(const LibraryApp());
}

class _HistoryPopupObserver extends NavigatorObserver {
  final ValueChanged<bool> onChanged;
  final routes = <Route<dynamic>>{};
  _HistoryPopupObserver(this.onChanged);
  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    if (route is PopupRoute) {
      routes.add(route);
      onChanged(true);
    }
  }

  void removed(Route<dynamic> route) {
    if (!routes.remove(route)) return;
    if (route is TransitionRoute) {
      route.completed.then((_) {
        if (routes.isEmpty) onChanged(false);
      });
    } else if (routes.isEmpty) {
      onChanged(false);
    }
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      removed(route);
  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      removed(route);
}

bool catalogRasterWarmup = false;
bool catalogRasterPrimed = false;
@pragma('vm:entry-point')
Future<void> warmCatalog() async {
  WidgetsFlutterBinding.ensureInitialized();
  catalogRasterWarmup = true;
  const MethodChannel('gymnota/catalogWarmup')
      .setMethodCallHandler((call) async {
        if (call.method == 'activate') return true;
        throw MissingPluginException();
      });
  try {
    await initializeGlass();
  } catch (_) {}
  runApp(const LibraryApp());
}

class LibraryApp extends StatefulWidget {
  const LibraryApp({super.key});
  @override
  State<LibraryApp> createState() => _LibraryAppState();
}

class _LibraryAppState extends State<LibraryApp> {
  final navigatorKey = GlobalKey<NavigatorState>();
  final glassProbe = GlassFrameProbe();
  late final popupObserver = _HistoryPopupObserver((active) {
    if (!const {
      '/embedded-history',
      '/embedded-coach',
    }.contains(config?['page']))
      return;
    if (active) {
      glassOverlay.value =
          catalogCachedBackdrop ?? const GlassOverlayBackdrop(null);
      bridge.invokeMethod('openCatalogOverlay').catchError((_) => null);
    } else {
      glassOverlay.value = null;
      bridge.invokeMethod('closeCatalogOverlay').catchError((_) => null);
    }
  });
  Json? pendingEmbedded;
  Json? config;
  String language = 'zh';
  Map<String, String> labels = {};
  String? failure;
  @override
  void initState() {
    super.initState();
    glassProbe.start();
    bridge.setMethodCallHandler((call) async {
      if (call.method == 'historySnapshot') {
        historySnapshotFailed.value = false;
        historySnapshots.value = object(call.arguments);
        return true;
      }
      if (call.method == 'historySnapshotError') {
        historySnapshotFailed.value = true;
        return true;
      }
      if (call.method == 'glassDiagnostics')
        return {
          ...glassProbe.snapshot(reset: call.arguments == true),
          'ready': config != null,
          'shaderProgramsLoaded': glassShadersReady,
          'catalogRasterPrimed': catalogRasterPrimed,
          'historyRasterPrimed': historyRasterPrimed,
          'historyRevision': historySnapshots.value?['revision'],
          'dialogGlassQuality': dialogGlassQuality,
          ...GlassPopupProbe.values,
        };
      if (call.method == 'embeddedBack') {
        if (WidgetsBinding.instance.platformDispatcher.views.any(
          (view) => view.viewInsets.bottom > 0,
        )) {
          FocusManager.instance.primaryFocus?.unfocus();
          await SystemChannels.textInput.invokeMethod<void>('TextInput.hide');
          return true;
        }
        return await navigatorKey.currentState?.maybePop() ?? false;
      }
      if (call.method == 'catalogBackdropCached') {
        final update = object(call.arguments);
        Object? session() =>
            pendingEmbedded?['catalogSession'] ?? config?['catalogSession'];
        if (update['session'] != session()) return false;
        final bytes = update['bytes'] as Uint8List;
        var valid = true;
        await precacheImage(
          MemoryImage(bytes),
          context,
          onError: (_, __) {
            valid = false;
          },
        );
        if (mounted && valid && update['session'] == session()) {
          catalogCachedBackdrop = GlassOverlayBackdrop(
            bytes,
            viewport: Size(
              (update['width'] as num).toDouble(),
              (update['height'] as num).toDouble(),
            ),
          );
        }
        return true;
      }
      if (call.method == 'embeddedState') {
        final next = object(call.arguments);
        final previous = pendingEmbedded ?? config ?? {};
        if (next['catalogSession'] != previous['catalogSession'] ||
            [
              'primary',
              'background',
              'dark',
              'inlineLeft',
              'inlineTop',
              'inlineWidth',
              'inlineHeight',
            ].any((key) => next[key] != previous[key])) {
          catalogCachedBackdrop = null;
        }
        pendingEmbedded = next;
        final parked = next['page'] == '/embedded-idle';
        if (parked) {
          historySnapshots.value = null;
          historySnapshotFailed.value = false;
          glassOverlay.value = null;
        }
        if (mounted && config != null)
          setState(() {
            config = parked ? next : {...config!, ...next};
            language = next['language'];
            labels = Map<String, String>.from(next['labels']);
          });
      }
      return null;
    });
    bootstrap();
  }

  @override
  void dispose() {
    bridge.setMethodCallHandler(null);
    glassProbe.stop();
    MediaCache.clear();
    catalogCachedBackdrop = null;
    historySnapshots.value = null;
    super.dispose();
  }

  Future<void> bootstrap() async {
    try {
      final c = object(await bridge.invokeMethod('bootstrap'));
      final lang = c['language'] as String;
      final l = Map<String, String>.from(
        c['labels'] ?? await bridge.invokeMethod('labels', {'language': lang}),
      );
      if (mounted) {
        setState(() {
          config = {...c, ...?pendingEmbedded};
          language = config!['language'] as String;
          labels = pendingEmbedded == null
              ? l
              : Map<String, String>.from(pendingEmbedded!['labels']);
          failure = null;
        });
        if (historyRasterWarmup) {
          await WidgetsBinding.instance.endOfFrame;
          if (!mounted) return;
          setState(() {
            historyRasterPrimed = true;
            historyRasterWarmup = false;
          });
          await const MethodChannel('gymnota/historyWarmup')
              .invokeMethod('primed');
        }
        if (catalogRasterWarmup) {
          await WidgetsBinding.instance.endOfFrame;
          // Let visible thumbnails and actual glass drawing reach the renderer.
          await Future<void>.delayed(const Duration(milliseconds: 80));
          await Future.wait(
            MediaCache._values.values.toList().map(
              (f) =>
                  f.then<void>((_) {}, onError: (Object _, StackTrace __) {}),
            ),
          );
          await WidgetsBinding.instance.endOfFrame;
          setState(() {
            catalogRasterWarmup = false;
            catalogRasterPrimed = true;
          });
          await WidgetsBinding.instance.endOfFrame;
          await const MethodChannel('gymnota/catalogWarmup')
              .invokeMethod('primed');
        }
      }
    } catch (_) {
      if (historyRasterWarmup) {
        const MethodChannel('gymnota/historyWarmup')
            .invokeMethod('failed')
            .catchError((_) => null);
      }
      if (mounted) setState(() => failure = 'Unable to load / 无法加载');
    }
  }

  Future<void> changeLanguage(String value) async {
    try {
      final l = Map<String, String>.from(
        await bridge.invokeMethod('labels', {'language': value}),
      );
      if (mounted) {
        setState(() {
          language = value;
          labels = l;
        });
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.maybeOf(context)?.showSnackBar(
          SnackBar(content: Text(labels['flutter_error'] ?? 'Unable to load')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final dark = config?['dark'] == true;
    final primary = Color((config?['primary'] as int?) ?? 0xff009688);
    final historyForeground = config?['onBackground'] as int?;
    final foreground = historyForeground == null
        ? primary
        : Color(historyForeground);
    return glassApplication(
      MaterialApp(
        debugShowCheckedModeBanner: false,
        navigatorKey: navigatorKey,
        navigatorObservers: [popupObserver],
        locale: Locale(language),
        supportedLocales: const [
          Locale("zh"),
          Locale("en"),
          Locale("es"),
          Locale("de"),
          Locale("ja"),
        ],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: primary,
            primary: primary,
            onSurface: historyForeground == null ? null : foreground,
            brightness: dark ? Brightness.dark : Brightness.light,
          ),
          textTheme: (dark ? ThemeData.dark() : ThemeData.light()).textTheme
              .apply(bodyColor: foreground, displayColor: primary),
          useMaterial3: true,
        ),
        builder: (context, child) => GlassScene(
          config: config ?? {},
          child: (catalogRasterWarmup || historyRasterWarmup) && config != null
              ? Stack(
                  children: [
                    child ?? const SizedBox.shrink(),
                    Positioned(
                      left: 20,
                      right: 20,
                      bottom: 24,
                      child: Material(
                        type: MaterialType.transparency,
                        child: Wrap(
                          spacing: 8,
                          children: [
                            if (historyRasterWarmup)
                              SizedBox(
                                width: 180,
                                child: GlassSegments<int>(
                                  values: const [0, 1],
                                  labels: const [' ', ' '],
                                  selected: 0,
                                  onChanged: (_) {},
                                ),
                              ),
                            GlassDialogAction(
                              label: labels['btn_cancel'] ?? '',
                              onPressed: () {},
                            ),
                            GlassDialogAction(
                              label: labels['catalog_use_exercise'] ?? '',
                              icon: Icons.add,
                              onPressed: () {},
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                )
              : child ?? const SizedBox.shrink(),
        ),
        initialRoute: '/',
        home: Builder(
          builder: (context) {
            if (config == null) {
              return Scaffold(
                body: Center(
                  child: failure == null
                      ? const CircularProgressIndicator()
                      : Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(failure!),
                            TextButton(
                              onPressed: bootstrap,
                              child: const Text('重试 / Retry'),
                            ),
                            TextButton(
                              onPressed: () => bridge.invokeMethod('close'),
                              child: const Text('返回 / Back'),
                            ),
                          ],
                        ),
                ),
              );
            }
            if (config!['embedded'] == true) {
              return Scaffold(
                backgroundColor: Colors.transparent,
                resizeToAvoidBottomInset: false,
                body: config!['page'] == '/embedded-idle'
                    ? const SizedBox.expand()
                    : CatalogViewport(
                        config: config!,
                        child: config!['page'] == '/embedded-coach'
                            ? MediaQuery.removePadding(
                                context: context,
                                removeTop: true,
                                removeBottom: true,
                                child: CoachPreview(
                                  key: ValueKey(config!['catalogSession']),
                                  labels: labels,
                                  stateChannel:
                                      config!['coachEventChannel'] as String?,
                                ),
                              )
                            : config!['page'] == '/embedded-history'
                            ? MediaQuery.removePadding(
                                context: context,
                                removeTop: true,
                                removeBottom: true,
                                child: HistoryPage(
                                  key: ValueKey(config!['catalogSession']),
                                  language: language,
                                  labels: labels,
                                  embedded: true,
                                  initialSnapshot:
                                      config!['initialHistory'] is Map
                                      ? object(config!['initialHistory'])
                                      : null,
                                ),
                              )
                            : config!['page'] == '/embedded-settings'
                            ? SettingsPage(
                                key: ValueKey(config!['catalogSession']),
                                config: config!,
                                labels: labels,
                              )
                            : config!['exerciseManager'] == true
                            ? ExerciseLibraries(
                                key: ValueKey(config!['catalogSession']),
                                config: config!,
                                labels: labels,
                              )
                            : EmbeddedCatalog(
                                key: ValueKey(config!['catalogSession']),
                                config: config!,
                                labels: labels,
                              ),
                      ),
              );
            }
            if (config!['page'] == '/coach-preview') {
              return CoachPreview(
                labels: labels,
                onClose: () => bridge.invokeMethod('close'),
              );
            }
            return LibraryHome(
              history: config!['page'] == '/history',
              language: language,
              labels: labels,
              onLanguage: changeLanguage,
              background: Color(config!['background'] as int),
            );
          },
        ),
      ),
    );
  }
}

class LibraryHome extends StatelessWidget {
  final bool history;
  final String language;
  final Map<String, String> labels;
  final ValueChanged<String> onLanguage;
  final Color background;
  const LibraryHome({
    super.key,
    required this.history,
    required this.language,
    required this.labels,
    required this.onLanguage,
    required this.background,
  });
  @override
  Widget build(BuildContext context) {
    if (history) return HistoryPage(language: language, labels: labels);
    final supported = {
      'zh': '中文',
      'en': 'English',
      'es': 'Español',
      'de': 'Deutsch',
      'ja': '日本語',
    };
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        leading: GlassIconAction(
          label: labels['flutter_native'] ?? '',
          icon: Icons.arrow_back,
          onPressed: () => bridge.invokeMethod('close'),
        ),
        title: Text(labels[history ? 'history_title' : 'catalog_title'] ?? ''),
        actions: [
          GlassIconAction(
            icon: Icons.translate,
            label: labels['settings_language'] ?? '',
            onPressed: () => showDialog<void>(
              context: context,
              builder: (dialogContext) => GlassAlertDialog(
                title: Text(labels['settings_language'] ?? ''),
                content: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (final entry in supported.entries)
                      ListTile(
                        title: Text(entry.value),
                        selected: entry.key == language,
                        onTap: () {
                          Navigator.pop(dialogContext);
                          onLanguage(entry.key);
                        },
                      ),
                  ],
                ),
                actions: [
                  GlassAction(
                    label: labels['btn_cancel'] ?? '',
                    onPressed: () => Navigator.pop(dialogContext),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
      body: history
          ? HistoryPage(language: language, labels: labels)
          : EmbeddedCatalog(
              config: {'language': language, 'showSearch': true},
              labels: labels,
            ),
    );
  }
}

class MediaCache {
  static void clear() {
    _values.clear();
    _ready.clear();
  }

  static final _ready = <String, Uint8List>{};
  static Uint8List? peek(String id, bool thumbnail) => _ready['$id:$thumbnail'];
  static final _values = <String, Future<Uint8List>>{};
  static Future<Uint8List> load(String id, bool thumbnail) {
    final key = '$id:$thumbnail';
    final old = _values.remove(key);
    if (old != null) {
      _values[key] = old;
      return old;
    }
    final value = bridge
        .invokeMethod<Uint8List>('media', {'id': id, 'thumbnail': thumbnail})
        .then((v) {
          if (_values.containsKey(key)) _ready[key] = v!;
          return v!;
        });
    _values[key] = value;
    if (_values.length > 64) {
      final oldest = _values.keys.first;
      _values.remove(oldest);
      _ready.remove(oldest);
    }
    value.then<void>(
      (_) {},
      onError: (Object e, StackTrace st) {
        if (identical(_values[key], value)) _values.remove(key);
      },
    );
    return value;
  }
}

class ExerciseImage extends StatefulWidget {
  final String id;
  final bool thumbnail;
  final double size;
  final String retryLabel;
  const ExerciseImage({
    super.key,
    required this.id,
    required this.thumbnail,
    this.size = 64,
    this.retryLabel = "",
  });
  @override
  State<ExerciseImage> createState() => _ExerciseImageState();
}

class _ExerciseImageState extends State<ExerciseImage> {
  late Future<Uint8List> bytes;
  @override
  void initState() {
    super.initState();
    bytes = MediaCache.load(widget.id, widget.thumbnail);
  }

  @override
  void didUpdateWidget(ExerciseImage old) {
    super.didUpdateWidget(old);
    if (old.id != widget.id || old.thumbnail != widget.thumbnail) {
      bytes = MediaCache.load(widget.id, widget.thumbnail);
    }
  }

  @override
  Widget build(BuildContext context) => SizedBox(
    width: widget.size,
    height: widget.size,
    child: FutureBuilder<Uint8List>(
      key: ValueKey('${widget.id}:${widget.thumbnail}'),
      future: bytes,
      initialData: MediaCache.peek(widget.id, widget.thumbnail),
      builder: (_, snapshot) {
        if (snapshot.hasError) {
          return IconButton(
            tooltip: widget.retryLabel,
            icon: const Icon(Icons.refresh),
            onPressed: () => setState(() {
              bytes = MediaCache.load(widget.id, widget.thumbnail);
            }),
          );
        }
        if (!snapshot.hasData) {
          final thumbnail = widget.thumbnail
              ? null
              : MediaCache.peek(widget.id, true);
          if (thumbnail != null)
            return ClipRRect(
              borderRadius: BorderRadius.circular(14),
              child: Image.memory(
                thumbnail,
                fit: BoxFit.contain,
                gaplessPlayback: true,
              ),
            );
          return const Center(
            child: SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          );
        }
        return ClipRRect(
          borderRadius: BorderRadius.circular(14),
          child: Image.memory(
            snapshot.data!,
            fit: BoxFit.contain,
            cacheWidth: widget.thumbnail ? 128 : null,
            errorBuilder: (_, e, st) => const Icon(Icons.broken_image_outlined),
          ),
        );
      },
    ),
  );
}

class HistoryPage extends StatefulWidget {
  final String language;
  final Map<String, String> labels;
  final bool embedded;
  final Json? initialSnapshot;
  const HistoryPage({
    super.key,
    required this.language,
    required this.labels,
    this.embedded = false,
    this.initialSnapshot,
  });
  @override
  State<HistoryPage> createState() => _HistoryPageState();
}

class _HistoryPageState extends State<HistoryPage> with WidgetsBindingObserver {
  List<Json> tasks = [], weights = [];
  Json? charts;
  bool showCharts = true, listVisited = false;
  bool loading = true, failed = false, busy = false;
  String query = '';
  final search = TextEditingController();
  int revision = -1;
  int? recordsRevision;
  bool recordsLoading = false, recordsFailed = false;

  void accept(Json data) {
    final incoming = (data['revision'] as num?)?.toInt() ?? revision;
    if (!mounted ||
        incoming < revision ||
        (incoming == revision && !loading && !failed && !busy))
      return;
    setState(() {
      revision = incoming;
      if (data['tasks'] is List && data['weights'] is List) {
        tasks = objects(data['tasks']);
        weights = objects(data['weights']);
        recordsRevision = incoming;
      }
      charts = data['charts'] is Map ? object(data['charts']) : null;
      loading = false;
      busy = false;
      failed = false;
    });
    if (widget.embedded && !showCharts && recordsRevision != revision) {
      unawaited(loadRecords());
    }
  }

  Future<void> loadRecords({bool force = false}) async {
    if ((recordsRevision == revision && !force) || recordsLoading) return;
    setState(() {
      recordsLoading = true;
      recordsFailed = false;
    });
    try {
      final data = object(
        await bridge.invokeMethod('history', {
          'backup': false,
          'records': true,
          if (force) 'refresh': true,
        }),
      );
      if (!mounted) return;
      setState(() {
        recordsRevision = (data['revision'] as num?)?.toInt() ?? revision;
        tasks = objects(data['tasks']);
        weights = objects(data['weights']);
      });
    } catch (_) {
      if (mounted) setState(() => recordsFailed = true);
    } finally {
      if (mounted) {
        setState(() => recordsLoading = false);
        if (!showCharts &&
            !recordsFailed &&
            (recordsRevision ?? -1) < revision) {
          unawaited(loadRecords());
        }
      }
    }
  }

  void onSnapshot() {
    if (historySnapshots.value case final data?) accept(data);
  }

  void onSnapshotError() {
    if (mounted && historySnapshotFailed.value) setState(() => failed = true);
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    historySnapshots.addListener(onSnapshot);
    historySnapshotFailed.addListener(onSnapshotError);
    final cached = historySnapshots.value;
    final seeded = widget.initialSnapshot;
    final initial =
        seeded != null &&
            (cached == null ||
                ((seeded['revision'] as num?) ?? 0) >=
                    ((cached['revision'] as num?) ?? 0))
        ? seeded
        : cached;
    if (initial != null) {
      accept(initial);
    } else {
      load();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    historySnapshots.removeListener(onSnapshot);
    historySnapshotFailed.removeListener(onSnapshotError);
    search.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!widget.embedded && state == AppLifecycleState.resumed) load();
  }

  Future<void> load({bool force = false}) async {
    if (busy) return;
    setState(() {
      busy = true;
      failed = false;
    });
    try {
      // Settings owns restore. This is the live Room snapshot, including restored records.
      final data = object(
        await bridge.invokeMethod('history', {
          'backup': false,
          if (force) 'refresh': true,
        }),
      );
      if (!mounted) return;
      accept(data);
    } catch (_) {
      if (mounted)
        setState(() {
          failed = true;
          loading = false;
          busy = false;
        });
    }
  }

  double get tabsHeight => glassSegmentHeight(context);
  Widget tabs() => GlassSegments<bool>(
    height: tabsHeight,
    key: const ValueKey('history-mode'),
    primary: true,
    values: const [false, true],
    labels: [widget.labels['tab_list'] ?? '', widget.labels['tab_chart'] ?? ''],
    selected: showCharts,
    onChanged: (value) {
      FocusScope.of(context).unfocus();
      setState(() {
        showCharts = value;
        if (!value) listVisited = true;
      });
      if (widget.embedded && !value) unawaited(loadRecords());
    },
  );
  @override
  Widget build(BuildContext context) {
    final l = widget.labels;
    final titleStyle = rootPageTitleStyle(context);
    final measure =
        TextPainter(
          text: TextSpan(text: l['history_title'] ?? '', style: titleStyle),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(
          maxWidth:
              (MediaQuery.sizeOf(context).width - (widget.embedded ? 32 : 88))
                  .clamp(1.0, double.infinity),
        );
    final titleHeight = measure.height + 24;
    final measureHeight = measure.height;
    measure.dispose();
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        scrolledUnderElevation: 0,
        systemOverlayStyle: Theme.of(context).brightness == Brightness.dark
            ? SystemUiOverlayStyle.light
            : SystemUiOverlayStyle.dark,
        toolbarHeight: widget.embedded ? 0 : titleHeight,
        primary: !widget.embedded,
        automaticallyImplyLeading: false,
        leading: widget.embedded
            ? null
            : GlassIconAction(
                label: l['flutter_native'] ?? '',
                icon: Icons.arrow_back,
                onPressed: () => bridge.invokeMethod('close'),
              ),
        centerTitle: false,
        title: widget.embedded
            ? null
            : MediaQuery(
                data: MediaQuery.of(context),
                child: Transform.translate(
                  offset: const Offset(0, 12),
                  child: Text(
                    l['history_title'] ?? '',
                    softWrap: true,
                    overflow: TextOverflow.visible,
                    style: titleStyle,
                  ),
                ),
              ),
        bottom: PreferredSize(
          preferredSize: Size.fromHeight(
            tabsHeight + 12 + (widget.embedded ? measureHeight + 48 : 0),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (widget.embedded)
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 32, 16, 16),
                  child: Semantics(
                    header: true,
                    child: Text(l['history_title'] ?? '', style: titleStyle),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: tabs(),
              ),
            ],
          ),
        ),
      ),
      body: Column(
        children: [
          if (!showCharts)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Glass(
                radius: 16,
                child: TextField(
                  key: const ValueKey('history-search'),
                  controller: search,
                  onChanged: (value) => setState(() => query = value),
                  decoration: InputDecoration(
                    prefixIcon: const Icon(Icons.search),
                    hintText: l['flutter_history_search'],
                    border: InputBorder.none,
                    enabledBorder: InputBorder.none,
                    focusedBorder: InputBorder.none,
                    suffixIcon: query.isEmpty
                        ? null
                        : IconButton(
                            tooltip: MaterialLocalizations.of(context)
                                .deleteButtonTooltip,
                            icon: const Icon(Icons.close),
                            onPressed: () {
                              search.clear();
                              setState(() => query = '');
                            },
                          ),
                  ),
                ),
              ),
            ),
          Expanded(
            child: loading
                ? const Center(child: CircularProgressIndicator())
                : failed
                ? Failure(labels: l, retry: () => load(force: true))
                : Stack(
                    children: [
                      Offstage(
                        offstage: !showCharts,
                        child: TickerMode(
                          enabled: showCharts,
                          child: RefreshIndicator(
                            onRefresh: () => load(force: true),
                            child: HistoryChartsView(
                              key: const PageStorageKey('history-charts'),
                              bundle: charts,
                              labels: l,
                              onNative: () => bridge.invokeMethod('close'),
                            ),
                          ),
                        ),
                      ),
                      if (listVisited)
                        Offstage(
                          offstage: showCharts,
                          child: TickerMode(
                            enabled: !showCharts,
                            child: RefreshIndicator(
                              onRefresh: () => widget.embedded
                                  ? loadRecords(force: true)
                                  : load(force: true),
                              child: recordsFailed && recordsRevision == null
                                  ? Failure(labels: l, retry: loadRecords)
                                  : recordsLoading && recordsRevision == null
                                  ? const Center(
                                      child: CircularProgressIndicator(),
                                    )
                                  : HistoryRecordsView(
                                      tasks: tasks,
                                      weights: weights,
                                      labels: l,
                                      query: query,
                                    ),
                            ),
                          ),
                        ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}

class Failure extends StatelessWidget {
  final Map<String, String> labels;
  final VoidCallback retry;
  const Failure({super.key, required this.labels, required this.retry});
  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.all(20),
          child: Text(labels['flutter_error'] ?? ''),
        ),
        TextButton(
          onPressed: retry,
          child: Text(labels['flutter_retry'] ?? ''),
        ),
      ],
    ),
  );
}
