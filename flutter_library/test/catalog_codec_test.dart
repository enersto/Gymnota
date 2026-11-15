import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('all bundled animated WebP resources decode complete loops', () async {
    final failures = <String>[];
    final files = Directory('../app/src/main/assets/catalog/videos')
        .listSync()
        .whereType<File>()
        .where((f) => f.path.endsWith('.webp'))
        .toList();
    expect(files.length, 1324);
    for (final file in files) {
      ui.Codec? codec;
      try {
        codec = await ui.instantiateImageCodec(await file.readAsBytes());
        for (var i = 0; i < codec.frameCount * 2; i++) {
          final frame = await codec.getNextFrame();
          frame.image.dispose();
        }
      } catch (_) {
        failures.add(file.uri.pathSegments.last);
      } finally {
        codec?.dispose();
      }
    }
    final report = Platform.environment['CATALOG_CODEC_REPORT'];
    if (report != null) File(report).writeAsStringSync(jsonEncode(failures));
    expect(
      failures,
      isEmpty,
      reason: 'Bundled animations must remain playable in Flutter',
    );
  }, timeout: const Timeout(Duration(minutes: 10)));
}
