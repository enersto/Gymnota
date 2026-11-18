import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart' as lg;

import 'coach_preview.dart';
import 'exercise_libraries.dart';
import 'glass.dart';
import 'main.dart' show HistoryPage, Json, bridge, object, objects;
import 'settings_page.dart';
import 'workout_preview_page.dart';

final rootNavigationEvents = ValueNotifier<String?>(null);

class RootNavigationPreview extends StatefulWidget {
  const RootNavigationPreview({
    super.key,
    required this.config,
    required this.labels,
  });
  final Json config;
  final Map<String, String> labels;

  @override
  State<RootNavigationPreview> createState() => _RootNavigationPreviewState();
}

class _RootNavigationPreviewState extends State<RootNavigationPreview> {
  int tab = 0;
  final visited = {0};
  String query = '';
  String label(String key) => widget.labels[key] ?? '';

  bool get primary => widget.config['primaryHost'] == true;
  String? requestedSection;
  int requestToken = 0;

  @override
  void initState() {
    super.initState();
    rootNavigationEvents.addListener(handleEvent);
  }

  @override
  void dispose() {
    rootNavigationEvents.removeListener(handleEvent);
    super.dispose();
  }

  void handleEvent() {
    final event = rootNavigationEvents.value;
    if (!mounted || !primary || event == null) return;
    if (event == 'rootModelSettings' || event == 'rootScheduleSettings') {
      setState(() {
        tab = 4;
        visited.add(tab);
        requestedSection = event == 'rootModelSettings'
            ? 'ai-model'
            : 'schedule-type';
        requestToken++;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final labels = widget.labels;
    final config = widget.config;
    const titles = [
      'tab_home',
      'tab_history',
      'tab_exercises',
      'tab_ai_coach',
      'tab_settings',
    ];
    const icons = [
      Icons.calendar_today_outlined,
      Icons.history,
      Icons.fitness_center,
      Icons.smart_toy_outlined,
      Icons.settings_outlined,
    ];
    final pages = <Widget Function()>[
      () => primary
          ? WorkoutPreviewPage(labels: labels, embedded: true, config: config)
          : _DailyPlanPreview(labels: labels),
      () => HistoryPage(
        language: config['language'] as String,
        labels: labels,
        embedded: true,
        initialSnapshot: config['initialHistory'] is Map
            ? object(config['initialHistory'])
            : null,
      ),
      () => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 32, 16, 12),
            child: Semantics(
              header: true,
              child: Text(
                label('tab_exercises'),
                style: rootPageTitleStyle(context),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: GlassInputSurface(
              child: TextField(
                onChanged: (value) => setState(() => query = value),
                decoration: InputDecoration(
                  prefixIcon: const Icon(Icons.search),
                  hintText: label('catalog_search'),
                  border: InputBorder.none,
                ),
              ),
            ),
          ),
          Expanded(
            child: ExerciseLibraries(
              config: {...config, 'query': query},
              labels: labels,
            ),
          ),
        ],
      ),
      () => CoachPreview(labels: labels),
      () => SettingsPage(
        config: {
          ...config,
          'requestedSection': requestedSection,
          'requestToken': requestToken,
        },
        labels: labels,
      ),
    ];
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value:
          (theme.brightness == Brightness.dark
                  ? SystemUiOverlayStyle.light
                  : SystemUiOverlayStyle.dark)
              .copyWith(statusBarColor: Colors.transparent),
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          bottom: false,
          child: Column(
            children: [
              if (!primary)
                Padding(
                  padding: const EdgeInsets.only(left: 16, right: 8),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          label('root_preview_title'),
                          style: theme.textTheme.labelLarge,
                        ),
                      ),
                      IconButton(
                        onPressed: () => bridge.invokeMethod<void>('close'),
                        tooltip: label('btn_close'),
                        icon: const Icon(Icons.close),
                      ),
                    ],
                  ),
                ),
              Expanded(
                child: Builder(
                  builder: (bodyContext) => MediaQuery.removePadding(
                    // Use the Scaffold body's already-adjusted insets.
                    context: bodyContext,
                    removeTop: true,
                    removeBottom: true,
                    child: IndexedStack(
                      index: tab,
                      sizing: StackFit.expand,
                      children: [
                        for (var i = 0; i < pages.length; i++)
                          TickerMode(
                            enabled: tab == i,
                            child: visited.contains(i)
                                ? pages[i]()
                                : const SizedBox.shrink(),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        bottomNavigationBar: Builder(
          builder: (context) => MediaQuery.viewInsetsOf(context).bottom > 0
              ? const SizedBox.shrink()
              : SafeArea(
                  top: false,
                  child: lg.GlassTabBar.bottom(
                    tabs: [
                      for (var i = 0; i < icons.length; i++)
                        lg.GlassTab(
                          icon: Icon(icons[i]),
                          label: label(titles[i]),
                        ),
                    ],
                    selectedIndex: tab,
                    onTabSelected: (index) {
                      if (index == tab) return;
                      FocusManager.instance.primaryFocus?.unfocus();
                      HapticFeedback.selectionClick();
                      setState(() {
                        tab = index;
                        visited.add(index);
                      });
                    },
                    selectedLabelColor: theme.colorScheme.onSurface,
                    unselectedLabelColor: theme.colorScheme.onSurface,
                    selectedIconColor: theme.colorScheme.primary,
                    unselectedIconColor: theme.colorScheme.onSurface,
                    quality: lg.GlassQuality.standard,
                    backgroundQuality: lg.GlassQuality.standard,
                    settings: glassSettings(context),
                    iconSize: 22,
                    labelFontSize: 10,
                    horizontalPadding: 12,
                    verticalPadding: 8,
                  ),
                ),
        ),
      ),
    );
  }
}

class _DailyPlanPreview extends StatefulWidget {
  const _DailyPlanPreview({required this.labels});
  final Map<String, String> labels;

  @override
  State<_DailyPlanPreview> createState() => _DailyPlanPreviewState();
}

class _DailyPlanPreviewState extends State<_DailyPlanPreview> {
  late Stream<dynamic> snapshots;

  @override
  void initState() {
    super.initState();
    subscribe();
  }

  void subscribe() {
    snapshots = const EventChannel('gymnota/preview/daily')
        .receiveBroadcastStream();
  }

  String label(String name) => widget.labels[name] ?? '';

  @override
  Widget build(BuildContext context) => StreamBuilder<dynamic>(
    stream: snapshots,
    builder: (context, snapshot) {
      if (snapshot.hasError)
        return Center(
          child: GlassCommand(
            label: label('flutter_retry'),
            onPressed: () => setState(subscribe),
          ),
        );
      if (!snapshot.hasData)
        return const Center(child: CircularProgressIndicator());
      final plan = object(snapshot.data);
      final day = DateTime.parse(plan['date'] as String);
      final tasks = objects(plan['tasks']);
      final timer = object(plan['timer']);
      final completed = tasks.where((task) => task['completed'] == true).length;
      final theme = Theme.of(context);
      return ListView.builder(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
        itemCount: 1 + tasks.length,
        itemBuilder: (context, index) => index == 0
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    MaterialLocalizations.of(context).formatFullDate(day),
                    style: theme.textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    plan['dayLabel'] as String,
                    style: rootPageTitleStyle(context),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    label('preview_daily_read_only'),
                    style: theme.textTheme.bodySmall,
                  ),
                  if (tasks.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 40),
                      child: Center(child: Text(label('preview_daily_empty'))),
                    )
                  else ...[
                    const SizedBox(height: 12),
                    LinearProgressIndicator(
                      value: completed / tasks.length,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    const SizedBox(height: 20),
                  ],
                ],
              )
            : taskCard(context, tasks[index - 1], timer),
      );
    },
  );

  Widget taskCard(BuildContext context, Json task, Json timer) {
    final theme = Theme.of(context);
    final metadata = [
      task['bodyLabel'],
      task['equipment'],
      task['recordType'],
    ].whereType<String>().where((part) => part.isNotEmpty).join(' · ');
    final sets = objects(task['sets']);
    final active =
        timer['id'] == task['id'] &&
        (timer['running'] == true || timer['paused'] == true);
    final seconds = (timer['seconds'] as int).clamp(0, 86400);
    final minutes = (seconds ~/ 60).toString().padLeft(2, '0');
    final remainder = (seconds % 60).toString().padLeft(2, '0');
    final timerLabel = label(
      timer['paused'] == true
          ? 'timer_pause'
          : timer['phase'] == 'PREP'
          ? 'timer_prep_title'
          : 'settings_timer_title',
    );
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Glass(
        definedEdge: true,
        radius: 16,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      task['name'] as String,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  if (task['completed'] == true)
                    Tooltip(
                      message: label('btn_done'),
                      child: const Icon(Icons.check_circle_outline),
                    ),
                ],
              ),
              if (metadata.isNotEmpty)
                Text(metadata, style: theme.textTheme.bodySmall),
              if ((task['target'] as String).isNotEmpty) ...[
                const SizedBox(height: 6),
                Text([label('label_target'), task['target']].join(' · ')),
              ],
              if (active) ...[
                const SizedBox(height: 8),
                Text(
                  '$timerLabel · $minutes:$remainder',
                  style: theme.textTheme.titleMedium,
                ),
              ],
              if (sets.isEmpty && (task['actualWeight'] as String).isNotEmpty)
                Text(task['actualWeight'] as String),
              if (sets.isNotEmpty) ...[
                const SizedBox(height: 8),
                for (final set in sets)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Text(
                      [set['number'], setLabel(task, set)].join('   '),
                    ),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  String setLabel(Json task, Json set) {
    String values(Object? value, Object? reps) => [
      value,
      reps,
    ].whereType<String>().where((text) => text.isNotEmpty).join(' × ');
    final left = values(set['value'], set['reps']);
    if (task['logType'] == 1)
      return set['value'] == '' ? '—' : values(set['value'], null);
    if (task['logType'] == 2) return values(null, set['reps']);
    if (task['unilateral'] != true) return left;
    final right = values(set['rightWeight'], set['rightReps']);
    return [
      [label('label_side_left'), left].join(' '),
      [label('label_side_right'), right].join(' '),
    ].join('   ');
  }
}
