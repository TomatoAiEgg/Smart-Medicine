---
version: alpha
name: Smart Pharmacy SaaS Admin Design
source:
  reference: getdesign.md Linear design analysis
  adaptation: Medical SaaS operations console
stack:
  framework: Vue 3 + Vite
  componentLibrary: TDesign Vue Next
  language: TypeScript
colors:
  primary: "#0052D9"
  primary-hover: "#003CAB"
  workspace: "#F5F6F7"
  surface: "#FFFFFF"
  surface-subtle: "#F2F3F5"
  text: "#181818"
  text-secondary: "#5E5E5E"
  text-placeholder: "#8B8B8B"
  border: "#DCDCDC"
  header: "#1F2D3D"
  sidebar: "#1F2D3D"
  sidebar-hover: "#26384A"
  sidebar-active: "#0B5CAB"
  success: "#2BA471"
  warning: "#E37318"
  danger: "#D54941"
typography:
  fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei, Noto Sans CJK SC, Arial, sans-serif"
  page-title:
    fontSize: 20px
    fontWeight: 600
    lineHeight: 28px
    letterSpacing: 0
  section-title:
    fontSize: 15px
    fontWeight: 600
    lineHeight: 22px
    letterSpacing: 0
  body:
    fontSize: 14px
    fontWeight: 400
    lineHeight: 22px
    letterSpacing: 0
  compact:
    fontSize: 13px
    fontWeight: 400
    lineHeight: 20px
    letterSpacing: 0
  meta:
    fontSize: 12px
    fontWeight: 400
    lineHeight: 18px
    letterSpacing: 0
rounded:
  control: 3px
  small: 2px
  medium: 4px
  panel: 6px
spacing:
  base: 4px
  control-height: 32px
  header-height: 48px
  sidebar-width: 208px
  tab-height: 36px
  menu-parent-height: 40px
  menu-child-height: 36px
  table-row-height: 44px
  workspace-padding: 12px 16px 20px
---

# Smart Pharmacy SaaS Admin Design

This project is a medical pharmacy operations admin console, not a marketing site. UI work must prioritize scanability, dense operational workflows, stable navigation, privacy, and repeatable actions.

The visual reference is getdesign.md's Linear design analysis, adapted for this product rather than copied. Use Linear's useful traits: restrained product-tool chrome, scarce accent color, hairline borders, tight density, clear hierarchy, and minimal decoration. Do not copy Linear branding, proprietary text, dark marketing canvas, product screenshots, or brand-specific lavender palette.

## Product Principles

- The user is an operations, institution, pharmacy, logistics, or platform administrator.
- The page should help them find records, compare status, inspect detail, and perform constrained actions quickly.
- The app shell is persistent: dark header/sidebar, route tabs, and a light data workspace.
- The primary surface is tables and forms, not metric-card decoration.
- Every UI change must preserve the original workflow and permission behavior.
- Business labels are preferred over raw IDs. Technical IDs appear only where diagnostics require them.
- Sensitive patient, phone, address, credential, token, key, and identity fields must be masked unless an authorized workflow explicitly needs the raw value.

## Visual Direction

- Use a restrained enterprise medical style: precise, calm, and operational.
- Use one primary blue for active navigation, links, primary buttons, focus, and selected states.
- Use semantic colors only for status and risk: success, warning, danger.
- Avoid decorative gradients, bokeh, glassmorphism, oversized heroes, marketing copy, and card-heavy dashboards.
- Prefer flat surfaces with 1px borders. Shadows are only for drawers, dialogs, popovers, tooltips, and temporary overlays.
- Keep border radius tight. Standard controls use 3px; panels should stay at 6px or below unless TDesign requires otherwise.

## Layout System

- Header: 48px fixed height.
- Sidebar: 208px desktop width; dark background; one parent menu group open at a time.
- Route tabs: 36px height; horizontal scroll when needed.
- Workspace: light gray background with compact padding.
- Page content should use full-width operational regions, not floating section cards.
- Tables with many business fields must scroll inside their table shell instead of causing page-level horizontal overflow.
- Drawers are the default for create/edit/detail actions so users keep list context.
- Mobile navigation uses a drawer/backdrop pattern. Dense tables may use internal horizontal scrolling when comparison is essential.

## Typography

- Use the existing system font stack; do not introduce brand fonts for this admin app.
- Page titles: 20px, 600 weight.
- Section titles: 15px, 600 weight.
- Standard body and table text: 13px to 14px.
- Metadata and helper text: 12px.
- Letter spacing must stay at 0 for app UI.
- Use tabular numerals where numeric comparison matters.

## Components

### Buttons

- Primary actions use filled blue and are limited to the main action in a toolbar or drawer footer.
- Secondary actions use white/neutral outline.
- Destructive actions use danger color and require clear labels.
- Icon buttons should use TDesign icons and accessible names/tooltips.
- Keep button height aligned to 32px desktop controls.

### Filter Toolbars

- Toolbars should be compact and wrap predictably.
- Search and high-frequency filters are visible first.
- More than five filters should be grouped, folded, or moved into an advanced section when the page becomes cramped.
- Query, reset, and export actions must remain visually connected to the filters they operate on.

### Tables

- Tables are the primary data surface.
- Header height should be about 40px; row height about 44px.
- Use horizontal dividers rather than zebra striping.
- Keep primary business fields left aligned and numeric values right aligned where comparison matters.
- Long names, addresses, prescriptions, and remarks must truncate or wrap intentionally without breaking row height unpredictably.
- Row actions should be compact. Show at most two common actions inline; move lower-frequency actions into a more menu when the page becomes crowded.
- Empty, loading, error, and permission-limited states must be visible and stable.

### Status Tags

- Status tags use semantic color plus text. Color alone is not enough.
- Tags should be low-saturation and compact, with clear contrast.
- Do not invent extra status colors unless the domain state requires them.

### Forms And Drawers

- Labels are required. Placeholders do not replace labels.
- Drawer forms should have grouped sections, stable footer actions, disabled/submitting states, and clear validation messages.
- Close buttons, Escape handling, focus restore, and focus-visible states are required for overlays.
- Read-only views must not expose editable controls by appearance.

### Navigation And Tabs

- The sidebar should keep parent and child rows visually distinct.
- Active navigation uses a strong selected state and a 3px indicator.
- Route tabs should restore direct routes and allow close/navigation without layout shift.
- Text must truncate within navigation rows instead of overlapping icons or counters.

## Responsive Rules

- Desktop: preserve density and comparison; use table shells and drawers.
- Tablet: allow toolbars to wrap; keep side navigation usable.
- Mobile: header remains compact; navigation moves behind the menu trigger; table shells may scroll internally; action clusters stack without overflow.
- No page-level horizontal overflow is allowed unless the browser viewport itself is smaller than the minimum supported width.

## Accessibility And Interaction

- Every interactive control must have a visible focus state.
- Buttons are buttons; links are links.
- Drawer/dialog title and close control are required.
- Disabled actions must be both visually disabled and functionally guarded.
- Loading states must preserve layout shape.
- Error states must explain what failed and provide a recovery action when possible.
- Hover states should be subtle: neutral background, border, or text shift.

## Implementation Rules For Future UI Work

Before any UI change:

1. Read this `DESIGN.md`.
2. Identify the workflow being preserved.
3. State the page's product goal and information hierarchy.
4. Keep changes within existing Vue, TDesign, route, API, and component patterns.
5. Avoid broad visual rewrites unless the task explicitly asks for a phase-level redesign.
6. After implementation, run the project build and Impeccable audit/polish as appropriate.

## Impeccable Usage Policy

- Use Impeccable as a reviewer and polish pass, not as permission to rewrite business logic.
- For new or changed UI, run an audit on the touched page/component path first.
- Apply polish only to the scoped UI area being changed.
- Review generated suggestions manually against this document, existing permissions, privacy masking, and real workflows.
- Do not commit generated screenshots, temporary logs, browser profiles, or secret-bearing artifacts.

## Do

- Build dense, clear, quiet operations screens.
- Use existing admin tokens and TDesign components.
- Prefer 1px borders, stable spacing, and real business text.
- Preserve table comparison and drawer workflows.
- Mask sensitive fields and keep permission states explicit.
- Verify desktop and mobile layouts after UI edits.

## Don't

- Do not copy third-party brand assets, marketing language, proprietary fonts, or screenshots.
- Do not introduce gradient hero sections, decorative blobs, glass panels, or oversized empty cards.
- Do not use multiple bright accent colors for decoration.
- Do not hide important operational actions behind purely decorative UI.
- Do not create page-level overflow, overlapping text, or shifting table widths.
- Do not change API contracts, permissions, or data flow during UI polish.
