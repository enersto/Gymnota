import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'history respects stored logType and both sides without inventing values',
    () {
      expect(formatWeight(76.4000015258789), '76.4');
      expect(formatWeight(70), '70');
      final labels = {'flutter_minutes': 'min', 'label_reps': 'reps'};
      final set = <String, dynamic>{
        'weightOrDuration': '25',
        'reps': '12',
        'rightWeight': '20',
        'rightReps': '10',
      };
      expect(setValue({'logType': 0}, set, labels), '25 kg × 12');
      expect(setValue({'logType': 0}, set, labels, right: true), '20 kg × 10');
      expect(setValue({'logType': 1}, set, labels), '25 min');
      expect(
        setValue(
          {'logType': 1},
          {...set, 'weightOrDuration': '2 min 30 s'},
          labels,
        ),
        '2 min 30 s',
      );
      expect(setValue({'logType': 2}, set, labels), '12 reps');
      expect(setValue({'logType': 0}, {}, labels), '— kg × —');
    },
  );
  final labels = <String, String>{
    'history_title': 'History',
    'tab_list': 'List',
    'tab_chart': 'Charts',
    'chart_title_heatmap': 'Muscle heatmap',
    'charts_unavailable': 'Charts unavailable',
    'flutter_current': 'Current records',
    'flutter_backup': 'View backup',
    'history_stats': 'Training summary',
    'history_no_train': 'No training this day',
    'btn_done': 'Done',
    'tag_uni': 'Single',
    'label_side_left': 'Left',
    'label_side_right': 'Right',
    'no_history': 'No records',
    'flutter_history_search': 'Search date or name',
    'flutter_error': 'Failed',
    'flutter_retry': 'Retry',
  };
  Map<String, dynamic> snapshot(String name) => {
    'tasks': [
      {
        'id': '1',
        'date': '2026-11-01',
        'name': name,
        'category': 'STRENGTH',
        'logType': 0,
        'unilateral': true,
        'sets': [
          {
            'setNumber': '1',
            'weightOrDuration': '25',
            'reps': '12',
            'rightWeight': '20',
            'rightReps': '10',
          },
        ],
      },
      {
        'id': '2',
        'date': '2026-11-01',
        'name': 'Legacy',
        'logType': 0,
        'target': '3 x 12',
        'actualWeight': '30',
        'sets': [],
      },
    ],
    'weights': [
      {'date': '2026-11-01', 'weight': 70.5},
      {'date': '2026-11-02', 'weight': 70.0},
    ],
    'summary': {'trainingDays': 99},
    'charts': {
      'heatmap': [],
      'exercises': [],
      'exerciseSeries': {},
      'cardio': {'daily': [], 'monthly': []},
      'body': {
        for (final k in [
          'weight',
          'bmi',
          'bmr',
          'bodyFatKg',
          'skeletalMuscleKg',
          'bodyWaterPercentage',
          'whr',
        ])
          k: {'daily': [], 'monthly': []},
      },
    },
  };
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  tearDown(() {
    messenger.setMockMethodCallHandler(bridge, null);
  });
  testWidgets(
    'charts are default and live restored records replace backup and summary tools',
    (tester) async {
      final sources = <bool>[];
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap')
          return {
            'page': '/history',
            'language': 'en',
            'dark': false,
            'primary': 0xff009688,
            'background': 0xffe0f2f1,
          };
        if (call.method == 'labels') return labels;
        if (call.method == 'history') {
          sources.add((call.arguments as Map)['backup'] == true);
          return snapshot('Personal name');
        }
        if (call.method == 'pickBackup')
          fail('History must not launch a backup picker');
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      expect(find.text('Muscle heatmap'), findsOneWidget);
      expect(find.text('View backup'), findsNothing);
      expect(find.text('Current records'), findsNothing);
      expect(find.text('Training summary'), findsNothing);
      expect(find.text('Personal name'), findsNothing);
      await tester.tap(find.text('List'));
      await tester.pumpAndSettle();
      expect(find.text('Personal name'), findsOneWidget);
      expect(find.text('70.5 kg'), findsOneWidget);
      expect(find.text('Single'), findsOneWidget);
      expect(find.text('Done'), findsWidgets);
      expect(find.text('Left: 25 kg × 12'), findsOneWidget);
      expect(find.text('Right: 20 kg × 10'), findsOneWidget);
      expect(find.text('No training this day'), findsOneWidget);
      expect(find.text('3 x 12 @ 30'), findsOneWidget);
      expect(sources, [false]);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'list search and scroll survive chart switching without rereading or changing sources',
    (tester) async {
      var reads = 0;
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'history') {
          reads++;
          return snapshot('My row');
        }
        return null;
      });
      await tester.pumpWidget(
        MaterialApp(
          home: HistoryPage(language: 'en', labels: labels),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('List'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('history-search')),
        'missing',
      );
      await tester.pumpAndSettle();
      expect(find.text('No records'), findsOneWidget);
      await tester.tap(find.text('Charts'));
      await tester.pumpAndSettle();
      expect(find.text('Muscle heatmap'), findsOneWidget);
      await tester.tap(find.text('List'));
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'missing',
      );
      expect(find.text('No records'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'My row');
      await tester.pumpAndSettle();
      expect(find.text('My row', findRichText: false), findsNWidgets(2));
      expect(reads, 1);
    },
  );
  testWidgets(
    'resume refresh reads only live database and keeps selected list',
    (tester) async {
      var name = 'Before restore';
      final sources = <bool>[];
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'history') {
          sources.add((call.arguments as Map)['backup'] == true);
          return snapshot(name);
        }
        return null;
      });
      await tester.pumpWidget(
        MaterialApp(
          home: HistoryPage(language: 'en', labels: labels),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('List'));
      await tester.pumpAndSettle();
      name = 'Restored personal name';
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(find.text(name), findsOneWidget);
      expect(sources, [false, false]);
    },
  );
  testWidgets('failed snapshot can retry without a backup workflow', (
    tester,
  ) async {
    var failRead = true;
    messenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'history') {
        if (failRead) throw PlatformException(code: 'read');
        return snapshot('Recovered');
      }
      return null;
    });
    await tester.pumpWidget(
      MaterialApp(
        home: HistoryPage(language: 'en', labels: labels),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Failed'), findsOneWidget);
    failRead = false;
    await tester.tap(find.text('List'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Recovered'), findsOneWidget);
  });
  testWidgets(
    'narrow large-text history header keeps both navigation targets reachable',
    (tester) async {
      tester.view.physicalSize = const Size(320, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      messenger.setMockMethodCallHandler(
        bridge,
        (call) async =>
            call.method == 'history' ? snapshot('Long exercise') : null,
      );
      await tester.pumpWidget(
        MaterialApp(
          builder: (_, child) => MediaQuery(
            data: const MediaQueryData(
              size: Size(320, 900),
              textScaler: TextScaler.linear(1.6),
            ),
            child: child!,
          ),
          home: HistoryPage(
            language: 'de',
            labels: {
              ...labels,
              'history_title': 'Trainingsverlauf',
              'tab_list': 'Liste',
              'tab_chart': 'Diagramm',
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Liste'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('history-search')), findsOneWidget);
      await tester.tap(find.text('Diagramm'));
      await tester.pumpAndSettle();
      expect(find.text('Muscle heatmap'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'embedded history paints seeded charts before fetching its record list',
    (tester) async {
      var reads = 0;
      final seeded = {...snapshot('Seeded personal name'), 'revision': 2};
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap')
          return {
            'embedded': true,
            'page': '/embedded-history',
            'language': 'en',
            'labels': labels,
            'dark': false,
            'primary': 0xff009688,
            'background': 0xffe0f2f1,
            'fixedViewport': true,
            'catalogSession': 1,
            'inlineLeft': 0.0,
            'inlineTop': 24.0,
            'inlineWidth': 800.0,
            'inlineHeight': 540.0,
            'initialHistory': {'charts': seeded['charts'], 'revision': 2},
          };
        if (call.method == 'history') {
          reads++;
          expect(call.arguments['records'], isTrue);
          return {...snapshot('Latest personal name'), 'revision': 3};
        }
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      expect(find.text('Muscle heatmap'), findsOneWidget);
      expect(reads, 0);
      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(find.text('History'), findsOneWidget);
      expect(tester.getTopLeft(find.text('History')), const Offset(16, 56));
      Future<void> update(Map<String, dynamic> data) async {
        await messenger.handlePlatformMessage(
          'gymnota/library',
          const StandardMethodCodec().encodeMethodCall(
            MethodCall('historySnapshot', data),
          ),
          (_) {},
        );
        await tester.pumpAndSettle();
      }

      await update({'charts': seeded['charts'], 'revision': 3});
      await update({...snapshot('Stale name'), 'revision': 1});
      await tester.tap(find.text('List'));
      await tester.pumpAndSettle();
      expect(find.text('Latest personal name'), findsOneWidget);
      expect(find.text('Stale name'), findsNothing);
      expect(reads, 1);
    },
  );
}
