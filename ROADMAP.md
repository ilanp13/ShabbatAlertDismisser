# Roadmap — Future Features

This document outlines planned features and improvements for Shabbat Alert Dismisser.

---

## Completed Features

### Phase 1 (v1.2.5)

- [x] **Improved Shabbat Status Screen layout** — No-scroll, compact 2-column layout for top section
- [x] **Red-Alert API integration** — Fetch alert history from pikud-haoref.com

### Phase 2 (Complete)

- [x] **Map view with osmdroid** — Interactive map with pins for active and historical alerts, mini-map on Status tab
- [x] **User-customizable region filters** — Region selector in Settings, "show alerts from selected/all" display mode
- [x] **Alert caching service** — 24-hour alert history cached locally
- [x] **Alerts tab** — Dedicated tab for browsing alert history with refresh/clear/refetch buttons
- [x] **Alert type filters** — Missile, aircraft, event filters configurable on the Map tab
- [x] **Alert state machine** — WARNING/ALARM/CLEAR states based on alert categories
- [x] **Threat banner** — Amber WARNING and red ALARM banners on the Status screen
- [x] **Shabbat mode banner** — Visual indicator during Shabbat or "always" mode
- [x] **Tiered alert grouping** — 1-minute recent, 10-minute, 30-minute buckets for older alerts
- [x] **Compact status layout** — 2-column layout for the top section of the Status screen
- [x] **5-tab interface** — Status, Settings, History, Map, Alerts

### Phase 2.1 (Complete)

- [x] **Within-alert region filtering** — Filter non-selected regions from within multi-region alerts (not just hide entire alerts)
- [x] **Full Hebrew localization** — All filter labels, map headers, toast messages, and overlay text properly translated
- [x] **Relative time display** — "X min ago" / "X hr ago" shown alongside timestamps in alert history
- [x] **Pause/play cycler button** — Toggle auto-cycling on the Status tab
- [x] **Region filter UX** — Moved from Settings radio to Map tab checkbox for quick access
- [x] **RTL layout fix** — Filter panel anchored to left side regardless of language direction
- [x] **Threat banner reliability** — Clears correctly after event_over (cat 13), replays cached alerts chronologically
- [x] **Alert type labels in groups** — Shows distinct alert titles in grouped/stacked alerts
- [x] **Selected region highlighting** — Bold text + yellow map marker borders for selected regions

### Phase 2.2 (Complete)

- [x] **Category 14 WARNING fix** — Handle Pikud HaOref API category 14 ("alerts expected soon") as WARNING in the state machine
- [x] **History grouping mode** — Settings option to choose tiered time buckets or single "all alerts" view; Shabbat-aware filtering shows only alerts since candle lighting
- [x] **Fragment crash fix** — Guard `requireContext()` in background thread callbacks to prevent crashes when switching tabs
- [x] **Background history refresh** — Periodic history fetch (~2.5 min) catches short-lived alerts missed by live polling
- [x] **Filter sync on tab switch** — Changing filters on Map tab applies immediately when returning to Status tab
- [x] **RTL arrow fix** — Prev/next navigation arrows point correctly in Hebrew

### Phase 2.3 (Complete)

- [x] **Green EVENT_ENDED banner** — Category 13 (event ended) now shows a green "Event ended — You can go out" banner with 10-minute auto-clear
- [x] **Polling frequency setting** — Configurable alert polling interval (off / 5-60 seconds) in Settings
- [x] **Auto-refetch on app open** — Single alert fetch when polling is disabled, triggered on resume
- [x] **Auto-dismissal race condition fix** — Re-checks window package before clicking dismiss to prevent tapping wrong UI
- [x] **Banner "since" time fix** — Threat banner shows actual alert timestamp instead of current time
- [x] **Empty refetch cache fix** — Failed API fetches no longer wipe the local alert cache
- [x] **Full 24h history fetch** — Per-category mode fetching when >3000 entries detected (works around API truncation limit)
- [x] **Region picker rewrite** — In-place search filtering with keyboard support, selected regions sorted to top
- [x] **Cycler vs polling fix** — Cycler preserves position when no new alerts; jumps to latest when new alerts arrive

### Phase 3.0 (Complete)

- [x] **Polygon map regions** — Alert areas rendered as filled polygons from Tzofar/RedAlert data (99.5% coverage) instead of dots
- [x] **Dark mode maps** — Inverted tile colors on both mini-map and full map tab when dark theme is active
- [x] **Color-coded alert types** — Red (alarm/missiles/aircraft), yellow (warning), green (event ended)
- [x] **Multi-block alert display** — All alert groups shown simultaneously as color-coded blocks on Status tab
- [x] **Color legend** — Compact legend row on Status tab
- [x] **Selected region borders** — Purple polygon outline for selected regions on maps
- [x] **Current location region** — Blue polygon outline for the region containing user's GPS position
- [x] **Map zoom setting** — 3-mode setting: disabled, on click, or auto-focus with bounding box zoom
- [x] **Map swipe fix** — ViewPager2 no longer steals horizontal swipes from map views
- [x] **Alert type system rewrite** — alarm/warning/event_ended types with category-based classification
- [x] **Event ended title inference** — "הסתיים" checked first in title to prevent misclassification
- [x] **Threat banner auto-timeout** — EVENT_ENDED state correctly times out after 10 minutes
- [x] **Smart block cycling** — No-flicker fast-path highlight updates, live header time refresh
- [x] **Filter system migration** — Old missile/aircraft/event filters auto-migrate to alarm/warning/event_ended
- [x] **Cache clear behavior** — Clear hides alerts without deleting cache; only refetch 24h brings them back

---

## Planned Features

### Phase 3.1 — Live Mode & Map UX (Complete)

- [x] **Live mode** — Real-time per-region coloring on map based on current alert state
- [x] **Per-region state tracking** — Each region tracks its own warning/alarm/ended state with timeouts
- [x] **Live mode cycler option** — New cycler mode showing real-time status instead of history cycling
- [x] **Show All Israel button** — One-tap zoom to full Israel view on mini map
- [x] **New region blink** — Newly alerted regions flash on the map to draw attention
- [x] **Live mode cache leak fix** — Only recent 30-minute alerts fed to tracker, not full 24h cache
- [x] **Grayscale map tiles** — Desaturated tiles remove topographic colors, cleaner in both themes
- [x] **State machine real-time only** — Cache replay removed from poll loop, prevents banner timeout replay loop
- [x] **Threat state change block rebuild** — Blocks update immediately when banner state transitions
- [x] **Map tab live mode** — Full map shows real-time RegionAlertTracker state with polling
- [x] **Map tab zoom-to-fit** — Respects zoom setting (off/click/auto) on prev/next navigation
- [x] **Map tab Show Israel button** — Same as status tab, physical-right for RTL
- [x] **Collapsible filter panel** — Compact ☰ toggle, default collapsed
- [x] **Instant map loading** — Cached data first, network fetch in background
- [x] **Map tab type splitting** — Each time bucket split by alert type, no mixed groups
- [x] **Map style selector** — 3-option toggle in filter panel: Minimal (CartoDB), Grayscale, Detailed
- [x] **CartoDB tile source** — Clean, sparse tiles via MapTileHelper shared by both map views

### Phase 4 (Long-term)

#### 1. Advanced Analytics

**Goal:** Provide trend data and insights from alert history.

**Features:**
- Most common alert times (by hour/day)
- Trends over time for selected regions
- Summary statistics (alerts per day, most affected areas)

#### 2. Alert Notifications (Optional)

**Goal:** Notify users about incoming alerts while respecting Shabbat mode.

**Features:**
- Push-style notifications for alerts in selected regions
- Respect Shabbat mode — suppress or defer notifications during Shabbat
- Configurable notification preferences (sound, vibration, silent)

#### 3. Export Alert History

**Goal:** Allow users to export cached alert data for external use.

**Features:**
- Export to CSV or JSON
- Date range selection
- Filter by region or alert type before export

---

## Implementation Priority

**Phase 1 (Short-term) — Done:**
1. [x] Improve Shabbat Status Screen layout (no scrolling)
2. [x] Add Red-Alert API integration for alert history

**Phase 2 (Medium-term) — Done:**
1. [x] Map view of alerts (interactive osmdroid map + mini-map on Status screen)
2. [x] User-customizable region filters
3. [x] Alert caching service (24h local cache)
4. [x] Alerts tab with refresh/clear/refetch
5. [x] Alert type filters (missile, aircraft, event)
6. [x] Alert state machine (WARNING/ALARM/CLEAR)
7. [x] Threat banner and Shabbat mode banner
8. [x] Tiered alert grouping
9. [x] Compact status layout and 5-tab interface

**Phase 2.1 (Polish) — Done:**
1. [x] Within-alert region filtering
2. [x] Full Hebrew localization (no English leakage)
3. [x] Relative time display in alert history
4. [x] Pause/play cycler button
5. [x] Region filter moved to Map tab
6. [x] RTL layout fix for filter panel
7. [x] Threat banner reliability (event_over handling)
8. [x] Alert type labels in grouped alerts
9. [x] Selected region highlighting (bold + yellow border)

**Phase 2.2 (Bug Fixes & History) — Done:**
1. [x] Category 14 WARNING fix (alerts expected soon)
2. [x] History grouping mode (tiered vs all 24h / since Shabbat)
3. [x] Fragment crash fix (background thread safety)
4. [x] Background history refresh (catches missed alerts)
5. [x] Filter sync on tab switch
6. [x] RTL arrow fix for Hebrew

**Phase 2.3 (Stability & UX) — Done:**
1. [x] Green EVENT_ENDED banner (10-min auto-clear)
2. [x] Polling frequency setting (off / 5-60s)
3. [x] Auto-refetch on app open
4. [x] Auto-dismissal race condition fix
5. [x] Banner "since" time fix
6. [x] Empty refetch cache protection
7. [x] Full 24h history fetch (per-category mode)
8. [x] Region picker rewrite (in-place search, keyboard, selected-first)
9. [x] Cycler vs polling fix (smart index preservation)

**Phase 3.0 (Polygon Maps & UX) — Done:**
1. [x] Polygon map regions (Tzofar data, 99.5% coverage)
2. [x] Dark mode maps (inverted tiles)
3. [x] Color-coded alert types (alarm/warning/event_ended)
4. [x] Multi-block alert display with legend
5. [x] Selected region + current location polygon borders
6. [x] Map zoom setting (off/click/auto)
7. [x] Map swipe fix (ViewPager2)
8. [x] Alert type system rewrite + filter migration
9. [x] Event ended title inference fix
10. [x] Threat banner auto-timeout fix
11. [x] Smart no-flicker block cycling

**Phase 3.1 (Live Mode & Map UX) — Done:**
1. [x] Real-time per-region map coloring
2. [x] Per-region state tracking with timeouts
3. [x] Live mode cycler option
4. [x] Show All Israel button
5. [x] New region blink animation
6. [x] Live mode cache leak fix
7. [x] Grayscale map tiles
8. [x] State machine real-time only (no cache replay)
9. [x] Threat state change block rebuild

**Phase 4 (Long-term) — Not started:**
1. Advanced analytics (trends, most common times)
2. Notifications about incoming alerts (optional, respects Shabbat)
3. Export alert history

---

## Technical Considerations

### Red-Alert API Integration (Implemented)
- **Endpoint:** pikud-haoref.com API with `hours=24` parameter for full history
- **Data format:** JSON with timestamp, region, alert type
- **Caching:** Stored locally via AlertCacheService, updated periodically
- **Privacy:** Never sends user location to the API; only fetches summary data

### Map Integration (Implemented)
- **Library:** osmdroid (open source, no API key needed)
- **Features:** Interactive pan/zoom, alert pins with color coding, mini-map on Status tab
- **Filtering:** By alert type (missile, aircraft, event) and by region

### Screen Layout (Implemented)
- **Structure:** Fragment-based with 5 bottom navigation tabs (Status, Settings, History, Map, Alerts)
- **State management:** Maintained across tab switches
- **Accessibility:** Service status changes handled across all tabs

---

## Getting Started

To contribute to Phase 3 features:

1. **Advanced Analytics:** Design summary views and chart components for trend visualization
2. **Notifications:** Investigate FCM or local notification scheduling that respects Shabbat windows
3. **Export:** Add file-writing logic with format selection (CSV/JSON)

All contributions welcome! Open an issue to discuss implementation approaches.
