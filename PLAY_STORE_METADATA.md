# Google Play Store Metadata

Use this content for your app listing on the Google Play Store.

---

## App Title (50 characters max)
```
Shabbat Alert Dismisser
```

---

## Short Description (80 characters max)
```
Auto-dismiss emergency alerts on Shabbat. Siren still plays. Screen unblocks.
```

---

## Full Description (4000 characters max)

```
Shabbat Alert Dismisser is an Android accessibility service that automatically
dismisses cell broadcast emergency alerts (e.g., Pikud HaOref / Home Front
Command) during Shabbat and Jewish holidays — so the full-screen alert doesn't
block your screen while the siren still plays.

🔔 KEY FEATURES

• Auto-dismiss during Shabbat only, Shabbat + holidays, always, or disabled
• Minhag profiles: Ashkenaz, Or HaChaim, Kise Rachamim, Ben Ish Chai, Jerusalem
• Choose between Standard (Gra) or Rabenu Tam end-of-Shabbat times
• Configurable 5–60 second delay before dismissal (time to hear the siren)
• Persistent status notification (optional, can be disabled)
• Hebrew and English UI
• Works offline using local sunset calculation
• Supports AOSP, Google, and Samsung cell broadcast packages

⚡ BATTERY EFFICIENT

The app is lightweight and event-driven:
• Accessibility service: near zero battery (only wakes on alert windows)
• Notification updates: once per minute
• Hebcal sync: once per week
• Location: only on demand

🛰️ HOW IT WORKS

1. The app listens for cell broadcast alert windows via Accessibility Service
2. When an alert is detected during configured times, it waits your delay setting
3. Then automatically taps the dismiss button
4. The siren continues to play — this only dismisses the visual overlay

Shabbat times are fetched from the Hebcal API based on your GPS location and
religious tradition. If no network is available, the app calculates times locally
using the NOAA solar algorithm.

📍 SETUP

1. Install and open the app
2. Tap "Open Accessibility Settings" and enable Shabbat Alert Dismisser
3. Return to the app and tap "Update Location" (allows GPS permission)
4. Select your minhag and preferred end-of-Shabbat time
5. Verify the times shown are correct for your area
6. Set your preferred dismissal delay
7. Done — the service runs silently in background

⚠️ IMPORTANT SAFETY NOTICE

This app dismisses the visual alert overlay ONLY. It does NOT:
• Suppress emergency sirens
• Block emergency notifications
• Prevent the alert from reaching your device
• Replace official emergency procedures

Always follow instructions from official emergency services (Pikud HaOref, etc.).
Stay aware of alerts through multiple means: sirens, radio, notifications,
official apps.

🔒 PRIVACY FIRST

• No analytics or tracking
• No ads or ad networks
• No personal data collection
• Location sent to Hebcal API only for Shabbat calculation
• Settings stored locally on your device only
• Open-source code — review everything

Read our full Privacy Policy and Terms of Service in the app settings or online.

🤖 BATTERY OPTIMIZATION (Samsung)

Some Samsung devices aggressively kill background services. To keep the app
working during Shabbat, add it to the "Never sleeping apps" list:
Settings → Battery → Background usage limits → Never sleeping apps

ℹ️ PERMISSIONS EXPLAINED

• Internet: Fetch Shabbat times from Hebcal API
• Location (Fine): Calculate Shabbat times for your area
• Notifications: Show status notification
• Accessibility Service: Detect and dismiss alert overlays
• Boot Completed: Refresh Shabbat times after device restart

💡 REQUIREMENTS

• Android 11 or newer
• Device support for cell broadcast alerts
• GPS location or manual entry of coordinates

📖 OPEN SOURCE

View the source code, report bugs, or contribute on GitHub:
github.com/ilanp13/ShabbatAlertDismisser

---
```

---

## Promotional Text (80 characters max)

```
Shabbat? Dismiss alerts automatically. Siren still plays. Privacy first.
```

---

## Category

**Category:** Tools
**Content Rating:** Everyone

---

## Screenshots

### Screenshot 1: Main Settings Screen
- Caption: "Configure when alerts auto-dismiss — Shabbat only, always, or disabled"

### Screenshot 2: Minhag Selection
- Caption: "Choose your community tradition (Ashkenaz, Or HaChaim, etc.) for accurate times"

### Screenshot 3: Shabbat Times
- Caption: "Real-time Shabbat start and end times based on your location"

### Screenshot 4: Battery-Efficient Service
- Caption: "Lightweight service that barely impacts battery"

### Screenshot 5: Privacy First
- Caption: "No tracking, no ads, no personal data. Open source."

---

## Keywords (comma-separated)

```
shabbat, jewish, alert, dismisser, emergency, pikud haoref, home front,
sabbath, accessibility, hebrew, jewish holidays, yom tov
```

---

## Notes for Store Review

**What reviewers need to know:**

1. **It's an Accessibility Service** — This is intentional. The app needs this permission to detect and dismiss emergency alerts.

2. **It's intentionally minimal** — No ads, no analytics, no tracking. The app does exactly one thing: dismisses alerts during Shabbat.

3. **Safety critical** — The siren still plays. Notifications still arrive. This only hides the visual overlay. The app includes clear warnings about not relying on it as the sole emergency method.

4. **Offline capable** — Works without internet using local sunset calculations.

5. **Open source** — Full source code available for transparency and community contribution.

---

## Changelogs (for each version)

### Version 1.0
```
Initial release of Shabbat Alert Dismisser

Features:
• Auto-dismiss emergency alerts during Shabbat
• Minhag profiles (Ashkenaz, Or HaChaim, Kise Rachamim, Ben Ish Chai, Jerusalem)
• Gra and Rabenu Tam end-of-Shabbat options
• Configurable dismissal delay (5–60 seconds)
• Offline Shabbat calculation using NOAA solar algorithm
• Persistent status notification
• Hebrew and English UI
• Battery-optimized accessibility service
```
