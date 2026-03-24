# Release Notes

## Version 2.1.1 - Current Release

### English (en-US)

**Version 2.1.1 — Grayscale Maps, Map Tab Live Mode & Banner Reliability**

✨ **New:**
  • Map tab live mode — full map shows real-time per-region coloring when live mode is active
  • Map tab zoom-to-fit — respects zoom setting (off/click/auto) on prev/next navigation
  • Show All Israel button (🇮🇱) on map tab
  • Collapsible filter panel — tap ☰ to expand/collapse, saves map space
  • Instant map loading — cached data shown immediately, network fetch in background

🔧 **Fixes:**
  • Grayscale map tiles — removed distracting topographic colors in both light and dark mode
  • Map tab alert grouping — split by type (was mixing alarm + warning + ended in one group)
  • Banner auto-timeout fix — event ended banner correctly clears after 10 minutes
  • State machine driven by real-time data only — no more cache replay loop
  • Removed compass overlay and built-in zoom buttons (pinch-zoom + 🇮🇱 button)

---

### Hebrew (iw-IL)

**גרסה 2.1.1 — מפות אפורות, מצב חי במפה ואמינות באנרים**

✨ **חדש:**
  • מצב חי בלשונית מפה — מפה מלאה מציגה צביעת אזורים בזמן אמת
  • זום למפה בלחיצה — כפתורי הקודם/הבא מזימים לפי הגדרות
  • כפתור הצג כל ישראל (🇮🇱) בלשונית מפה
  • פאנל סינון מתקפל — לחיצה על ☰ לפתיחה/סגירה
  • טעינה מיידית — נתונים מהמטמון מוצגים מיד

🔧 **תיקונים:**
  • אריחי מפה באפור — הסרת צבעים טופוגרפיים מסיחים
  • קיבוץ התראות במפה — הפרדה לפי סוג (היה מערבב אזעקה + אזהרה + הסתיים)
  • באנר "האירוע הסתיים" נסגר נכון אחרי 10 דקות
  • מכונת מצב מונעת בזמן אמת בלבד — ללא לולאת השמעה חוזרת
  • הסרת מצפן וכפתורי זום מובנים
  • תיקון דליפת מטמון במצב חי — סקר לא מזין 24 שעות מלאות לעוקב אזורים
  • שינוי מצב איום מרענן בלוקים מיידית — ללא עיכוב בין באנר לבלוקים

---

## Previous Versions

### Version 2.1.0

**Version 2.1.0 — Live Mode, Polygon Maps & Color-Coded Alerts**

✨ **New:**
  • Live Mode — real-time per-region coloring on map with alarm/warning/ended states
  • Show All Israel button (🇮🇱) — one-tap zoom to full Israel view on mini map
  • New region blink — newly alerted regions flash on the map to draw attention
  • Polygon map regions — alert areas shown as filled polygons instead of dots
  • Dark mode map — map tiles invert automatically in dark theme
  • Color-coded alert types — red (alarm), yellow (warning), green (event ended)
  • Multi-block alert display — all alert groups visible simultaneously with color coding
  • Selected region borders (purple) and current location border (blue) on maps
  • Map zoom setting — zoom to alert area on click or auto-focus

---

### Version 2.0.2

**Version 2.0.2 — Stability, Region Picker & Smart Cycler**

🔧 **Fixes & Improvements:**
  • Green "Event Ended" banner — clear visual indicator when a threat has passed
  • Configurable polling frequency (off / 5–60 seconds)
  • Region picker rewrite — search with keyboard, selected regions shown first
  • Smart alert cycler — stays on current group unless new alerts arrive
  • Auto-dismissal safety — prevents accidental taps on wrong windows
  • Full 24h history — works around API 3000-entry limit with per-category fetching
  • Multiple crash and cache stability fixes

---

### Version 2.0.1

**Version 2.0.1 — Background Alert Detection, Filter Sync & RTL Fixes**

🔧 **Fixes:**
  • Background history refresh — every ~2.5 minutes the app fetches recent alert history, so short-lived warnings (e.g. "alerts expected soon") are detected even if missed by the 30-second live poll
  • Filter changes now apply immediately — switching back to the Status tab after changing filters on the Map tab refreshes the display without needing a manual refetch
  • RTL arrow fix — prev/next navigation arrows now point in the correct direction in Hebrew

---

### Version 2.0.0

**Version 2.0.0 — Live Alerts, Map View, Threat Monitoring & Full Localization**

✨ **New:**
  • 5-tab interface — Status, Settings, History, Map, and Alerts
  • Live alert map — interactive osmdroid map showing active and historical alert locations across Israel
  • 24-hour alert history — automatic caching with tiered time grouping (1min/10min/30min buckets)
  • History grouping modes — choose between tiered time buckets or a single "all alerts" view; during Shabbat, shows only alerts since candle lighting
  • Region monitoring — select specific regions to monitor; highlighted with bold text and yellow map marker borders
  • Within-alert region filtering — when "show non-selected regions" is off, only your selected regions appear even within multi-region alerts
  • Alert type filters — filter by category (missiles, hostile aircraft, events) via checkboxes on the Map tab
  • Threat state machine — persistent WARNING (amber) and ALARM (red) banners based on alert categories for your selected regions
  • Shabbat mode banner — visual indicator on Status tab when auto-dismiss is active
  • Relative time display — "X min ago" / "X hr ago" alongside timestamps in alert history
  • Alert cycler with pause/play — cycle through history groups with prev/next navigation
  • Refetch full 24-hour history from Pikud HaOref API
  • Torah portion (Parasha) display on status screen
  • Full Hebrew localization — all UI text properly translated with RTL support
  • Light/Dark/System theme selector

**Features:**
  • 4 dismiss modes + minhag profiles
  • Hebcal times with offline fallback
  • Persistent notification & battery efficient
  • Your privacy: All data stored locally only

Requires accessibility permission.

---

### Hebrew (iw-IL)

**גרסה 2.0.0 — התראות חיות, מפה, ניטור איומים ותרגום מלא**

✨ **חדש:**
  • ממשק 5 לשוניות — סטטוס, הגדרות, היסטוריה, מפה והתראות
  • מפת התראות חיה — מפה אינטראקטיבית המציגה מיקומי התראות פעילות והיסטוריות ברחבי ישראל
  • היסטוריית התראות 24 שעות — שמירה אוטומטית עם קיבוץ לפי זמנים (1 דק'/10 דק'/30 דק')
  • מצבי קיבוץ היסטוריה — בחר בין קיבוץ לפי זמנים או תצוגת "כל ההתראות" יחד; בשבת, מציג רק התראות מתחילת השבת
  • ניטור אזורים — בחר אזורים ספציפיים למעקב; מודגשים בטקסט מודגש ומסגרת צהובה במפה
  • סינון אזורים בתוך התראות — כשההצגה מוגבלת לאזורים נבחרים, רק הם מוצגים גם בהתראות מרובות אזורים
  • סינון לפי סוג התראה — טילים, כלי טיס, אירועים — דרך תיבות סימון בלשונית המפה
  • מכונת מצב איומים — באנר אזהרה (כתום) ואזעקה (אדום) לאזורים הנבחרים
  • באנר מצב שבת — חיווי ויזואלי כשהסגירה האוטומטית פעילה
  • תצוגת זמן יחסי — "לפני X דק׳" / "לפני X שע׳" ליד חותמות הזמן
  • מחזור התראות עם השהייה/המשך — ניווט קדימה/אחורה בין קבוצות היסטוריה
  • טעינת היסטוריה מלאה של 24 שעות מ-API פיקוד העורף
  • הצגת פרשת השבוע במסך הסטטוס
  • תרגום עברית מלא — כל טקסט הממשק מתורגם עם תמיכת RTL
  • בחירת ערכת עיצוב: בהיר/אפל/ברירת מחדל

**מאפיינים:**
  • 4 מצבי סגירה + פרופילי מנהגים
  • זמנים דרך Hebcal עם גיבוי מקומי
  • התראה קבועה וחיסכון בסוללה
  • הפרטיות שלך: הכל מאוחסן בהתקן בלבד

דורש הרשאת נגישות.

---

## Previous Versions

### Version 1.2.4

**Version 1.2.4 — Improved interface with history and customization**

✨ **New:**
  • 3-tab interface (Status, Settings, History)
  • Language & theme selectors (Light/Dark/System)
  • Local log of dismissed alerts with full context

**Features:**
  • 4 dismiss modes + minhag profiles
  • Hebcal times with offline fallback
  • Persistent notification & battery efficient
  • Your privacy: All data stored locally only

### Version 1.1.0

Multi-tab UI restructure with history logging and language selection.

### Version 1.0.9

Initial stable release with auto-dismiss functionality.
