import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart' as lg;

bool glassShadersReady = false;
Future<void> initializeGlass() async {
  await lg.LiquidGlassWidgets.initialize(enablePerformanceMonitor: false);
  glassShadersReady = true;
}

Widget glassApplication(Widget child) => lg.LiquidGlassWidgets.wrap(
  child: child,
  brightnessResolver: Theme.maybeBrightnessOf,
  adaptiveQuality: true,
  theme: const lg.GlassThemeData(
    style: lg.GlassStyle.ios26,
    light: lg.GlassThemeVariant(
      quality: lg.GlassQuality.standard,
      settings: lg.GlassThemeSettings(
        blur: 4,
        thickness: 18,
        refractiveIndex: 1.12,
        glassColor: Color(0x18FFFFFF),
      ),
    ),
    dark: lg.GlassThemeVariant(
      quality: lg.GlassQuality.standard,
      settings: lg.GlassThemeSettings(
        blur: 5,
        thickness: 20,
        refractiveIndex: 1.12,
        glassColor: Color(0x281C1C1E),
      ),
    ),
  ),
);

lg.LiquidGlassSettings glassSettings(
  BuildContext context, {
  bool platter = false,
}) => lg.LiquidGlassSettings(
  glassColor: Theme.of(context).brightness == Brightness.dark
      ? const Color(0x381C1C1E)
      : const Color(0x28FFFFFF),
  thickness: platter ? 24 : 18,
  blur: platter ? 8 : 4,
  refractiveIndex: 1.12,
  chromaticAberration: 0.01,
  lightIntensity: 0.6,
);

// The same optical settings serve full-width actions, pickers and navigation.
lg.LiquidGlassSettings _controlGlass(BuildContext context, Color tint) =>
    glassSettings(context).copyWith(
      glassColor: tint,
      blur: 2.5,
      thickness: 28,
      refractiveIndex: 1.32,
      lightIntensity: 1.1,
      chromaticAberration: .008,
      rimShade: .35,
      rimLight: .58,
    );

// Match Settings: Material headlineSmall and the host theme's onBackground.
TextStyle rootPageTitleStyle(BuildContext context) => Theme.of(context)
    .textTheme
    .headlineSmall!
    .copyWith(
      color: Theme.of(context).colorScheme.onSurface,
      fontWeight: FontWeight.normal,
    );

class GlassInputSurface extends StatefulWidget {
  const GlassInputSurface({super.key, required this.child});
  final Widget child;

  @override
  State<GlassInputSurface> createState() => _GlassInputSurfaceState();
}

class _GlassInputSurfaceState extends State<GlassInputSurface> {
  bool focused = false;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Focus(
      canRequestFocus: false,
      onFocusChange: (value) => setState(() => focused = value),
      child: lg.AdaptiveGlass(
        shape: const lg.LiquidRoundedSuperellipse(borderRadius: 16),
        settings: _controlGlass(
          context,
          colors.primary.withValues(alpha: focused ? .16 : .07),
        ),
        quality: lg.GlassQuality.standard,
        allowElevation: false,
        child: Material(
          color: colors.surface.withValues(alpha: .27),
          borderRadius: BorderRadius.circular(16),
          child: widget.child,
        ),
      ),
    );
  }
}

class _NavigationGlass extends StatelessWidget {
  const _NavigationGlass({required this.radius, required this.child});
  final double radius;
  final Widget child;

  @override
  Widget build(BuildContext context) => Stack(
    children: [
      Positioned.fill(
        child: IgnorePointer(
          child: lg.AdaptiveGlass(
            shape: lg.LiquidRoundedRectangle(borderRadius: radius),
            settings: _controlGlass(
              context,
              Theme.of(context).colorScheme.primary
                  .withValues(alpha: _segmentTrackTint),
            ),
            quality: lg.GlassQuality.standard,
            allowElevation: false,
            child: const SizedBox.expand(),
          ),
        ),
      ),
      child,
    ],
  );
}

/// Large/scrolling content stays shader-free. Only control chrome uses refraction.
class Glass extends StatelessWidget {
  final Widget child;
  final bool blur;
  final double radius;
  final bool definedEdge;
  const Glass({
    super.key,
    required this.child,
    this.blur = false,
    this.radius = 24,
    this.definedEdge = false,
  });
  @override
  Widget build(BuildContext context) {
    if (blur)
      return lg.AdaptiveGlass(
        shape: lg.LiquidRoundedSuperellipse(borderRadius: radius),
        settings: glassSettings(context),
        quality: lg.GlassQuality.standard,
        allowElevation: false,
        child: Material(type: MaterialType.transparency, child: child),
      );
    final theme = Theme.of(context);
    final surface = lg.GlassContainer.card(
      surfaceStyle: lg.GlassSurfaceStyle.solid,
      padding: EdgeInsets.zero,
      shape: lg.LiquidRoundedSuperellipse(borderRadius: radius),
      color: Color.alphaBlend(
        Colors.black.withValues(alpha: .06),
        Theme.of(context).colorScheme.surface,
      ).withValues(alpha: definedEdge ? .32 : .46),
      child: Material(type: MaterialType.transparency, child: child),
    );
    if (!definedEdge) return surface;
    return Container(
      foregroundDecoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(
          color: Colors.white.withValues(
            alpha: theme.brightness == Brightness.dark ? .16 : .62,
          ),
          width: .75,
        ),
      ),
      child: surface,
    );
  }
}

class GlassCommand extends StatelessWidget {
  const GlassCommand({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.primary = false,
    this.fullWidth = false,
    this.minHeight = 48,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool primary, fullWidth;
  final double minHeight;

  static Size measureLabel(
    BuildContext context,
    String label,
    double width, {
    required bool hasIcon,
  }) {
    final horizontal = hasIcon ? 54.0 : 28.0;
    final text = TextPainter(
      text: TextSpan(
        text: label,
        style: Theme.of(context).textTheme.labelLarge!
            .copyWith(fontWeight: FontWeight.w600),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
    )..layout(maxWidth: (width - horizontal).clamp(1.0, double.infinity));
    final size = Size(text.width + horizontal, text.height + 24);
    text.dispose();
    return size;
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final theme = Theme.of(context);
      final colors = theme.colorScheme;
      final style = theme.textTheme.labelLarge!.copyWith(
        color: colors.onSurface.withValues(alpha: onPressed == null ? .42 : 1),
        fontWeight: FontWeight.w600,
      );
      final limit = constraints.hasBoundedWidth ? constraints.maxWidth : 360.0;
      final measured = measureLabel(
        context,
        label,
        limit,
        hasIcon: icon != null,
      );
      final width = fullWidth || constraints.hasTightWidth
          ? limit
          : measured.width.clamp(0.0, limit);
      final height = measured.height.clamp(minHeight, double.infinity);

      return lg.GlassButton.custom(
        label: label,
        onTap: onPressed ?? () {},
        enabled: onPressed != null,
        width: width,
        height: height,
        shape: lg.LiquidRoundedRectangle(
          borderRadius: (height / 2).clamp(20.0, 28.0),
        ),
        quality: lg.GlassQuality.standard,
        settings: _controlGlass(
          context,
          colors.primary.withValues(alpha: primary ? .17 : .075),
        ),
        interactionScale: 1.02,
        stretch: 0,
        anchorStretch: false,
        persistPressOnDrag: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (icon != null) ...[
                Icon(
                  icon,
                  size: 18,
                  color: primary
                      ? style.color
                      : colors.primary.withValues(
                          alpha: onPressed == null ? .42 : 1,
                        ),
                ),
                const SizedBox(width: 8),
              ],
              Flexible(
                child: Text(label, style: style, textAlign: TextAlign.center),
              ),
            ],
          ),
        ),
      );
    },
  );
}

class TintedGlassTile extends StatelessWidget {
  const TintedGlassTile({
    super.key,
    required this.tint,
    required this.onTap,
    required this.child,
  });

  final Color tint;
  final VoidCallback onTap;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final blend = tint.withValues(alpha: .84);
    final composite = Color.alphaBlend(blend, theme.colorScheme.surface);
    final foreground = composite.computeLuminance() > .18
        ? Colors.black
        : Colors.white;
    return lg.AdaptiveGlass(
      shape: const lg.LiquidRoundedRectangle(borderRadius: 16),
      settings: _controlGlass(
        context,
        blend,
      ).copyWith(thickness: 18, blur: 3.5),
      quality: lg.GlassQuality.standard,
      allowElevation: false,
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          child: DefaultTextStyle.merge(
            style: TextStyle(color: foreground),
            child: child,
          ),
        ),
      ),
    );
  }
}

class GlassAction extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  const GlassAction({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
  });
  @override
  Widget build(BuildContext context) => lg.GlassButton.custom(
    label: label,
    onTap: onPressed ?? () {},
    enabled: onPressed != null,
    quality: lg.GlassQuality.standard,
    settings: glassSettings(context),
    height: 44,
    interactionScale: 1.02,
    stretch: .15,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[Icon(icon, size: 18), const SizedBox(width: 6)],
          Flexible(
            child: Text(
              label,
              maxLines: 2,
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    ),
  );
}

class GlassFilter extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const GlassFilter({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
  });
  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      selected: selected,
      button: true,
      child: lg.AdaptiveGlass(
        shape: const lg.LiquidRoundedRectangle(borderRadius: 24),
        settings: _controlGlass(
          context,
          colors.primary.withValues(
            alpha: selected ? _segmentIndicatorTint : _segmentTrackTint,
          ),
        ),
        quality: lg.GlassQuality.standard,
        allowElevation: false,
        child: Material(
          color: selected
              ? colors.primary.withValues(alpha: _segmentSelectionTint)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(24),
          child: InkWell(
            borderRadius: BorderRadius.circular(24),
            onTap: onTap,
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 44),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 10,
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (selected) ...[
                      Icon(Icons.check, size: 16, color: colors.onSurface),
                      const SizedBox(width: 6),
                    ],
                    Flexible(
                      child: Text(
                        label,
                        style: _segmentLabel(context, selected),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

double glassSegmentHeight(BuildContext context) =>
    (MediaQuery.textScalerOf(context).scale(16) * 1.3 + 16).clamp(44.0, 96.0);

const _segmentTrackTint = .06;
const _segmentIndicatorTint = .15;
const _segmentSelectionTint = .14;
const _segmentPadding = EdgeInsets.all(4);
const glassMenuGap = 12.0;

TextStyle? _segmentLabel(BuildContext context, bool selected) =>
    Theme.of(context).textTheme.labelLarge?.copyWith(
      color: Theme.of(context).colorScheme.onSurface,
      fontWeight: selected ? FontWeight.bold : FontWeight.normal,
    );

class GlassSegments<T> extends StatefulWidget {
  final List<T> values;
  final List<String> labels;
  final T selected;
  final ValueChanged<T> onChanged;
  final bool primary;
  final double? height;
  const GlassSegments({
    super.key,
    required this.values,
    required this.labels,
    required this.selected,
    required this.onChanged,
    this.primary = false,
    this.height,
  });
  @override
  State<GlassSegments<T>> createState() => _GlassSegmentsState<T>();
}

class _GlassSegmentsState<T> extends State<GlassSegments<T>> {
  late T lastEmitted;
  @override
  void initState() {
    super.initState();
    lastEmitted = widget.selected;
  }

  @override
  void didUpdateWidget(GlassSegments<T> old) {
    super.didUpdateWidget(old);
    lastEmitted = widget.selected;
  }

  @override
  Widget build(BuildContext context) {
    final height = widget.height ?? glassSegmentHeight(context);
    return _NavigationGlass(
      radius: height / 2,
      child: lg.GlassSegmentedControl(
        segments: [
          for (final label in widget.labels) lg.GlassSegment(label: label),
        ],
        selectedIndex: widget.values.indexOf(widget.selected),
        onSegmentSelected: (index) {
          final value = widget.values[index];
          if (value == lastEmitted) return;
          lastEmitted = value;
          if (widget.primary) {
            const MethodChannel('gymnota/library')
                .invokeMethod('primaryHaptic')
                .catchError((_) => null);
          }
          widget.onChanged(value);
        },
        height: height,
        borderRadius: height / 2,
        indicatorBorderRadius: height / 2 - 4,
        padding: _segmentPadding,
        quality: lg.GlassQuality.standard,
        settings: _controlGlass(context, Colors.transparent),
        backgroundColor: Colors.transparent,
        indicatorSettings: _controlGlass(
          context,
          Theme.of(context).colorScheme.primary
              .withValues(alpha: _segmentIndicatorTint),
        ),
        indicatorColor: Theme.of(context).colorScheme.primary
            .withValues(alpha: _segmentSelectionTint),
        indicatorShadow: const [],
        selectedTextStyle: _segmentLabel(context, true),
        unselectedTextStyle: _segmentLabel(context, false),
      ),
    );
  }
}

/// Drag the active indicator to select, or swipe elsewhere to scroll the track.
class GlassScrollableSegments<T> extends StatefulWidget {
  final List<T> values;
  final List<String> labels;
  final T selected;
  final ValueChanged<T> onChanged;
  const GlassScrollableSegments({
    super.key,
    required this.values,
    required this.labels,
    required this.selected,
    required this.onChanged,
  });
  @override
  State<GlassScrollableSegments<T>> createState() =>
      _GlassScrollableSegmentsState<T>();
}

class _GlassScrollableSegmentsState<T>
    extends State<GlassScrollableSegments<T>> {
  late T lastEmitted;
  @override
  void initState() {
    super.initState();
    lastEmitted = widget.selected;
  }

  @override
  void didUpdateWidget(GlassScrollableSegments<T> old) {
    super.didUpdateWidget(old);
    lastEmitted = widget.selected;
  }

  @override
  Widget build(BuildContext context) {
    final height = glassSegmentHeight(context);
    return _NavigationGlass(
      radius: height / 2,
      child: lg.GlassSegmentedControl.scrollable(
        segments: [
          for (final label in widget.labels) lg.GlassSegment(label: label),
        ],
        selectedIndex: widget.values.indexOf(widget.selected),
        onSegmentSelected: (index) {
          final value = widget.values[index];
          if (lastEmitted == value) return;
          lastEmitted = value;
          widget.onChanged(value);
        },
        dragBehavior: lg.SegmentDragBehavior.selectIndicator,
        height: height,
        borderRadius: height / 2,
        indicatorBorderRadius: height / 2 - 4,
        padding: _segmentPadding,
        labelPadding: const EdgeInsets.symmetric(horizontal: 16),
        backgroundColor: Colors.transparent,
        indicatorSettings: _controlGlass(
          context,
          Theme.of(context).colorScheme.primary
              .withValues(alpha: _segmentIndicatorTint),
        ),
        indicatorColor: Theme.of(context).colorScheme.primary
            .withValues(alpha: _segmentSelectionTint),
        quality: lg.GlassQuality.standard,
        settings: _controlGlass(context, Colors.transparent),
        indicatorShadow: const [],
        selectedTextStyle: _segmentLabel(context, true),
        unselectedTextStyle: _segmentLabel(context, false),
      ),
    );
  }
}

/// The glass platter and controls are siblings so the platter cannot suppress their refraction.
class GlassAlertDialog extends StatelessWidget {
  final Widget? title, content;
  final List<Widget>? actions;
  final bool compact;
  final bool wide;
  const GlassAlertDialog({
    super.key,
    this.title,
    this.content,
    this.actions,
    this.compact = false,
    this.wide = false,
  });
  @override
  Widget build(BuildContext context) => Dialog(
    insetPadding: wide
        ? const EdgeInsets.symmetric(horizontal: 16, vertical: 24)
        : null,
    backgroundColor: Colors.transparent,
    surfaceTintColor: Colors.transparent,
    child: ConstrainedBox(
      constraints: BoxConstraints(
        maxWidth: wide ? 640 : 420,
        maxHeight: MediaQuery.sizeOf(context).height - 64,
      ),
      child: Stack(
        children: [
          Positioned.fill(
            child: lg.AdaptiveGlass(
              shape: const lg.LiquidRoundedSuperellipse(borderRadius: 28),
              settings: glassSettings(context, platter: true).copyWith(
                blur: 4,
                glassColor: Theme.of(context).brightness == Brightness.dark
                    ? const Color(0xCC1E1E1E)
                    : (wide
                          ? Color.alphaBlend(
                              Colors.black.withValues(alpha: .08),
                              Theme.of(context).colorScheme.surface,
                            ).withValues(alpha: .72)
                          : const Color(0xC7FFFFFF)),
              ),
              quality: lg.GlassQuality.minimal,
              child: const SizedBox.expand(),
            ),
          ),
          Padding(
            padding: EdgeInsets.all(compact ? 16 : 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (title != null) ...[
                  ConstrainedBox(
                    constraints: BoxConstraints(
                      maxHeight: (MediaQuery.sizeOf(context).height * .30)
                          .clamp(72, 240),
                    ),
                    child: SingleChildScrollView(
                      child: DefaultTextStyle(
                        style: Theme.of(context).textTheme.headlineSmall!,
                        child: title!,
                      ),
                    ),
                  ),
                  SizedBox(height: compact ? 8 : 16),
                ],
                if (content != null)
                  Flexible(
                    child: DefaultTextStyle(
                      style: Theme.of(context).textTheme.bodyMedium!,
                      child: content!,
                    ),
                  ),
                if (actions != null) ...[
                  SizedBox(height: compact ? 8 : 20),
                  OverflowBar(
                    alignment: MainAxisAlignment.end,
                    spacing: 8,
                    overflowSpacing: 8,
                    children: actions!
                        .map(
                          (action) => action is GlassAction
                              ? GlassDialogAction(
                                  label: action.label,
                                  icon: action.icon,
                                  onPressed: action.onPressed,
                                )
                              : action,
                        )
                        .toList(),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class GlassOverlayBackdrop {
  final Uint8List? bytes;
  final Size? viewport;
  const GlassOverlayBackdrop(this.bytes, {this.viewport});
}

final glassOverlay = ValueNotifier<GlassOverlayBackdrop?>(null);
GlassOverlayBackdrop? catalogCachedBackdrop;

Rect catalogInlineRect(Map<String, dynamic> config) => Rect.fromLTWH(
  (config['inlineLeft'] as num? ?? 0).toDouble(),
  (config['inlineTop'] as num? ?? 0).toDouble(),
  (config['inlineWidth'] as num? ?? 0).toDouble().clamp(0, double.infinity),
  (config['inlineHeight'] as num? ?? 0).toDouble().clamp(0, double.infinity),
);

class _InlineClipper extends CustomClipper<Rect> {
  final Rect rect;
  const _InlineClipper(this.rect);
  @override
  Rect getClip(Size size) => rect.intersect(Offset.zero & size);
  @override
  bool shouldReclip(_InlineClipper old) => old.rect != rect;
}

/// Full-window Navigator, inline content. Dialogs never change this viewport.
class CatalogViewport extends StatefulWidget {
  final Map<String, dynamic> config;
  final Widget child;
  const CatalogViewport({super.key, required this.config, required this.child});
  @override
  State<CatalogViewport> createState() => _CatalogViewportState();
}

class _CatalogViewportState extends State<CatalogViewport> {
  Rect? lastVisible;
  @override
  Widget build(BuildContext context) {
    if (widget.config['fixedViewport'] != true) return widget.child;
    final rect = catalogInlineRect(widget.config);
    final size = MediaQuery.sizeOf(context);
    final bottom = size.height - MediaQuery.viewInsetsOf(context).bottom;
    final height = rect.height.clamp(
      0.0,
      (bottom - rect.top).clamp(0.0, double.infinity),
    );
    final hidden = rect.isEmpty || height <= 0;
    if (!hidden)
      lastVisible = Rect.fromLTWH(rect.left, rect.top, rect.width, height);
    final layout = lastVisible ?? (Offset.zero & size);
    return Stack(
      fit: StackFit.expand,
      children: [
        Positioned.fromRect(
          rect: layout,
          child: Offstage(
            offstage: hidden,
            child: ClipRect(child: widget.child),
          ),
        ),
      ],
    );
  }
}

class GlassScene extends StatelessWidget {
  final Map<String, dynamic> config;
  final Widget child;
  const GlassScene({super.key, required this.config, required this.child});
  @override
  Widget build(BuildContext context) =>
      ValueListenableBuilder<GlassOverlayBackdrop?>(
        valueListenable: glassOverlay,
        builder: (_, overlay, __) {
          final fixed = config['fixedViewport'] == true;
          final gradient = CustomPaint(painter: _ScenePainter(config));
          final background = fixed
              ? Stack(
                  fit: StackFit.expand,
                  children: [
                    if (overlay?.bytes != null)
                      Image.memory(
                        overlay!.bytes!,
                        fit: BoxFit.fill,
                        gaplessPlayback: true,
                      ),
                    ClipRect(
                      clipper: _InlineClipper(catalogInlineRect(config)),
                      child: gradient,
                    ),
                  ],
                )
              : gradient;
          return lg.GlassPage(
            background: background,
            enableBackgroundSampling: true,
            statusBarStyle: lg.GlassStatusBarStyle.none,
            edgeToEdge: false,
            child: child,
          );
        },
      );
}

class _ScenePainter extends CustomPainter {
  final Map<String, dynamic> state;
  _ScenePainter(this.state);
  @override
  void paint(Canvas canvas, Size size) {
    final base = Color(state['background'] as int? ?? 0xffe0f2f1);
    final primary = Color(state['primary'] as int? ?? 0xff009688);
    final secondary = Color(state['secondary'] as int? ?? primary.toARGB32());
    final x = (state['sceneX'] as num? ?? 0).toDouble(),
        y = (state['sceneY'] as num? ?? 0).toDouble();
    final width = (state['sceneWidth'] as num? ?? size.width).toDouble(),
        height = (state['sceneHeight'] as num? ?? size.height).toDouble();
    final shader = ui.Gradient.linear(
      Offset(-x, -y),
      Offset(width - x, height - y),
      [
        Color.alphaBlend(primary.withValues(alpha: .18), base),
        Color.alphaBlend(secondary.withValues(alpha: .10), base),
        base,
      ],
      const [0, .5, 1],
    );
    canvas.drawRect(Offset.zero & size, Paint()..shader = shader);
  }

  @override
  bool shouldRepaint(_ScenePainter old) => old.state != state;
}

class GlassIconAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;
  const GlassIconAction({
    super.key,
    required this.icon,
    required this.label,
    this.onPressed,
  });
  @override
  Widget build(BuildContext context) => Tooltip(
    message: label,
    child: Align(
      widthFactor: 1,
      heightFactor: 1,
      child: SizedBox.square(
        dimension: 44,
        child: lg.GlassButton(
          icon: Icon(icon),
          label: label,
          onTap: onPressed ?? () {},
          enabled: onPressed != null,
          width: 44,
          height: 44,
          quality: lg.GlassQuality.standard,
          settings: glassSettings(context),
          interactionScale: 1.02,
          stretch: .15,
        ),
      ),
    ),
  );
}

class _WrappingGlassPicker extends StatelessWidget {
  final String value, label;
  final VoidCallback onTap;
  const _WrappingGlassPicker({
    required this.value,
    required this.label,
    required this.onTap,
  });
  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    label: label,
    value: value,
    child: lg.AdaptiveGlass(
      shape: const lg.LiquidRoundedRectangle(borderRadius: 10),
      settings: _controlGlass(
        context,
        Theme.of(context).colorScheme.primary.withValues(alpha: .06),
      ),
      quality: lg.GlassQuality.standard,
      allowElevation: false,
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(10),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 48),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      value,
                      softWrap: true,
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: Theme.of(context).colorScheme.primary,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Icon(
                    Icons.unfold_more,
                    size: 22,
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class GlassSearchPicker extends StatelessWidget {
  final Map<String, String> options;
  final String value, label, cancelLabel;
  final ValueChanged<String> onSelected;
  const GlassSearchPicker({
    super.key,
    required this.options,
    required this.value,
    required this.label,
    required this.cancelLabel,
    required this.onSelected,
  });
  @override
  Widget build(BuildContext context) => _WrappingGlassPicker(
    value: options[value] ?? label,
    label: label,
    onTap: () async {
      final result = await showGeneralDialog<String>(
        context: context,
        barrierDismissible: true,
        barrierLabel: MaterialLocalizations.of(context)
            .modalBarrierDismissLabel,
        barrierColor: Colors.black45,
        requestFocus: false,
        transitionDuration: const Duration(milliseconds: 145),
        transitionBuilder: (_, animation, __, child) =>
            FadeTransition(opacity: animation, child: child),
        pageBuilder: (_, __, ___) => _ExercisePickerDialog(
          label: label,
          cancelLabel: cancelLabel,
          options: options,
          value: value,
        ),
      );
      if (result != null && context.mounted) onSelected(result);
    },
  );
}

class _ExercisePickerDialog extends StatefulWidget {
  const _ExercisePickerDialog({
    required this.options,
    required this.value,
    required this.label,
    required this.cancelLabel,
  });

  final Map<String, String> options;
  final String value, label, cancelLabel;

  @override
  State<_ExercisePickerDialog> createState() => _ExercisePickerDialogState();
}

class _ExercisePickerDialogState extends State<_ExercisePickerDialog> {
  late final tokens = [
    for (final option in widget.options.entries)
      (option, option.value.toLowerCase()),
  ];
  String query = '';

  @override
  Widget build(BuildContext context) {
    final matches = query.isEmpty
        ? tokens
        : tokens.where((entry) => entry.$2.contains(query)).toList();
    final theme = Theme.of(context);
    final background = Color.alphaBlend(
      theme.colorScheme.primary.withValues(alpha: .08),
      theme.colorScheme.surface,
    );
    return SafeArea(
      child: Dialog(
        backgroundColor: background,
        surfaceTintColor: Colors.transparent,
        insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        child: SizedBox(
          width: 420,
          height: (MediaQuery.sizeOf(context).height * .75).clamp(230.0, 600.0),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(widget.label, style: theme.textTheme.titleLarge),
                const SizedBox(height: 8),
                TextField(
                  onChanged: (text) =>
                      setState(() => query = text.trim().toLowerCase()),
                  decoration: InputDecoration(
                    prefixIcon: const Icon(Icons.search),
                    hintText: widget.label,
                    filled: true,
                    fillColor: theme.colorScheme.primary.withValues(alpha: .08),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide.none,
                    ),
                    isDense: true,
                  ),
                ),
                const SizedBox(height: 6),
                Expanded(
                  child: ListView.builder(
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    itemCount: matches.length,
                    itemBuilder: (context, index) {
                      final option = matches[index].$1;
                      final selected = option.key == widget.value;
                      return ListTile(
                        title: Text(
                          option.value,
                          style: selected
                              ? const TextStyle(fontWeight: FontWeight.bold)
                              : null,
                        ),
                        selected: selected,
                        selectedColor: theme.colorScheme.onSurface,
                        onTap: () => Navigator.pop(context, option.key),
                      );
                    },
                  ),
                ),
                Align(
                  alignment: AlignmentDirectional.centerEnd,
                  child: TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text(widget.cancelLabel),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

String dialogGlassQuality = 'unbuilt';

/// Two bounded controls use the full backdrop refraction pipeline.
class GlassDialogAction extends StatelessWidget {
  final String label;
  final IconData? icon;
  final bool primary;
  final bool fullWidth;
  final VoidCallback? onPressed;
  const GlassDialogAction({
    super.key,
    required this.label,
    this.icon,
    this.primary = false,
    this.fullWidth = false,
    this.onPressed,
  });
  @override
  Widget build(BuildContext context) => lg.GlassAdaptiveScope(
    minQuality: lg.GlassQuality.premium,
    maxQuality: lg.GlassQuality.premium,
    initialQuality: lg.GlassQuality.premium,
    child: Builder(
      builder: (context) {
        dialogGlassQuality = lg.GlassAdaptiveScopeData.of(context)
            .effectiveQuality
            .name;
        return LayoutBuilder(
          builder: (context, constraints) {
            final style = Theme.of(context).textTheme.labelLarge!.copyWith(
              color: primary
                  ? Theme.of(context).colorScheme.onSurface
                  : Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.w600,
            );
            final limit = constraints.hasBoundedWidth
                ? constraints.maxWidth
                : 360.0;
            final text =
                TextPainter(
                  text: TextSpan(text: label, style: style),
                  textDirection: Directionality.of(context),
                  textScaler: MediaQuery.textScalerOf(context),
                  maxLines: 2,
                )..layout(
                  maxWidth: (limit - 40 - (icon == null ? 0 : 24)).clamp(
                    1,
                    1000,
                  ),
                );
            final width = primary || fullWidth
                ? limit
                : (text.width + 40 + (icon == null ? 0 : 24)).clamp(
                    72.0,
                    limit,
                  );
            final height = (text.height + 24).clamp(48.0, 96.0);
            text.dispose();
            final isPageAction = primary || fullWidth;
            return lg.GlassButton.custom(
              label: label,
              onTap: onPressed ?? () {},
              enabled: onPressed != null,
              width: width,
              height: height,
              // The upstream custom default is an oval, not a capsule.
              shape: lg.LiquidRoundedRectangle(borderRadius: height / 2),
              quality: lg.GlassQuality.premium,
              settings: isPageAction
                  ? _controlGlass(
                      context,
                      Theme.of(context).colorScheme.primary
                          .withValues(alpha: primary ? .09 : .05),
                    )
                  : glassSettings(context).copyWith(
                      blur: 1.5,
                      thickness: 28,
                      refractiveIndex: 1.32,
                      lightIntensity: 1.1,
                      chromaticAberration: .008,
                      glassColor:
                          Theme.of(context).brightness == Brightness.dark
                          ? const Color(0x181C1C1E)
                          : const Color(0x12FFFFFF),
                    ),
              interactionScale: isPageAction ? 1.027 : 1.015,
              stretch: 0,
              anchorStretch: false,
              persistPressOnDrag: false,
              useOwnLayer: true,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    if (icon != null) ...[
                      Icon(icon, size: 18, color: style.color),
                      const SizedBox(width: 6),
                    ],
                    Flexible(
                      child: Text(
                        label,
                        style: style,
                        maxLines: 2,
                        textAlign: TextAlign.center,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    ),
  );
}
