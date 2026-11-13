import 'package:flutter/material.dart';

import '../core/design/theme.dart';
import '../core/layout/window_size.dart';

/// Root widget. Feature screens and routing are added with each vertical slice
/// (see docs/architecture.md). The prototype's default is dark, so dark is used
/// unless the platform asks for light.
class AurelleApp extends StatelessWidget {
  const AurelleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Aurelle',
      debugShowCheckedModeBanner: false,
      theme: AurelleTheme.light(),
      darkTheme: AurelleTheme.dark(),
      themeMode: ThemeMode.system,
      home: const _StartupScreen(),
    );
  }
}

/// Temporary start screen until the catalog slice provides the storefront.
class _StartupScreen extends StatelessWidget {
  const _StartupScreen();

  @override
  Widget build(BuildContext context) {
    final size = WindowSize.of(context);
    final text = Theme.of(context).textTheme;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: EdgeInsets.all(size.gutter),
            child: Semantics(
              header: true,
              child: Text('AURELLE', style: text.displayMedium),
            ),
          ),
        ),
      ),
    );
  }
}
