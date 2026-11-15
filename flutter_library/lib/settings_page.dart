import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart' as lg;

import 'glass.dart';
import 'model_settings_page.dart';
import 'main.dart' show Json, bridge, object, objects;

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key, required this.config, required this.labels});

  final Json config;
  final Map<String, String> labels;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool showModel = false;
  final scheduleType = GlobalKey();

  String label(String key) => widget.labels[key] ?? '';

  @override
  void initState() {
    super.initState();
    revealRequestedSection();
  }

  @override
  void didUpdateWidget(SettingsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.config['requestedSection'] !=
            widget.config['requestedSection'] ||
        oldWidget.config['requestToken'] != widget.config['requestToken']) {
      revealRequestedSection();
    }
  }

  void revealRequestedSection() {
    if (widget.config['requestedSection'] == 'ai-model') {
      showModel = true;
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final target = switch (widget.config['requestedSection']) {
        'schedule-type' => scheduleType.currentContext,
        _ => null,
      };
      if (target != null) Scrollable.ensureVisible(target);
    });
  }

  Future<void> action(String name, [Object? value]) async {
    try {
      await bridge.invokeMethod('settingsAction', {
        'action': name,
        'value': value,
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(label('flutter_error'))));
      }
    }
  }

  Widget section(String title, Widget child, {Key? key}) => Column(
    key: key,
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(
        label(title),
        style: Theme.of(context).textTheme.titleMedium
            ?.copyWith(fontWeight: FontWeight.bold),
      ),
      const SizedBox(height: 8),
      child,
    ],
  );

  Widget dataRow(
    (String, IconData, String) first,
    (String, IconData, String) second,
  ) => LayoutBuilder(
    builder: (context, constraints) {
      final width = (constraints.maxWidth - 12) / 2;
      final height = [
        60.0,
        for (final option in [first, second])
          GlassCommand.measureLabel(
            context,
            label(option.$1),
            width,
            hasIcon: true,
          ).height,
      ].reduce((a, b) => a > b ? a : b);
      return Row(
        spacing: 12,
        children: [
          for (final (key, icon, name) in [first, second])
            Expanded(
              child: GlassCommand(
                label: label(key),
                icon: icon,
                minHeight: height,
                onPressed: () => action(name),
              ),
            ),
        ],
      );
    },
  );

  Widget shortcut(
    String title,
    IconData icon,
    String name, {
    String? subtitle,
    VoidCallback? onTap,
  }) => Glass(
    radius: 16,
    definedEdge: true,
    child: ListTile(
      leading: Icon(icon),
      title: Text(
        label(title),
        style: const TextStyle(fontWeight: FontWeight.bold),
      ),
      subtitle: subtitle == null ? null : Text(subtitle),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap ?? () => action(name),
    ),
  );

  Widget timerControls(Json timer) => Glass(
    radius: 16,
    definedEdge: true,
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          timerSwitch('timer_sound', 'sound', timer['sound'] == true),
          const SizedBox(height: 12),
          timerSwitch('timer_prep', 'prep', timer['prep'] == true),
          if (timer['prep'] == true)
            durationControl('prepSeconds', timer['prepSeconds'] as int, 30),
          const SizedBox(height: 12),
          timerSwitch('timer_final', 'final', timer['final'] == true),
          if (timer['final'] == true)
            durationControl('finalSeconds', timer['finalSeconds'] as int, 15),
        ],
      ),
    ),
  );

  Widget timerSwitch(String key, String name, bool value) {
    final title = label(key + '_title');
    final theme = Theme.of(context);
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
              Text(label(key + '_desc'), style: theme.textTheme.bodySmall),
            ],
          ),
        ),
        const SizedBox(width: 12),
        SizedBox(
          height: 48,
          child: Center(
            widthFactor: 1,
            child: lg.GlassSwitch(
              semanticLabel: title,
              value: value,
              onChanged: (enabled) => action(name, enabled),
              activeColor: theme.colorScheme.primary,
              quality: lg.GlassQuality.standard,
              settings: glassSettings(context),
            ),
          ),
        ),
      ],
    );
  }

  Widget durationControl(String name, int seconds, int max) => Row(
    children: [
      Expanded(child: Text(label('label_duration_sec'))),
      IconButton(
        icon: const Icon(Icons.remove),
        onPressed: seconds > 3 ? () => action(name, seconds - 1) : null,
      ),
      Text('$seconds'),
      IconButton(
        icon: const Icon(Icons.add),
        onPressed: seconds < max ? () => action(name, seconds + 1) : null,
      ),
    ],
  );

  Widget themeOptions() {
    final colors = Theme.of(context).colorScheme;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        spacing: 12,
        children: [
          for (final item in objects(widget.config['themes']))
            Semantics(
              selected: item['id'] == widget.config['selectedTheme'],
              label: '${label('theme_style')} ${item['id']}',
              child: Material(
                color: Color(item['color'] as int),
                shape: CircleBorder(
                  side: BorderSide(
                    color: item['id'] == widget.config['selectedTheme']
                        ? colors.onSurface
                        : Colors.white.withValues(alpha: .4),
                    width: item['id'] == widget.config['selectedTheme'] ? 3 : 1,
                  ),
                ),
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: () => action('theme', item['id']),
                  child: SizedBox.square(
                    dimension: 52,
                    child: item['id'] == widget.config['selectedTheme']
                        ? const Icon(Icons.check, color: Colors.white)
                        : null,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget scheduleRow(Json row) {
    final day = row['day'] as int;
    final type = switch (row['type']) {
      'CORE' => 'type_core',
      'LIGHT' => 'type_light',
      'ACTIVE_REST' => 'type_active',
      _ => 'type_rest',
    };
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: TintedGlassTile(
        tint: Color(row['color'] as int),
        onTap: () => action('schedule', day),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 64),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    label('week_$day'),
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
                const SizedBox(width: 12),
                Flexible(child: Text(label(type), textAlign: TextAlign.end)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final config = widget.config;
    if (showModel) {
      return PopScope(
        canPop: false,
        onPopInvokedWithResult: (popped, _) {
          if (!popped) setState(() => showModel = false);
        },
        child: ModelSettingsPage(
          labels: widget.labels,
          config: config,
          onBack: () => setState(() => showModel = false),
          changeSetting: action,
        ),
      );
    }
    final version = label('about_version_format')
        .replaceAll(r'%1$s', config['version'] as String? ?? '');
    final model = config['modelConfigured'] == true
        ? [
            config['provider'],
            config['model'],
          ].whereType<String>().where((text) => text.isNotEmpty).join(' · ')
        : label('ai_status_unconfigured');
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 32, 16, 8),
          child: Semantics(
            header: true,
            child: Text(
              label('settings_advanced'),
              style: rootPageTitleStyle(context),
            ),
          ),
        ),
        Expanded(
          child: SingleChildScrollView(
            key: const PageStorageKey('settings-home'),
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              spacing: 24,
              children: [
                shortcut('settings_old_title', Icons.history, 'old'),
                section(
                  'settings_language',
                  GlassSegments<String>(
                    values: const ['zh', 'en', 'es', 'ja', 'de'],
                    labels: const ['中文', 'EN', 'ES', 'JA', 'DE'],
                    selected: config['language'] as String,
                    onChanged: (value) => action('language', value),
                  ),
                ),
                section(
                  'settings_data_management',
                  Column(
                    spacing: 12,
                    children: [
                      dataRow(
                        ('export_csv_btn', Icons.share_outlined, 'export'),
                        ('import_csv_btn', Icons.upload_outlined, 'import'),
                      ),
                      dataRow(
                        ('settings_help_reference', Icons.help_outline, 'help'),
                        ('settings_profile', Icons.person_outline, 'profile'),
                      ),
                      dataRow(
                        ('btn_backup_db', Icons.backup_outlined, 'backup'),
                        ('btn_restore_db', Icons.restore, 'restore'),
                      ),
                    ],
                  ),
                ),
                shortcut('weekly_plan', Icons.edit_calendar_outlined, 'weekly'),
                shortcut(
                  'ai_config_title',
                  Icons.smart_toy_outlined,
                  'ai',
                  subtitle: model,
                  onTap: () => setState(() => showModel = true),
                ),
                section(
                  'settings_timer_title',
                  timerControls(object(config['timer'])),
                ),
                section('theme_style', themeOptions()),
                section(
                  'schedule_type_title',
                  Column(
                    children: [
                      for (final row in objects(config['schedule']))
                        scheduleRow(row),
                    ],
                  ),
                  key: scheduleType,
                ),
                Column(
                  children: [
                    Text(
                      version,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    Text(
                      label('about_credit'),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
