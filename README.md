# Aurelle App

Retail and stock-management platform: Java 21 + Spring Boot backend, PostgreSQL, GraphQL (HTTP + WebSocket subscriptions), and a Flutter client for Android and Web.

The prototype lives in a separate repository: [jadofils/aurelle-mobile-web-prototype](https://github.com/jadofils/aurelle-mobile-web-prototype). Use it as a visual and workflow reference only. It is a browser-only demo and its localStorage data is not production data. (Locally it sits alongside this directory at `../aurelle`. Claude Code guidance files are kept locally and are not committed.)

| Folder | Contents |
| --- | --- |
| [backend/](backend/README.md) | Java 21 + Spring Boot modular monolith (GraphQL, PostgreSQL, Flyway) |
| [frontend/](frontend/README.md) | Flutter/Dart client for Android and Web (Riverpod) |
| [docs/architecture.md](docs/architecture.md) | Prototype discovery and phased delivery plan |
| [docs/decisions.md](docs/decisions.md) | Decision log (decided / proposed / open) |
