import 'dart:async';
import 'dart:typed_data';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/main.dart';
import 'package:gymnota_library/glass.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart' as lg;

const labels = <String, String>{
  'catalog_setup_hint': 'Check settings before saving',
  'catalog_all_parts': 'All parts',
  'part_chest': 'Chest',
  'part_back': 'Back',
  'part_shoulders': 'Shoulders',
  'part_hips': 'Hips',
  'part_thighs': 'Thighs',
  'part_calves': 'Calves',
  'part_arms': 'Arms',
  'part_abs': 'Core',
  'part_cardio': 'Cardio',
  'part_other': 'Other',
  'catalog_empty': 'No matches',
  'catalog_use_exercise': 'Use exercise',
  'btn_cancel': 'Cancel',
  'label_target': 'Target',
  'label_log_type': 'Record type',
  'label_instruction': 'Instructions',
  'label_instruction_empty': 'No instructions',
  'catalog_search': 'Search',
};
Json entry(String name) => {
  'id': '0001',
  'name': name,
  'bodyPart': 'part_chest',
  'bodyLabel': 'Chest',
  'equipment': 'Barbell',
  'categoryLabel': 'Strength',
  'logTypeLabel': 'Weight × reps',
  'unilateral': true,
  'target': '3 × 12',
  'instruction': 'Lower slowly',
};
Json config({bool search = false}) => {
  'embedded': true,
  'page': '/embedded-catalog',
  'language': 'en',
  'query': '',
  'showSearch': search,
  'canSwipe': true,
  'dark': false,
  'primary': 0xff009688,
  'background': 0xffe0f2f1,
  'labels': labels,
  'catalogLanguage': 'en',
  'initialCatalog': {
    'entries': [entry('Press')],
  },
};

Future<void> nativeState(WidgetTester tester, Json values) async {
  await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
    'gymnota/library',
    const StandardMethodCodec().encodeMethodCall(
      MethodCall('embeddedState', values),
    ),
    (_) {},
  );
  await tester.pump();
}

Future<dynamic> nativeBack(WidgetTester tester) async {
  dynamic result;
  await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
    'gymnota/library',
    const StandardMethodCodec().encodeMethodCall(
      const MethodCall('embeddedBack'),
    ),
    (data) {
      if (data != null)
        result = const StandardMethodCodec().decodeEnvelope(data);
    },
  );
  await tester.pumpAndSettle();
  return result;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  tearDown(() {
    messenger.setMockMethodCallHandler(bridge, null);
  });

  testWidgets(
    'original tab content uses bootstrap rows without another page or search bar; details return selection without closing host',
    (tester) async {
      String? selected;
      var calls = 0;
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap') return config();
        if (call.method == 'catalog') {
          calls++;
          return {
            'entries': [entry('Press')],
          };
        }
        if (call.method == 'media') throw PlatformException(code: 'test');
        if (call.method == 'useExercise')
          selected = (call.arguments as Map)['id'];
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      expect(calls, 0);
      expect(find.text('Press'), findsOneWidget);
      expect(find.byType(AppBar), findsNothing);
      expect(find.byType(TextField), findsNothing);
      expect(find.textContaining('Flutter'), findsNothing);
      expect(find.textContaining('full screen'), findsNothing);
      await tester.tap(find.text('Press'));
      await tester.pumpAndSettle();
      expect(find.byType(GlassAlertDialog), findsOneWidget);
      expect(find.text('Record type: Weight × reps'), findsOneWidget);
      expect(find.text('Target: 3 × 12'), findsOneWidget);
      expect(find.text('Lower slowly'), findsOneWidget);
      expect(await nativeBack(tester), isTrue);
      expect(find.byType(GlassAlertDialog), findsNothing);
      await tester.tap(find.text('Press'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Use exercise'));
      await tester.pumpAndSettle();
      expect(selected, '0001');
      expect(find.byType(GlassAlertDialog), findsNothing);
      expect(find.text('Press'), findsOneWidget);
      await tester.tap(find.text('Back'));
      await tester.pumpAndSettle();
      expect(calls, 1);
    },
  );

  testWidgets(
    'native search and language updates reach the same list and late replies cannot replace current results',
    (tester) async {
      final late = Completer<Json>();
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap') return config();
        if (call.method == 'media') throw PlatformException(code: 'test');
        if (call.method == 'catalog') {
          final args = object(call.arguments);
          if (args['query'] == 'old') return late.future;
          return {
            'entries': args['query'] == 'missing'
                ? []
                : [
                    entry(
                      args['language'] == 'de' ? 'Bankdrücken' : 'New press',
                    ),
                  ],
          };
        }
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      await nativeState(tester, {
        'language': 'en',
        'query': 'old',
        'labels': labels,
      });
      await tester.pump(const Duration(milliseconds: 200));
      await nativeState(tester, {
        'language': 'en',
        'query': 'new',
        'labels': labels,
      });
      await tester.pump(const Duration(milliseconds: 200));
      await tester.pumpAndSettle();
      expect(find.text('New press'), findsOneWidget);
      late.complete({
        'entries': [entry('Stale result')],
      });
      await tester.pumpAndSettle();
      expect(find.text('Stale result'), findsNothing);
      await nativeState(tester, {
        'language': 'de',
        'query': 'new',
        'labels': labels,
      });
      await tester.pumpAndSettle();
      expect(find.text('Bankdrücken'), findsOneWidget);
      await nativeState(tester, {
        'language': 'de',
        'query': 'missing',
        'labels': labels,
      });
      await tester.pump(const Duration(milliseconds: 200));
      await tester.pumpAndSettle();
      expect(find.text('No matches'), findsOneWidget);
    },
  );

  testWidgets(
    'animation picker reuses the same content with its own search field',
    (tester) async {
      var query = '';
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap') return config(search: true);
        if (call.method == 'media') throw PlatformException(code: 'test');
        if (call.method == 'catalog') {
          query = object(call.arguments)['query'];
          return {'entries': []};
        }
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      expect(find.byType(TextField), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'row');
      await tester.pump(const Duration(milliseconds: 200));
      await tester.pumpAndSettle();
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      addTearDown(tester.view.resetViewInsets);
      await tester.pump();
      expect(await nativeBack(tester), isTrue);
      expect(tester.testTextInput.isVisible, isFalse);
      expect(query, 'row');
      expect(find.text('No matches'), findsOneWidget);
    },
  );
  testWidgets(
    'full-window overlay closes before native editing and restores the list offset without querying again',
    (tester) async {
      final events = <String>[];
      final c = config();
      c['initialCatalog'] = {
        'entries': [for (var i = 0; i < 30; i++) entry('Press $i')],
      };
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap') return c;
        if (call.method == 'media')
          return base64Decode(
            'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4////fwAJ+wP9KobjigAAAABJRU5ErkJggg==',
          );
        events.add(call.method);
        if (call.method == 'openCatalogOverlay') return true;
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      await tester.drag(find.byType(ListView), const Offset(0, -350));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Press 5'));
      final offset = tester
          .state<ScrollableState>(find.byType(Scrollable).last)
          .position
          .pixels;
      await tester.tap(find.text('Press 5'));
      await tester.tap(
        find.text('Press 5'),
      ); // Rapid repeated taps open only one modal.
      await tester.pumpAndSettle();
      expect(events, ['openCatalogOverlay']);
      expect(find.byType(GlassAlertDialog), findsOneWidget);
      await tester.tap(
        find.descendant(
          of: find.byType(GlassAlertDialog),
          matching: find.byType(ExerciseImage),
        ),
      );
      await tester.pumpAndSettle();
      final image = find.byType(InteractiveViewer);
      expect(image, findsOneWidget);
      final viewport = tester.view.physicalSize / tester.view.devicePixelRatio;
      expect(
        (tester.getCenter(image) - viewport.center(Offset.zero)).distance,
        lessThan(1),
      );
      expect(
        tester.getSize(image).width,
        closeTo(tester.getSize(image).height, 0.1),
      );
      expect(await nativeBack(tester), isTrue);
      expect(find.byType(GlassAlertDialog), findsOneWidget);
      expect(events, ['openCatalogOverlay']);

      await tester.tap(find.text('Use exercise'));
      await tester.pumpAndSettle();
      expect(events, [
        'openCatalogOverlay',
        'closeCatalogOverlay',
        'useExercise',
      ]);
      expect(find.byType(GlassAlertDialog), findsNothing);
      expect(
        tester
            .state<ScrollableState>(find.byType(Scrollable).last)
            .position
            .pixels,
        closeTo(offset, 0.1),
      );
    },
  );
  testWidgets(
    'detail never requests a screenshot and keeps its opening backdrop',
    (tester) async {
      final backdrop = Completer<Uint8List?>();
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap') return config();
        if (call.method == 'media') throw PlatformException(code: 'test');
        if (call.method == 'openCatalogOverlay') return true;
        if (call.method == 'catalogOverlayBackdrop') return backdrop.future;
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      final backgrounds = <GlassOverlayBackdrop>[];
      void recordBackdrop() {
        if (glassOverlay.value != null) backgrounds.add(glassOverlay.value!);
      }

      glassOverlay.addListener(recordBackdrop);
      addTearDown(() => glassOverlay.removeListener(recordBackdrop));
      await tester.tap(find.text('Press'));
      await tester.pump(const Duration(milliseconds: 200));
      await tester.pumpAndSettle();
      expect(find.byType(GlassAlertDialog), findsOneWidget);
      expect(backgrounds.length, 1);
      final button = find.widgetWithText(GlassDialogAction, 'Use exercise');
      expect(button, findsOneWidget);
      expect(tester.getSize(button).height, inInclusiveRange(44, 90));
      expect(tester.getSize(button).width, inInclusiveRange(72, 372));
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(find.byType(GlassAlertDialog), findsNothing);
      backdrop.complete(null);
      await tester.pumpAndSettle();
      expect(glassOverlay.value, isNull);
      expect(backgrounds.length, 1);
      expect(find.text('Press'), findsOneWidget);
    },
  );
  testWidgets(
    'body filters drag to the last part and diagonal flings never switch the library',
    (tester) async {
      final methods = <String>[];
      String? selectedPart;
      messenger.setMockMethodCallHandler(bridge, (call) async {
        methods.add(call.method);
        if (call.method == 'bootstrap') return config();
        if (call.method == 'media') throw PlatformException(code: 'test');
        if (call.method == 'catalog') {
          selectedPart = (call.arguments as Map)['part'] as String?;
          return {
            'entries': List.generate(
              30,
              (i) => {...entry('Press $i'), 'id': '$i'},
            ),
          };
        }
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      final track = find.byType(lg.GlassSegmentedControl);
      expect(track, findsOneWidget);
      final segmented = tester.widget<lg.GlassSegmentedControl>(track);
      expect(segmented.isScrollable, isTrue);
      expect(segmented.dragBehavior, lg.SegmentDragBehavior.selectIndicator);
      expect(
        tester.getTopLeft(track).dy,
        lessThan(
          tester.getTopLeft(find.text('Check settings before saving')).dy,
        ),
      );
      final filters = find.byWidgetPredicate(
        (w) =>
            w is SingleChildScrollView && w.scrollDirection == Axis.horizontal,
      );
      await tester.drag(filters, const Offset(-1600, 0));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Other'));
      await tester.tap(find.text('Other'));
      await tester.pumpAndSettle();
      expect(selectedPart, 'part_other');
      await tester.fling(find.byType(ListView), const Offset(150, -500), 2500);
      await tester.pumpAndSettle();
      expect(methods, isNot(contains('showPersonal')));
      expect(find.byType(ListView), findsOneWidget);
      expect(tester.takeException(), isNull);
      await nativeState(tester, {
        'language': 'en',
        'query': '',
        'labels': labels,
        'catalogSession': 2,
      });
      await tester.pumpAndSettle();
      expect(tester.widget<lg.GlassSegmentedControl>(track).selectedIndex, 0);
      expect(find.text('Press'), findsOneWidget);
    },
  );
  testWidgets(
    'fixed viewport positions the list while dialogs stay centered in the full canvas',
    (tester) async {
      final c = {
        ...config(),
        'fixedViewport': true,
        'inlineLeft': 40.0,
        'inlineTop': 150.0,
        'inlineWidth': 500.0,
        'inlineHeight': 420.0,
      };
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method == 'bootstrap') return c;
        if (call.method == 'media') throw PlatformException(code: 'test');
        if (call.method == 'openCatalogOverlay') return true;
        if (call.method == 'catalogOverlayBackdrop')
          fail('No readback on the click path');
        return null;
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      final pane = find.byType(CatalogViewport);
      expect(tester.getSize(pane), const Size(800, 600));
      final list = find.byType(ListView);
      final before = tester.getRect(list);
      expect(before.left, 40);
      expect(before.right, 540);
      await tester.tap(find.text('Press'));
      await tester.pumpAndSettle();
      expect(
        tester.getCenter(find.byType(GlassAlertDialog)),
        const Offset(400, 300),
      );
      expect(tester.getRect(list), before);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(tester.getRect(list), before);
    },
  );
  testWidgets('long catalog detail title wraps without a one-line ellipsis', (
    tester,
  ) async {
    const name =
        'Einarmiges vorgebeugtes Kurzhantelrudern mit langem persönlichen Übungsnamen';
    final c = {
      ...config(),
      'initialCatalog': {
        'entries': [entry(name)],
      },
    };
    messenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'bootstrap') return c;
      if (call.method == 'media') throw PlatformException(code: 'test');
      if (call.method == 'openCatalogOverlay') return true;
      return null;
    });
    await tester.pumpWidget(const LibraryApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text(name));
    await tester.pumpAndSettle();
    final title = tester.widgetList<Text>(find.text(name)).last;
    expect(title.maxLines, isNull);
    expect(title.overflow, isNot(TextOverflow.ellipsis));
    expect(tester.takeException(), isNull);
  });
}
