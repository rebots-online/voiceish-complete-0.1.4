# Design System: Voiceish SIM

## 1. Visual Theme & Atmosphere
A quiet personal communications desk. Daily-app density 5, asymmetric variance 6, fluid motion 4. A narrow charcoal utility rail anchors an airy conversation index and a larger white message workspace. The working inbox is the first screen; no marketing hero. Unpaired visitors see an honest connection state and a composed phone setup prompt. Sample conversations exist only in explicitly labeled demo mode.

## 2. Color Palette & Roles
- Paper (#FAFAFA): message canvas.
- Surface (#FFFFFF): composer and dialogs.
- Charcoal (#202124): navigation rail and primary text.
- Steel (#65656F): secondary text.
- Hairline (#E4E4E7): dividers.
- Signal Teal (#287C73): the sole accent, for primary actions, selected conversations and outgoing messages. Saturation remains below 80 percent.

## 3. Typography Rules
Geist for headings and body, Geist Mono for technical metadata; local sans-serif fallbacks. Body 16px, labels 14px, metadata 12px minimum. Headings use modest clamp-based sizing, tight tracking and weight. Body measure is 65ch. No serif or Inter.

## 4. Component Stylings
Buttons have 44px minimum targets and tactile transform feedback. Only the main task uses solid teal. Inputs have persistent labels, visible focus rings and inline errors. Message bubbles use 18px rounding with restrained directional corners. Dividers organize lists without card clutter. Dialogs use 24px rounding. Connection attempts use skeleton shimmer; empty states explain a concrete next action. Unknown sends must never appear sent.

## 5. Layout Principles
Desktop grid: 80px utility rail, 320px conversation index, remaining message workspace. Details use a dialog, not a fourth column. At widths below 768px, show one main pane at a time, with a back control from conversations. Keep all targets reachable; no horizontal overflow or overlapping content. Minimum page height is 100dvh. Use CSS Grid and real document flow.

## 6. Motion & Interaction
Use transform and opacity only, 180–260ms ease with spring-like settling. Stagger initial list appearances sparingly. An active connection attempt has a subtle repeating opacity state; avoid perpetual motion on reading surfaces. Honor reduced-motion. Keyboard Enter sends; Shift+Enter adds a line. Sending always requires an explicit user action.

## 7. Anti-Patterns (Banned)
No emoji decoration, Inter, pure black, neon glows, oversaturated accents, gradient headlines, custom cursors, equal three-card feature grids, generic placeholder names, fake metrics, filler slogans or centered heroes. Never display demo conversations as real SMS. No remote scripts, analytics or private pairing data in source. Do not advertise future capabilities as implemented.


## Release 0.1.4 theme
User reference: Voiceish desktop and Android portrait.png, imported into this project. Deep navy #040f1b, raised panels #0a1c2c, cobalt outgoing messages #1d70da, gold brand and selected navigation #ffbc32, cool light text #ebf3fc. Retain the working SIM message flows and responsive layout. Android and extension use the same palette.
