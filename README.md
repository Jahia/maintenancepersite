# Maintenance Per Site

A Jahia module that lets you put an individual site (web project) into
maintenance: while it is enabled, the site's live pages serve a dedicated
maintenance page with an HTTP `503 Service Unavailable` status.

## How it works

The module ships the `jmix:maintenancePerSite` mixin (extending
`jnt:virtualsite`) with a mandatory `maintenancePage` reference (a page picker).
A render filter (`MaintenancePerSiteFilter`) runs on the **live** workspace
only: when the resolved site carries the mixin, every HTML page request is
served the configured maintenance page's content — except the maintenance page
itself, so it stays reachable.

The maintenance response uses **HTTP 503** (not a 302 redirect) with
`Retry-After` and `Cache-Control: no-store` headers, so:

- search engines and uptime monitors treat the site as temporarily down rather
  than indexing/caching the maintenance page as healthy content;
- the maintenance state is not pinned by an HTML or CDN cache, so removing the
  mixin restores the site immediately.

The filter **fails open**: if the mixin is set but the `maintenancePage`
reference is missing or dangling, it logs a warning and serves normal output —
a misconfiguration never takes the whole site down with a 500.

## Usage

1. Install and enable the module on the target site.
2. Edit the site node and enable the `jmix:maintenancePerSite` mixin.
3. Select the page to use as the maintenance page (`maintenancePage`).
4. Publish. Live visitors of that site now receive the maintenance page
   (HTTP 503) until the mixin is removed.

## Scope and notes

- Edit mode is not affected (the filter sets `applyOnEditMode(false)`), so
  editors can keep working on the site while it is in maintenance.
- The filter applies to `html` template types on **page** configurations only.
  Non-page endpoints (static file downloads, REST/GraphQL APIs, AJAX fragments)
  are **not** covered — the site is in maintenance for full HTML page loads, not
  fully locked down. If true lockdown is required, complement this module with a
  servlet/security-level filter.
- The maintenance page itself is exempted by node path to avoid a redirect loop.
