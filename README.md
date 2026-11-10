<p align="center"><img src="docs/logo.png" alt="Stagebook" width="120"></p>

<h1 align="center">Stagebook</h1>

**English** · [Português (Brasil)](README.pt-BR.md)

[![Release](https://img.shields.io/github/v/release/julianoAffonso/stagebook?label=release)](../../releases/latest)
[![License: PolyForm Shield 1.0.0](https://img.shields.io/badge/license-PolyForm%20Shield%201.0.0-blue)](LICENSE.md)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)](../../releases/latest)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/julianoaffonso)

Lyrics, chords and backing tracks in one app, to play live on Android. Solo or with the band.

**Help, with a picture of every screen:** [docs/help](docs/help/index.html)

## What it does
- **Stage:** full screen, always lit, in pages or scrolling. Turn with a Bluetooth or MIDI pedal (USB and Bluetooth),
  a tap or auto-scroll. In Scroll mode, tap the bottom half or drag up to go on, the top half or drag down to go
  back. Each pedal button can do something else.
- **Chords:** chords above the lyrics (ChordPro), transpose, capo, tablature and each chord's diagram on acoustic or
  electric guitar, ukulele, cavaquinho or keyboard, with sound. It also shows the key's harmonic field. Notes on the
  chords line (2x, Intro:, a chord's frets like B/F# 224442) stay on it, and the frets move when you transpose. Tap a
  chord, in the lyrics or in the chord list, to see its diagram.
- **Setlists:** build the show and drag to reorder; list them newest first or A to Z. Send it to whoever is next to
  you by QR, far away with 3 words over the internet, or to the whole band on the same Wi-Fi.
- **Band:** with "Lead the band", whoever leads the setlist changes songs and everyone's stage follows. Before
  starting, the app says who is missing a song.
- **Backing tracks:** OGG with any number of channels, MP3 or WAV, on a USB audio interface or the device's output.
  With a mixer, per-output routing, EQ and normalization.
- **Metronome**, lyrics and chords import from websites, **Now Playing** for OBS and Resolume
  ([format](docs/now-playing.md)), backup to a `.stagebook` file (tap one in a chat, a download or the file manager
  and it opens in the app) and an **essential mode** that turns off what you won't use.
- No account, no ads, no tracking. Your songs stay on the device. What goes to the band or through the 3 words is
  encrypted.

## USB player
- A **class compliant** USB audio interface (USB Audio Class 1.0 or 2.0, works on a computer without a driver).
- **44.1 or 48 kHz** support.
- A phone or tablet with **USB OTG** and the right cable or adapter.

Both USB Audio 2.0 and USB Audio 1.0 interfaces work. Without an interface, the player uses the device's headphones,
Bluetooth or speaker.

Two ways to play on the interface:
- **Through Stagebook's engine (default):** talks to the interface directly.
- **Through Android:** Settings > Metronome and player > Play on the interface through Android. Use it only if the
  interface connects and the sound doesn't come out or breaks up.

Each interface keeps its own choice on each device: with the interface connected, the switch applies only to it. If
the sound keeps failing on the engine, the app turns the switch on for that interface and tells you; if no sound comes
out through Android, it turns it back off. Your own choice always wins.

The first time the engine is used, Android asks for permission to use the USB interface: tap OK. Before the first note
the app checks the interface's clock for about 2 seconds (the stage says to wait if it takes longer). After 10 seconds
without playing, the USB stream closes to save battery and opens again on play.

The player warns when the interface is charging the device (some interfaces fail on the engine that way) and when a
USB cable is plugged in but the interface doesn't answer. Both warnings need Android to let the app read the USB port;
where it can't, they never show.

### Tested
All passed on app version 0.1.900. A pass means: a whole song heard clean, pause and resume, and unplugging and
plugging the cable with the music playing came back on its own. Through Stagebook's engine unless noted.

| Device | Behringer FLOW 8 mixer (USB Audio 2.0, 4 outputs) | Nanocore card (USB Audio 1.0, 44.1 kHz) | Behringer UMC404HD (USB Audio 2.0, 4 outputs) | Behringer XR18 digital mixer (USB Audio 2.0, 18 outputs) |
|---|---|---|---|---|
| TCL NXTPAPER 11 Plus (tablet, 8 cores, Android 14) | Pass | Pass | Pass (4) | Not tested |
| Chuwi Hi10 XPro (budget tablet, Android 13) | Pass, with a note (1) | Pass | Pass | Fails (5) |
| Samsung Galaxy S26 Ultra (phone, Android 17) | Pass, with a note (2) | Pass | Pass | Pass |
| OnePlus 10 Pro (phone, Android 16) | Pass on both routes (3) | Pass | Pass | Not tested |

1. This tablet's USB host loses reads on the interface's input, so the engine paces the output from the system clock.
   The sound is clean, with a rare short hiccup every 15 to 60 s. That is the tablet's USB limit; it also happens
   through Android's own audio.
2. The interface's input stopped answering after about a second, and the engine paced itself. Newer versions start
   with short input transfers, and on this phone the input keeps answering.
3. Through Android's audio and through the engine. The engine asks for Android's USB permission window the first time.
4. On version 0.1.1011, plugged in directly and powered by the tablet: a whole song heard clean on the engine, with the
   interface's own clock; stop and the cable came back on their own. On the other devices, on versions 0.1.1038 to 0.1.1066.
5. On version 0.1.1066 the tablet's USB delivered only 96 to 99.5% of the packets to this 18-channel desk, and the
   sound stuttered, on both routes. It's the tablet's limit: the app measures it when the interface opens and warns
   that using it is at your own risk.

Not tested: the cells above that say so, other interfaces, and a plain USB headset (supported, but not in this matrix).

## How light it is
Measured on real devices for one minute per screen (CPU as a share of **one** core; all four have 8 cores):

| Screen | TCL NXTPAPER 11 Plus | Galaxy S26 Ultra | Chuwi Hi10 XPro | OnePlus 10 Pro |
|---|---|---|---|---|
| Song list, idle | 0.4% · 112 MB | 0.1% · 132 MB | 0.1% · 68 MB | 0.1% · 32 MB |
| Stage, idle | 0.6% · 143 MB | 0.2% · 131 MB | 0.4% · 73 MB | 0.4% · 39 MB |
| Stage, turning a page every 3 s | 3.9% · 132 MB | 5.4% · 126 MB | 5.1% · 74 MB | 14.7% · 38 MB |
| Stage, following the band | — | 0.2% · 253 MB | 8.8% · 114 MB | — |
| Leading the band for 5 minutes (9 songs, a page every 2 s) | 5.0% · 194 MB | 0.8% · 186 MB (following) | 2.4% · 124 MB (following) | — |
| Sending a setlist to the band | 2.6% · 198 MB | 0.0% · 160 MB (receiving) | — | — |
| Backing track on a USB interface (USB Audio 2.0 mixer) | 5.1%¹ · 160 MB | 6.7% · 140 MB | 29.3% · 80 MB | 31.3% · 42 MB |
| Backing track on a USB interface (USB Audio 1.0 card) | 3.0%¹ · 101 MB | 3.7% · 86 MB | 16.9% · 83 MB | 22.2% · 41 MB |
| Player on, nothing playing (the USB stream closes after 10 s) | 0.4% · 142 MB | 0.1% · 112 MB | 0.1% · 96 MB | 0.4% · 46 MB |
| Backing track playing on the device output | 17.1% · 147 MB | 3.9% · 140 MB | 13.9% · 71 MB | 18.9% · 44 MB |
| Band settings, idle | 0.2% · 175 MB | 0.2% · 145 MB | 0.3% · 75 MB | 0.6% · 41 MB |

Even playing a track, Stagebook uses less than 4% of the whole processor (the worst case measured, 31.3% of one core, is 3.9% of eight). Essential mode barely changes these numbers:
its gain is a simpler screen.

¹ On the TCL, Android keeps six of its eight cores at low frequency even with a good battery (71% and 70% when measured), so the raw reading is inflated (19.5% and 10.0%). These two numbers count only the two cores that are available, weighted by their frequency, as one core at full speed. With the player on and an interface plugged in but nothing playing, the engine keeps the USB stream alive with silence and uses about 19% of one core on the OnePlus.

## Use with AI assistants (MCP)
Stagebook has an [MCP server](mcp/README.md) that lets Claude Code, Claude Desktop, Cursor, VS Code, Gemini CLI, Codex
and other AI assistants read and edit your library: "build a 15-song setlist for Saturday", "move every song by this
artist down a half tone", "check the chords of this song", "import this page". It runs on your computer and talks to the
tablet over your Wi-Fi through **Settings → Edit on the computer**; you approve the computer once on the tablet.
It's on npm as [`stagebook-mcp`](https://www.npmjs.com/package/stagebook-mcp): with [Node.js](https://nodejs.org) on
the computer, Claude Code connects with `claude mcp add stagebook -- npx -y stagebook-mcp`. Setup for each assistant:
[mcp/README.md](mcp/README.md).

## Install
Stagebook isn't on the Play Store; it's only here, for free. Needs Android 9 or newer.

1. On the phone or tablet, open the [latest release](../../releases/latest) and download `stagebook.apk`.
2. Open the downloaded file. The first time, Android asks to allow installing apps from that source (the browser or
   the files app): allow it, go back and tap **Install**. If Play Protect says it doesn't know the app, choose to
   install anyway.
3. That's it. From then on the app tells you when a new version is out and updates itself; on the first update
   Android asks for the same permission, this time for Stagebook. On Android 12 or newer, the next ones go in
   without asking again and the app restarts to finish. On Android 11 or older, Android asks to confirm every version.
   If the server fails for a moment, checking and downloading try again by themselves.

**Why not the Play Store?** The app imports lyrics and chords from websites, and the store's rules on copyrighted
content leave this kind of app open to being pulled at any time. It also updates itself straight from these releases,
which the store doesn't allow. This way new versions come out without waiting for a review, and the code stays open
for anyone to check.

## Support
Stagebook is free and ad-free. If it helps your rehearsals and gigs, how about buying me a coffee on
[Buy Me a Coffee](https://buymeacoffee.com/julianoaffonso)? Any amount helps keep the app going.

## Translate
The app ships in English and Brazilian Portuguese. Do you play and speak another language? The how-to is in
[docs/TRANSLATING.md](docs/TRANSLATING.md). No coding needed. Other languages come from the community, by pull request
here on GitHub.

## For developers
Kotlin, Jetpack Compose and an audio engine in C. `./gradlew assembleDebug` builds and `./gradlew testDebugUnitTest`
runs the tests (Java 21 and the Android NDK). Releases are published by GitHub Actions. Everyone here follows the
[Code of Conduct](CODE_OF_CONDUCT.md). Found a security problem? See the [security policy](SECURITY.md).

### Pull requests
Pull requests go to `main`. They pass these checks:
- **Check:** build, unit tests and lint, and the release notes (`tools/release_notes.py`).
- **Help:** a change people notice comes with the help in both languages, a changed screen with new screenshots
  (`tools/help_check.py`).
- **Privacy:** no session links, credentials or personal data (`tools/privacy-check.sh`).
- **Engine simulator** (only when the audio engine changes) and **MCP check** (only when `mcp/` changes).

Each commit that changes something people notice ends with `Release-Note: English sentence · Frase em português`,
short and in the musician's words; one nobody notices ends with `Release-Note: none`. When the help or the screenshots
don't need to change, say so on a line of its own: `Help: none` or `Screens: none`. When a later commit changes again
something an earlier one already described in the same pull request, it carries `Release-Note-Replaces: <commit>`, and
only its own sentence goes into the version. The Check lists every sentence of the pull request, to read them against
what it finally does.

### Hotfix
A bug in a published version that people can't wait for (an update that doesn't install, a crash on stage) doesn't wait
for the work on `dev`:
1. Branch `hotfix/<name>` from `main` (the last release) with only the fix and its test. Its commit carries a
   `Release-Note` that tells people what to do, if anything (it shows in the update window).
2. Open a pull request into `main`. The required check is **Check** (about 7 minutes); the simulator isn't required.
3. Merge it and run **Actions → Release** on `main` with "publish" ticked. The fix is out in about 10 minutes.
4. Bring `main` back into `dev`, where the same fix is committed too.

A published version with a bug that blocks people (for example, updating from the app) is deleted from Releases, tag
included, so nobody new downloads it, and a pinned issue in both languages explains who is affected and how to get out
of it. The next release counts its notes from the version before, so nobody misses a change.

### Engine simulator
`tools/engine-sim/run.sh` runs the audio engine on a Mac with a fake USB interface and compares its sound with the
version validated on a real mixer. It also tests drops, the clock plans and load. In Actions it runs on synthetic
tracks (real ones never go to the repository) and isn't a required check. When even the fourth open is judged noisy,
the runner is marked as too noisy and the four plan A tests show as skipped; the plan B tests run anywhere. It doesn't
replace the test on a real mixer.

### Notices on Telegram
The maintainer can get a Telegram message when a release or the MCP server is published or fails, when a check
fails on a pull request or on `main`, when the help site is down, on a security alert, and when someone else opens or comments on an issue, pull
request or discussion (plus a daily summary, only when there is news). GitHub doesn't start workflows on security
alerts, so those and the help site are checked by the scheduled runs, three times a day, not the moment they happen.
To set it up on a fork:
1. In Telegram, talk to `@BotFather`, send `/newbot` and copy the token. Send any message to the new bot.
2. Get your chat id (for example from `https://api.telegram.org/bot<token>/getUpdates`, in `message.chat.id`).
3. In the repository, Settings → Secrets and variables → Actions, add the secrets `TELEGRAM_BOT_TOKEN` and
   `TELEGRAM_CHAT_ID`. Optional: the secret `SECURITY_ALERTS_TOKEN` (a token that can read Dependabot alerts) and the
   variable `SITE_URL` (the help site to watch).

Without the secrets the workflow runs and skips. It never checks out or runs code from a pull request; titles and
texts are only data, sent as plain text.

### USB engine pacing
- **Plan A:** follow the interface's input clock (implicit feedback), so output and interface never drift apart.
- **Plan B:** pace the output from the monotonic clock when the input is missing or can't be trusted.
- **The choice is made at open, before any sound:** the output stays silent while the input is read for 8 windows of
  0.25 s (at most 3 s). A clock off by more than 0.3% on average, or readings so noisy that one second can be off by
  more than 0.6%, means plan B for as long as the stream stays open. A clock that is right but noisy keeps plan A,
  with a tolerance fitted to the device's noise.
- **Plan A is left only for a real failure:** 3 one-second windows more than 1% off, no answer from the input for
  0.3 s, or the input count and what was sent 100 ms apart. A stream that left plan A this way reads the input again
  every 60 s and goes back after 5 s of good time; a stream judged off at open stays on plan B.
- Output URBs carry 8 packets (1 ms), up to 32 in the queue. Input URBs start with 16 packets; if the check at open
  loses more than 3% of the packets, they go to 128 and the clock is checked again.

## License
Source available under **PolyForm Shield 1.0.0** ([LICENSE.md](LICENSE.md)). Use it as you like, paid gigs included,
change it and share it. You just can't use the code to make a competing app.

`Required Notice: Copyright Stagebook (https://stagebook.app.br)`

Third-party audio decoders, in `app/src/main/cpp/third_party`, each with its license in the file itself: stb_vorbis
(public domain or MIT), dr_mp3 and dr_wav (public domain or MIT-0).
