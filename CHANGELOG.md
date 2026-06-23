# Changelog

All notable changes to the Maintenance Per Site module are documented in this file.

## [Unreleased]

### Changed
- Cleaned up `MaintenancePerSiteFilter`: made the `MIXIN_MAINTENANCE` constant `private static final`, made the logger `final`, removed an unused import and a redundant `prepare()` override, and switched to parameterized (SLF4J `{}`) logging. (Resolves 8 SonarQube issues.)

### Added
- First unit-test suite (JUnit 4 + Mockito, 4 tests) covering the live-workspace redirect, the maintenance-page-self exemption, the no-mixin case, and the non-live-workspace case. JaCoCo coverage wiring for SonarQube.
- Expanded README describing the `jmix:maintenancePerSite` mixin, the redirect behavior, and usage.

### Notes
- `src/main/resources/javascript/**` and the `virtualsite.maintenancepersite.jsp` view still contain the original app-shell "declare new module" example scaffolding (a documentation-link action and a placeholder settings page). These are left untouched pending product decisions on the intended settings UI.
