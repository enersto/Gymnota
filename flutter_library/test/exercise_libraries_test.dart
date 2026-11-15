import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/glass.dart';
import 'package:gymnota_library/main.dart';

import 'embedded_catalog_test.dart' as native;

void main() {
  testWidgets(
    'personal exercises keep their IDs across search and catalogue switching',
    (tester) async {
      final messenger = tester.binding.defaultBinaryMessenger;
      final actions = <Map<Object?, Object?>>[];
      final queries = <String>[];
      final state = native.config()
        ..addAll({
          'exerciseManager': true,
          'personalSelected': true,
          'personalSearchRevision': 1,
          'onBackground': 0xff074038,
          'personalRows': [
            {
              'id': 42,
              'name': '我的卧推',
              'category': 'STRENGTH',
              'bodyLabel': 'Chest',
              'equipment': 'Barbell',
            },
            {'id': 51, 'name': 'Bike ride', 'category': 'CARDIO'},
          ],
          'labels': {
            ...native.labels,
            'catalog_personal_title': 'My exercises',
            'catalog_title': 'Built-in exercises',
            'category_strength': 'Strength',
            'category_cardio': 'Cardio',
            'category_core': 'Core',
            'btn_add': 'Add',
            'btn_delete': 'Delete',
          },
        });

      messenger.setMockMethodCallHandler(bridge, (call) async {
        switch (call.method) {
          case 'bootstrap':
            return state;
          case 'exerciseManagerAction':
            actions.add(Map<Object?, Object?>.from(call.arguments as Map));
            return true;
          case 'personalSearch':
            queries.add(call.arguments['query'] as String);
            return [42];
          case 'primaryHaptic':
            return true;
          default:
            return null;
        }
      });
      addTearDown(() => messenger.setMockMethodCallHandler(bridge, null));

      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      expect(find.text('我的卧推'), findsOneWidget);
      expect(find.text('Press'), findsNothing);
      expect(find.byType(GlassSegments<String>), findsOneWidget);
      final strength = tester.getCenter(find.text('Strength')).dx;
      final cardio = tester.getCenter(find.text('Cardio')).dx;
      final core = tester.getCenter(find.text('Core')).dx;
      final track = tester.getRect(find.byType(GlassSegments<String>));
      expect(cardio, closeTo(track.center.dx, 2));
      expect(cardio - strength, closeTo(core - cardio, 2));
      expect(core - strength, greaterThan(track.width / 2));

      await tester.tap(find.text('我的卧推'));
      await tester.tap(find.byTooltip('Delete'));
      await tester.tap(find.byTooltip('Add').hitTestable());
      expect(actions.take(3), [
        {'action': 'view', 'id': 42},
        {'action': 'delete', 'id': 42},
        {'action': 'add', 'id': null},
      ]);

      await tester.tap(find.text('Cardio'));
      await tester.pumpAndSettle();
      expect(find.text('Bike ride'), findsOneWidget);

      await tester.tap(find.text('Built-in exercises'));
      expect(actions.last['action'], 'showCatalog');
      await native.nativeState(tester, {...state, 'personalSelected': false});
      await tester.pumpAndSettle();
      expect(find.text('Press'), findsOneWidget);
      expect(find.text('Bike ride'), findsNothing);
      expect(find.byType(GlassScrollableSegments<String?>), findsOneWidget);

      await tester.tap(find.text('My exercises'));
      expect(actions.last['action'], 'showPersonal');
      await native.nativeState(tester, state);
      await tester.pumpAndSettle();
      expect(find.text('Bike ride'), findsOneWidget);

      await tester.tap(find.text('Strength'));
      await native.nativeState(tester, {...state, 'query': '卧推'});
      await tester.pumpAndSettle();
      expect(queries, ['卧推']);
      expect(find.text('我的卧推'), findsOneWidget);
    },
  );
}
