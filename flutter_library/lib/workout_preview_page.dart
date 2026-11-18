import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'exercise_libraries.dart';
import 'glass.dart';
import 'training_forms.dart';
import 'main.dart' show Json, object, objects;

class WorkoutPreviewPage extends StatefulWidget {
  const WorkoutPreviewPage({
    super.key,
    required this.labels,
    this.embedded = false,
    this.config = const {},
  });
  final Map<String, String> labels;
  final bool embedded;
  final Json config;
  @override
  State<WorkoutPreviewPage> createState() => _WorkoutPreviewPageState();
}

class _WorkoutPreviewPageState extends State<WorkoutPreviewPage>
    with WidgetsBindingObserver {
  static const commands = MethodChannel('gymnota/workout');
  late Stream<dynamic> stream;
  final pendingFields = <(int, int, String), String>{};
  Timer? fieldDebounce;
  Future<void> fieldWrite = Future<void>.value();
  final removed = <int>{};
  int? celebration;
  int? revealedTask;
  final expanded = <int>{};
  final timerMinutes = <(int, int), String>{};
  final timerSeconds = <(int, int), String>{};
  String l(String key) => widget.labels[key] ?? '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    listen();
    commands.setMethodCallHandler((call) async {
      if (call.method != 'notificationGuide' || !mounted) return null;
      await showDialog<void>(
        context: context,
        builder: (dialog) => GlassAlertDialog(
          title: Text(l('dialog_lock_screen_title')),
          content: Text(l('dialog_lock_screen_content')),
          actions: [
            GlassCommand(
              label: l('btn_later'),
              onPressed: () {
                Navigator.pop(dialog);
                act('notification-later');
              },
            ),
            GlassCommand(
              label: l('btn_go_to_settings'),
              onPressed: () {
                Navigator.pop(dialog);
                act('notification-settings');
              },
            ),
          ],
        ),
      );
      return null;
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    unawaited(flushFields());
    commands.setMethodCallHandler(null);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) unawaited(flushFields());
  }

  void queueField(int id, int index, String field, String text) {
    pendingFields[(id, index, field)] = text;
    fieldDebounce?.cancel();
    fieldDebounce = Timer(const Duration(milliseconds: 300), flushFields);
  }

  Future<void> flushFields() {
    fieldDebounce?.cancel();
    if (pendingFields.isEmpty) return fieldWrite;
    final edits = pendingFields.entries.map((edit) {
      final (id, index, field) = edit.key;
      return {'id': id, 'index': index, 'field': field, 'text': edit.value};
    }).toList();
    pendingFields.clear();
    fieldWrite = fieldWrite.then((_) async {
      await send('setFields', {'edits': edits});
    });
    return fieldWrite;
  }

  void listen() {
    stream = const EventChannel('gymnota/preview/daily')
        .receiveBroadcastStream();
  }

  Future<bool> act(String method, [Json args = const {}]) async {
    await flushFields();
    return send(method, args);
  }

  Future<bool> send(String method, Json args) async {
    try {
      await commands.invokeMethod<void>(method, args);
      return true;
    } catch (_) {
      if (mounted)
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(l('flutter_error'))));
      return false;
    }
  }

  List<Json> get personalRows => objects(widget.config['personalRows']);

  Future<void> recordWeight() async {
    if (!widget.embedded) {
      await act('weight');
      return;
    }
    final data = await editBodyMetrics(
      context,
      widget.labels,
      object(widget.config['bodyProfile']),
      recordWeight: true,
    );
    if (data != null && mounted) await act('saveWeight', data);
  }

  Future<void> pickExercise() async {
    if (!widget.embedded) {
      await act('pick');
      return;
    }
    final chosen = await pickPersonalExercise(
      context,
      personalRows,
      widget.labels,
    );
    if (chosen != null && mounted) {
      await act('addExercise', {'id': int.parse(chosen)});
    }
  }

  Future<void> showExerciseDetail(Json task) async {
    final matches = personalRows.where(
      (row) => row['id'] == task['templateId'],
    );
    final row =
        matches.firstOrNull ??
        {...task, 'logTypeLabel': task['recordType'] ?? ''};
    await showDialog<void>(
      context: context,
      builder: (_) => PersonalExerciseDetail(
        row: row,
        labels: widget.labels,
        editable: false,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return StreamBuilder<dynamic>(
      stream: stream,
      builder: (context, snapshot) {
        if (snapshot.hasError)
          return Center(
            child: GlassCommand(
              label: l('flutter_retry'),
              onPressed: () => setState(listen),
            ),
          );
        if (!snapshot.hasData)
          return const Center(child: CircularProgressIndicator());
        final plan = object(snapshot.data);
        final all = objects(plan['tasks']);
        final tasks = all
            .where((task) => !removed.contains(task['id']))
            .toList();
        final completed = tasks
            .where((task) => task['completed'] == true)
            .length;
        final timer = object(plan['timer']);
        return Scaffold(
          backgroundColor: Colors.transparent,
          resizeToAvoidBottomInset: !widget.embedded,
          appBar: widget.embedded
              ? null
              : AppBar(
                  backgroundColor: Colors.transparent,
                  systemOverlayStyle: theme.brightness == Brightness.dark
                      ? SystemUiOverlayStyle.light
                      : SystemUiOverlayStyle.dark,
                  surfaceTintColor: Colors.transparent,
                  title: Text(
                    l('preview_workout_title'),
                    style: rootPageTitleStyle(context),
                  ),
                ),
          floatingActionButton: plan['rest'] == true
              ? null
              : GlassIconAction(
                  icon: Icons.add,
                  label: l('btn_add'),
                  onPressed: pickExercise,
                ),
          body: Stack(
            children: [
              CustomScrollView(
                slivers: [
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            MaterialLocalizations.of(context).formatFullDate(
                              DateTime.parse(plan['date'] as String),
                            ),
                            style: theme.textTheme.titleMedium?.copyWith(
                              color: theme.colorScheme.onSurface.withValues(
                                alpha: .68,
                              ),
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 10,
                            runSpacing: 6,
                            crossAxisAlignment: WrapCrossAlignment.center,
                            children: [
                              InkWell(
                                onTap: () => act('schedule'),
                                child: Text(
                                  plan['dayLabel'] as String,
                                  style: theme.textTheme.headlineLarge
                                      ?.copyWith(
                                        color: Color.lerp(
                                          theme.colorScheme.primary,
                                          theme.colorScheme.onSurface,
                                          .65,
                                        ),
                                        fontWeight: FontWeight.w900,
                                      ),
                                ),
                              ),
                              if (plan['weightAlert'] == true)
                                FilledButton(
                                  style: FilledButton.styleFrom(
                                    backgroundColor: const Color(0xFFFF9800),
                                    minimumSize: const Size(0, 28),
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 4,
                                    ),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    textStyle: const TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.bold,
                                    ),
                                    foregroundColor: Colors.white,
                                  ),
                                  onPressed: recordWeight,
                                  child: Text(l('log_weight')),
                                ),
                            ],
                          ),
                          const SizedBox(height: 20),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: LinearProgressIndicator(
                              value: tasks.isEmpty
                                  ? 0
                                  : completed / tasks.length,
                              minHeight: 10,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  if (tasks.isEmpty)
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 40),
                        child: Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.fitness_center,
                                size: 64,
                                color: theme.colorScheme.primary.withValues(
                                  alpha: .24,
                                ),
                              ),
                              const SizedBox(height: 16),
                              Text(
                                plan['rest'] == true
                                    ? plan['dayLabel'] as String
                                    : l('no_plan'),
                              ),
                              if (plan['rest'] != true) ...[
                                const SizedBox(height: 12),
                                GlassCommand(
                                  label: l('apply_routine'),
                                  onPressed: () => act('routine'),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ),
                    )
                  else
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 72),
                      sliver: SliverList.separated(
                        itemCount: tasks.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 12),
                        itemBuilder: (context, index) =>
                            taskCard(context, tasks[index], timer),
                      ),
                    ),
                ],
              ),
              if (timer['running'] == true && timer['alert'] == true)
                Positioned.fill(
                  child: IgnorePointer(
                    child: ColoredBox(
                      color: Colors.black.withValues(alpha: .9),
                      child: Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              l(
                                timer['phase'] == 'PREP'
                                    ? 'timer_overlay_prep'
                                    : 'timer_overlay_finish',
                              ),
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: Colors.white.withValues(alpha: .7),
                                fontSize: 36,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 24),
                            Text(
                              timer['seconds'].toString(),
                              style: TextStyle(
                                color: timer['phase'] == 'PREP'
                                    ? Colors.orange
                                    : Colors.red,
                                fontSize: 140,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              if (celebration case final int stamp)
                Positioned.fill(
                  child: IgnorePointer(
                    child: TweenAnimationBuilder<double>(
                      key: ValueKey(stamp),
                      duration: const Duration(milliseconds: 1000),
                      tween: Tween(begin: 0, end: 1),
                      onEnd: () {
                        if (mounted && celebration == stamp)
                          setState(() => celebration = null);
                      },
                      builder: (_, progress, child) => CustomPaint(
                        painter: _WorkoutParticles(progress),
                        child: child,
                      ),
                      child: const Center(
                        child: Text('🎉', style: TextStyle(fontSize: 100)),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }

  String clock(int seconds) => [
    (seconds ~/ 60).toString().padLeft(2, '0'),
    (seconds % 60).toString().padLeft(2, '0'),
  ].join(':');

  Widget taskCard(BuildContext context, Json task, Json timer) {
    final theme = Theme.of(context);
    final id = task['id'] as int;
    final open = expanded.contains(id);
    final complete = task['completed'] == true;
    final sets = objects(task['sets']);
    final timed = task['logType'] == 1;
    final repsOnly = task['logType'] == 2;
    final detail = () => showExerciseDetail(task);
    final info = [
      task['bodyLabel'],
      task['equipment'],
    ].whereType<String>().where((text) => text.isNotEmpty).join(' | ');
    return _WorkoutSwipeTile(
      key: ValueKey(id),
      revealed: revealedTask == id,
      deleteLabel: l('btn_delete'),
      onReveal: (show) {
        setState(() {
          if (show)
            revealedTask = id;
          else if (revealedTask == id)
            revealedTask = null;
        });
      },
      onDelete: () async {
        setState(() {
          removed.add(id);
          if (revealedTask == id) revealedTask = null;
        });
        if (!await act('removeTask', {'id': id}) && mounted)
          setState(() => removed.remove(id));
      },
      child: Glass(
        radius: 20,
        definedEdge: true,
        translucent: true,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: InkWell(
                      onTap: () => setState(() {
                        if (revealedTask == id) {
                          revealedTask = null;
                        } else if (open) {
                          expanded.remove(id);
                        } else {
                          expanded.add(id);
                        }
                      }),
                      child: Row(
                        children: [
                          if (task['hasMedia'] == true &&
                              task['templateId'] is int) ...[
                            GestureDetector(
                              onTap: open ? detail : null,
                              child: PersonalExerciseThumbnail(
                                key: ValueKey((
                                  task['templateId'],
                                  task['mediaRevision'],
                                )),
                                id: task['templateId'] as int,
                              ),
                            ),
                            const SizedBox(width: 12),
                          ],
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  task['name'] as String,
                                  style: theme.textTheme.titleMedium?.copyWith(
                                    fontWeight: FontWeight.w800,
                                    color: Color.lerp(
                                      theme.colorScheme.onSurface,
                                      theme.brightness == Brightness.dark
                                          ? Colors.white
                                          : Colors.black,
                                      .20,
                                    )!.withValues(alpha: complete ? .5 : 1),
                                    decoration: complete
                                        ? TextDecoration.lineThrough
                                        : null,
                                  ),
                                ),
                                if (info.isNotEmpty ||
                                    task['unilateral'] == true)
                                  Padding(
                                    padding: const EdgeInsets.only(top: 4),
                                    child: Wrap(
                                      spacing: 6,
                                      runSpacing: 4,
                                      crossAxisAlignment:
                                          WrapCrossAlignment.center,
                                      children: [
                                        if (task['unilateral'] == true)
                                          DecoratedBox(
                                            decoration: BoxDecoration(
                                              color: theme
                                                  .colorScheme
                                                  .tertiaryContainer
                                                  .withValues(alpha: .5),
                                              borderRadius:
                                                  BorderRadius.circular(5),
                                            ),
                                            child: Padding(
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                    horizontal: 5,
                                                    vertical: 2,
                                                  ),
                                              child: Text(
                                                l('label_unilateral_mode'),
                                                style: theme
                                                    .textTheme
                                                    .labelSmall
                                                    ?.copyWith(
                                                      color: theme
                                                          .colorScheme
                                                          .onTertiaryContainer,
                                                    ),
                                              ),
                                            ),
                                          ),
                                        if (info.isNotEmpty)
                                          Text(
                                            info,
                                            style: theme.textTheme.labelSmall
                                                ?.copyWith(
                                                  color: theme
                                                      .colorScheme
                                                      .onSurface
                                                      .withValues(alpha: .7),
                                                ),
                                          ),
                                      ],
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  GlassCommand(
                    label: l(complete ? 'btn_done' : 'btn_check'),
                    minWidth: 92,
                    primary: complete,
                    icon: complete ? Icons.check : null,
                    onPressed: () async {
                      if (await act('check', {'id': id}) &&
                          !complete &&
                          mounted)
                        setState(() => celebration = (celebration ?? 0) + 1);
                    },
                  ),
                ],
              ),
              AnimatedSize(
                duration: const Duration(milliseconds: 150),
                alignment: Alignment.topCenter,
                child: open
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const SizedBox(height: 12),
                          const Divider(),
                          if (task['hasMedia'] != true)
                            Align(
                              alignment: AlignmentDirectional.centerStart,
                              child: TextButton(
                                onPressed: detail,
                                child: Text(l('label_instruction')),
                              ),
                            ),
                          if (!timed)
                            Row(
                              children: [
                                SizedBox(
                                  width: 32,
                                  child: Text(
                                    l('header_set_no'),
                                    style: theme.textTheme.labelSmall,
                                  ),
                                ),
                                if (!repsOnly && task['unilateral'] == true)
                                  SizedBox(width: sideColumnWidth(context)),
                                if (!repsOnly)
                                  Expanded(
                                    child: Text(
                                      l('header_weight_time'),
                                      style: theme.textTheme.labelSmall,
                                    ),
                                  ),
                                Expanded(
                                  child: Text(
                                    l('label_reps'),
                                    style: theme.textTheme.labelSmall,
                                  ),
                                ),
                              ],
                            ),
                          for (final (index, set) in sets.indexed)
                            Padding(
                              key: ValueKey((index, set['number'])),
                              padding: const EdgeInsets.only(top: 8),
                              child: timed
                                  ? timerRow(task, timer, set, index)
                                  : setRow(task, set, index, repsOnly),
                            ),
                          const SizedBox(height: 12),
                          Center(
                            child: GlassCommand(
                              label: l('btn_add_set'),
                              icon: Icons.add,
                              onPressed: () => act('addSet', {'id': id}),
                            ),
                          ),
                        ],
                      )
                    : const SizedBox.shrink(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget input(String value, String hint, ValueChanged<String> change) => Focus(
    canRequestFocus: false,
    onFocusChange: (focused) {
      if (!focused) unawaited(flushFields());
    },
    child: TextFormField(
      initialValue: value,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      onChanged: change,
      decoration: InputDecoration(
        hintText: hint,
        filled: true,
        fillColor: Theme.of(context).colorScheme.primary
            .withValues(alpha: .075),
        contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide.none,
        ),
        isDense: true,
      ),
    ),
  );

  double sideColumnWidth(BuildContext context) =>
      MediaQuery.textScalerOf(context).scale(52).clamp(52.0, 95.0);

  Widget setRow(Json task, Json set, int index, bool repsOnly) {
    final sided = !repsOnly && task['unilateral'] == true;
    Widget field(String key) => Expanded(
      child: input(
        (set[key] as String?) ?? '',
        l(
          key == 'value' || key == 'rightWeight'
              ? 'header_weight_time'
              : 'label_reps',
        ),
        (text) => queueField(task['id'] as int, index, key, text),
      ),
    );
    Widget row({required bool right}) => Row(
      children: [
        SizedBox(
          width: 32,
          child: right ? null : Text(set['number'].toString()),
        ),
        if (sided)
          SizedBox(
            width: sideColumnWidth(context),
            child: Padding(
              padding: const EdgeInsetsDirectional.only(end: 6),
              child: Text(
                l(right ? 'label_side_right' : 'label_side_left'),
                style: Theme.of(context).textTheme.labelSmall,
              ),
            ),
          ),
        if (!repsOnly) ...[
          field(right ? 'rightWeight' : 'value'),
          const SizedBox(width: 8),
        ],
        field(right ? 'rightReps' : 'reps'),
      ],
    );
    return Column(
      children: [
        row(right: false),
        if (sided) ...[const SizedBox(height: 6), row(right: true)],
      ],
    );
  }

  Widget timerRow(Json task, Json timer, Json set, int index) {
    final taskId = task['id'] as int;
    final key = (taskId, index);
    final args = <String, dynamic>{'id': taskId, 'index': index};
    final active = timer['id'] == taskId && timer['setIndex'] == index;
    final running = active && timer['running'] == true;
    final showSeconds = task['showSeconds'] == true;
    final value = set['value'] as String;
    final done =
        value.isNotEmpty && (set['reps'] == 'Done' || value.contains('min'));
    final defaultMinutes =
        RegExp(r'\d+').firstMatch(task['target'] as String)?.group(0) ?? '30';
    final minutes = timerMinutes.putIfAbsent(key, () => defaultMinutes);
    final seconds = timerSeconds.putIfAbsent(
      key,
      () => showSeconds ? '30' : '0',
    );
    return Row(
      children: [
        SizedBox(width: 32, child: Text((index + 1).toString())),
        if (active)
          Expanded(
            child: Text(
              clock(timer['seconds'] as int),
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                color: timer['phase'] == 'PREP'
                    ? Colors.orange.shade800
                    : Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
            ),
          )
        else if (done)
          Expanded(child: Text('✓ ' + value))
        else
          Expanded(
            child: Row(
              children: [
                Expanded(
                  child: input(
                    minutes,
                    'm',
                    (text) => timerMinutes[key] = text,
                  ),
                ),
                const SizedBox(width: 4),
                const Text('m'),
                if (showSeconds) ...[
                  const SizedBox(width: 8),
                  Expanded(
                    child: input(
                      seconds,
                      's',
                      (text) => timerSeconds[key] = text,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Text('s'),
                ],
              ],
            ),
          ),
        if (value.isEmpty || active)
          IconButton(
            tooltip: l(
              running
                  ? 'timer_pause'
                  : active
                  ? 'timer_resume'
                  : 'timer_start',
            ),
            icon: Icon(running ? Icons.pause : Icons.play_circle_fill),
            onPressed: () {
              if (running) {
                act('pause');
                return;
              }
              final duration = active
                  ? (timer['total'] as int) / 60
                  : (int.tryParse(timerMinutes[key] ?? '') ?? 0) +
                        (int.tryParse(timerSeconds[key] ?? '') ?? 0) / 60;
              if (duration > 0) act('start', {...args, 'minutes': duration});
            },
          ),
        IconButton(
          tooltip: l(active ? 'timer_stop' : 'btn_delete'),
          icon: Icon(active ? Icons.stop : Icons.delete_outline),
          onPressed: () => act(active ? 'stop' : 'removeSet', args),
        ),
      ],
    );
  }
}

class _WorkoutParticles extends CustomPainter {
  const _WorkoutParticles(this.progress);
  final double progress;

  @override
  void paint(Canvas canvas, Size size) {
    const colors = [Colors.red, Colors.yellow, Colors.blue, Colors.green];
    final center = size.center(Offset.zero);
    for (var i = 0; i < 20; i++) {
      final angle = i * math.pi / 10;
      final radius = progress * (55 + (i % 5) * 23);
      final point =
          center + Offset(math.cos(angle) * radius, math.sin(angle) * radius);
      canvas.drawCircle(
        point,
        3.5,
        Paint()..color = colors[i % 4].withValues(alpha: 1 - progress),
      );
    }
  }

  @override
  bool shouldRepaint(_WorkoutParticles oldDelegate) =>
      oldDelegate.progress != progress;
}

class _WorkoutSwipeTile extends StatefulWidget {
  const _WorkoutSwipeTile({
    super.key,
    required this.revealed,
    required this.onReveal,
    required this.deleteLabel,
    required this.onDelete,
    required this.child,
  });

  final bool revealed;
  final ValueChanged<bool> onReveal;
  final String deleteLabel;
  final VoidCallback onDelete;
  final Widget child;

  @override
  State<_WorkoutSwipeTile> createState() => _WorkoutSwipeTileState();
}

class _WorkoutSwipeTileState extends State<_WorkoutSwipeTile> {
  static const revealWidth = 76.0;
  double? dragging;
  bool startedRevealed = false;

  void finish(double width) {
    final distance = dragging ?? 0;
    final delete =
        startedRevealed && distance >= revealWidth + math.max(44, width * .16);
    setState(() => dragging = null);
    if (delete) {
      widget.onDelete();
    } else {
      widget.onReveal(distance >= revealWidth / 2);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return LayoutBuilder(
      builder: (context, constraints) => GestureDetector(
        onHorizontalDragStart: (_) {
          startedRevealed = widget.revealed;
          setState(() => dragging = startedRevealed ? revealWidth : 0);
        },
        onHorizontalDragUpdate: (event) {
          final change = event.delta.dx * (rtl ? 1 : -1);
          setState(() {
            dragging = ((dragging ?? 0) + change).clamp(
              0.0,
              constraints.maxWidth,
            );
          });
        },
        onHorizontalDragEnd: (_) => finish(constraints.maxWidth),
        onHorizontalDragCancel: () => setState(() => dragging = null),
        child: TweenAnimationBuilder<double>(
          duration: dragging == null
              ? const Duration(milliseconds: 180)
              : Duration.zero,
          curve: Curves.easeOutCubic,
          tween: Tween(end: dragging ?? (widget.revealed ? revealWidth : 0)),
          child: widget.child,
          builder: (context, distance, card) => Stack(
            clipBehavior: Clip.hardEdge,
            children: [
              if (distance > 0)
                PositionedDirectional(
                  top: 0,
                  bottom: 0,
                  end: 0,
                  width: distance,
                  child: ClipRRect(
                    borderRadius: const BorderRadiusDirectional.only(
                      topEnd: Radius.circular(20),
                      bottomEnd: Radius.circular(20),
                    ),
                    child: Material(
                      color: colors.error,
                      child: InkWell(
                        onTap: widget.revealed ? widget.onDelete : null,
                        child: Align(
                          alignment: AlignmentDirectional.centerEnd,
                          child: SizedBox(
                            width: revealWidth,
                            child: Center(
                              child: Tooltip(
                                message: widget.deleteLabel,
                                child: Icon(
                                  Icons.delete_outline,
                                  color: colors.onError,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              Transform.translate(
                offset: Offset((rtl ? 1 : -1) * distance, 0),
                child: card,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
