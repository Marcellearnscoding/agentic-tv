# Agentic TV

A custom home screen for the guest room TV, built step by step. Later it gets an
AI assistant, guest profiles and phone-as-remote. For now: a clean, simple home
screen that anyone can use.

**The TV:** Metz 2K TV, Android TV 11, 1280x720 screen, ~1 GB RAM (details in [docs/tv-specs.md](docs/tv-specs.md)).

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
| `supabase/functions/tv-agent/` | The assistant's "brain" (runs on Supabase, asks Claude what to do) |
| `scripts/` | Small Mac scripts that talk to the TV |
| `docs/` | Notes about the TV and setup |
| `.github/workflows/` | The automatic build |

The app files, from easiest to change to hardest:

- `Favorites.kt`: which apps appear in the "Watch" row, and which are hidden
- `Theme.kt`: colours and the big backdrop behind the top of the screen
- `HomeScreen.kt`: the layout (clock, cards, app tiles)
- `GuestCard.kt`: the Wi-Fi pop-up with the QR code
- `AssistantPanel.kt`: the "Ask the TV" pop-up
- `Assistant.kt`: talks to the brain and carries out what it says
- `Apps.kt`: finds installed apps and opens them
- `LocalConfig.kt` / `ConfigReceiver.kt`: settings that live only on the TV
- `MainActivity.kt`: ties it all together

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

Optional extras:

```bash
scripts/set-guest-info.sh       # Wi-Fi name + password for the welcome card and QR code
scripts/set-agent.sh <URL>      # turns on the assistant (asks for the TV token)
```

If Home still opens the old screen, run `scripts/set-home.sh --force`.
To go back to the original anytime: `scripts/restore-home.sh`.

## Updating

After any change is merged to `main`, wait for the green tick on GitHub, then run
`scripts/install.sh` again.

## Privacy

- The app's only permission is **internet**, used just when you ask the assistant
  something. No microphone, camera or location permission: voice typing is done
  by the TV's own Google speech screen.
- When you ask something, only your words and the names of installed apps go to
  our Supabase function, which asks Claude. Nothing is stored there.
- People (the name chips at the top left) are saved only on the TV. For a family
  member, their name and what Ask remembered they like are sent along with each
  question. Guests are never remembered. Press your chip again to see or erase it.
- The Claude API key lives in Supabase secrets, never on the TV or in GitHub.
  The TV proves it's ours with a token that only exists on the TV.
- Guest Wi-Fi details are saved only on the TV, never in GitHub.
- We switched off the TV's "ACR" app, which watched what's on screen for ad profiling.
- `tv-app/app/debug.keystore` is the standard Android "debug" signing key. It isn't
  a secret; it just makes sure each new build installs over the old one.

## Roadmap

1. ✅ Basic home screen: clock, greeting, favourite apps, all apps, settings
2. ✅ Look great on the 720p screen: wallpaper, guest welcome card with Wi-Fi QR
3. ✅ AI assistant: ask for something in plain words, the TV does it
4. ✅ People: name chips, own app order, Ask remembers what each person likes
5. Tap an NFC tag with your phone to use it as a remote
