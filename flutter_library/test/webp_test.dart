import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'bundled WebP plays multiple frames in Flutter at original resolution',
    () async {
      final files =
          Directory('../app/src/main/assets/catalog/videos')
              .listSync()
              .whereType<File>()
              .where((f) => f.path.endsWith('.webp'))
              .toList()
            ..sort((a, b) => a.path.compareTo(b.path));
      expect(files.length, 1324);
      final codec = await ui.instantiateImageCodec(
        await files.first.readAsBytes(),
      );
      try {
        expect(codec.frameCount, greaterThan(1));
        for (var i = 0; i < codec.frameCount; i++) {
          final frame = await codec.getNextFrame();
          expect(frame.image.width, 180);
          expect(frame.image.height, 180);
          expect(frame.duration.inMilliseconds, greaterThan(0));
          frame.image.dispose();
        }
      } finally {
        codec.dispose();
      }
    },
  );
}
