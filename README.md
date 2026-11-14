# Claude for Wear OS

A standalone Wear OS app for chatting with Claude from your wrist. Ask by voice
or keyboard, and the answer streams in as Claude writes it. It talks to the
[Claude API](https://platform.claude.com/docs) directly from the watch, so it
needs no phone app.

## Features

- **Voice first**: tap the big button at the bottom of the screen and speak.
  **Type** opens the watch keyboard (or your paired phone's keyboard).
- **Streaming replies**: text appears as Claude writes it. **Stop** cancels.
- **Multi-turn**: follow-up questions keep the conversation's context.
  **New chat** starts over.
- **Short answers**: the system prompt asks for one to three plain-text
  sentences, because the screen is small and shows no markdown.
- **Safe failures**: a bad key, rate limits, overload and network errors each
  get a clear message on the watch.

> This repository also contains **MédiPote** (`medipote/`), a separate Android
> phone app that turns taking medication into caring for a virtual pet. See
> [medipote/README.md](medipote/README.md).
>
> It also contains **Radar Occaz** (`radaroccaz/`), an Android phone app that
> searches used-car listings on several French sites, ranks them against the
> user's criteria and sends alerts for new listings. See
> [radaroccaz/README.md](radaroccaz/README.md).

## Project layout

| Module | What it is |
|---|---|
| `core/` | Plain Kotlin/JVM library around the official [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java): `ClaudeChat` (conversation history and streaming as a `Flow<ChatEvent>`) and `ChatConfig`. It has no Android dependencies, so it is unit-tested on the JVM against a local fake server. |
| `medipote/` | MédiPote, a standalone Android phone app for medication reminders (see its README). |
| `radaroccaz/` | Radar Occaz, an Android phone app that compares used-car listings and sends alerts (see its README). |
| `app/` | The Wear OS app: Compose for Wear OS (Material 3), a `ChatViewModel`, voice and keyboard input, and settings. The API key is kept in DataStore. |

### API settings (`core/.../ChatConfig.kt`)

| Setting | Default | Why |
|---|---|---|
| `model` | `claude-opus-5-5` | Current default Claude model |
| `effort` | `LOW` | Fast, direct answers for quick questions; raise it for harder ones |
| `maxTokens` | `8192` | Watch replies are deliberately short (thinking counts toward this) |
| thinking | adaptive | Claude decides how much to think |
| `serverSideFallback` | `true` | If a safety classifier declines a request, the API retries it on Anthropic's recommended fallback model (`fallbacks: "default"`, beta `server-side-fallback-2026-07-01`) |

Each full assistant message, thinking blocks included, is added back to the
history unchanged, as the API expects for multi-turn conversations. If a
request is declined (`stop_reason: "refusal"`), the partial reply is thrown
away and that question is left out of the history.

## Build and install

You need JDK 17+ and the Android SDK, with platform 37 installed (Wear Compose
1.7 needs `compileSdk 37`).

```bash
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test             # Claude client tests (fake SSE server, no network)
./gradlew :app:testDebugUnitTest # Robolectric screen tests; PNGs in app/build/screenshots
```

Install on a watch or Wear OS emulator:

```bash
adb connect <watch-ip>:<port>    # Developer options → Wireless debugging on the watch
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and run the `app` configuration on a
Wear OS device or emulator.

## API key

Get a key at [console.anthropic.com](https://console.anthropic.com/). Then
either:

- **On the watch**: open the app and tap **Set API key** (or go to Settings).
  Typing a long key on a watch is fiddly, so use the paired phone's keyboard
  if the input screen offers it.
- **For development**: add `claude.apiKey=sk-ant-...` to `local.properties`
  (git ignores this file). Debug builds use this key until you save or remove
  one in Settings. Release builds never contain it.

The key stays in the app's private storage on the watch and is excluded from
backups and device transfers. Anyone holding the key can use your API account,
so this app is meant for personal use. To distribute it to other people, route
requests through your own backend instead of shipping keys to devices.

## Notes

- **APK size**: the Anthropic Java SDK includes every API model class, so the
  release APK is about 65 MB. R8 is off (`isMinifyEnabled = false`) because
  the SDK deserialises responses with Jackson reflection. Turning R8 on needs
  keep rules for `com.anthropic.**` and Jackson, then testing on a device.
- **Voice input** uses the system speech recognizer
  (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`), which most Wear OS watches
  include.
- **Minimum**: Wear OS 3 (API 30).
