import 'dart:ui';

import 'package:flutter/scheduler.dart';

/// Bounded developer diagnostics. No messages, exercise names, database values or credentials.
class GlassFrameProbe {
  final frames = <FrameTiming>[];
  void start() => SchedulerBinding.instance.addTimingsCallback(record);
  void stop() => SchedulerBinding.instance.removeTimingsCallback(record);
  void record(List<FrameTiming> values) {
    frames.addAll(values);
    if (frames.length > 240) frames.removeRange(0, frames.length - 240);
  }

  Map<String, Object> snapshot({bool reset = false}) {
    final build =
        frames.map((f) => f.buildDuration.inMicroseconds / 1000).toList()
          ..sort();
    final raster =
        frames.map((f) => f.rasterDuration.inMicroseconds / 1000).toList()
          ..sort();
    final rate = PlatformDispatcher.instance.views.first.display.refreshRate;
    final budget = 1000 / (rate > 0 ? rate : 60);
    double percentile(List<double> samples, double p) =>
        samples.isEmpty ? 0 : samples[((samples.length - 1) * p).round()];
    final result = <String, Object>{
      'samples': frames.length,
      'shaderFilterSupported': ImageFilter.isShaderFilterSupported,
      'frameBudgetMs': budget,
      'buildP50Ms': percentile(build, .5),
      'buildP95Ms': percentile(build, .95),
      'rasterP50Ms': percentile(raster, .5),
      'rasterP95Ms': percentile(raster, .95),
      'overBudget': frames
          .where(
            (f) =>
                f.buildDuration.inMicroseconds / 1000 > budget ||
                f.rasterDuration.inMicroseconds / 1000 > budget,
          )
          .length,
    };
    if (reset) frames.clear();
    return result;
  }
}

class GlassPopupProbe {
  static bool active = false;
  static double? firstPaintMs, fullyVisibleMs;
  static Map<String,Object> get values => {'popupActive':active,
    if(firstPaintMs!=null)'popupFirstPaintMs':firstPaintMs!,
    if(fullyVisibleMs!=null)'popupFullyVisibleMs':fullyVisibleMs!};
}
