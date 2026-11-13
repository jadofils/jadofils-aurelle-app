# Aurelle frontend (Flutter)

Flutter/Dart client for Android and Web, with Riverpod for state and dependency injection.

## First-time setup

Platform folders (`android/`, `web/`) are not committed yet. Generate them once Flutter (Dart ≥ 3.12) is installed. `flutter create` only adds missing files, so it keeps `lib/` and `test/` as they are:

```sh
cd frontend
flutter create --platforms=android,web --org com.aurelle --project-name aurelle .
flutter pub get
flutter analyze
flutter test
flutter run -d chrome
```

## Layout

```
lib/
  main.dart                ProviderScope + app root
  app/app.dart             MaterialApp, theme wiring (routing arrives with slice 2)
  core/design/tokens.dart  colours/spacing/type/shape from the prototype's style.css
  core/design/theme.dart   ThemeData built from tokens (square corners, 44 dp targets)
  core/layout/window_size.dart  compact/medium/expanded/large/extraLarge width classes
  features/<feature>/      added per vertical slice: data (GraphQL repository), application (providers), presentation (widgets)
```

Rules: `../.claude/rules/flutter-frontend.md`.
