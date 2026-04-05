# Chronograph Shabbat Watch Face Design

**Date:** 2026-04-05
**Branch:** `feat/wear-os-shabbat-mode`

---

## Overview

Replace the current simple analog clock with a chronograph-style watch face featuring 3 sub-dials, refined tick marks, bold hands, and a polished look appropriate for a 480x480 round Wear OS display.

---

## Layout

The face is divided into zones on a black (#111111) background:

### Outer Ring
- 60 minute tick marks: thick (1.5px) gray (#555) lines from R-22 to R-10
- 12 hour tick marks: bold (3px) white (#EEEEEE) lines from R-35 to R-10
- Hour numerals (18px, white, bold): shown at positions 1, 2, 4, 5, 6, 7, 8, 10, 11 — positions 12, 3, 9 are replaced by sub-dials
- Subtle chapter ring: 1px #333 circle at R-8

### Clock Hands
- **Hour hand**: 80px length, 7px width, white, rounded caps
- **Minute hand**: 115px length, 4.5px width, white, rounded caps
- **No second hand** (battery saving — configurable via existing `showSeconds` setting; if enabled, thin 1px accent-colored second hand)
- **Center dot**: 5px gold circle with 2px dark inner circle

### Sub-Dial: Battery (12 o'clock, centered at y = C-85)
- 42px radius circle, dark background (#1a1a1a), 1px #444 border
- Arc indicator: 4px stroke, sweeps proportional to battery level
- Arc color: gold (#D4AF37) above 50%, orange (#FF8800) 20-50%, red (#FF4444) below 20%
- Center text: percentage in white 14px bold
- Label: "BATTERY" in gray 10px (or Hebrew equivalent based on locale)

### Sub-Dial: Shabbat Status (9 o'clock, centered at x = C-95, y = C+15)
- 42px radius circle, dark background, 1px border
- **During Shabbat/holiday**: Two candle icons with gold bodies and orange/yellow flame gradients, label "שבת" below
- **Outside Shabbat**: Same candle icons + countdown text below: "3ש 42ד" (hours and minutes to next candle lighting), label "הדלקת נרות" in smaller gray text
- **No schedule data**: Candles grayed out, "---" text

### Sub-Dial: Hebrew Date (3 o'clock, centered at x = C+95, y = C+15)
- 42px radius circle, dark background, 1px border
- Line 1: Day number in white 16px bold (e.g., "י״ח")
- Line 2: Month name in gold 13px bold (e.g., "ניסן")
- No year shown (saves space on small sub-dial)
- Uses `android.icu.util.HebrewCalendar` for accurate dates

### Bottom Text Area (below center)
- Havdalah time: gold 12px (e.g., "מוצ״ש: 19:42") at y = C+110
- Parasha: gray 11px (e.g., "פרשת אחרי מות") at y = C+128
- Both controlled by existing `showHavdalah` and `showParasha` toggles

### Top Text (above battery sub-dial)
- Indicator text: gold 15px bold at y = C-145
- During Shabbat: "שבת שלום" or holiday name
- Always-on mode outside Shabbat: "מצב שבת פעיל"

---

## Accent Color Support

The existing accent color setting (gold/blue/green/white/red/purple) applies to:
- Hour tick accents (subtle inner tick highlight)
- Battery arc color (replaces gold, keeps orange/red thresholds)
- Candle flame color
- Hebrew month text
- Havdalah time text
- Center dot
- Indicator text
- Second hand (if enabled)

Ambient/gray colors remain unchanged regardless of accent.

---

## Clock Size Support

The existing clock size setting (small/medium/large) scales the entire face:
- **Small**: tick marks and sub-dials are closer to center, more padding from edges
- **Medium**: default layout as described above
- **Large**: tick marks extend further, sub-dials slightly larger

Implementation: apply a scale factor (0.85 / 1.0 / 1.15) to all radius calculations.

---

## Show/Hide Toggles

Existing toggles control visibility:
- `showBattery` = false → battery sub-dial hidden (just empty dark circle or no circle)
- `showHebrewDate` = false → Hebrew date sub-dial hidden
- `showHavdalah` = false → havdalah time text hidden
- `showParasha` = false → parasha text hidden
- `showSeconds` = true → add thin accent-colored second hand

When a sub-dial is hidden, the hour numeral at that position reappears.

---

## Ambient Mode Rendering

When `isAmbient = true` (currently always true for Shabbat mode):
- All accent colors → gray (#888888)
- All white → gray (#888888)
- Sub-dial backgrounds → black (same as main background, borders stay)
- No second hand
- Battery arc → gray
- Candle flames → gray outlines only (no fill)

---

## Digital Clock Mode

When `useAnalog = false`, the digital clock continues to use the current `DigitalClock` composable (large centered time text). The sub-dials, tick marks, and hands are not shown. The bottom text area (havdalah, parasha) and top indicator remain.

---

## Files Changed

- **Rewrite**: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/AnalogClock.kt` — complete rewrite with chronograph Canvas drawing
- **Modify**: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/ShabbatFace.kt` — pass new parameters (battery level, countdown info, Shabbat status) to AnalogClock
- **Modify**: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt` — compute countdown to next candle lighting, pass to face

---

## New Parameters for AnalogClock

```
batteryLevel: Int          // 0-100
accentColor: Color         // from theme
isShabbatActive: Boolean   // candles lit vs countdown
candleLightingCountdown: String?  // "3ש 42ד" or null
hebrewDay: String          // "י״ח"
hebrewMonth: String        // "ניסן"
showBattery: Boolean
showHebrewDate: Boolean
showSeconds: Boolean
isAmbient: Boolean
scaleFactor: Float         // from clock size setting
```

The `AnalogClock` composable becomes self-contained — it draws everything (ticks, hands, sub-dials, text) within a single Canvas. No sub-composables needed.

---

## Mockup

Interactive HTML mockup at: `docs/mockup/chronograph.html`
