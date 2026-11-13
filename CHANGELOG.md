# Changelog

All notable changes to the Maintenance Per Site module are documented in this file.

## [Unreleased]

### Fixed
- The 503 maintenance response was sent with a cacheable header such as
  `Cache-Control: public, must-revalidate, max-age=1, s-maxage=3600, stale-while-revalidate=15`
  instead of `no-store`, so a CDN or shared proxy could keep serving the maintenance page after
  maintenance ended. Jahia's client-cache-control service rewrites `Cache-Control` from the render
  context policy and ignores the module's own header in strict mode. The filter now calls
  `renderContext.computeClientCachePolicy(ClientCachePolicy.PRIVATE)`, which yields Jahia's
  `private, no-cache, no-store, ...` template.
- The 503 maintenance response had an empty body, and requesting the maintenance page directly
  afterwards returned that empty body too. The filter rendered the maintenance page after the
  requested page, on the same request; Jahia's page render leaves request attributes behind
  (`templateSet`, `skipWrapper`) that make a second page render skip its page template, and the
  empty result was stored in the HTML cache under the maintenance page. The filter now renders the
  maintenance page before the requested page, as the main resource, and the requested page is not
  rendered at all.

### Changed
- **Requires Jahia 8.2.3.7 or later** (parent `jahia-modules` 8.2.3.7): the client cache policy
  API does not exist in Jahia 8.2.0.x.
- **Maintenance responses now use HTTP `503 Service Unavailable`** (with `Retry-After`
  and `Cache-Control: no-store` headers) and serve the maintenance page body in place,
  instead of issuing a `302` redirect. This stops search engines and uptime monitors
  from treating a site under maintenance as healthy, and prevents the maintenance state
  from being pinned by an HTML/CDN cache after the mixin is removed.
- `MaintenancePerSiteFilter` now **fails open** on misconfiguration: a missing or dangling
  `maintenancePage` reference is logged and normal output is returned, instead of risking a
  `PathNotFoundException`/`NullPointerException` 500. `RepositoryException`s are caught and
  logged with context.
- Fixed the i18n property key for the mixin: the resource bundles used the misspelled
  `jmix_maintenancePerSite.maintainancePage` (which never matched the CND property
  `maintenancePage`), so the edit-mode UI showed the raw property name. Bundles are now
  reduced to the mixin label + description, correctly spelled and localized in all
  shipped languages (en, fr, de, es, it, pt), with English fallbacks in the base bundle.
- `dependabot.yml` now declares real ecosystems (`maven` + `github-actions`); it previously
  had an empty `package-ecosystem`, so no dependency/CVE updates were ever produced.
- `pom.xml` `<scm>` now points at the real repository instead of the `scm:dummy:uri`
  placeholder.

### Added
- Expanded the unit-test suite to 8 tests (JUnit 4 + Mockito): the 503 maintenance path
  (status + headers + body), the maintenance-page self-exemption, no-mixin, non-live
  workspace, and three fail-open misconfiguration branches (property unset, reference
  unresolved, node null), plus `activate()` configuration. JaCoCo line coverage is now
  ~87% (branch 100%).

### Removed
- Deleted the leftover app-shell "declare new module" example scaffolding that shipped
  with the module and served no product purpose:
  - `src/main/resources/javascript/**` (the disabled `jahia.json` bundle key was
    `jahia*disabled*`, so the React UI extension never loaded) and `locales/en.json`;
  - `src/main/resources/jnt_virtualsite/html/virtualsite.maintenancepersite.jsp`
    (placeholder "Settings example" page linking to app-shell docs);
  - `src/main/import/repository.xml`, which mounted module sources into the JCR and
    embedded a **stale** copy of the filter (dead `org.kie.internal` import, mutable
    statics, stamped `1.0.0-SNAPSHOT`);
  - the empty `META-INF/rules.drl.disabled` and `META-INF/maintenancepersite.tld.disabled`
    template stubs.
  The module is now a single mixin definition + a single render filter, configured
  entirely through Jahia's stock node-type edit UI.

### Notes
- Scope is unchanged: the filter covers `html` page renders on the live workspace only.
  Non-page endpoints (file downloads, REST/GraphQL, AJAX fragments) are not locked down;
  this is documented in the README.

## Previous

### Changed
- Cleaned up `MaintenancePerSiteFilter`: made the `MIXIN_MAINTENANCE` constant `private static final`, made the logger `final`, removed an unused import and a redundant `prepare()` override, and switched to parameterized (SLF4J `{}`) logging. (Resolves 8 SonarQube issues.)

### Added
- First unit-test suite (JUnit 4 + Mockito, 4 tests) covering the live-workspace redirect, the maintenance-page-self exemption, the no-mixin case, and the non-live-workspace case. JaCoCo coverage wiring for SonarQube.
- Expanded README describing the `jmix:maintenancePerSite` mixin, the redirect behavior, and usage.
