# Release Notes

## Version 2.2.0 - Current Release

### English (en-US)

**Version 2.2.0 — Warning Banner Fix, History Grouping & Stability**

✨ **New:**
  • History grouping mode — choose between tiered time buckets (1min/10min/30min) or a single "all alerts" view; in Shabbat, "all" mode shows only alerts since candle lighting
  • Fixed WARNING banner for "alerts expected soon" messages — the app now correctly handles Pikud HaOref API category 14 alerts that were previously ignored
  • Category 14 ("בדקות הקרובות") now properly mapped as event/warning type
  • Fixed crashes when switching tabs during background network operations

🔧 **Bug Fixes:**
  • AlertStateMachine now handles category 14 as WARNING (was falling through as "unknown category")
  • Fragment-not-attached crash fixed — background thread callbacks now guard against detached fragments
  • RedAlertService maps category 14 to "event" type for proper filtering

**Features:**
  • 4 dismiss modes + minhag profiles
  • Hebcal times with offline fallback
  • Persistent notification & battery efficient
  • Your privacy: All data stored locally only

Requires accessibility permission.

---

### Hebrew (iw-IL)

**גרסה 2.2.0 — תיקון באנר אזהרה, קיבוץ היסטוריה ויציבות**

✨ **חדש:**
  • מצב קיבוץ היסטוריה — בחר בין קיבוץ לפי זמנים (1 דק'/10 דק'/30 דק') או תצוגת "כל ההתראות" יחד; בשבת, מצב "כל" מציג רק התראות מתחילת השבת
  • תיקון באנר אזהרה עבור הודעות "בדקות הקרובות" — האפליקציה מטפלת כעת נכון בקטגוריה 14 מ-API של פיקוד העורף
  • קטגוריה 14 ("בדקות הקרובות") ממופה כעת נכון כסוג אירוע/אזהרה
  • תיקון קריסות בעת מעבר בין לשוניות במהלך פעולות רשת ברקע

🔧 **תיקוני באגים:**
  • מכונת מצב האיום מטפלת כעת בקטגוריה 14 כאזהרה (לפני כן הייתה מתעלמת)
  • תיקון קריסת "Fragment not attached" — קריאות חוזרות מרקע מגינות כעת מפני פרגמנטים מנותקים
  • RedAlertService ממפה קטגוריה 14 לסוג "אירוע" לסינון תקין

**מאפיינים:**
  • 4 מצבי סגירה + פרופילי מנהגים
  • זמנים דרך Hebcal עם גיבוי מקומי
  • התראה קבועה וחיסכון בסוללה
  • הפרטיות שלך: הכל מאוחסן בהתקן בלבד

דורש הרשאת נגישות.

---

## Previous Versions

### Version 2.1.0

**Version 2.1.0 — Improved Filtering, Localization & UX**

✨ **New:**
  • Within-alert region filtering — when "show non-selected regions" is off, only your selected regions are shown even within multi-region alerts
  • Relative time display — alert history shows "X min ago" / "X hr ago" alongside timestamps
  • Full Hebrew localization — all filter labels, map headers, and UI text properly translated (no more English leaking in Hebrew mode)
  • Pause/play button for the alert cycler on the Status tab
  • Region filter moved from Settings to Map tab as a checkbox for quick access
  • RTL layout fix — filter panel stays on the left side (over the sea) regardless of language direction
  • Threat banner improvements — clears correctly after event_over alerts, even with different regions
  • Alert type labels shown in grouped/stacked alerts (e.g. "3 alerts: missiles, aircraft")
  • Selected regions highlighted with bold text and yellow map marker borders

### Version 2.0.0

**Version 2.0.0 — Live Alerts, Map View & Threat Monitoring**

✨ **New:**
  • 5-tab interface (Status, Settings, History, Map, Alerts)
  • Live alert map — interactive map showing active and historical alert locations across Israel
  • 24-hour alert history with automatic caching and tiered time grouping
  • Region monitoring — select specific regions and get highlighted alerts
  • Alert type filters (missiles, aircraft, events)
  • Threat state machine — persistent WARNING (amber) and ALARM (red) banners for your selected regions
  • Shabbat mode indicator — visual banner when auto-dismiss is active
  • Compact status layout with split-screen alerts + map view
  • Previous/Next navigation through alert history groups
  • Refetch full 24-hour history from Pikud HaOref API
  • Torah portion (Parasha) display on status screen

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
