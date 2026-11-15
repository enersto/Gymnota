import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'glass.dart';

typedef ChartMap = Map<String, dynamic>;
ChartMap chartMap(dynamic value) => Map<String, dynamic>.from(value as Map);
List<ChartMap> chartRows(dynamic value) =>
    (value as List? ?? []).map(chartMap).toList();
String chartNumber(num value) =>
    value.toStringAsFixed(2).replaceFirst(RegExp(r'\.?0+$'), '');

class HistoryChartsView extends StatelessWidget {
  final ChartMap? bundle;
  final Map<String, String> labels;
  final VoidCallback onNative;
  const HistoryChartsView({
    super.key,
    required this.bundle,
    required this.labels,
    required this.onNative,
  });
  @override
  Widget build(BuildContext context) {
    final l = labels;
    if (bundle == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(l['charts_unavailable'] ?? ''),
              TextButton(
                onPressed: onNative,
                child: Text(l['flutter_native'] ?? ''),
              ),
            ],
          ),
        ),
      );
    }
    final data = bundle!;
    final body = chartMap(data['body']);
    Widget heading(String key) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Text(
        l[key] ?? '',
        style: Theme.of(context).textTheme.titleLarge?.copyWith(
          fontWeight: FontWeight.bold,
          color: Theme.of(context).colorScheme.onSurface,
        ),
      ),
    );
    return ListView(
      key: const PageStorageKey('history-chart-scroll'),
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
      children: [
        BodyHeatmap(rows: chartRows(data['heatmap']), labels: l),
        heading('header_strength_train'),
        ExerciseChartSection(category: 'STRENGTH', bundle: data, labels: l),
        heading('header_core_train'),
        ExerciseChartSection(category: 'CORE', bundle: data, labels: l),
        heading('header_cardio_train'),
        HistoryGraphCard(
          title: l['chart_title_cardio_total'] ?? '',
          series: chartMap(data['cardio']),
          labels: l,
          bars: true,
          unit: l['flutter_minutes'] ?? '',
        ),
        const SizedBox(height: 12),
        ExerciseChartSection(category: 'CARDIO', bundle: data, labels: l),
        heading('chart_title_body_status'),
        for (final item in const {
          'weight': 'chart_title_weight',
          'bmi': 'chart_title_bmi',
          'bmr': 'chart_title_bmr',
          'bodyFatKg': 'chart_title_body_fat',
          'skeletalMuscleKg': 'chart_title_skeletal_muscle',
          'bodyWaterPercentage': 'chart_title_body_water',
          'whr': 'chart_title_whr',
        }.entries)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: HistoryGraphCard(
              key: ValueKey(item.key),
              title: l[item.value] ?? '',
              series: chartMap(body[item.key]),
              labels: l,
            ),
          ),
      ],
    );
  }
}

class ExerciseChartSection extends StatefulWidget {
  final String category;
  final ChartMap bundle;
  final Map<String, String> labels;
  const ExerciseChartSection({
    super.key,
    required this.category,
    required this.bundle,
    required this.labels,
  });
  @override
  State<ExerciseChartSection> createState() => _ExerciseChartSectionState();
}

class _ExerciseChartSectionState extends State<ExerciseChartSection> {
  String? selected;
  bool right = false;
  @override
  Widget build(BuildContext context) {
    final choices = chartRows(widget.bundle['exercises'])
        .where((e) => e['category'] == widget.category)
        .toList();
    final l = widget.labels;
    if (choices.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Text(l['chart_no_data'] ?? ''),
      );
    }
    if (!choices.any((e) => e['name'] == selected)) {
      selected = choices.first['name'] as String;
      right = false;
    }
    final exercise = choices.firstWhere((e) => e['name'] == selected);
    final sides =
        widget.category == 'STRENGTH' && exercise['unilateral'] == true;
    final type = exercise['logType'];
    final mode = type == 1
        ? 'duration'
        : type == 2
        ? 'reps'
        : (sides && right ? 'rightWeight' : 'weight');
    final unit = type == 1
        ? l['flutter_minutes'] ?? ''
        : type == 2
        ? l['label_reps'] ?? ''
        : 'kg';
    final title =
        l[widget.category == 'STRENGTH'
            ? 'chart_title_strength_single'
            : widget.category == 'CORE'
            ? 'chart_title_core_single'
            : 'chart_title_cardio_single'] ??
        '';
    final all = chartMap(widget.bundle['exerciseSeries']);
    final series = chartMap(chartMap(all[selected])[mode]);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        GlassSearchPicker(
          value: selected!,
          label: l['title_select_exercise'] ?? '',
          cancelLabel: l['btn_cancel'] ?? '',
          options: {
            for (final e in choices) e['name'] as String: e['name'] as String,
          },
          onSelected: (value) {
            FocusScope.of(context).unfocus();
            setState(() {
              selected = value;
              right = false;
            });
          },
        ),
        if (sides)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: SizedBox(
                width: 220,
                child: GlassSegments<bool>(
                  values: const [false, true],
                  labels: [
                    l['label_side_left'] ?? '',
                    l['label_side_right'] ?? '',
                  ],
                  selected: right,
                  onChanged: (value) => setState(() => right = value),
                ),
              ),
            ),
          ),
        const SizedBox(height: 12),
        HistoryGraphCard(title: title, series: series, labels: l, unit: unit),
      ],
    );
  }
}

class HistoryGraphCard extends StatefulWidget {
  final String title, unit;
  final ChartMap series;
  final Map<String, String> labels;
  final bool bars;
  const HistoryGraphCard({
    super.key,
    required this.title,
    required this.series,
    required this.labels,
    this.unit = '',
    this.bars = false,
  });
  @override
  State<HistoryGraphCard> createState() => _HistoryGraphCardState();
}

class _HistoryGraphCardState extends State<HistoryGraphCard> {
  bool month = false;
  int? selected;
  @override
  void didUpdateWidget(HistoryGraphCard old) {
    super.didUpdateWidget(old);
    if (old.series != widget.series) {
      selected = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final points = chartRows(widget.series[month ? 'monthly' : 'daily']);
    final l = widget.labels;
    final selection = selected != null && selected! < points.length
        ? points[selected!]
        : null;
    return Glass(
      radius: 16,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              widget.title,
              style: Theme.of(context).textTheme.titleMedium
                  ?.copyWith(fontWeight: FontWeight.bold),
            ),
            Wrap(
              alignment: WrapAlignment.end,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 4,
              children: [
                GlassIconAction(
                  label: l['charts_data'] ?? '',
                  onPressed: points.isEmpty
                      ? null
                      : () => showDialog<void>(
                          context: context,
                          builder: (_) => GlassAlertDialog(
                            title: Text(
                              widget.title,
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            content: SizedBox(
                              width: 400,
                              height: 360,
                              child: ListView.builder(
                                itemCount: points.length,
                                itemBuilder: (_, i) => ListTile(
                                  title: Text(points[i]['date'] as String),
                                  trailing: Text(
                                    '${chartNumber(points[i]['value'] as num)} ${widget.unit}',
                                  ),
                                ),
                              ),
                            ),
                            actions: [
                              GlassAction(
                                onPressed: () => Navigator.of(context).pop(),
                                label: l['btn_confirm'] ?? '',
                              ),
                            ],
                          ),
                        ),
                  icon: Icons.table_rows_outlined,
                ),
                SizedBox(
                  width: 160,
                  child: GlassSegments<bool>(
                    values: const [false, true],
                    labels: [
                      l['chart_granularity_day'] ?? '',
                      l['chart_granularity_month'] ?? '',
                    ],
                    selected: month,
                    onChanged: (value) => setState(() {
                      month = value;
                      selected = null;
                    }),
                  ),
                ),
              ],
            ),
            if (points.isEmpty)
              SizedBox(
                height: 180,
                child: Center(child: Text(l['chart_no_data'] ?? '')),
              )
            else
              LayoutBuilder(
                builder: (context, constraints) {
                  final painter = HistoryGraphPainter(
                    points: points,
                    color: Theme.of(context).colorScheme.onSurface,
                    textColor: Theme.of(context).colorScheme.onSurface,
                    bars: widget.bars,
                    selected: selected,
                    textDirection: Directionality.of(context),
                  );
                  final size = Size(constraints.maxWidth, 220);
                  return Semantics(
                    label: widget.title,
                    child: GestureDetector(
                      onTapDown: (tap) => setState(() {
                        final plot = painter.plotRect(size);
                        final fraction =
                            ((tap.localPosition.dx - plot.left) / plot.width)
                                .clamp(0.0, 1.0);
                        selected = points.length == 1
                            ? 0
                            : (fraction * (points.length - 1)).round();
                      }),
                      child: RepaintBoundary(
                        child: CustomPaint(size: size, painter: painter),
                      ),
                    ),
                  );
                },
              ),
            if (selection != null)
              Text(
                '${selection['date']} · ${chartNumber(selection['value'] as num)} ${widget.unit}',
                textAlign: TextAlign.center,
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
          ],
        ),
      ),
    );
  }
}

class HistoryGraphPainter extends CustomPainter {
  final List<ChartMap> points;
  final Color color, textColor;
  final bool bars;
  final int? selected;
  final TextDirection textDirection;
  HistoryGraphPainter({
    required this.points,
    required this.color,
    required this.textColor,
    required this.bars,
    required this.selected,
    required this.textDirection,
  });
  Rect plotRect(Size size) {
    if (points.isEmpty)
      return Rect.fromLTRB(12, 18, size.width - 6, size.height - 32);
    final values = points.map((p) => (p['value'] as num).toDouble()).toList();
    final low = math.min(0.0, values.reduce(math.min) * 1.2);
    final high = math.max(1.0, values.reduce(math.max) * 1.2);
    var width = 0.0;
    for (var i = 0; i <= 3; i++) {
      final label = TextPainter(
        text: TextSpan(
          text: chartNumber(low + (high - low) * i / 3),
          style: TextStyle(fontSize: 10, color: textColor),
        ),
        textDirection: textDirection,
      )..layout();
      width = math.max(width, label.width);
      label.dispose();
    }
    return Rect.fromLTRB(width + 8, 18, size.width - 6, size.height - 32);
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (points.isEmpty || size.width <= 68) return;
    final plot = plotRect(size);
    final values = points.map((p) => (p['value'] as num).toDouble()).toList();
    final low = math.min(0.0, values.reduce(math.min) * 1.2),
        high = math.max(1.0, values.reduce(math.max) * 1.2);
    double y(double v) => plot.bottom - (v - low) / (high - low) * plot.height;
    double x(int i) => bars
        ? plot.left + (i + .5) * plot.width / points.length
        : points.length == 1
        ? plot.center.dx
        : plot.left + i * plot.width / (points.length - 1);
    void label(String text, Offset location, {bool center = true}) {
      final painter = TextPainter(
        text: TextSpan(
          text: text,
          style: TextStyle(color: textColor, fontSize: 10),
        ),
        textDirection: textDirection,
        maxLines: 1,
      )..layout();
      painter.paint(
        canvas,
        Offset(
          center
              ? (location.dx - painter.width / 2).clamp(
                  0.0,
                  size.width - painter.width,
                )
              : 0,
          location.dy,
        ),
      );
      painter.dispose();
    }

    for (var i = 0; i <= 3; i++) {
      final value = low + (high - low) * i / 3;
      final yy = y(value);
      canvas.drawLine(
        Offset(plot.left, yy),
        Offset(plot.right, yy),
        Paint()..color = textColor.withValues(alpha: .15),
      );
      label(chartNumber(value), Offset(0, yy - 6), center: false);
    }
    if (!bars && points.length > 1) {
      final path = Path()..moveTo(x(0), y(values[0]));
      for (var i = 1; i < points.length; i++) {
        path.lineTo(x(i), y(values[i]));
      }
      canvas.drawPath(
        path,
        Paint()
          ..color = color
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2,
      );
    }
    for (var i = 0; i < points.length; i++) {
      final xx = x(i), yy = y(values[i]);
      if (bars) {
        final width = (plot.width / points.length * .6).clamp(1.0, 28.0);
        canvas.drawRect(
          Rect.fromLTRB(
            xx - width / 2,
            math.min(yy, y(0)),
            xx + width / 2,
            math.max(yy, y(0)),
          ),
          Paint()..color = color.withValues(alpha: .75),
        );
      } else {
        canvas.drawCircle(
          Offset(xx, yy),
          selected == i ? 5 : 3,
          Paint()..color = color,
        );
      }
      if (i == 0 ||
          i == points.length - 1 ||
          i % math.max(1, (points.length / 4).ceil()) == 0) {
        final date = points[i]['date'] as String;
        label(
          date.length >= 10 ? date.substring(5) : date,
          Offset(xx, plot.bottom + 10),
        );
      }
    }
  }

  @override
  bool shouldRepaint(HistoryGraphPainter old) =>
      old.points != points ||
      old.color != color ||
      old.textColor != textColor ||
      old.selected != selected ||
      old.bars != bars ||
      old.textDirection != textDirection;
}

class BodyHeatmap extends StatefulWidget {
  final List<ChartMap> rows;
  final Map<String, String> labels;
  const BodyHeatmap({super.key, required this.rows, required this.labels});
  @override
  State<BodyHeatmap> createState() => _BodyHeatmapState();
}

class _BodyHeatmapState extends State<BodyHeatmap> {
  String? selected;
  @override
  Widget build(BuildContext context) {
    final data = {
      for (final row in widget.rows) row['bodyPart'] as String: row,
    };
    final l = widget.labels;
    const grid = [
      [null, null, 'head', null, null],
      [null, 'part_shoulders', 'part_chest', 'part_shoulders', null],
      ['part_arms', null, 'part_back', null, 'part_arms'],
      ['part_arms', null, 'part_abs', null, 'part_arms'],
      [null, 'part_hips', 'part_hips', 'part_hips', null],
      [null, 'part_thighs', null, 'part_thighs', null],
      [null, 'part_thighs', null, 'part_thighs', null],
      [null, 'part_calves', null, 'part_calves', null],
    ];
    Color heat(double value) => value <= 0
        ? Colors.grey.withValues(alpha: .15)
        : value < .5
        ? const Color(0xff81c784)
        : value < .8
        ? const Color(0xffffb74d)
        : const Color(0xffe57373);
    return Glass(
      radius: 16,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Text(
              l['chart_title_heatmap'] ?? '',
              style: Theme.of(context).textTheme.titleMedium
                  ?.copyWith(fontWeight: FontWeight.bold),
            ),
            SizedBox(
              height: 32,
              child: Center(
                child: Text(
                  selected == null
                      ? ''
                      : '${l[selected] ?? selected}: ${chartNumber((data[selected]?['volume'] as num?) ?? 0)}',
                ),
              ),
            ),
            for (final row in grid)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  for (final part in row)
                    Padding(
                      padding: const EdgeInsets.all(3),
                      child: part == null
                          ? const SizedBox(width: 24, height: 24)
                          : Semantics(
                              label: l[part],
                              button: part != 'head',
                              child: InkWell(
                                onTap: part == 'head'
                                    ? null
                                    : () => setState(() => selected = part),
                                child: Container(
                                  width: 24,
                                  height: 24,
                                  decoration: BoxDecoration(
                                    color: part == 'head'
                                        ? Colors.grey.withValues(alpha: .2)
                                        : heat(
                                            (data[part]?['intensity'] as num?)
                                                    ?.toDouble() ??
                                                0,
                                          ),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                ),
                              ),
                            ),
                    ),
                ],
              ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 16,
              children: [
                Text(
                  '0',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
                ),
                Text(
                  l['charts_low'] ?? '',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
                ),
                Text(
                  l['charts_high'] ?? '',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              l['hint_heatmap_volume'] ?? '',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}
