# Agentic TV

A custom home screen for the guest room TV, built step by step. Later it gets an
AI assistant, guest profiles and phone-as-remote. For now: a clean, simple home
screen that anyone can use.

**The TV:** Metz 2K TV, Android TV 11 (details in [docs/tv-specs.md](docs/tv-specs.md)).

## How it fits together

```
You (or Claude) change code  →  push to GitHub  →  GitHub builds the app (APK)
                                                         ↓
                         scripts/install.sh downloads it and puts it on the TV
```

You never need Android Studio. GitHub does the building.

## Folders

| Folder | What's in it |
| --- | --- |
| `tv-app/` | The home screen app (Kotlin). The main code is in `tv-app/app/src/main/java/com/agentictv/launcher/` |
| `scripts/` | Small Mac scripts that talk to the TV |
| `docs/` | Notes about the TV and setup |
| `.github/workflows/` | The automatic build |

The four app files, from easiest to change to hardest:

- `Favorites.kt`: which apps appear in the big "Watch" row
- `HomeScreen.kt`: what the screen looks like (colours, clock, tiles)
- `Apps.kt`: finds installed apps and opens them
- `MainActivity.kt`: starts the screen

## First-time setup (about 10 minutes)

1. **On the TV:** Settings > Device Preferences > About, click **Build** 7 times.
   Then in Settings > Device Preferences > Developer options, turn on **USB debugging**
   (and **Network debugging** / **Wireless debugging** if you see it).
2. **Find the TV's IP:** Settings > Network & Internet > your Wi-Fi network.
3. **On your Mac**, in this folder:

   ```bash
   scripts/setup-mac.sh            # installs adb, once
   scripts/connect.sh 192.168.x.x  # use the TV's IP; accept the prompt on the TV
   scripts/install.sh              # downloads the newest build and installs it
   scripts/set-home.sh             # makes it the home screen
   ```

If Home still opens the old screen, run `scripts/set-home.sh --force`.
To go back to the original anytime: `scripts/restore-home.sh`.

## Updating

After any change is merged to `main`, wait for the green tick on GitHub, then run
`scripts/install.sh` again.

## Privacy

- The app asks for **no permissions**: no internet, no microphone, no camera.
  It only looks at which apps are installed, so it can show them.
- Nothing is tracked or sent anywhere.
- When the AI assistant comes later, the API key will live on a small server we
  control, never on the TV, and guests' data will stay separate and be easy to wipe.
- `tv-app/app/debug.keystore` is the standard Android "debug" signing key. It isn't
  a secret; it just makes sure each new build installs over the old one.

## Roadmap

1. ✅ Basic home screen: clock, greeting, favourite apps, all apps, settings
2. Make it look great (wallpaper, guest welcome card with Wi-Fi info)
3. AI assistant: ask for something in plain words, the TV does it
4. Guest profiles with their own memory of what they like
5. Tap an NFC tag with your phone to use it as a remote
