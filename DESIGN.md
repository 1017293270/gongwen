# 公文助手 UI Design System

Source style: Anthropic-inspired, derived from public Anthropic and Claude web surfaces observed on 2026-05-25.

This file is the concrete UI contract for the project. Frontend code must use these tokens and rules instead of inventing ad hoc colors, spacing, or component styles.

## 1. Visual Theme & Atmosphere

The interface should feel like a quiet professional writing room: warm ivory surfaces, dark ink text, restrained borders, strong readability, and low visual noise. The product is for public-sector and enterprise document work, so the UI must feel trustworthy, calm, and precise.

Design keywords:

- Warm institutional
- Document-first
- Low noise
- High legibility
- Editorial hierarchy
- Calm AI assistance
- Dense but breathable

Avoid generic SaaS blue dashboards, heavy gradients, glassmorphism, decorative blobs, and futuristic AI visuals. The primary artifact is always the document.

## 2. Color Palette & Roles

Use CSS variables only. Do not hardcode colors in components.

```css
:root {
  /* Anthropic-derived neutrals */
  --color-ivory-light: #faf9f5;
  --color-ivory-medium: #f0eee6;
  --color-ivory-dark: #e8e6dc;
  --color-oat: #e3dacc;
  --color-manilla: #ebdbbc;

  --color-slate-dark: #141413;
  --color-slate-medium: #3d3d3a;
  --color-slate-light: #5e5d59;
  --color-cloud-dark: #87867f;
  --color-cloud-medium: #b0aea5;
  --color-cloud-light: #d1cfc5;

  /* Anthropic-derived accents */
  --color-accent: #c6613f;
  --color-clay: #d97757;
  --color-olive: #788c5d;
  --color-cactus: #bcd1ca;
  --color-sky: #6a9bcc;
  --color-heather: #cbcadb;
  --color-fig: #c46686;
  --color-coral: #ebcece;

  /* App semantic colors */
  --color-bg: var(--color-ivory-light);
  --color-bg-subtle: var(--color-ivory-medium);
  --color-bg-muted: var(--color-ivory-dark);
  --color-surface: #ffffff;
  --color-surface-warm: #fffdf8;

  --color-text: var(--color-slate-dark);
  --color-text-muted: var(--color-slate-light);
  --color-text-subtle: var(--color-cloud-dark);
  --color-text-inverse: var(--color-ivory-light);

  --color-border: rgba(20, 20, 19, 0.12);
  --color-border-strong: rgba(20, 20, 19, 0.2);
  --color-border-subtle: rgba(20, 20, 19, 0.07);

  --color-primary: var(--color-slate-dark);
  --color-primary-hover: var(--color-slate-medium);
  --color-accent-muted: #f3dbcf;

  --color-success: var(--color-olive);
  --color-success-bg: #eef3e8;
  --color-warning: var(--color-clay);
  --color-warning-bg: #fbefe9;
  --color-danger: #b53333;
  --color-danger-bg: #f7e4e1;
  --color-info: var(--color-sky);
  --color-info-bg: #eaf2fa;

  /* RGB helpers for rgba() */
  --rgb-slate-dark: 20 20 19;
  --rgb-ivory-light: 250 249 245;
  --rgb-accent: 198 97 63;
  --rgb-clay: 217 119 87;
  --rgb-sky: 106 155 204;
}
```

Role rules:

- Page background: `--color-bg`.
- Workbench side panels: `--color-bg-subtle` or `--color-surface-warm`.
- Document paper: `--color-surface`.
- Primary buttons: dark slate background, ivory text.
- Secondary buttons: transparent or warm surface, slate border.
- AI suggestions: use semantic left border, not full saturated panels.
- Warnings should be clay/orange, not bright yellow.
- Errors should be muted red, not neon red.

## 3. Typography Rules

Anthropic uses Anthropic Sans, Anthropic Serif, and Anthropic Mono on its public surfaces. Those are not assumed to be available in this project, so use compatible stacks and keep the family roles.

```css
:root {
  --font-sans: "Inter", "Noto Sans SC", "Microsoft YaHei", "PingFang SC", Arial, sans-serif;
  --font-serif: "Source Serif 4", "Noto Serif SC", "Songti SC", SimSun, Georgia, serif;
  --font-mono: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;

  --font-weight-regular: 400;
  --font-weight-medium: 500;
  --font-weight-semibold: 600;
  --font-weight-bold: 700;

  --leading-tight: 1.1;
  --leading-title: 1.25;
  --leading-body: 1.65;
  --leading-doc: 1.9;
}
```

Font usage:

- App shell, navigation, forms, buttons, metadata: `--font-sans`.
- Long document preview body: `--font-serif` is allowed when it improves official-document feeling; otherwise use `--font-sans`.
- Code, placeholders, trace ids, template variables: `--font-mono`.
- Chinese body text must use line-height at least `1.65`; document preview body should use `1.9`.
- Letter spacing should be `0`; do not use negative letter spacing.

Type scale:

```css
:root {
  --text-xs: 0.75rem;   /* 12px */
  --text-sm: 0.875rem;  /* 14px */
  --text-md: 1rem;      /* 16px */
  --text-lg: 1.125rem;  /* 18px */
  --text-xl: 1.25rem;   /* 20px */
  --text-2xl: 1.5rem;   /* 24px */
  --text-3xl: 2rem;     /* 32px */
}
```

Workbench type roles:

- Page title: 24px, sans, semibold.
- Panel heading: 14px, sans, semibold.
- Field label: 12px, sans, medium, muted.
- Body text: 15-16px.
- Document title: 20-24px, semibold/bold.
- Document body: 15-16px, line-height 1.9.

## 4. Component Stylings

### Buttons

```css
.btn {
  min-height: 36px;
  padding: 0 14px;
  border-radius: 6px;
  border: 1px solid transparent;
  font-family: var(--font-sans);
  font-size: var(--text-sm);
  font-weight: var(--font-weight-medium);
  transition: background-color 140ms ease, border-color 140ms ease, color 140ms ease, box-shadow 140ms ease;
}

.btn-primary {
  background: var(--color-primary);
  color: var(--color-text-inverse);
  border-color: var(--color-primary);
}

.btn-primary:hover {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.btn-secondary {
  background: transparent;
  color: var(--color-text);
  border-color: var(--color-border-strong);
}

.btn-secondary:hover {
  background: var(--color-slate-dark);
  color: var(--color-text-inverse);
  border-color: var(--color-slate-dark);
}

.btn:focus-visible {
  outline: 2px solid rgba(var(--rgb-accent), 0.45);
  outline-offset: 2px;
}

.btn:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}
```

Use icon-only buttons for common tools when available: save, upload, export, retry, cancel, settings, delete, search, preview. Provide tooltips for unfamiliar icons.

### Inputs

```css
.field {
  min-height: 38px;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 8px 10px;
  font-family: var(--font-sans);
  font-size: var(--text-sm);
}

.field:hover {
  border-color: var(--color-border-strong);
}

.field:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgba(var(--rgb-accent), 0.12);
  outline: none;
}

.field[aria-invalid="true"] {
  border-color: var(--color-danger);
  box-shadow: 0 0 0 3px rgba(181, 51, 51, 0.1);
}
```

### Panels

```css
.panel {
  background: var(--color-surface-warm);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.panel-header {
  min-height: 48px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.panel-body {
  padding: 14px;
}
```

Do not nest cards inside cards. Panels can hold grouped controls; repeated objects such as materials or checks can be cards.

### Cards

```css
.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
}

.card:hover {
  border-color: var(--color-border-strong);
}
```

### AI Check Items

```css
.check-item {
  border: 1px solid var(--color-border-subtle);
  border-left-width: 4px;
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--color-surface);
}

.check-item[data-tone="success"] {
  border-left-color: var(--color-success);
  background: var(--color-success-bg);
}

.check-item[data-tone="warning"] {
  border-left-color: var(--color-warning);
  background: var(--color-warning-bg);
}

.check-item[data-tone="danger"] {
  border-left-color: var(--color-danger);
  background: var(--color-danger-bg);
}

.check-item[data-tone="info"] {
  border-left-color: var(--color-info);
  background: var(--color-info-bg);
}
```

### Document Preview

```css
.document-stage {
  background: var(--color-bg-muted);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 24px;
  overflow: auto;
}

.document-paper {
  width: min(100%, 760px);
  min-height: 960px;
  margin: 0 auto;
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: 0 12px 28px rgba(var(--rgb-slate-dark), 0.14);
  padding: 56px 64px;
}

.document-paper [contenteditable="true"]:focus {
  outline: 2px solid rgba(var(--rgb-accent), 0.32);
  outline-offset: 4px;
  border-radius: 4px;
}
```

## 5. Layout Principles

Spacing scale:

```css
:root {
  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-3: 0.75rem;
  --space-4: 1rem;
  --space-5: 1.5rem;
  --space-6: 2rem;
  --space-7: 2.5rem;
  --space-8: 3rem;
  --space-9: 4rem;
  --space-10: 5rem;
}
```

Workbench layout:

- Desktop: three columns.
- Left panel: 300-340px.
- Middle document canvas: fluid, minimum 520px.
- Right panel: 300-340px.
- Gap: 16px.
- App topbar height: 56-64px.

```css
.workbench {
  display: grid;
  grid-template-columns: minmax(280px, 320px) minmax(520px, 1fr) minmax(300px, 340px);
  gap: var(--space-4);
  min-height: calc(100vh - 64px);
}
```

Responsive:

- Below 1180px: collapse to single column with tabs or stacked panels.
- Below 768px: document preview becomes full width, side panels become accordions.
- Touch targets at least 44x44px.

## 6. Depth & Elevation

Use subtle shadows only for document paper, menus, popovers, and modals.

```css
:root {
  --shadow-xs: 0 1px 2px rgba(var(--rgb-slate-dark), 0.06);
  --shadow-sm: 0 4px 10px rgba(var(--rgb-slate-dark), 0.08);
  --shadow-md: 0 12px 28px rgba(var(--rgb-slate-dark), 0.12);
  --shadow-focus: 0 0 0 3px rgba(var(--rgb-accent), 0.14);
}
```

Rules:

- Panels generally use borders, not shadows.
- Document paper may use `--shadow-md`.
- Menus/popovers use `--shadow-sm`.
- Avoid floating section cards.

## 7. Animation & Interaction

Motion level: L1 refined static UI with selective L2 interaction for AI generation and document transitions.

Timing:

```css
:root {
  --ease-standard: cubic-bezier(0.2, 0, 0, 1);
  --duration-fast: 120ms;
  --duration-base: 180ms;
  --duration-slow: 260ms;
}
```

Allowed motion:

- Button/input hover transitions.
- Panel reveal by opacity + translateY 4px.
- AI streaming cursor or progressive paragraph reveal.
- Skeleton shimmer with very low contrast.
- Toast enter/exit.
- Document block focus outline transition.

Avoid:

- Bouncy motion.
- Flashing loading indicators.
- Decorative parallax.
- Large layout-shifting animations.

```css
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
```

AI states:

- Generating: calm inline spinner or progress text near action.
- Streaming: paragraph appears progressively without reflowing surrounding layout.
- Cancelled: neutral status, preserve partial draft.
- Failed: muted red status with retry action.
- Completed: small success confirmation, not a celebratory animation.

## 8. Do's and Don'ts

Do:

- Use warm ivory backgrounds and dark slate text.
- Make the document preview the primary visual focus.
- Keep controls dense but readable.
- Use semantic left borders for quality checks.
- Use structure and spacing rather than decoration.
- Keep AI actions close to the selected document block.
- Preserve layout during loading and streaming.
- Provide keyboard focus for every interactive control.
- Use real empty/error/loading states.
- Keep Chinese text line-height generous.

Don't:

- Do not use generic blue SaaS gradients.
- Do not use purple AI glow, blobs, or bokeh backgrounds.
- Do not place cards inside cards.
- Do not make the page look like a marketing landing page.
- Do not hide document structure inside chat-only UI.
- Do not use bright red/yellow warning slabs.
- Do not use negative letter spacing.
- Do not scale fonts with viewport width.
- Do not use full-page custom cursor or scroll-jacking.
- Do not hardcode colors outside CSS variables.

## 9. Responsive Behavior

Breakpoints:

```css
:root {
  --bp-mobile: 640px;
  --bp-tablet: 768px;
  --bp-workbench-collapse: 1180px;
  --bp-desktop: 1280px;
}
```

Desktop:

- Three-column workbench.
- Sticky side panel headers allowed.
- Document preview centered.

Tablet:

- Left and right panels collapse into top tabs: Fields, Materials, AI Checks.
- Document stays visible below current tab.

Mobile:

- Single-column flow.
- Primary actions become bottom action bar.
- Document preview switches to compact reading mode.
- Editing can open a focused block editor sheet.

Minimum accessibility:

- Contrast target: WCAG AA for body text and controls.
- Keyboard focus visible.
- Form labels programmatically associated.
- Error messages connected via `aria-describedby`.
- Loading states announced where appropriate.

## 10. Source Notes

Observed public source references:

- Anthropic public website uses warm ivory, slate, clay/accent, Anthropic Sans/Serif/Mono roles, and a restrained editorial layout.
- Claude product pages use the same restrained surface language with stronger product accent colors.

This design system is inspired by those public surfaces and adapted for a Chinese enterprise/government document workbench. It must not claim official Anthropic branding or use proprietary brand assets unless the project has explicit rights.

