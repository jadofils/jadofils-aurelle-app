import 'package:flutter/painting.dart';

/// Design tokens taken from the prototype's `css/style.css` (`:root` and
/// `:root[data-theme="light"]`). Widgets use these through the [ThemeData]
/// built in `theme.dart`, never as hard-coded literals.
abstract final class AurelleColors {
  // Brand accent (shared by both themes).
  static const accent = Color(0xFFFF7200); // --gold
  static const accentHigh = Color(0xFFF6AA22); // --gold-hi
  static const accentLow = Color(0xFFC24F00); // --gold-lo
}

/// One colour set per theme.
final class AurellePalette {
  const AurellePalette({
    required this.background,
    required this.surface1,
    required this.surface2,
    required this.surface3,
    required this.text,
    required this.textSecondary,
    required this.muted,
    required this.ink,
    required this.onAccent,
    required this.ok,
    required this.bad,
    required this.warn,
    required this.info,
    required this.neutral,
  });

  final Color background, surface1, surface2, surface3;
  final Color text, textSecondary, muted, ink, onAccent;
  final Color ok, bad, warn, info, neutral;

  static const dark = AurellePalette(
    background: Color(0xFF000000),
    surface1: Color(0xFF0F0F0F),
    surface2: Color(0xFF181818),
    surface3: Color(0xFF232323),
    text: Color(0xFFFFFFFF),
    textSecondary: Color(0xFFD8D8D8),
    muted: Color(0xFF9A9A9A),
    ink: Color(0xFFFF8A1F),
    onAccent: Color(0xFF000000),
    ok: Color(0xFF7BD88F),
    bad: Color(0xFFFF7A7A),
    warn: Color(0xFFFFC35C),
    info: Color(0xFF8AB4FF),
    neutral: Color(0xFFA8A8A8),
  );

  static const light = AurellePalette(
    background: Color(0xFFFBF7F1),
    surface1: Color(0xFFFFFFFF),
    surface2: Color(0xFFF2ECE3),
    surface3: Color(0xFFE5DCCF),
    text: Color(0xFF1A130B),
    textSecondary: Color(0xFF3B3228),
    muted: Color(0xFF6B6156),
    ink: Color(0xFFC24F00),
    onAccent: Color(0xFF000000),
    ok: Color(0xFF146C2E),
    bad: Color(0xFFA51E1E),
    warn: Color(0xFF774600),
    info: Color(0xFF1D55B0),
    neutral: Color(0xFF5E554B),
  );
}

/// 4-px spacing scale (`--sp-1` … `--sp-7`).
abstract final class AurelleSpacing {
  static const s1 = 4.0, s2 = 8.0, s3 = 12.0, s4 = 16.0, s5 = 24.0, s6 = 32.0, s7 = 48.0;
}

/// Type sizes (`--fs-*`) and families (`--disp`, `--ui`).
abstract final class AurelleType {
  static const xs = 12.0, sm = 13.0, md = 15.0, lg = 17.0, xl = 20.0, xxl = 26.0;
  static const displayFamily = 'Fraunces';
  static const uiFamily = 'Manrope';
}

/// Square corners everywhere; circles only for swatches, avatars and round icon buttons.
abstract final class AurelleShape {
  static const radius = BorderRadius.zero;
}

/// Minimum touch target (`--tap`).
abstract final class AurelleTouch {
  static const minTarget = 44.0;
}
