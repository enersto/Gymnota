import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/history_charts.dart';
import 'package:gymnota_library/main.dart';

const labels = {
  'chart_granularity_day': 'Day',
  'chart_granularity_month': 'Month',
  'charts_data': 'Data',
  'btn_confirm': 'OK',
  'label_side_left': 'Left',
  'label_side_right': 'Right',
  'title_select_exercise': 'Exercise',
  'chart_title_strength_single': 'Strength',
  'tab_list': 'List',
  'tab_chart': 'Charts',
  'chart_title_heatmap': 'Heatmap',
  'no_history': 'No records',
  'charts_unavailable': 'Charts unavailable',
  'flutter_native': 'Return',
};
ChartMap series(double daily, double monthly) => {
  'daily': [
    {'date': '2026-11-01', 'value': daily},
  ],
  'monthly': [
    {'date': '2026-11-01', 'value': monthly},
  ],
};
HistoryGraphPainter painter(WidgetTester tester) =>
    (tester
            .widget<CustomPaint>(
              find.byWidgetPredicate(
                (w) => w is CustomPaint && w.painter is HistoryGraphPainter,
              ),
            )
            .painter!
        as HistoryGraphPainter);

void main() {
  testWidgets(
    'day/month uses supplied Rust points and supports tap/data table',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: HistoryGraphCard(
              title: 'Weight',
              series: series(40, 30),
              labels: labels,
            ),
          ),
        ),
      );
      expect(painter(tester).points.single['value'], 40);
      await tester.tap(find.text('Month'));
      await tester.pumpAndSettle();
      expect(painter(tester).points.single['value'], 30);
      await tester.tap(
        find.byWidgetPredicate(
          (w) => w is CustomPaint && w.painter is HistoryGraphPainter,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('2026-11-01 · 30'), findsOneWidget);
      await tester.tap(find.byTooltip('Data'));
      await tester.pumpAndSettle();
      expect(find.text('2026-11-01'), findsOneWidget);
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
    },
  );

  testWidgets(
    'unilateral side and searchable exercise selection update the chart',
    (tester) async {
      final bundle = <String, dynamic>{
        'exercises': [
          {
            'name': 'Press',
            'category': 'STRENGTH',
            'unilateral': true,
            'logType': 0,
          },
          {
            'name': 'Other',
            'category': 'STRENGTH',
            'unilateral': false,
            'logType': 0,
          },
        ],
        'exerciseSeries': {
          'Press': {'weight': series(40, 30), 'rightWeight': series(20, 15)},
          'Other': {'weight': series(60, 50)},
        },
      };
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ExerciseChartSection(
                category: 'STRENGTH',
                bundle: bundle,
                labels: labels,
              ),
            ),
          ),
        ),
      );
      expect(painter(tester).points.single['value'], 40);
      await tester.tap(find.text('Right'));
      await tester.pumpAndSettle();
      expect(painter(tester).points.single['value'], 20);
      await tester.tap(find.text('Press'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Other');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Other').last);
      await tester.pumpAndSettle();
      expect(painter(tester).points.single['value'], 60);
      expect(find.text('Right'), findsNothing);
    },
  );

  testWidgets('history keeps list/chart navigation and a native fallback', (
    tester,
  ) async {
    var closed = false;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'close') {
        closed = true;
        return true;
      }
      if (call.method == 'history') {
        return {'tasks': [], 'weights': [], 'engine': 'kotlin-fallback'};
      }
      return null;
    });
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: HistoryPage(language: 'en', labels: labels),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Charts unavailable'), findsOneWidget);
    await tester.tap(find.text('Return'));
    await tester.pumpAndSettle();
    expect(closed, true);
    await tester.tap(find.text('List'));
    await tester.pumpAndSettle();
    expect(find.text('No records'), findsOneWidget);
    messenger.setMockMethodCallHandler(bridge, null);
  });
  test('axis gutter follows measured values rather than reserving a fixed fifty pixels', () {
    HistoryGraphPainter make(double value) => HistoryGraphPainter(
      points: [
        {'date': '2026-11-09', 'value': value},
      ],
      color: Colors.green,
      textColor: Colors.black,
      bars: false,
      selected: null,
      textDirection: TextDirection.ltr,
    );
    final small = make(1).plotRect(const Size(360, 220));
    final large = make(12345678).plotRect(const Size(360, 220));
    expect(small.left, lessThan(50));
    expect(large.left, greaterThan(small.left));
    expect(small.right, 354);
  });
}
