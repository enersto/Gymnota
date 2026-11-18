import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/exercise_editor.dart';
import 'package:gymnota_library/glass.dart';
import 'package:gymnota_library/main.dart' show bridge;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const labels = {
    'title_new_exercise': 'New exercise',
    'title_edit_exercise': 'Edit exercise',
    'label_name': 'Name',
    'label_target': 'Target',
    'label_category': 'Category',
    'category_strength': 'Strength',
    'category_cardio': 'Cardio',
    'category_core': 'Core',
    'label_body_part': 'Body part',
    'label_equipment': 'Equipment',
    'label_log_type': 'Record type',
    'label_is_unilateral': 'Separate sides',
    'label_instruction': 'Instructions',
    'source_gallery': 'Gallery',
    'source_camera': 'Camera',
    'remove_exercise_photo': 'Remove photo',
    'catalog_choose_animation': 'Choose animation',
    'exercise_remove_animation': 'Remove animation',
    'exercise_media_separate': 'Independent photo and animation',
    'log_type_weight_reps': 'Weight × reps',
    'log_type_duration': 'Duration',
    'log_type_reps_only': 'Reps',
    'btn_save': 'Save',
    'btn_cancel': 'Cancel',
    'exercise_name_required': 'Name required',
    'flutter_error': 'Save failed',
  };
  const config = {
    'language': 'en',
    'bodyPartOptions': [
      {'key': 'part_back', 'label': 'Back'},
    ],
    'equipmentOptions': [
      {'key': 'equip_cable', 'label': 'Cable'},
    ],
  };
  const initial = {
    'id': 42,
    'name': 'Personal name',
    'target': '3x12',
    'category': 'STRENGTH',
    'bodyPart': 'part_back',
    'equipmentKey': 'equip_cable',
    'logType': 0,
    'unilateral': true,
    'instruction': 'Keep control',
    'imageUri': '/existing.jpg',
    'animationUri': 'file:///android_asset/catalog/videos/0150-test.webp',
  };
  late List<MethodCall> calls;
  bool failSave = false;
  setUp(() {
    calls = [];
    failSave = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(bridge, (call) async {
          calls.add(call);
          final args = call.arguments as Map;
          if (args['operation'] == 'photo') return '/draft.jpg';
          if (args['operation'] == 'save' && failSave)
            throw PlatformException(code: 'save_failed');
          return null;
        });
  });
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(bridge, null),
  );
  Future<void> open(WidgetTester tester, {bool edit = true}) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => showExerciseEditor(
                context,
                config,
                labels,
                initial: edit ? initial : null,
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
  }

  testWidgets(
    'all stored fields and independent media survive a Flutter edit',
    (tester) async {
      await open(tester);
      await tester.enterText(
        find.byKey(const ValueKey('exercise-editor-label_name')),
        'Renamed personal exercise',
      );
      await tester.enterText(
        find.byKey(const ValueKey('exercise-editor-label_target')),
        '4x10',
      );
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      final args =
          calls
                  .singleWhere(
                    (c) => (c.arguments as Map)['operation'] == 'save',
                  )
                  .arguments
              as Map;
      expect(args['id'], 42);
      expect(args['name'], 'Renamed personal exercise');
      expect(args['target'], '4x10');
      for (final key in [
        'bodyPart',
        'equipmentKey',
        'logType',
        'unilateral',
        'instruction',
        'imageUri',
        'animationUri',
      ]) {
        expect(args[key], initial[key], reason: key);
      }
      expect(find.byType(ExerciseEditor), findsNothing);
    },
  );
  testWidgets(
    'photo removal followed by cancel leaves saved photo and animation alone',
    (tester) async {
      await open(tester);
      final remove = find.widgetWithText(GlassCommand, 'Remove photo');
      await tester.ensureVisible(remove);
      await tester.tap(remove);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(
        calls.where((c) => (c.arguments as Map)['operation'] == 'save'),
        isEmpty,
      );
      expect(
        calls.where((c) => (c.arguments as Map)['operation'] == 'discardPhoto'),
        isEmpty,
      );
    },
  );
  testWidgets('cancel disposes a newly picked draft instead of saving it', (
    tester,
  ) async {
    await open(tester, edit: false);
    final pick = find.widgetWithText(GlassCommand, 'Gallery');
    await tester.ensureVisible(pick);
    await tester.tap(pick);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(
      calls.any((c) => (c.arguments as Map)['operation'] == 'save'),
      false,
    );
    final discard = calls.singleWhere(
      (c) => (c.arguments as Map)['operation'] == 'discardPhoto',
    );
    expect((discard.arguments as Map)['uri'], '/draft.jpg');
  });
  testWidgets('empty name is rejected and a failed save retains the draft', (
    tester,
  ) async {
    await open(tester, edit: false);
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(find.text('Name required'), findsOneWidget);
    failSave = true;
    await tester.enterText(
      find.byKey(const ValueKey('exercise-editor-label_name')),
      'Keep this draft',
    );
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(find.text('Save failed'), findsOneWidget);
    expect(find.text('Keep this draft'), findsOneWidget);
    expect(find.byType(ExerciseEditor), findsOneWidget);
  });
  testWidgets('category selection selects a valid duration record mode', (
    tester,
  ) async {
    await open(tester, edit: false);
    await tester.enterText(
      find.byKey(const ValueKey('exercise-editor-label_name')),
      'Cardio',
    );
    await tester.ensureVisible(find.text('Cardio').last);
    await tester.tap(find.text('Cardio').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    final args =
        calls
                .singleWhere((c) => (c.arguments as Map)['operation'] == 'save')
                .arguments
            as Map;
    expect(args['category'], 'CARDIO');
    expect(args['logType'], 1);
  });
  testWidgets('editor keeps inputs and footer inside a small keyboard viewport', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(400, 700);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    await open(tester, edit: false);
    await tester.tap(find.byKey(const ValueKey('exercise-editor-label_name')));
    tester.view.viewInsets = const FakeViewPadding(bottom: 360);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Save'), findsOneWidget);
  });

}
