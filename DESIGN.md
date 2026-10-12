---
version: alpha
name: Heart Rate Mirror
description: A training instrument that mirrors a watch or chest strap's heart rate, read at arm's length mid-effort.
colors:
  ground: "#0B0C0E"
  surface-dialog: "#16181C"
  text: "#F2F3F5"
  text-muted: "#A3A8B0"
  hairline: "#2A2E35"
  outline: "#6B727D"
  action: "#F2F3F5"
  on-action: "#0B0C0E"
  zone-below: "#8A93A0"
  zone-fat-burn: "#38B6C8"
  zone-cardio: "#F2A93B"
  zone-peak: "#FF5A6E"
  on-zone: "#0B0C0E"
typography:
  display-bpm:
    fontFamily: Barlow Condensed
    fontSize: 200px
    fontWeight: 600
    lineHeight: 0.85
    letterSpacing: -4px
  numeral-lg:
    fontFamily: Barlow Condensed
    fontSize: 24px
    fontWeight: 500
    lineHeight: 1.1
  numeral-md:
    fontFamily: Barlow Condensed
    fontSize: 18px
    fontWeight: 500
    lineHeight: 1.1
  title:
    fontFamily: Barlow
    fontSize: 22px
    fontWeight: 600
    lineHeight: 1.2
  label-lg:
    fontFamily: Barlow
    fontSize: 17px
    fontWeight: 600
    lineHeight: 1.2
  body:
    fontFamily: Barlow
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.35
  body-sm:
    fontFamily: Barlow
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.35
rounded:
  none: 0px
  sm: 2px
  md: 4px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 20px
  xl: 28px
components:
  screen:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    padding: "{spacing.lg}"
  caption:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.text-muted}"
    typography: "{typography.body-sm}"
  button-primary:
    backgroundColor: "{colors.action}"
    textColor: "{colors.on-action}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    height: 56px
  button-secondary:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.text}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    height: 48px
  button-text:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.text-muted}"
    typography: "{typography.body}"
    height: 48px
  button-disabled:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.outline}"
    typography: "{typography.label-lg}"
    height: 48px
  zone-chip-below:
    backgroundColor: "{colors.zone-below}"
    textColor: "{colors.on-zone}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    height: 36px
  zone-chip-fat-burn:
    backgroundColor: "{colors.zone-fat-burn}"
    textColor: "{colors.on-zone}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    height: 36px
  zone-chip-cardio:
    backgroundColor: "{colors.zone-cardio}"
    textColor: "{colors.on-zone}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    height: 36px
  zone-chip-peak:
    backgroundColor: "{colors.zone-peak}"
    textColor: "{colors.on-zone}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    height: 36px
  zone-scale-segment:
    rounded: "{rounded.sm}"
    height: 14px
  list-row:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    height: 64px
  dialog:
    backgroundColor: "{colors.surface-dialog}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
  dialog-caption:
    backgroundColor: "{colors.surface-dialog}"
    textColor: "{colors.text-muted}"
    typography: "{typography.body-sm}"
---

# Heart Rate Mirror

## Overview

A training instrument, not a dashboard: one person reads it at arm's length, mid-effort, with the phone propped on a treadmill or bike. The number comes first, where you are in your zones second, everything else is reference. Dark only, because the screen stays on for a whole session.

## Colors

- **Ground (#0B0C0E):** every screen background. No filled cards on it.
- **Text (#F2F3F5) / Text muted (#A3A8B0):** the only two text tones. No alpha greys.
- **Action (#F2F3F5) with On-action (#0B0C0E):** the primary button. There is no brand accent.
- **Outline (#6B727D):** borders of secondary buttons and text fields (3:1 on ground). **Hairline (#2A2E35):** row dividers only.
- **Zones:** Below #8A93A0, Fat burn #38B6C8, Cardio #F2A93B, Peak #FF5A6E. They differ in lightness as well as hue. Text on a zone fill is On-zone (#0B0C0E).
- Colour appears only where it means a zone. Zone colour always comes from `zoneColor(zone)` using the age-based zone; no colour is derived from a raw bpm value.

## Typography

- **Barlow Condensed** (500, 600) for every number that is data: bpm, durations, zone bounds. Narrow letterforms let the live number be larger in the same width.
- **Barlow** (400, 500, 600) for all words.
- The live bpm is `display-bpm` and is always Text colour, never a zone colour.
- Minimum size 14sp. Seven roles only; no one-off sizes.
- Sentence case everywhere. No all-caps, no letter-spaced labels.

## Layout

- Screen edge padding 20dp; 28dp between sections; 8–12dp inside a group.
- Screens are a single column that flows top to bottom. No element is placed by a fixed offset or a fraction of screen height.
- Left-aligned. Only dialogs centre.
- Every tappable element is at least 48dp tall, and two actions never sit closer than 8dp.
- System bars are handled with window insets, not fixed spacers.

## Elevation & Depth

None. No shadows, no tonal surfaces. Separation is by 1dp hairlines and spacing. Dialogs are the one exception: a `surface-dialog` fill.

## Shapes

- 4dp corners on buttons, chips, fields and dialogs.
- 2dp on zone bar segments and swatches; segments are separated by a 2dp gap.
- No pills, no circles except the 8dp connection dot.

## Components

- **Primary button:** action fill, on-action text, 56dp, full width. One per screen.
- **Secondary button:** 1dp outline, text colour, 48dp, full width.
- **Text button:** text-muted, 48dp target. Destructive actions are text buttons too, and always confirm in a dialog; they are not coloured.
- **Zone chip:** zone fill, on-zone text, 36dp. Names the current zone on the live screen.
- **Zone scale:** four segments sized by each zone's bpm span from the user's age. Segments other than the current one draw at 35% opacity; a 3dp text-coloured marker shows the current bpm; zone bounds are labelled beneath in `numeral-md`.
- **Trace graph:** text-coloured 2dp line over zone bands tinted at 14% opacity, with gridline labels at the user's zone bounds.
- **Time-in-zone row:** 10dp square swatch, name, duration in `numeral-md`; the current zone's row is weight 600, the rest text-muted.
- **List row:** 64dp, hairline beneath, no fill.
- **Status and error lines:** text colour with a leading icon; never a colour.
- **Icons:** 24dp outlined vector drawables in `res/drawable`, 2dp stroke, tinted text colour. Never a text glyph.

## Do's and Don'ts

- Do keep the live number white and let the chip and scale carry the zone.
- Do draw every zone colour, gridline and boundary from the age-based model.
- Do take every colour, size and radius from the theme tokens.
- Don't write a hex value, `Color.White.copy(alpha = …)` or a raw `sp` size in a component.
- Don't use colour for brand, emphasis, errors, links or destructive actions.
- Don't use gradients, shadows, filled cards, pills, or cards with a coloured edge.
- Don't use emoji or text characters as icons.
- Don't show zone elements when no age is set; prompt for the age instead.
