# Maintenance Per Site

A Jahia module that lets you put an individual site (web project) into
maintenance by redirecting its live pages to a dedicated maintenance page.

## How it works

The module ships the `jmix:maintenancePerSite` mixin (extending
`jnt:virtualsite`) with a mandatory `maintenancePage` reference (a page picker).
A render filter (`MaintenancePerSiteFilter`) runs on the **live** workspace
only: when the resolved site carries the mixin, every page request is redirected
to the configured maintenance page — except the maintenance page itself, so it
stays reachable.

## Usage

1. Install and enable the module on the target site.
2. Edit the site node and enable the `jmix:maintenancePerSite` mixin.
3. Select the page to use as the maintenance page (`maintenancePage`).
4. Publish. Live visitors of that site are now redirected to the maintenance
   page until the mixin is removed.

## Notes

- Edit mode is not affected (the filter sets `applyOnEditMode(false)`), so
  editors can keep working on the site while it is in maintenance.
- The redirect applies to `html` template types on page configurations.
