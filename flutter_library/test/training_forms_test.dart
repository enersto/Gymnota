import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/main.dart';
import 'package:gymnota_library/training_forms.dart';

void main() {
  testWidgets('personal picker returns a template id from Rust search', (
    tester,
  ) async {
    final messenger = tester.binding.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(bridge, (call) async {
      expect(call.method, 'personalSearch');
      expect(call.arguments, {'query': 'curl alias'});
      return [17];
    });
    addTearDown(() => messenger.setMockMethodCallHandler(bridge, null));

    String? selected;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async {
                selected = await pickPersonalExercise(
                  context,
                  const [
                    {'id': 17, 'name': 'Personal curl', 'category': 'STRENGTH'},
                    {'id': 21, 'name': 'Run', 'category': 'CARDIO'},
                  ],
                  const {'title_select_exercise': 'Pick an exercise'},
                );
              },
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'curl alias');
    await tester.pumpAndSettle();
    expect(find.text('Run'), findsNothing);
    await tester.tap(find.text('Personal curl'));
    await tester.pumpAndSettle();
    expect(selected, '17');
  });
}
