# Smart Pharmacy SaaS React Admin Product Notes

The React admin is the parallel management console for the Smart Pharmacy SaaS platform. It supports prescription fulfillment, order operations, workflow handling, decoction, logistics, institution access, reporting, and platform configuration from one authenticated backend.

The legacy screenshot baseline in `docs/00_项目总览/老项目UI截图基线/` is the source for page structure and menu coverage. The React implementation must keep business workflows and permission semantics unchanged.

## Users

- Platform administrators who manage users, roles, menus, permissions, and sessions.
- Institution operators who manage institution records, applications, API permissions, IP allowlists, and logistics rules.
- Pharmacy operations users who inspect prescriptions, order status, workflow tasks, decoction work, label printing, SMS, logistics, reports, and exception handling.
- Logistics and operations staff who need dense record comparison, status triage, export, and follow-up actions.

## Product Purpose

Success means an authorized operator can find records, understand status, inspect details, and perform permitted actions without losing context or exposing sensitive data unnecessarily.

## Operating Context

- The app runs as `frontend/admin-react` and is built with React, Vite, TypeScript, Ant Design, ProComponents, React Router, and TanStack Query.
- Users authenticate through the admin auth flow; permissions and data scope come from backend sessions.
- The interface is used for high-frequency operations with dense tables, filters, route tabs, drawers, exports, and status-driven actions.
- The current Vue project remains a rollback/reference path until the React entry is verified and explicitly switched.

## Capabilities And Constraints

- Preserve existing backend API contracts, permission checks, route keys, and data masking rules.
- UI changes must not change business logic, API behavior, or security semantics.
- Sensitive fields such as patient identity, phone, address, credentials, tokens, keys, and raw integration details must be masked unless an authorized workflow explicitly requires raw access.
- Table-heavy pages must preserve scanability and internal scrolling for wide business datasets.
- Future UI work must read `DESIGN.md` before editing.

## Evidence On Hand

- `docs/00_项目总览/老项目UI截图基线/` is the page-structure and menu-coverage baseline.
- `docs/00_项目总览/老项目审查报告.md` records the legacy business map, menu structure, and workflow facts.
- `docs/superpowers/specs/2026-08-15-admin-ui-baseline-redesign.md` records the React rebuild direction.
- `frontend/admin-web/src/api/` records the current backend API contract behavior to preserve.
- `frontend/admin-web/vite.config.ts` records the gateway proxy prefixes mirrored by this React scaffold.

## Product Principles

- Preserve operational continuity: list, filter, inspect, act, and return without losing context.
- Prefer business-readable labels and statuses over raw technical identifiers.
- Keep privacy and permission boundaries visible in the UI and enforced in handlers.
- Optimize for dense, repeatable admin work over expressive marketing presentation.
- Treat design polish as scoped refinement, not a reason to change data flow.
