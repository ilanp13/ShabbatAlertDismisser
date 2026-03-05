# Privacy Policy

**Last Updated:** March 5, 2026

## Overview

Shabbat Alert Dismisser ("the App") is designed with privacy as a core principle. We do not collect, transmit, or store personal data beyond what is strictly necessary for the app to function. This privacy policy explains what data the app accesses, how it's used, and your rights.

## Data Collection

### Location Data
- **What:** The app requests fine GPS location permissions (`ACCESS_FINE_LOCATION`) when you tap "Update Location" in the app.
- **Why:** To calculate accurate Shabbat times for your specific geographic location.
- **How it's used:** Location coordinates are sent to the Hebcal API (see Third-Party Services below) to fetch accurate candle-lighting and Havdalah times. The location is also used locally to calculate sunset times using the NOAA solar algorithm when offline.
- **Stored locally:** Location coordinates may be cached locally on your device to support offline calculations.
- **Note:** Location is only requested when you manually tap the button — the app does not continuously track your location.

### Network Requests
- **Hebcal API:** When you update location or weekly (as configured), the app makes HTTP requests to the public Hebcal API (`https://www.hebcal.com/api/...`) to fetch Shabbat times.
- **Request data:** Your location coordinates and selected minhag preference are sent to Hebcal.
- **No PII:** No personally identifiable information, device identifiers, or device IDs are sent.

### App Settings
- **Stored locally:** Your app preferences (minhag selection, alert dismissal delay, notification settings, etc.) are stored locally on your device only.
- **Not transmitted:** Settings are never sent to external servers.

### Accessibility Service
- **What it accesses:** The Accessibility Service scans for cell broadcast alert windows to detect when an alert is displayed.
- **What it does:** It monitors accessibility events for emergency alert overlays and taps the dismiss button automatically during configured times.
- **Data logging:** The app does not log or transmit the content of alerts or any alert metadata to external services.
- **Persistent storage:** No alert data is recorded or stored.

### Alert Dismissal History
- **What is stored:** A local log of dismissed alerts is kept on your device only.
- **Data captured:** Each entry includes:
  - Timestamp of dismissal (milliseconds since epoch)
  - Package name of the alert source (e.g., "com.android.cellbroadcastreceiver")
  - Button text that was tapped (e.g., "OK", "אישור")
  - Visible text content from the alert window
- **Storage:** Stored locally in SharedPreferences as a JSON array
- **Retention:** The app keeps a maximum of 200 recent records; older entries are automatically deleted
- **No transmission:** This history is never transmitted to any server or external service
- **Privacy:** You have full visibility and control — you can view the entire history within the app
- **Clear on uninstall:** All history is deleted when you uninstall the app or clear app data

## Data Not Collected

- **No analytics:** The app does not use analytics libraries or track app usage.
- **No advertising:** The app does not include ad networks or advertising platforms.
- **No device identifiers:** No Android ID, AAID, IMEI, or device-specific identifiers are collected or transmitted.
- **No crash reporting:** The app does not automatically report crashes to external services.
- **No personal information:** The app does not request or store your name, phone number, email, or other personal identifiers.

## Third-Party Services

### Hebcal API
- **Service:** The app uses the public Hebcal API (`https://www.hebcal.com`) to fetch accurate Shabbat times.
- **Data shared:** Your location coordinates and minhag preference.
- **Their privacy policy:** https://www.hebcal.com/privacy
- **Note:** Hebcal is a public service. Review their privacy policy to understand how they handle location data.

## Data Retention

- **Local storage:** App settings and cached Hebcal data are stored on your device indefinitely until you uninstall the app or manually clear data.
- **Server retention:** Hebcal may retain logs of API requests (including IP addresses and request timestamps) according to their privacy policy.
- **No long-term storage:** The app does not maintain a persistent database or cloud storage of user data.

## Permissions Explained

| Permission | Purpose |
|---|---|
| `INTERNET` | Fetch Shabbat times from Hebcal API |
| `ACCESS_FINE_LOCATION` | Calculate local Shabbat times based on your location |
| `ACCESS_COARSE_LOCATION` | Fallback for location (if fine location is unavailable) |
| `POST_NOTIFICATIONS` | Display persistent status notification (Android 13+) |
| `BIND_ACCESSIBILITY_SERVICE` | Detect and dismiss emergency alert windows |
| `RECEIVE_BOOT_COMPLETED` | Refresh Shabbat times when device restarts |

## Your Rights

- **Access:** You can see all your app settings within the app.
- **Deletion:** Uninstalling the app removes all locally stored data.
- **Control:** You can revoke location permissions at any time via Android Settings, and the app will use offline calculations instead.
- **Transparency:** The app is open-source. You can review the source code: https://github.com/ilanp13/ShabbatAlertDismisser

## Security

- **Local-first:** Data is stored locally on your device by default.
- **No account creation:** The app does not require creating an account.
- **No password storage:** No credentials or sensitive data are stored in the app.
- **HTTPS:** All network requests to external services (Hebcal) use HTTPS encryption.

## Changes to This Policy

We may update this privacy policy to reflect changes in our practices or technology. Continued use of the app after updates constitutes acceptance of the new policy.

## Contact

If you have questions or concerns about this privacy policy, you can:
- Open an issue on GitHub: https://github.com/ilanp13/ShabbatAlertDismisser/issues
- Review the source code to verify our practices: https://github.com/ilanp13/ShabbatAlertDismisser

## Summary

**Shabbat Alert Dismisser respects your privacy.** We don't track you, don't sell data, and only access the minimum information needed to calculate Shabbat times for your location. Your data stays on your device.
