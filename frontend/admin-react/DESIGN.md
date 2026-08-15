# Smart Pharmacy SaaS React Admin Design

This React admin is a high-density medical operations console. Preserve the legacy system's information architecture and workflows, but do not copy JSP, iframe, jQuery, EasyUI, Bootstrap, fixed-pixel styling, or the old visual skin.

Use Ant Design and ProComponents as the implementation layer. Standard pages must prioritize page title, compact filters, data tables, pagination, drawers/dialogs, status tags, and stable route tabs.

## Product Principles

- Users are platform, institution, pharmacy, logistics, and operations administrators.
- Pages must help users find records, compare status, inspect detail, and perform permitted actions quickly.
- The React app must preserve legacy menu coverage, operation order, workflow meaning, and permission semantics.
- Business labels are preferred over raw IDs. Technical IDs appear only where diagnostics require them.
- Sensitive patient, phone, address, credential, token, key, and identity fields must be masked unless an authorized workflow explicitly needs raw values.

## Visual Direction

- Use a restrained enterprise medical style: dense, precise, calm, and operational.
- Use `#0052D9` as the primary blue for active navigation, primary actions, links, focus, and selected states.
- Use semantic colors only for status and risk: success, warning, and danger.
- Avoid decorative gradients, glass panels, marketing heroes, oversized cards, bokeh, ornamental icons, and low-density dashboards.
- Prefer flat surfaces with 1px borders. Shadows are reserved for drawers, dialogs, popovers, tooltips, and temporary overlays.
- Keep border radius tight. The default React theme radius is 6px and should not be inflated for decoration.

## Layout System

- Preserve the old admin work habit: dark header/sidebar, grouped left navigation, closable route tabs, and a light central workspace.
- React Router replaces iframe navigation. Route state, URL parameters, route tabs, and TanStack Query cache must carry page state.
- Page content should use full-width operational regions, not floating card stacks.
- Wide tables must scroll inside their table shell instead of causing page-level horizontal overflow.
- Drawers are the default for create, edit, and detail workflows so users keep list context.
- Mobile navigation uses a drawer/backdrop pattern. Dense tables may use internal horizontal scrolling when comparison is essential.

## Components

- Use Ant Design buttons, forms, tables, drawers, modals, tabs, tags, alerts, and feedback components before custom controls.
- Use ProComponents for query tables and form-heavy admin pages when they reduce repeated CRUD boilerplate.
- Primary actions use filled blue and are limited to the main action in a toolbar or drawer footer.
- Secondary actions use neutral styling. Destructive actions use danger color and require clear labels.
- Icon buttons must have accessible names or tooltips.
- Filter toolbars should be compact, wrap predictably, and keep query/reset/export actions visually connected to the filters they operate on.
- Status tags must use text plus color. Color alone is not enough.

## Accessibility And Interaction

- Every interactive control must have visible focus state.
- Buttons are buttons; links are links.
- Drawer and modal surfaces require title, close control, Escape handling where supported, and focus restore.
- Disabled actions must be both visually disabled and functionally guarded.
- Loading, empty, error, read-only, and permission-limited states must be visible and stable.
- Hover states should be subtle: neutral background, border, or text shift.

## Implementation Rules

- Keep changes within React, TypeScript, Vite, Ant Design, ProComponents, React Router, and TanStack Query conventions.
- Do not copy Vue components, Vue Router wiring, TDesign styles, JSP snippets, old inline styles, or `legacy-*` class patterns.
- Do not change backend API contracts, permissions, data flow, or business state transitions during UI implementation.
- Standard page verification must include production build and, for visual tasks, desktop/mobile overflow checks.

## Do

- Build dense, clear, quiet operations screens.
- Preserve table comparison, filter order, pagination, drawers, route tabs, and permission-aware actions.
- Use real business labels and status text.
- Mask sensitive fields and keep permission states explicit.

## Don't

- Do not copy JSP, iframe, jQuery, EasyUI, Bootstrap, fixed-pixel styling, or the old visual skin.
- Do not introduce marketing layouts, decorative illustrations, large gradients, or generic metric-card dashboards.
- Do not hide important operational actions behind purely decorative UI.
- Do not create page-level overflow, overlapping text, or shifting table widths.
