import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/material.dart';

import 'glass.dart';
import 'coach_markdown.dart';

/// Flutter coach surface backed by the native request and persistence layer.
class CoachPreview extends StatefulWidget {
  const CoachPreview({
    super.key,
    required this.labels,
    this.onClose,
    this.stateChannel,
  });
  final Map<String, String> labels;
  final VoidCallback? onClose;
  final String? stateChannel;

  @override
  State<CoachPreview> createState() => _CoachPreviewState();
}

class _CoachPreviewState extends State<CoachPreview> {
  bool chat = false;
  static const commands = MethodChannel('gymnota/coach');
  Map<dynamic, dynamic> state = {};
  late final StreamSubscription<dynamic> subscription;
  bool attached = false;
  bool sending = false;
  final focus = <String>{'COMPREHENSIVE'};
  final injuries = <String>{};
  final scene = <String>{'GYM'};
  bool get busy => state['busy'] == true || state['csvBusy'] == true || sending;
  bool get configured => state['configured'] == true;

  @override
  void initState() {
    super.initState();
    subscription = EventChannel(widget.stateChannel ?? 'gymnota/coach/state')
        .receiveBroadcastStream()
        .listen((event) {
          if (!mounted) return;
          final update = Map<dynamic, dynamic>.from(event as Map);
          if (update['csv'] case final String csv) {
            unawaited(
              editText('weekly_plan', csv).then((value) async {
                if (mounted && value != null && value.trim().isNotEmpty)
                  await command('import', value);
              }),
            );
          } else {
            setState(() => state = update);
          }
        }, onError: (_) => showError());
  }

  void showError() {
    if (mounted)
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(label('coach_stream_failed'))));
  }

  Future<T?> command<T>(String method, [Object? args]) async {
    try {
      return await commands.invokeMethod<T>(method, args);
    } on PlatformException {
      showError();
      return null;
    }
  }

  Future<String?> editText(String heading, [String initial = '']) async {
    var value = initial;
    return showDialog<String>(
      context: context,
      builder: (dialog) => GlassAlertDialog(
        title: Text(label(heading)),
        content: TextFormField(
          initialValue: initial,
          minLines: 3,
          maxLines: 8,
          onChanged: (text) => value = text,
        ),
        actions: [
          GlassCommand(
            label: label('btn_cancel'),
            onPressed: () => Navigator.pop(dialog),
          ),
          GlassCommand(
            label: label('btn_confirm'),
            onPressed: () => Navigator.pop(dialog, value),
          ),
        ],
      ),
    );
  }

  Future<void> send() async {
    if (busy || !configured) return;
    final text = (chat ? message : goal).text.trim();
    if (chat && text.isEmpty && !attached) return;
    FocusScope.of(context).unfocus();
    setState(() => sending = true);
    await command<void>('send', {
      'chat': chat,
      'text': text,
      'weeks': weeks,
      'focus': focus.toList(),
      'scene': scene.toList(),
      'injuries': injuries.toList(),
    });
    if (mounted)
      setState(() {
        sending = false;
        if (chat) attached = false;
      });
  }

  Future<void> history() async {
    final reply = await command<List<dynamic>>('history');
    if (!mounted || reply == null) return;
    final records = reply
        .map((row) => Map<dynamic, dynamic>.from(row as Map))
        .toList();
    final positions = {
      for (var i = 0; i < records.length; i++) records[i]['id']: i,
    };
    await showDialog<void>(
      context: context,
      builder: (dialog) => StatefulBuilder(
        builder: (panel, update) => GlassAlertDialog(
          compact: true,
          wide: true,
          title: Text(
            label('title_ai_history'),
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          content: SizedBox(
            width: 640,
            height: MediaQuery.sizeOf(dialog).height * .60,
            child: records.isEmpty
                ? Center(child: Text(label('msg_no_history')))
                : ListView.separated(
                    itemCount: records.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (_, index) {
                      final row = records[index] as Map;
                      final timestamp = DateTime.fromMillisecondsSinceEpoch(
                        (row['timestamp'] as int?) ?? 0,
                      );
                      final localizations = MaterialLocalizations.of(dialog);
                      return Glass(
                        radius: 12,
                        child: InkWell(
                          onTap: () {
                            Navigator.pop(dialog);
                            unawaited(
                              command<void>('loadHistory', row['answer']),
                            );
                          },
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        '${localizations.formatShortDate(timestamp)} · ${localizations.formatTimeOfDay(TimeOfDay.fromDateTime(timestamp))}',
                                        style: Theme.of(dialog)
                                            .textTheme
                                            .labelSmall,
                                      ),
                                    ),
                                    IconButton(
                                      icon: const Icon(Icons.delete_outline),
                                      tooltip:
                                          localizations.deleteButtonTooltip,
                                      onPressed: () async {
                                        update(() => records.remove(row));
                                        final removed = await command<bool>(
                                          'deleteHistory',
                                          row['id'],
                                        );
                                        if (removed != true && panel.mounted) {
                                          update(() {
                                            records.add(row);
                                            records.sort(
                                              (a, b) =>
                                                  positions[a['id']]!.compareTo(
                                                    positions[b['id']]!,
                                                  ),
                                            );
                                          });
                                        }
                                      },
                                    ),
                                  ],
                                ),
                                if ((row['goal'] as String? ?? '')
                                    .trim()
                                    .isNotEmpty)
                                  Text(
                                    row['goal'] as String,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                Text(
                                  (row['answer'] ?? '').toString(),
                                  maxLines: 4,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          ),
          actions: [
            GlassCommand(
              label: label('btn_close'),
              onPressed: () => Navigator.pop(dialog),
            ),
          ],
        ),
      ),
    );
  }

  Widget filters(
    String heading,
    Map<String, String> options,
    Set<String> selected,
    String fallback,
  ) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const SizedBox(height: 12),
      Text(label(heading), style: const TextStyle(fontWeight: FontWeight.bold)),
      const SizedBox(height: 6),
      Wrap(
        spacing: 8,
        runSpacing: 6,
        children: [
          for (final entry in options.entries)
            GlassFilter(
              label: label(entry.value),
              selected: selected.contains(entry.key),
              onTap: () => setState(() {
                if (entry.key == 'COMPREHENSIVE') {
                  selected
                    ..clear()
                    ..add(entry.key);
                } else {
                  selected.remove('COMPREHENSIVE');
                  if (!selected.add(entry.key)) selected.remove(entry.key);
                  if (selected.isEmpty && fallback.isNotEmpty)
                    selected.add(fallback);
                }
              }),
            ),
        ],
      ),
    ],
  );
  int weeks = 3;
  final goal = TextEditingController();
  final message = TextEditingController();

  String label(String key) => widget.labels[key] ?? '';

  @override
  void dispose() {
    unawaited(subscription.cancel());
    goal.dispose();
    message.dispose();
    super.dispose();
  }

  Widget _connectionIndicator(BuildContext context) {
    final connection = state['connection'];
    if (connection == 'testing') {
      return SizedBox.square(
        dimension: 14,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          semanticsLabel: label('coach_stream_connecting'),
        ),
      );
    }
    final colors = Theme.of(context).colorScheme;
    final color = switch (connection) {
      'connected' => Colors.green,
      'error' => colors.error,
      _ => configured ? colors.outline : colors.error,
    };
    final hint = switch (connection) {
      'connected' => label('msg_connection_success'),
      'error' => label('coach_stream_failed'),
      _ => label(configured ? 'btn_test_connection' : 'ai_status_unconfigured'),
    };
    return Tooltip(
      message: hint,
      child: Icon(Icons.circle, size: 10, color: color),
    );
  }

  @override
  Widget build(BuildContext context) {
    final title = rootPageTitleStyle(context);
    final responseTitle = title.copyWith(fontWeight: FontWeight.bold);
    return Scaffold(
      resizeToAvoidBottomInset: widget.onClose != null,
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
          child: Column(
            children: [
              ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Row(
                  children: [
                    Expanded(child: Text(label('tab_ai_coach'), style: title)),
                    GlassIconAction(
                      icon: Icons.history,
                      label: label('title_ai_history'),
                      onPressed: history,
                    ),
                    if (widget.onClose != null)
                      GlassIconAction(
                        icon: Icons.close,
                        label: label('btn_close'),
                        onPressed: widget.onClose,
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              GlassSegments<bool>(
                primary: true,
                values: const [false, true],
                labels: [label('weekly_plan'), label('btn_free_chat')],
                selected: chat,
                onChanged: (value) => setState(() => chat = value),
              ),
              const SizedBox(height: glassMenuGap),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.only(bottom: 24),
                  children: [
                    Glass(
                      radius: 16,
                      child: Row(
                        children: [
                          Expanded(
                            child: InkWell(
                              borderRadius: BorderRadius.circular(16),
                              onTap: busy
                                  ? null
                                  : () => command<void>('settings'),
                              child: ConstrainedBox(
                                constraints: const BoxConstraints(
                                  minHeight: 44,
                                ),
                                child: Padding(
                                  padding: const EdgeInsets.fromLTRB(
                                    12,
                                    4,
                                    4,
                                    4,
                                  ),
                                  child: Row(
                                    children: [
                                      _connectionIndicator(context),
                                      const SizedBox(width: 8),
                                      Expanded(
                                        child: Text(
                                          configured
                                              ? [
                                                      state['provider'],
                                                      state['model'],
                                                    ]
                                                    .whereType<String>()
                                                    .where(
                                                      (value) =>
                                                          value.isNotEmpty,
                                                    )
                                                    .join(' · ')
                                              : label('ai_status_unconfigured'),
                                          style: Theme.of(context)
                                              .textTheme
                                              .bodySmall,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          ),
                          IconButton(
                            constraints: const BoxConstraints.tightFor(
                              width: 44,
                              height: 44,
                            ),
                            style: IconButton.styleFrom(
                              padding: EdgeInsets.zero,
                              iconSize: 18,
                              foregroundColor: Theme.of(context)
                                  .colorScheme
                                  .onSurface,
                            ),
                            icon: const Icon(Icons.network_check),
                            tooltip: label('btn_test_connection'),
                            onPressed:
                                configured &&
                                    !busy &&
                                    state['connection'] != 'testing'
                                ? () => command<void>('testConnection')
                                : null,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),
                    if (!chat)
                      Glass(
                        radius: 16,
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                label('label_context_length'),
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 8),
                              GlassSegments<int>(
                                values: const [1, 2, 3, 4],
                                labels: [
                                  for (final key in [
                                    'option_1_week',
                                    'option_2_weeks',
                                    'option_3_weeks',
                                    'option_4_weeks',
                                  ])
                                    label(key),
                                ],
                                selected: weeks,
                                onChanged: (value) =>
                                    setState(() => weeks = value),
                              ),
                              filters(
                                'label_focus',
                                const {
                                  'COMPREHENSIVE': 'focus_comprehensive',
                                  'STRENGTH': 'category_strength',
                                  'CARDIO': 'category_cardio',
                                  'CORE': 'category_core',
                                },
                                focus,
                                'COMPREHENSIVE',
                              ),
                              filters(
                                'label_scene',
                                const {
                                  'GYM': 'scene_gym',
                                  'HOME_EQUIP': 'scene_home_equip',
                                  'HOME_NONE': 'scene_home_none',
                                  'OUTDOOR': 'scene_outdoor',
                                  'POOL': 'scene_pool',
                                  'YOGA_STUDIO': 'scene_yoga',
                                  'LIMITED_GYM': 'scene_limited_gym',
                                  'CROSSFIT_BOX': 'scene_crossfit',
                                },
                                scene,
                                'GYM',
                              ),
                              filters(
                                'label_injuries',
                                const {
                                  'Knee': 'injury_knee',
                                  'LowerBack': 'injury_lower_back',
                                  'Shoulder': 'injury_shoulder',
                                  'Wrist': 'injury_wrist',
                                  'Neck': 'injury_neck',
                                },
                                injuries,
                                '',
                              ),
                            ],
                          ),
                        ),
                      ),
                    const SizedBox(height: 16),
                    GlassInputSurface(
                      child: TextField(
                        controller: chat ? message : goal,
                        minLines: 2,
                        maxLines: 5,
                        decoration: InputDecoration(
                          labelText: label(
                            chat ? 'btn_free_chat' : 'label_current_goal',
                          ),
                          hintText: label(
                            chat ? 'hint_input_chat' : 'hint_input_goal',
                          ),
                          prefixIcon: Icon(
                            chat
                                ? Icons.chat_bubble_outline
                                : Icons.edit_outlined,
                          ),
                          contentPadding: const EdgeInsets.fromLTRB(
                            16,
                            14,
                            16,
                            14,
                          ),
                          filled: false,
                          border: InputBorder.none,
                          enabledBorder: InputBorder.none,
                          focusedBorder: InputBorder.none,
                        ),
                      ),
                    ),
                    if (chat)
                      Align(
                        alignment: Alignment.centerRight,
                        child: GlassIconAction(
                          icon: attached ? Icons.link_off : Icons.attach_file,
                          label: attached
                              ? label('action_delete_photo')
                              : label('source_gallery'),
                          onPressed: busy
                              ? null
                              : () async {
                                  if (attached) {
                                    await command<void>('clearImage');
                                    if (mounted)
                                      setState(() => attached = false);
                                  } else if (await command<bool>('image') ==
                                          true &&
                                      mounted) {
                                    setState(() => attached = true);
                                  }
                                },
                        ),
                      ),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(label('coach_stream_enabled')),
                      value: state['streaming'] != false,
                      onChanged: busy
                          ? null
                          : (value) => command<void>('streaming', value),
                    ),
                    const SizedBox(height: 4),
                    GlassCommand(
                      primary: true,
                      fullWidth: true,
                      label: label(
                        chat ? 'btn_send_message' : 'btn_get_advice',
                      ),
                      onPressed: configured && !busy ? send : null,
                    ),
                    if (state['busy'] == true) ...[
                      const SizedBox(height: 10),
                      const LinearProgressIndicator(),
                      const SizedBox(height: 8),
                      GlassCommand(
                        fullWidth: true,
                        label: label('coach_stream_stop'),
                        onPressed: () => command<void>('stop'),
                      ),
                    ],
                    if (const {
                      'failed',
                      'cancelled',
                      'incomplete',
                    }.contains(state['status']))
                      Text(
                        label(switch (state['status']) {
                          'cancelled' => 'coach_stream_stopped',
                          'incomplete' => 'coach_stream_incomplete',
                          _ => 'coach_stream_failed',
                        }),
                      ),
                    if ((state['reasoning'] as String? ?? '').isNotEmpty)
                      ExpansionTile(
                        title: Text(label('coach_stream_reasoning_summary')),
                        children: [Text(state['reasoning'] as String)],
                      ),
                    if ((state['answer'] as String? ?? '').isNotEmpty) ...[
                      const SizedBox(height: 16),
                      Glass(
                        radius: 16,
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Text(
                                label('ai_response_title'),
                                style: responseTitle,
                              ),
                              const SizedBox(height: 12),
                              if (state['busy'] == true)
                                SelectableText(state['answer'] as String)
                              else
                                CoachMarkdown(
                                  source: state['answer'] as String,
                                ),
                            ],
                          ),
                        ),
                      ),
                      if (!chat && state['canImport'] == true) ...[
                        const SizedBox(height: 12),
                        GlassCommand(
                          fullWidth: true,
                          label: label('btn_generate_and_import'),
                          onPressed: busy
                              ? null
                              : () async {
                                  final feedback = await editText(
                                    'msg_refine_dialog_hint',
                                  );
                                  if (mounted && feedback != null)
                                    await command<void>('refine', feedback);
                                },
                        ),
                      ],
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
