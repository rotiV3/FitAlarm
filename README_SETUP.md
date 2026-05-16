# FitAlarm – Setup Guide

FitAlarm is an Android app that reads your Google Calendar events, auto-sets a wake-up alarm for the day, detects gym sessions, tracks your location at the gym, and rewards you with achievements.

## Prerequisites

- Android Studio Ladybug (2024.2+) or newer
- Android SDK API 35
- A Google account with calendar events
- A Google Cloud project

## Step 1 — Google Cloud Project Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (or use an existing one)
3. Enable these APIs:
   - **Google Calendar API**
   - **Maps SDK for Android**
4. Go to **APIs & Services → Credentials**

## Step 2 — OAuth 2.0 Credentials

1. Click **Create Credentials → OAuth client ID**
2. Select **Android** as application type
3. Enter package name: `com.rotiv3.fitalarm`
4. Enter your debug SHA-1 fingerprint:
   ```
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
5. Also create a **Web application** OAuth client (needed for `WEB_CLIENT_ID`)
6. Download or copy the web client ID

## Step 3 — google-services.json (Optional for Firebase)

If you need Firebase features, download `google-services.json` from Firebase Console and place it in `app/`.

For Google Sign-In without Firebase, just use the OAuth credentials from Step 2.

## Step 4 — local.properties

`local.properties` is gitignored (it contains machine-specific paths and secret keys).  
You need to **create it** by copying the provided example file:

```bash
# macOS / Linux
cp local.properties.example local.properties

# Windows
copy local.properties.example local.properties
```

Then open `local.properties` and fill in your values:

```properties
sdk.dir=/Users/yourname/Library/Android/sdk   # set by Android Studio automatically

MAPS_API_KEY=AIzaSy...your_maps_api_key...
WEB_CLIENT_ID=123456789-abc...your_web_client_id....apps.googleusercontent.com
```

> **Note:** Android Studio also creates/updates `sdk.dir` automatically when you first open the project, so you only need to add the two API key lines manually.

## Step 5 — Build & Run

1. Open the project in Android Studio
2. Sync Gradle files (`File → Sync Project with Gradle Files`)
3. Select a device or emulator (API 26+)
4. Run the app (`Shift+F10`)

## Required Device Permissions

The app will request these permissions at runtime:
- **Location (Fine)** — for gym check-in detection
- **Background Location** — to track gym presence when app is backgrounded
- **Post Notifications** — for alarms and achievement notifications
- **Schedule Exact Alarms** — for precise wake-up alarms (Android 12+)

## App Features

| Feature | Description |
|---|---|
| Google Sign-In | OAuth2 with Calendar read scope |
| Calendar Sync | Reads Day / Week / Month events |
| Auto Wake-Up Alarm | Sets alarm X minutes before first daily event |
| Auto Next-Day Alarm | When you dismiss today's alarm, tomorrow's is scheduled |
| Gym Event Detection | Detects keywords: gym, workout, HIIT, yoga, run, etc. |
| Training Plan | Extracts plan from event description |
| GPS Gym Check-In | 5-min presence check within configurable radius |
| Achievements | 6 badge types, shareable to social media |
| Dark Theme | Material Design 3 dark theme |

## Architecture

```
app/
├── alarm/          # AlarmManager, AlarmReceiver, AlarmActivity, CalendarSyncService
├── data/
│   ├── local/      # Room database, DAOs
│   ├── model/      # Data classes (CalendarEvent, GymLocation, Achievement, WakeupAlarm)
│   ├── remote/     # Google Calendar API client
│   └── repository/ # CalendarRepository, AlarmRepository, GymRepository
├── di/             # Hilt modules (AppModule, NetworkModule)
├── location/       # GymCheckInService (foreground), LocationUtils
└── ui/
    ├── achievement/ # Achievements screen
    ├── calendar/    # Day/Week/Month calendar view
    ├── eventdetail/ # Event detail with training plan
    ├── gym/         # Gym setup with Google Maps
    ├── home/        # Home screen with today's events + alarm
    └── settings/    # App settings
```
