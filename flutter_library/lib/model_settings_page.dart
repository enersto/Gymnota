import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart' as lg;

import 'glass.dart';
import 'main.dart' show Json, bridge, object;

class ModelSettingsPage extends StatefulWidget {
  const ModelSettingsPage({
    super.key,
    required this.config,
    required this.labels,
    required this.onBack,
    required this.changeSetting,
  });

  final Json config;
  final Map<String, String> labels;
  final VoidCallback onBack;
  final Future<void> Function(String, Object?) changeSetting;

  @override
  State<ModelSettingsPage> createState() => _ModelSettingsPageState();
}

class _ModelSettingsPageState extends State<ModelSettingsPage> {
  final apiKey = TextEditingController();
  final model = TextEditingController();
  final baseUrl = TextEditingController();
  List<String> providers = [];
  String? provider;
  bool loading = true, busy = false, error = false, showConnection = false;
  bool saved = false;

  String label(String key) => widget.labels[key] ?? '';

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    apiKey.dispose();
    model.dispose();
    baseUrl.dispose();
    super.dispose();
  }

  Future<void> load([String? nextProvider]) async {
    setState(() {
      loading = nextProvider == null;
      busy = true;
      error = saved = showConnection = false;
    });
    try {
      final data = object(
        await bridge.invokeMethod('settingsModel', {
          'operation': 'load',
          'provider': nextProvider,
        }),
      );
      if (!mounted) return;
      setState(() {
        provider = data['provider'] as String;
        providers = (data['providers'] as List).cast<String>();
        apiKey.text = data['apiKey'] as String;
        model.text = data['model'] as String;
        baseUrl.text = data['baseUrl'] as String;
      });
    } catch (_) {
      if (mounted) setState(() => error = true);
    } finally {
      if (mounted) setState(() => loading = busy = false);
    }
  }

  Future<void> submit(String operation) async {
    FocusScope.of(context).unfocus();
    setState(() {
      busy = true;
      error = saved = false;
      showConnection = operation == 'test';
    });
    try {
      await bridge.invokeMethod('settingsModel', {
        'operation': operation,
        'provider': provider,
        'apiKey': apiKey.text,
        'model': model.text,
        'baseUrl': baseUrl.text,
      });
      if (mounted && operation == 'save') setState(() => saved = true);
    } catch (_) {
      if (mounted) setState(() => error = true);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Widget field(
    String key,
    TextEditingController controller, {
    bool secret = false,
    TextInputType keyboard = TextInputType.text,
  }) => GlassInputSurface(
    child: TextField(
      controller: controller,
      enabled: !busy,
      obscureText: secret,
      autocorrect: false,
      enableSuggestions: false,
      keyboardType: keyboard,
      onChanged: (_) {
        if (saved || showConnection)
          setState(() => saved = showConnection = false);
      },
      decoration: InputDecoration(
        labelText: label(key),
        border: InputBorder.none,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final background = Color(
      widget.config['background'] as int? ?? colors.surface.toARGB32(),
    );
    final cache = widget.config['aiCache'] == true;
    final maxHistory = (widget.config['aiHistoryLimit'] as int? ?? 10).clamp(
      1,
      20,
    );
    final connection = showConnection ? widget.config['aiConnection'] : null;
    final testing = connection == 'testing';
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color.alphaBlend(colors.primary.withValues(alpha: .13), background),
            background,
          ],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 24, 16, 8),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_back),
                  tooltip: MaterialLocalizations.of(context).backButtonTooltip,
                  onPressed: widget.onBack,
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    label('ai_config_title'),
                    style: rootPageTitleStyle(context),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: loading
                ? const Center(child: CircularProgressIndicator())
                : ListView(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                    children: [
                      if (error) ...[
                        Text(
                          label('flutter_error'),
                          style: TextStyle(color: colors.error),
                        ),
                        const SizedBox(height: 8),
                      ],
                      if (provider == null)
                        GlassCommand(
                          label: label('flutter_retry'),
                          onPressed: load,
                        )
                      else ...[
                        GlassInputSurface(
                          child: DropdownButtonFormField<String>(
                            initialValue: provider,
                            isExpanded: true,
                            dropdownColor: colors.surface,
                            decoration: InputDecoration(
                              labelText: label('ai_provider_label'),
                              border: InputBorder.none,
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 16,
                                vertical: 12,
                              ),
                            ),
                            items: [
                              for (final name in providers)
                                DropdownMenuItem(
                                  value: name,
                                  child: Text(name),
                                ),
                            ],
                            onChanged: busy || testing
                                ? null
                                : (name) {
                                    if (name != null && name != provider)
                                      load(name);
                                  },
                          ),
                        ),
                        const SizedBox(height: 12),
                        field('ai_api_key_label', apiKey, secret: true),
                        const SizedBox(height: 12),
                        field('ai_model_label', model),
                        const SizedBox(height: 12),
                        field(
                          'ai_base_url_label',
                          baseUrl,
                          keyboard: TextInputType.url,
                        ),
                        const SizedBox(height: 20),
                        Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    label('label_ai_cache_title'),
                                    style: theme.textTheme.titleSmall?.copyWith(
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  Text(
                                    label('label_ai_cache_desc'),
                                    style: theme.textTheme.bodySmall,
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 16),
                            lg.GlassSwitch(
                              semanticLabel: label('label_ai_cache_title'),
                              value: cache,
                              activeColor: colors.primary,
                              quality: lg.GlassQuality.standard,
                              onChanged: (value) =>
                                  widget.changeSetting('aiCache', value),
                            ),
                          ],
                        ),
                        if (cache)
                          Row(
                            children: [
                              Expanded(child: Text(label('label_max_history'))),
                              IconButton(
                                onPressed: maxHistory > 1
                                    ? () => widget.changeSetting(
                                        'aiHistoryLimit',
                                        maxHistory - 1,
                                      )
                                    : null,
                                icon: const Icon(Icons.remove),
                              ),
                              Text('$maxHistory'),
                              IconButton(
                                onPressed: maxHistory < 20
                                    ? () => widget.changeSetting(
                                        'aiHistoryLimit',
                                        maxHistory + 1,
                                      )
                                    : null,
                                icon: const Icon(Icons.add),
                              ),
                            ],
                          ),
                        const SizedBox(height: 20),
                        if (testing ||
                            connection == 'success' ||
                            connection == 'error' ||
                            saved)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: Text(
                              label(
                                testing
                                    ? 'coach_stream_connecting'
                                    : saved
                                    ? 'settings_ai_saved'
                                    : connection == 'success'
                                    ? 'msg_connection_success'
                                    : 'settings_ai_connection_error',
                              ),
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: connection == 'error'
                                    ? colors.error
                                    : colors.onSurface,
                              ),
                            ),
                          ),
                        ValueListenableBuilder<TextEditingValue>(
                          valueListenable: apiKey,
                          builder: (context, value, _) => GlassCommand(
                            label: label('btn_test_connection'),
                            icon: Icons.network_check,
                            fullWidth: true,
                            onPressed:
                                !busy &&
                                    !testing &&
                                    value.text.trim().isNotEmpty
                                ? () => submit('test')
                                : null,
                          ),
                        ),
                        const SizedBox(height: 10),
                        GlassCommand(
                          label: label('btn_save_config'),
                          icon: Icons.check,
                          fullWidth: true,
                          primary: true,
                          onPressed: busy || testing
                              ? null
                              : () => submit('save'),
                        ),
                      ],
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}
