import 'package:flutter/material.dart';

import 'tokens.dart';

/// Builds Material [ThemeData] from Aurelle tokens so every widget inherits
/// the brand instead of re-declaring colours, radii or sizes.
abstract final class AurelleTheme {
  static ThemeData dark() => _build(AurellePalette.dark, Brightness.dark);
  static ThemeData light() => _build(AurellePalette.light, Brightness.light);

  static ThemeData _build(AurellePalette p, Brightness brightness) {
    final scheme = ColorScheme(
      brightness: brightness,
      primary: AurelleColors.accent,
      onPrimary: p.onAccent,
      secondary: AurelleColors.accentHigh,
      onSecondary: p.onAccent,
      error: p.bad,
      onError: p.background,
      surface: p.surface1,
      onSurface: p.text,
      surfaceContainerHighest: p.surface3,
      onSurfaceVariant: p.muted,
    );

    const square = RoundedRectangleBorder(borderRadius: AurelleShape.radius);
    const minSize = Size(AurelleTouch.minTarget, AurelleTouch.minTarget);

    final base = ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: p.background,
      fontFamily: AurelleType.uiFamily,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      visualDensity: VisualDensity.standard,
    );

    return base.copyWith(
      textTheme: base.textTheme.copyWith(
        displayMedium: base.textTheme.displayMedium?.copyWith(
          fontFamily: AurelleType.displayFamily,
          fontFamilyFallback: const ['Georgia', 'serif'],
          color: p.text,
        ),
        bodyMedium: base.textTheme.bodyMedium?.copyWith(fontSize: AurelleType.md, color: p.text),
        bodySmall: base.textTheme.bodySmall?.copyWith(fontSize: AurelleType.sm, color: p.muted),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(shape: square, minimumSize: minSize),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(shape: square, minimumSize: minSize),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(shape: square, minimumSize: minSize),
      ),
      cardTheme: CardThemeData(shape: square, color: p.surface1, margin: EdgeInsets.zero),
      dialogTheme: DialogThemeData(shape: square, backgroundColor: p.surface1),
      inputDecorationTheme: InputDecorationTheme(
        border: const OutlineInputBorder(borderRadius: AurelleShape.radius),
        filled: true,
        fillColor: p.surface2,
      ),
      focusColor: AurelleColors.accentHigh.withValues(alpha: 0.35),
    );
  }
}
