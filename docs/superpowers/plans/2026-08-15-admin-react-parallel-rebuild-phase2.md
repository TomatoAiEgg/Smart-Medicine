# Admin React Full Content Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace every React migration notice with a real, API-connected management page while preserving the legacy menu structure and workflows.

**Architecture:** Keep the React shell and 68 existing child routes. Port stable TypeScript API contracts from `frontend/admin-web` into domain-focused React API modules, then implement dense Ant Design list, filter, drawer, detail, export, and permission states. The Vue application remains the production rollback until all release gates pass.

**Tech Stack:** React 19, TypeScript, Vite, Ant Design, ProComponents, TanStack Query, React Router.

---

## Current State

- The React shell exposes 11 parent menus and 68 child routes.
- Five routes are mapped to React components, but the user page still has an empty stub data source.
- Sixty-three routes render `MigrationNoticePage`.
- Production was rolled back to the complete Vue image after this gap was confirmed.

## Scope And Order

### Batch 1: System, Parameters, Institutions

- System: users, roles, menu registry.
- Parameters: dictionaries, system configuration, decoction centers, operators.
- Institutions: institution list, IP allowlist, API list, API permissions.
- Preserve list filters, pagination, create/edit drawers, enabled states, permission states, and business labels.

### Batch 2: Logistics, Orders, Maintenance

- Logistics: special rules, address costs, delivery, tracking, printing, merges, unreceived follow-up.
- Orders: prescriptions, audit, dispense, recheck, multi-recheck, recheck records, address and prescription changes, actions, reprint, warehouse, intercept rules, manual process, receipts.
- Maintenance: order process, exception logs, MQ messages, problem registrations.
- Preserve workflow transitions and disable any mutation whose backend contract cannot be verified.

### Batch 3: Labels, SMS, Drugs, Reports, Decoction

- Labels: templates and printing.
- SMS: templates, single send, records.
- Drugs: herbs, index logs, indexes, imports, areas.
- Reports: all 16 report routes and exports.
- Decoction: equipment, prescription bindings, printer relations, water pails, cloud print records.

## Out Of Scope

- Backend API, database schema, gateway routes, authentication semantics, and production data.
- Copying Vue, TDesign, JSP, iframe, jQuery, EasyUI, Bootstrap, or the old visual skin into React.
- Inventing write behavior when no stable backend contract exists.

## Release Gates

- Every menu item has `implemented: true`.
- `MigrationNoticePage` is not reachable from any menu route.
- No page uses a fabricated empty request as its production data source.
- TypeScript no-emit check passes.
- Production build passes without chunk-size or circular dependency warnings.
- Impeccable detector reports no static findings.
- Desktop and mobile screenshots show no overlap or page-level overflow.
- Authenticated smoke checks cover one read path and one permitted action path per parent menu.
- Production remains on Vue until every gate passes and the user explicitly approves the React cutover.

## Rollback

- Keep the current Vue production image and container definition intact.
- Deploy React as a separately tagged image.
- On cutover failure, replace the React container with the known-good Vue image without touching backend services or data.
