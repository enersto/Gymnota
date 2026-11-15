import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/glass.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart' as lg;

void main() {
  testWidgets('scrolling cards do not allocate a refractive surface per row', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ListView(
            children: [
              for (var i = 0; i < 20; i++) Glass(child: Text('Row $i')),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(lg.AdaptiveGlass), findsNothing);
    expect(find.byType(lg.GlassContainer), findsWidgets);
  });
  testWidgets(
    'small dark dialog keeps translated actions and content within its bounds',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData.dark(),
          home: Scaffold(
            body: GlassAlertDialog(
              title: const Text('Neue Übung'),
              content: SingleChildScrollView(
                child: Text(
                  List.filled(40, 'Ausführungsbeschreibung').join(' '),
                ),
              ),
              actions: [
                GlassAction(label: 'Abbrechen', onPressed: () {}),
                GlassAction(
                  label: 'Diese Übung verwenden',
                  icon: Icons.add,
                  onPressed: () {},
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Diese Übung verwenden'), findsOneWidget);
      expect(
        tester.getCenter(find.byType(GlassAlertDialog)).dy,
        closeTo(320, 1),
      );
    },
  );
  testWidgets(
    'segmented controls keep a single label and update in both directions',
    (tester) async {
      var selected = false;
      final events = <bool>[];
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, update) => SizedBox(
                width: 240,
                child: GlassSegments<bool>(
                  values: const [false, true],
                  labels: const ['左侧', '右侧'],
                  selected: selected,
                  onChanged: (value) => update(() {
                    selected = value;
                    events.add(value);
                  }),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('右侧'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('左侧'));
      await tester.pumpAndSettle();
      expect(events, [true, false]);
      expect(find.text('左侧'), findsOneWidget);
      expect(find.text('右侧'), findsOneWidget);
    },
  );
  testWidgets(
    'dialog actions use the real shader with stable capsule geometry',
    (tester) async {
      var taps = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: GlassDialogAction(
                label: 'Use exercise',
                icon: Icons.add,
                onPressed: () => taps++,
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final control = tester.widget<lg.GlassButton>(
        find.byType(lg.GlassButton),
      );
      expect(control.quality, lg.GlassQuality.premium);
      expect(control.shape, isA<lg.LiquidRoundedRectangle>());
      expect(control.width!.isFinite, isTrue);
      expect(control.height, greaterThanOrEqualTo(48));
      await tester.tap(find.text('Use exercise'));
      await tester.pumpAndSettle();
      expect(taps, 1);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('icon glass keeps a 44 square inside a taller app bar', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          appBar: AppBar(
            toolbarHeight: 72,
            leading: GlassIconAction(
              icon: Icons.arrow_back,
              label: 'Back',
              onPressed: () {},
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.getSize(find.byType(lg.GlassButton)), const Size(44, 44));
    expect(tester.takeException(), isNull);
  });
  testWidgets(
    'primary segments request system haptic once per actual selection',
    (tester) async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      var haptics = 0;
      var selected = 1;
      messenger.setMockMethodCallHandler(
        const MethodChannel('gymnota/library'),
        (call) async {
          if (call.method == 'primaryHaptic') haptics++;
          return true;
        },
      );
      addTearDown(
        () => messenger.setMockMethodCallHandler(
          const MethodChannel('gymnota/library'),
          null,
        ),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (_, update) => GlassSegments<int>(
                primary: true,
                values: const [0, 1],
                labels: const ['List', 'Charts'],
                selected: selected,
                onChanged: (v) => update(() => selected = v),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('List'));
      await tester.pumpAndSettle();
      expect(haptics, 1);
      await tester.tap(find.text('List'));
      await tester.pumpAndSettle();
      expect(haptics, 1);
      await tester.tap(find.text('Charts'));
      await tester.pumpAndSettle();
      expect(haptics, 2);
    },
  );
  testWidgets(
    'responsive compact picker exposes more rows while retaining a touchable cancel',
    (tester) async {
      tester.view.physicalSize = const Size(400, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: GlassSearchPicker(
              value: '0',
              label: 'Exercises',
              cancelLabel: 'Cancel',
              options: {for (var i = 0; i < 20; i++) '$i': 'Exercise $i'},
              onSelected: (_) {},
            ),
          ),
        ),
      );
      await tester.tap(find.text('Exercise 0'));
      await tester.pumpAndSettle();
      expect(find.text('Exercise 5').hitTestable(), findsOneWidget);
      expect(find.text('Cancel').hitTestable(), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
    },
  );
  testWidgets('picker keyboard keeps options scrollable and cancel reachable', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 900);
    tester.view.devicePixelRatio = 1;
    tester.view.viewInsets = const FakeViewPadding(bottom: 350);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GlassSearchPicker(
            value: '0',
            label: 'Exercises',
            cancelLabel: 'Cancel',
            options: {for (var i = 0; i < 20; i++) '$i': 'Exercise $i'},
            onSelected: (_) {},
          ),
        ),
      ),
    );
    await tester.tap(find.text('Exercise 0'));
    await tester.pumpAndSettle();
    expect(find.text('Cancel').hitTestable(), findsOneWidget);
    expect(find.byType(ListView), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
  });
}
