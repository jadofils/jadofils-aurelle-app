import 'package:aurelle/app/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final width in [320.0, 412.0, 800.0, 1440.0, 1920.0]) {
    testWidgets('renders without overflow at $width dp', (tester) async {
      tester.view.physicalSize = Size(width, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(const ProviderScope(child: AurelleApp()));

      expect(find.text('AURELLE'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('brand heading is exposed to screen readers as a header', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(const ProviderScope(child: AurelleApp()));

    expect(
      tester.getSemantics(find.text('AURELLE')),
      matchesSemantics(label: 'AURELLE', isHeader: true),
    );
    handle.dispose();
  });

  testWidgets('scales text to 2x without overflow on a narrow phone', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    await tester.pumpWidget(const ProviderScope(child: AurelleApp()));

    expect(find.text('AURELLE'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
