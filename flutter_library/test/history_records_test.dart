import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/history_records.dart';
import 'package:gymnota_library/glass.dart';

void main() {
  testWidgets(
    'record cards use stored log types for duration reps sides and legacy values',
    (tester) async {
      const l = {
        'flutter_minutes': 'min',
        'label_reps': 'reps',
        'btn_done': 'Done',
        'label_side_left': 'L',
        'label_side_right': 'R',
      };
      final rows = [
        {
          'id': '1',
          'date': '2026-11-01',
          'name': 'Cardio',
          'category': 'CARDIO',
          'logType': 1,
          'sets': [
            {'weightOrDuration': '25'},
          ],
        },
        {
          'id': '2',
          'date': '2026-11-01',
          'name': 'Core',
          'category': 'CORE',
          'logType': 2,
          'sets': [
            {'reps': '12', 'weightOrDuration': '999'},
          ],
        },
        {
          'id': '3',
          'date': '2026-11-01',
          'name': 'Strength',
          'category': 'STRENGTH',
          'logType': 0,
          'sets': [
            {'weightOrDuration': '30', 'reps': '10'},
            {'weightOrDuration': '35', 'reps': '8'},
          ],
        },
      ];
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: HistoryRecordsView(tasks: rows, weights: [], labels: l),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('25 min'), findsOneWidget);
      expect(find.text('12 reps'), findsOneWidget);
      expect(find.text('30 kg × 10  |  35 kg × 8'), findsOneWidget);
      expect(find.textContaining('999'), findsNothing);
      for (final glass in tester.widgetList<Glass>(find.byType(Glass))) {
        expect(glass.blur, isFalse);
        expect(glass.radius, 16);
      }
    },
  );
  final translations = {
    'en': ['Single', 'Completed', 'Left', 'Right'],
    'es': ['Unilateral', 'Hecho', 'Izquierda', 'Derecha'],
    'de': ['Einseitig', 'Erledigt', 'Links', 'Rechts'],
    'ja': ['片側', '完了', '左側', '右側'],
    'zh': ['单边', '完成', '左侧', '右侧'],
  };
  for (final language in translations.keys) {
    testWidgets(
      'long personal names and missing right values fit narrow enlarged $language records',
      (tester) async {
        tester.view.physicalSize = const Size(320, 900);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await tester.pumpWidget(
          MaterialApp(
            theme: ThemeData.dark(),
            builder: (_, child) => MediaQuery(
              data: const MediaQueryData(
                size: Size(320, 900),
                textScaler: TextScaler.linear(1.6),
              ),
              child: child!,
            ),
            home: Scaffold(
              body: HistoryRecordsView(
                tasks: [
                  {
                    'id': '1',
                    'date': '2026-11-01',
                    'name': 'Personal 日本語 中文 entrenamiento Krafttraining very long name',
                    'unilateral': true,
                    'logType': 0,
                    'sets': [
                      {'weightOrDuration': '25', 'reps': '12'},
                    ],
                  },
                ],
                weights: [],
                labels: {
                  'tag_uni': translations[language]![0],
                  'btn_done': translations[language]![1],
                  'label_side_left': translations[language]![2],
                  'label_side_right': translations[language]![3],
                },
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.textContaining('${translations[language]![3]}: — kg × —'),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }
}
