import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/workout_preview_page.dart';

void main() {
  testWidgets(
    'workout preview edits the existing row through workout commands',
    (tester) async {
      final messenger = tester.binding.defaultBinaryMessenger;
      const events = MethodChannel('gymnota/preview/daily');
      const actions = MethodChannel('gymnota/workout');
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(events, (_) async => null);
      messenger.setMockMethodCallHandler(actions, (call) async {
        calls.add(call);
      });
      addTearDown(() {
        messenger.setMockMethodCallHandler(events, null);
        messenger.setMockMethodCallHandler(actions, null);
      });
      await tester.pumpWidget(
        const MaterialApp(
          home: WorkoutPreviewPage(
            embedded: true,
            labels: {
              'preview_workout_title': 'Workout preview',
              'legacy_workout_title': 'Original workout',
              'label_side_left': 'Left',
              'label_side_right': 'Right',
              'label_unilateral_mode': 'Unilateral',
              'btn_delete': 'Delete',
              'btn_check': 'Check in',
              'btn_add_set': 'Add set',
              'btn_add': 'Add exercise',
              'header_set_no': '#',
              'header_weight_time': 'Weight',
              'label_reps': 'Reps',
              'btn_done': 'Done',
            },
          ),
        ),
      );
      await tester.pump();
      await messenger.handlePlatformMessage(
        events.name,
        const StandardMethodCodec().encodeSuccessEnvelope({
          'date': '2026-11-16',
          'dayLabel': 'Training day',
          'timer': {'id': -1, 'running': false},
          'tasks': [
            {
              'id': 42,
              'name': 'Bench press',
              'target': '',
              'finished': false,
              'logType': 0,
              'unilateral': true,
              'sets': [
                {'number': 1, 'value': '45', 'reps': '8'},
              ],
            },
          ],
        }),
        (_) {},
      );
      await tester.pumpAndSettle();
      expect(find.byType(TextFormField), findsNothing);
      expect(find.text('Unilateral'), findsOneWidget);
      expect(find.text('Original workout'), findsNothing);
      await tester.tap(find.text('Bench press'));
      await tester.pumpAndSettle();
      expect(find.byType(TextFormField), findsNWidgets(4));
      final number = tester.getTopLeft(find.text('1')).dx;
      final left = tester.getTopLeft(find.text('Left')).dx;
      final right = tester.getTopLeft(find.text('Right')).dx;
      expect(left, greaterThan(number));
      expect(right, closeTo(left, 1));
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(400, 700);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetViewInsets);
      await tester.pumpAndSettle();
      await tester.tap(find.byType(TextFormField).first);
      tester.view.viewInsets = const FakeViewPadding(bottom: 425);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.enterText(find.byType(TextFormField).first, '5');
      await tester.enterText(find.byType(TextFormField).first, '50');
      expect(calls.where((call) => call.method == 'setFields'), isEmpty);
      await tester.pump(const Duration(milliseconds: 300));
      tester.view.resetViewInsets();
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Add set'));
      expect(calls.last.method, 'setFields');
      expect(calls.last.arguments, {
        'edits': [
          {'id': 42, 'index': 0, 'field': 'value', 'text': '50'},
        ],
      });
      await tester.tap(find.text('Add set'));
      expect(calls.last.method, 'addSet');
      await tester.tap(find.text('Check in'));
      expect(calls.last.method, 'check');
      await tester.pumpAndSettle();

      await tester.drag(find.text('Bench press'), const Offset(-160, 0));
      await tester.pumpAndSettle();
      expect(find.text('Bench press'), findsOneWidget);
      expect(calls.where((call) => call.method == 'removeTask'), isEmpty);
      await tester.tap(find.byTooltip('Delete'));
      await tester.pumpAndSettle();
      expect(calls.last.method, 'removeTask');
      expect(find.text('Bench press'), findsNothing);
    },
  );
}
