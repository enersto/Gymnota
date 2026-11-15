import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/coach_markdown.dart';
import 'package:gymnota_library/coach_preview.dart';
import 'package:gymnota_library/glass.dart';
import 'package:gymnota_library/main.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart' as lg;

void main() {
  testWidgets(
    'deleting advice updates this dialog immediately and restores the row on failure',
    (tester) async {
      final messenger = tester.binding.defaultBinaryMessenger;
      const events = MethodChannel('gymnota/coach/state');
      const commands = MethodChannel('gymnota/coach');
      var deleted = Completer<bool>();
      messenger.setMockMethodCallHandler(events, (_) async => null);
      messenger.setMockMethodCallHandler(commands, (call) async {
        if (call.method == 'history') {
          return List<Object>.of([
            {
              'id': 12,
              'goal': 'Previous advice',
              'answer': 'Plan',
              'timestamp': 0,
            },
          ], growable: false);
        }
        if (call.method == 'deleteHistory') {
          expect(call.arguments, 12);
          if (await deleted.future) return true;
          throw PlatformException(code: 'deleteHistory');
        }
        return null;
      });
      addTearDown(() {
        messenger.setMockMethodCallHandler(events, null);
        messenger.setMockMethodCallHandler(commands, null);
      });
      await tester.pumpWidget(
        const MaterialApp(
          home: CoachPreview(
            labels: {
              'tab_ai_coach': 'Coach',
              'title_ai_history': 'History',
              'msg_no_history': 'No saved advice',
              'btn_close': 'Close',
              'coach_stream_failed': 'Unable to delete',
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('History'));
      await tester.pumpAndSettle();
      expect(find.text('Previous advice'), findsOneWidget);
      await tester.tap(find.byIcon(Icons.delete_outline));
      await tester.pump();
      expect(find.text('Previous advice'), findsNothing);
      expect(find.text('No saved advice'), findsOneWidget);
      deleted.complete(false);
      await tester.pumpAndSettle();
      expect(find.text('Previous advice'), findsOneWidget);
      deleted = Completer<bool>()..complete(true);
      await tester.tap(find.byIcon(Icons.delete_outline));
      await tester.pumpAndSettle();
      expect(find.text('No saved advice'), findsOneWidget);
    },
  );

  testWidgets(
    'configured model opens settings and finished replies use selectable Markdown',
    (tester) async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      const codec = StandardMethodCodec();
      const events = MethodChannel('gymnota/coach/state');
      const commands = MethodChannel('gymnota/coach');
      final called = <String>[];
      Map<Object?, Object?>? sent;
      messenger.setMockMethodCallHandler(events, (_) async => null);
      messenger.setMockMethodCallHandler(commands, (call) async {
        called.add(call.method);
        if (call.method == "send")
          sent = call.arguments as Map<Object?, Object?>;
        return call.method == 'history' ? <Object>[] : null;
      });
      addTearDown(() {
        messenger.setMockMethodCallHandler(events, null);
        messenger.setMockMethodCallHandler(commands, null);
      });

      await tester.pumpWidget(
        MaterialApp(
          home: CoachPreview(
            labels: const {
              'tab_ai_coach': 'Coach',
              'btn_close': 'Close',
              'title_ai_history': 'History',
              'msg_no_history': 'No saved advice',
              'weekly_plan': 'Weekly plan',
              'btn_free_chat': 'Chat',
              'btn_get_advice': 'Get advice',
              'ai_response_title': 'Answer',
              'coach_stream_stop': 'Stop',
              'btn_generate_and_import': 'Generate and import',
              'btn_test_connection': 'Test connection',
              'msg_connection_success': 'Connected',
              'focus_comprehensive': 'Balanced',
              'category_strength': 'Strength',
              'category_cardio': 'Cardio',
              'scene_gym': 'Gym',
              'scene_home_equip': 'Home equipment',
              'injury_knee': 'Knee',
              'injury_wrist': 'Wrist',
            },
            onClose: () {},
          ),
        ),
      );
      await tester.pump();
      await messenger.handlePlatformMessage(
        events.name,
        codec.encodeSuccessEnvelope({
          'configured': true,
          'provider': 'Provider',
          'model': 'Model',
          'answer': '## Training plan\n1. **Warm up**\n> *Go slowly*',
        }),
        (_) {},
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Provider · Model'));
      expect(called, contains('settings'));
      await tester.tap(find.byIcon(Icons.network_check));
      expect(called, contains('testConnection'));

      Future<void> select(String title) async {
        final chip = find.widgetWithText(GlassFilter, title);
        await tester.scrollUntilVisible(
          chip,
          140,
          scrollable: find.byType(Scrollable).first,
        );
        await tester.tap(find.text(title));
        await tester.pumpAndSettle();
        expect(tester.widget<GlassFilter>(chip).selected, isTrue);
        expect(
          find.descendant(of: chip, matching: find.byIcon(Icons.check)),
          findsOneWidget,
        );
      }

      await select('Strength');
      expect(
        tester
            .widget<GlassFilter>(find.widgetWithText(GlassFilter, 'Balanced'))
            .selected,
        isFalse,
      );
      await select('Cardio');
      await select('Home equipment');
      await select('Knee');
      await select('Wrist');
      final button = find.widgetWithText(GlassCommand, 'Get advice');
      await tester.scrollUntilVisible(
        button,
        180,
        scrollable: find.byType(Scrollable).first,
      );
      expect(tester.widget<GlassCommand>(button).primary, isTrue);
      await tester.tap(find.text('Get advice'));
      await tester.pumpAndSettle();
      expect(sent?['focus'], unorderedEquals(['STRENGTH', 'CARDIO']));
      expect(sent?['scene'], unorderedEquals(['GYM', 'HOME_EQUIP']));
      expect(sent?['injuries'], unorderedEquals(['Knee', 'Wrist']));

      final markdown = find.byType(CoachMarkdown);
      await tester.scrollUntilVisible(
        markdown,
        180,
        scrollable: find.byType(Scrollable).first,
      );
      expect(
        find.descendant(of: markdown, matching: find.byType(SelectionArea)),
        findsOneWidget,
      );
      expect(find.text('Training plan', findRichText: true), findsOneWidget);
      expect(find.text('Warm up', findRichText: true), findsOneWidget);

      await tester.tap(find.byTooltip('History'));
      await tester.pumpAndSettle();
      final dialog = tester.widget<GlassAlertDialog>(
        find.byType(GlassAlertDialog),
      );
      expect(dialog.wide, isTrue);
      expect(find.text('No saved advice'), findsOneWidget);
      await tester.tap(find.text('Close').last);
      await tester.pumpAndSettle();

      Future<void> update(Map<String, Object> state) async {
        await messenger.handlePlatformMessage(
          events.name,
          codec.encodeSuccessEnvelope({'configured': true, ...state}),
          (_) {},
        );
        await tester.pump();
      }

      Future<void> checkCapsule(String text) async {
        final action = find.widgetWithText(GlassCommand, text);
        await tester.scrollUntilVisible(
          action,
          120,
          scrollable: find.byType(Scrollable).first,
        );
        final button = tester.widget<lg.GlassButton>(
          find.descendant(of: action, matching: find.byType(lg.GlassButton)),
        );
        expect(button.shape, isA<lg.LiquidRoundedRectangle>());
        expect(
          (button.shape as lg.LiquidRoundedRectangle).borderRadius,
          (button.height! / 2).clamp(20.0, 28.0),
        );
      }

      await update({'busy': true, 'connection': 'connected'});
      await tester.scrollUntilVisible(
        find.byTooltip('Connected'),
        -160,
        scrollable: find.byType(Scrollable).first,
      );
      await checkCapsule('Stop');
      await update({'canImport': true, 'answer': 'Plan'});
      await checkCapsule('Generate and import');
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'embedded root coach keeps the themed title and glass goal inside its viewport',
    (tester) async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      const events = MethodChannel('gymnota/coach/state');
      messenger.setMockMethodCallHandler(events, (_) async => null);
      messenger.setMockMethodCallHandler(bridge, (call) async {
        if (call.method != 'bootstrap') return null;
        return {
          'embedded': true,
          'page': '/embedded-coach',
          'language': 'zh',
          'fixedViewport': true,
          'inlineTop': 24.0,
          'inlineLeft': 0.0,
          'inlineWidth': 800.0,
          'inlineHeight': 470.0,
          'catalogSession': 1,
          'primary': 0xff5a9270,
          'onBackground': 0xff264633,
          'background': 0xffebf5e7,
          'labels': {
            'tab_ai_coach': 'AI 私教',
            'btn_close': '关闭',
            'label_current_goal': '当前目标',
            'weekly_plan': '周度方案',
            'btn_free_chat': '自由对话',
            'title_ai_history': '历史建议',
          },
        };
      });
      addTearDown(() {
        messenger.setMockMethodCallHandler(events, null);
        messenger.setMockMethodCallHandler(bridge, null);
      });
      await tester.pumpWidget(const LibraryApp());
      await tester.pumpAndSettle();
      final title = find.text('AI 私教');
      expect(tester.widget<Text>(title).style?.color, const Color(0xff264633));
      expect(tester.getTopLeft(title).dx, 16);
      expect(find.byTooltip('关闭'), findsNothing);
      final input = find.byType(TextField);
      await tester.scrollUntilVisible(
        input,
        140,
        scrollable: find.byType(Scrollable).first,
      );
      expect(
        tester.widget<TextField>(input).decoration?.enabledBorder,
        InputBorder.none,
      );
      await tester.enterText(input, '增强核心力量');
      expect(find.text('增强核心力量'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
