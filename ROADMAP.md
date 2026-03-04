# Roadmap — Future Features

This document outlines planned features and improvements for Shabbat Alert Dismisser.

---

## 🎯 Planned Features

### 1. Alert History & Summary (During Shabbat)

**Goal:** Show users which areas were affected by emergency alerts during Shabbat in a non-intrusive way.

**Features:**
- Integrate with **Red-Alert API** (pikud-haoref.com) to fetch historical alert data
- Display **summary of alerts** that occurred during current/recent Shabbat periods
- Show **location-based filtering** — allow user to mark interesting regions/cities they care about
- Alert summary includes:
  - Number of alerts per region
  - Most affected areas
  - Time of first/last alert
  - Duration of alert period

**User Experience:**
- Small, scrollable list in a collapsible section (not required on main screen)
- Quick glance at "what happened in my region"
- Optional: clickable regions to see details

**Technical Notes:**
- Requires new network call to Red-Alert API
- Cache results locally (updates every few hours)
- Respect API rate limits
- Handle network failures gracefully (offline fallback)

---

### 2. Map View of Alerts During Shabbat

**Goal:** Visual representation of all alerts that occurred during Shabbat on an interactive map.

**Features:**
- **Map integration** (Google Maps or Open Street Map)
- Show **all alert locations** as pins/circles on the map
- **Density visualization** — heat map or clustering of repeated alerts in same area
- Clickable pins showing:
  - Region/city name
  - Time of alert
  - Number of times alert was sounded

**User Experience:**
- Access from a "Map" tab or button on main screen during/after Shabbat
- Can zoom/pan to see affected areas
- Shows alerts from "Shabbat start" to "current time" or "Shabbat end"
- Timeline slider to filter alerts by time of day

**Technical Notes:**
- Consider privacy implications of showing specific locations
- Cache map data
- Lightweight rendering (cluster nearby alerts)
- Only fetch/show if Shabbat is active or recently ended

---

### 3. Improved Shabbat Status Screen (No Scrolling Required)

**Goal:** Consolidate all essential Shabbat information on one screen without requiring vertical scrolling.

**Current Issues:**
- Main screen requires scrolling on smaller devices
- Important information (Shabbat times, status, modes) requires scroll navigation
- Settings (delay, notification toggle) are below the fold

**Proposed Improvements:**
- **Vertical layout optimization:**
  - Move less critical info to separate tabs or collapsible sections
  - Keep essential info (status, times, modes) above fold
  - Group related settings more efficiently

- **Tab-based navigation:**
  - Tab 1: "Status" — Current state, Shabbat times, mode selection
  - Tab 2: "Settings" — Delay, notification, screen-on options
  - Tab 3: "Alerts" — History & map (when available)
  - Tab 4: "About" — Info, Privacy Policy, Terms

- **Or: Bottom Navigation:**
  - Similar structure with bottom navigation tabs
  - Modern Material Design approach

- **Conditional hiding:**
  - Hide sections not relevant to current time (e.g., mode options disabled when accessibility off)
  - Collapse/expand advanced settings

**Technical Notes:**
- Requires layout restructuring (fragment-based or ViewPager/ViewPager2)
- Material Design tab layout or bottom navigation
- Preserve all current functionality
- Test on small devices (4-5 inches)

---

## 📋 Implementation Priority

**Phase 1 (Short-term):**
1. Improve Shabbat Status Screen layout (no scrolling)
2. Add Red-Alert API integration for alert history

**Phase 2 (Medium-term):**
1. Map view of alerts
2. User-customizable region filters

**Phase 3 (Long-term):**
1. Advanced analytics (trends, most common times)
2. Notifications about incoming alerts (optional, respects Shabbat)
3. Export alert history

---

## 🔧 Technical Considerations

### Red-Alert API Integration
- **Endpoint:** pikud-haoref.com API (or similar public API)
- **Rate limiting:** Check documentation
- **Data format:** JSON, likely includes timestamp, region, alert type
- **Caching:** Store locally, update every 1-2 hours
- **Privacy:** Never send user location to Red-Alert API; only fetch summary data

### Map Integration
- **Library options:**
  - Google Maps SDK (requires API key, Play Services)
  - Open Street Map + Leaflet.js (open source, no key needed)
  - Mapbox (freemium)
- **Lightweight approach:** Consider static map image instead of interactive map for faster loading

### Screen Layout
- **Current:** Single scrollable activity
- **Proposed:** Fragment-based with tabs/bottom nav
- **Considerations:**
  - Maintain state across tabs
  - Handle accessibility service status changes across tabs
  - Keep loading times fast

---

## 🚀 Getting Started

To contribute to these features:

1. **Alert History:** Start by exploring Red-Alert API documentation
2. **Map View:** Choose a map library and create a prototype screen
3. **Layout:** Create a new layout with tab navigation and test on various screen sizes

All contributions welcome! Open an issue to discuss implementation approaches.
