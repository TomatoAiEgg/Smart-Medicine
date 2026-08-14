# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary users are inferred from the repository and current product scope:

- Platform administrators who manage users, roles, menus, permissions, and sessions.
- Institution operators who manage institution records, applications, API permissions, IP allowlists, and logistics rules.
- Pharmacy operations users who inspect prescriptions, order status, workflow tasks, decoction work, label printing, SMS, logistics, reports, and exception handling.
- Logistics and operations staff who need dense record comparison, status triage, export, and follow-up actions.

## Product Purpose

The admin web app is the management console for the Smart Pharmacy SaaS platform. It supports prescription fulfillment, order operations, workflow handling, decoction, logistics, institution access, reporting, and platform configuration from one authenticated backend.

Success means an authorized operator can find records, understand status, inspect details, and perform permitted actions without losing context or exposing sensitive data unnecessarily.

## Positioning

The product is a medical/pharmacy operations backend, not a generic dashboard. Its differentiator is an end-to-end workflow across institution integration, prescription/order processing, pharmacy work, decoction, logistics, callbacks, reports, and operational audit.

## Operating Context

- The app is a Vue 3 + Vite + TypeScript frontend using TDesign Vue Next.
- It runs as `frontend/admin-web` and is deployed behind Nginx/gateway.
- Users authenticate through the admin auth flow; permissions and data scope come from backend sessions.
- The interface is used for high-frequency operations with dense tables, filters, route tabs, drawers, exports, and status-driven actions.
- The current production/dev entry is a web admin console, not a native mobile app.

## Capabilities and Constraints

- Preserve existing backend API contracts, permission checks, route keys, and data masking rules.
- UI changes must not change business logic, API behavior, or security semantics.
- Sensitive fields such as patient identity, phone, address, credentials, tokens, keys, and raw integration details must be masked unless an authorized workflow explicitly requires raw access.
- Table-heavy pages must preserve scanability and internal scrolling for wide business datasets.
- Future UI work must read `DESIGN.md` before editing and should use Impeccable audit/polish as a scoped review pass.
- Open decision: no public brand assets, customer evidence, testimonials, or final commercial positioning are confirmed in the repository; future work must not fabricate them.

## Brand Commitments

- Product name in the current UI: Smart Pharmacy SaaS / 智能药房 SaaS.
- Brand tone: clinical restraint, operational clarity, trustworthy enterprise tooling.
- Existing visual commitments: dark admin shell, light workspace, primary blue, tight border radius, dense tables, drawers, TDesign controls.
- Visual reference: getdesign.md Linear design analysis, adapted only as a product-tool quality reference. Third-party brand assets, proprietary copy, and brand-specific imagery are not part of this product.

## Evidence on Hand

- `frontend/admin-web/package.json` confirms the frontend stack and scripts.
- `frontend/admin-web/src/styles/admin-tokens.css` records the current admin design tokens.
- `frontend/admin-web/src/styles/admin-shell.css` records the current app shell layout.
- `frontend/admin-web/DESIGN.md` records the project UI design policy for future work.
- `docs/00_项目总览` and `docs/06_部署运维` describe the broader Smart Pharmacy SaaS platform context.
- No confirmed customer logos, marketing claims, production SLA, or real patient data should be used as UI evidence.

## Product Principles

- Preserve operational continuity: list, filter, inspect, act, and return without losing context.
- Prefer business-readable labels and statuses over raw technical identifiers.
- Keep privacy and permission boundaries visible in the UI and enforced in handlers.
- Optimize for dense, repeatable admin work over expressive marketing presentation.
- Treat design polish as scoped refinement, not a reason to change data flow.

## Accessibility & Inclusion

- The web admin must remain keyboard accessible for core navigation, filters, drawers, and actions.
- Focus-visible states are required for interactive controls.
- Status must use text plus color, not color alone.
- Mobile and narrow viewport behavior must avoid incoherent overlap and page-level overflow.
