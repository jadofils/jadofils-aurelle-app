import 'package:aurelle/core/layout/window_size.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('WindowSize.forWidth', () {
    test('narrow phones are compact', () {
      expect(WindowSize.forWidth(320), WindowSize.compact);
      expect(WindowSize.forWidth(412), WindowSize.compact);
      expect(WindowSize.forWidth(599.9), WindowSize.compact);
    });

    test('boundaries map to the next class', () {
      expect(WindowSize.forWidth(600), WindowSize.medium);
      expect(WindowSize.forWidth(840), WindowSize.expanded);
      expect(WindowSize.forWidth(1200), WindowSize.large);
      expect(WindowSize.forWidth(1600), WindowSize.extraLarge);
      expect(WindowSize.forWidth(3840), WindowSize.extraLarge);
    });
  });
}
