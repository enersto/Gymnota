import 'package:gymnota_library/exercise_editor.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/exercise_libraries.dart';
import 'package:gymnota_library/main.dart';

void main() {
  testWidgets(
    'root preview shares five tabs and shows live read-only workout data',
    (tester) async {
      final messenger = tester.binding.defaultBinaryMessenger;
      const daily = MethodChannel('gymnota/preview/daily');
      const coach = MethodChannel('gymnota/coach/state');
      final calls = <String>[];
      messenger.setMockMethodCallHandler(daily, (_) async => null);
      messenger.setMockMethodCallHandler(coach, (_) async => null);
      messenger.setMockMethodCallHandler(bridge, (call) async {
        calls.add(call.method);
        if (call.method != 'bootstrap') return null;
        return {
          'page': '/root-preview',
          'language': 'en',
          'primary': 0xff009688,
          'background': 0xffe0f2f1,
          'onBackground': 0xff004d40,
          'initialHistory': {'revision': 1, 'charts': null},
          'previewReadOnly': true,
          'personalRows': [
            {'id': 17, 'name': 'Personal exercise', 'category': 'STRENGTH'},
          ],
          'themes': [],
          'schedule': [],
          'timer': {'sound': true, 'prep': false, 'final': false},
          'labels': {
            'root_preview_title': 'Flutter preview',
            'preview_daily_read_only': 'Read-only workout',
            'preview_open_settings': 'Open regular settings',
            'preview_daily_empty': 'Empty workout',
            'tab_home': 'Workout',
            'tab_history': 'History tab',
            'tab_exercises': 'Exercises tab',
            'tab_ai_coach': 'Coach',
            'tab_settings': 'Settings tab',
            'history_title': 'Training history',
            'tab_list': 'List',
            'tab_chart': 'Charts',
            'catalog_personal_title': 'My exercises',
            'catalog_title': 'Built-ins',
            'category_strength': 'Strength',
            'category_cardio': 'Cardio',
            'category_core': 'Core',
            'btn_close': 'Close',
            'btn_done': 'Completed',
            'settings_advanced': 'Settings',
            'settings_timer_title': 'Timer',
            'label_side_left': 'Left',
            'label_side_right': 'Right',
          },
        };
      });
      addTearDown(() {
        messenger.setMockMethodCallHandler(daily, null);
        messenger.setMockMethodCallHandler(coach, null);
        messenger.setMockMethodCallHandler(bridge, null);
      });

      await tester.pumpWidget(const LibraryApp());
      await tester.pump();
      Future<void> publish(int seconds) async {
        await messenger.handlePlatformMessage(
          daily.name,
          const StandardMethodCodec().encodeSuccessEnvelope({
            'date': '2026-11-16',
            'dayLabel': 'Training day',
            'timer': {'id': 5, 'running': true, 'seconds': seconds},
            'tasks': [
              {
                'id': 5,
                'name': 'One-arm row',
                'bodyLabel': 'Back',
                'equipment': 'Dumbbell',
                'recordType': 'Weight and repetitions',
                'completed': true,
                'target': '',
                'actualWeight': '',
                'logType': 0,
                'unilateral': true,
                'sets': [
                  {
                    'number': 1,
                    'value': '20',
                    'reps': '8',
                    'rightWeight': '22',
                    'rightReps': '7',
                  },
                ],
              },
            ],
          }),
          (_) {},
        );
        await tester.pumpAndSettle();
      }

      await publish(67);
      expect(find.text('Training day'), findsOneWidget);
      expect(find.text('Read-only workout'), findsOneWidget);
      expect(find.text('One-arm row'), findsOneWidget);
      expect(find.textContaining('Right 22 × 7'), findsOneWidget);
      expect(find.text('Timer · 01:07'), findsOneWidget);
      expect(find.byTooltip('Completed'), findsOneWidget);

      for (final (label, content) in [
        ('History tab', 'Training history'),
        ('Exercises tab', 'Personal exercise'),
        ('Coach', 'Coach'),
        ('Settings tab', 'Settings'),
      ]) {
        await tester.tap(find.text(label).last);
        await tester.pumpAndSettle();
        expect(find.text(content), findsWidgets);
        if (label == 'History tab') {
          await tester.tap(find.text('List'));
          await tester.pumpAndSettle();
          final search = find.byKey(const ValueKey('history-search'));
          await tester.tap(search);
          tester.view.devicePixelRatio = 1;
          tester.view.physicalSize = const Size(400, 700);
          tester.view.viewInsets = const FakeViewPadding(bottom: 405);
          addTearDown(tester.view.resetDevicePixelRatio);
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetViewInsets);
          await tester.pumpAndSettle();
          await tester.enterText(search, 'bench');
          expect(tester.takeException(), isNull);
          expect(find.text('History tab'), findsNothing);
          tester.view.resetViewInsets();
          tester.view.resetPhysicalSize();
          tester.view.resetDevicePixelRatio();
          await tester.pumpAndSettle();
        }
      }
      await tester.tap(find.text('Workout'));
      await tester.pumpAndSettle();
      await publish(66);
      expect(find.text('Timer · 01:06'), findsOneWidget);
      expect(calls.where((method) => method == 'bootstrap'), hasLength(1));
      await tester.tap(find.byTooltip('Close'));
      expect(calls.last, 'close');
    },
  );

  testWidgets('personal details and editing both stay in Flutter', (
    tester,
  ) async {
    final messenger = tester.binding.defaultBinaryMessenger;
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(bridge, (call) async {
      calls.add(call);
      return null;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(bridge, null));
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ExerciseLibraries(
            config: {
              'primaryHost': true,
              'personalRows': [
                {
                  'id': 17,
                  'name': 'Personal exercise',
                  'category': 'STRENGTH',
                  'instruction': 'Keep the back straight.',
                  'hasAnimation': true,
                },
              ],
            },
            labels: {
              'catalog_personal_title': 'My exercises',
              'catalog_title': 'Built-ins',
              'btn_close': 'Close',
              'btn_edit': 'Edit',
            },
          ),
        ),
      ),
    );
    await tester.tap(find.text('Personal exercise'));
    await tester.pump();
    expect(find.text('Keep the back straight.'), findsOneWidget);
    expect(calls, isEmpty);
    await tester.pumpAndSettle();
    expect(calls.single.method, 'personalDetailMedia');
    expect(calls.single.arguments, {'id': 17, 'animation': true});
    await tester.tap(find.text('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Keep the back straight.'), findsNothing);
    expect(find.text('Personal exercise'), findsOneWidget);
    await tester.tap(find.text('Personal exercise'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit'));
    await tester.pumpAndSettle();
    expect(find.byType(ExerciseEditor), findsOneWidget);
    expect(calls.where((call) => call.method == 'exerciseManagerAction'), isEmpty);
  });
}
