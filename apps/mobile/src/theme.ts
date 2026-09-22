/**
 * Provisional tokens for the smoke screen.
 *
 * Colour, typography and spacing are Dasha's call (plan 023, "Design decisions owned by Dasha").
 * These values exist so nothing is unstyled by accident, and they keep body text above the 4.5:1
 * contrast floor the plan states as an acceptance criterion.
 */
export const theme = {
  background: "#f6f7f9",
  surface: "#ffffff",
  border: "#d3d7de",
  text: "#16181d",
  mutedText: "#4c5561",
  accent: "#1b4d89",
  selectedSurface: "#e4ecf7",
} as const;

/** Touch targets stay at or above this, per the plan's accessibility criteria. */
export const MIN_TOUCH_TARGET = 44;
