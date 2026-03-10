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

---

## Planned Features

### Phase 3 (Long-term)

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

**Phase 3 (Long-term) — Not started:**
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
