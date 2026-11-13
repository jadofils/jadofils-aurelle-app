import 'package:flutter/widgets.dart';

import '../design/tokens.dart';

/// Width classes used to reflow layouts (not to scale a phone screen).
///
/// Mobile-first: [compact] covers the 320–412 dp phones the brief targets.
/// Boundaries follow Material 3 window size classes.
enum WindowSize {
  /// Phones (< 600 dp): bottom tab bar, single column, tables as cards.
  compact(gutter: AurelleSpacing.s4),

  /// Small tablets / foldables (600–839 dp): navigation rail, two columns.
  medium(gutter: AurelleSpacing.s5),

  /// Tablets landscape / small laptops (840–1199 dp): rail or side panel.
  expanded(gutter: AurelleSpacing.s5),

  /// Desktops (1200–1599 dp): persistent side navigation, data tables.
  large(gutter: AurelleSpacing.s6),

  /// Large screens and TV (≥ 1600 dp): constrained content width, focus navigation.
  extraLarge(gutter: AurelleSpacing.s7);

  const WindowSize({required this.gutter});

  /// Horizontal page padding for this class.
  final double gutter;

  static WindowSize forWidth(double width) {
    if (width < 600) return compact;
    if (width < 840) return medium;
    if (width < 1200) return expanded;
    if (width < 1600) return large;
    return extraLarge;
  }

  static WindowSize of(BuildContext context) => forWidth(MediaQuery.sizeOf(context).width);
}
