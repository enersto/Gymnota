import 'package:flutter/material.dart';
import 'package:gymnota_library/glass.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/main.dart';

import 'embedded_catalog_test.dart' as native;

void main() {
  testWidgets(
    'settings preserves native picker actions and named section jumps',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 800);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final messenger = tester.binding.defaultBinaryMessenger;
      final actions = <Map<Object?, Object?>>[];
      final modelRequests = <Map<Object?, Object?>>[];
      final state = <String, dynamic>{
        'embedded': true,
        'page': '/embedded-settings',
        'language': 'en',
        'primary': 0xff009688,
        'onBackground': 0xff004d40,
        'background': 0xffe0f2f1,
        'selectedTheme': 7,
        'themes': [
          {'id': 7, 'color': 0xff009688},
        ],
        'schedule': [
          {'day': 1, 'type': 'CORE', 'color': 0xffff5722},
        ],
        'timer': {
          'sound': true,
          'prep': true,
          'prepSeconds': 10,
          'final': true,
          'finalSeconds': 5,
        },
        'version': '6.3.0',
        'labels': {
          'settings_advanced': 'Settings',
          'settings_old_title': 'Old settings',
          'ai_provider_label': 'Provider',
          'ai_api_key_label': 'API key',
          'ai_model_label': 'Model',
          'ai_base_url_label': 'Base URL',
          'btn_test_connection': 'Test connection',
          'btn_save_config': 'Save configuration',
          'settings_ai_saved': 'Saved',
          'settings_language': 'Language',
          'settings_data_management': 'Data management',
          'export_csv_btn': 'Export CSV',
          'import_csv_btn': 'Import CSV',
          'settings_help_reference': 'Reference',
          'settings_profile': 'Profile',
          'btn_backup_db': 'Back up data',
          'btn_restore_db': 'Datenbank wiederherstellen',
          'weekly_plan': 'Weekly plan',
          'ai_config_title': 'AI model configuration',
          'ai_status_unconfigured': 'Unconfigured',
          'settings_timer_title': 'Timer settings',
          'timer_sound_title': 'Timer sounds',
          'timer_prep_title': 'Countdown',
          'timer_final_title': 'Final countdown',
          'label_duration_sec': 'Seconds',
          'theme_style': 'Theme',
          'schedule_type_title': 'Training day types',
          'week_1': 'Monday',
          'type_core': 'Main training',
          'about_version_format': r'Gymnota %1$s',
        },
      };
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap') return state;
        if (call.method == 'settingsModel') {
          modelRequests.add(Map<Object?, Object?>.from(call.arguments as Map));
          if (call.arguments['operation'] == 'load') {
            return {
              'providers': ['OpenAI', 'Custom'],
              'provider': 'OpenAI',
              'apiKey': 'test-key',
              'model': 'test-model',
              'baseUrl': '',
            };
          }
          return {};
        }
        if (call.method == 'settingsAction') {
          actions.add(Map<Object?, Object?>.from(call.arguments as Map));
          return true;
        }
        return null;
      });
      addTearDown(() => messenger.setMockMethodCallHandler(bridge, null));
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();

      final backup = tester.getRect(
        find.widgetWithText(GlassCommand, 'Back up data'),
      );
      final restore = tester.getRect(
        find.widgetWithText(GlassCommand, 'Datenbank wiederherstellen'),
      );
      expect(backup.width, closeTo(restore.width, 1));
      expect(restore.height, closeTo(backup.height, 1));
      expect(restore.height, greaterThanOrEqualTo(60));
      expect(tester.takeException(), isNull);
      expect(find.text('Old settings'), findsNothing);

      await tester.tap(find.text('Datenbank wiederherstellen').hitTestable());
      expect(actions.last, {'action': 'restore', 'value': null});

      await native.nativeState(tester, {
        ...state,
        'requestedSection': 'ai-model',
        'requestToken': '1',
      });
      await tester.pumpAndSettle();
      expect(find.text('AI model configuration'), findsOneWidget);
      expect(find.text('Data management'), findsNothing);
      expect(
        tester.widget<TextField>(find.byType(TextField).first).obscureText,
        isTrue,
      );
      await tester.ensureVisible(find.text('Save configuration'));
      await tester.tap(find.text('Save configuration').hitTestable());
      await tester.pumpAndSettle();
      expect(modelRequests.last['operation'], 'save');
      expect(modelRequests.last['model'], 'test-model');
      expect(await native.nativeBack(tester), isTrue);
      expect(find.text('Data management'), findsOneWidget);

      await native.nativeState(tester, {
        ...state,
        'requestedSection': 'schedule-type',
      });
      await tester.pumpAndSettle();
      await tester.tap(find.text('Monday').hitTestable());
      expect(actions.last, {'action': 'schedule', 'value': 1});
      expect(find.text('Gymnota 6.3.0'), findsOneWidget);
      expect(find.byType(TintedGlassTile), findsOneWidget);
    },
  );
}
