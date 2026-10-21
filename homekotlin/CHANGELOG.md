# Changelog

## [1.0.2] - 2026-10-21
### Removed
- Removed deprecated `build.yaml` in favor of native multi-stage `Dockerfile`.

## [1.0.1] - 2026-10-21
### Fixed
- Fixed self-contained multi-stage Docker build context and Gradle wrapper packaging for isolated Home Assistant supervisor builds.

## [1.0.0] - 2026-10-21
### Added
- Initial release of HomeKotlin Add-on.
- Reactive Kotlin DSL with state, numeric, interval, and clock triggers.
- Dynamic script compiler supporting `.kts` files with hot reloading.
- Home Assistant WebSocket client with state store and typed service calls.
- Ingress web dashboard with live SSE streaming, manual trigger test, and entity inspector.
- Type-safe entity code generator (`Entities.kt`).
- Built-in simulation / demo mode for local development.
