<p align="center"><img src="docs/logo.png" alt="Stagebook" width="120"></p>

# Stagebook

**English** · [Português (Brasil)](README.pt-BR.md)

[![Release](https://img.shields.io/github/v/release/julianoAffonso/stagebook?label=release)](../../releases/latest)
[![License: PolyForm Shield 1.0.0](https://img.shields.io/badge/license-PolyForm%20Shield%201.0.0-blue)](LICENSE.md)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)](../../releases/latest)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/julianoaffonso)

Lyrics, chords and backing tracks in one app, to play live on Android. Solo or with the band.

**Help, with a picture of every screen:** [docs/help](docs/help/index.html)

## What it does
- **Stage:** full screen, always lit, in pages or scrolling. Turn with a Bluetooth or MIDI pedal (USB and Bluetooth),
  a tap or auto-scroll. Each pedal button can do something else.
- **Chords:** chords above the lyrics (ChordPro), transpose, capo, tablature and each chord's diagram on acoustic or
  electric guitar, ukulele, cavaquinho or keyboard, with sound. It also shows the key's harmonic field.
- **Setlists:** build the show and drag to reorder. Send it to whoever is next to you by QR, far away with 3 words over
  the internet, or to the whole band on the same Wi-Fi.
- **Band:** with "Lead the band", whoever leads the setlist changes songs and everyone's stage follows. Before
  starting, the app says who is missing a song.
- **Backing tracks:** OGG with any number of channels, MP3 or WAV, on a USB audio interface or the device's output.
  With a mixer, per-output routing, EQ and normalization.
- **Metronome**, lyrics and chords import from websites, **Now Playing** for OBS and Resolume
  ([format](docs/now-playing.md)), backup to a `.stagebook` file and an **essential mode** that turns off what you
  won't use.
- No account, no ads, no tracking. Your songs stay on the device. What goes to the band or through the 3 words is
  encrypted.

## USB player
- A **class compliant** USB audio interface (USB Audio Class 2.0, works on a computer without a driver).
- **48 kHz** support.
- A phone or tablet with **USB OTG** and the right cable or adapter.

Interfaces that are only USB Audio 1.0 or only 44.1 kHz don't work yet. Without an interface, the player uses the
device's headphones, Bluetooth or speaker.

## How light it is
Measured on real devices for one minute per screen (CPU as a share of **one** core; all three have 8 cores):

| Screen | TCL NXTPAPER 11 Plus | Galaxy S26 Ultra | Chuwi Hi10 XPro |
|---|---|---|---|
| Song list, idle | 1.4% · 194 MB | 0.1% · 136 MB | 1.5% · 117 MB |
| Stage, idle | 1.4% · 219 MB | 0.3% · 167 MB | — |
| Stage, turning a page every 3 s | 12.7% · 198 MB | 3.8% · 146 MB | — |
| Stage, following the band | — | 0.2% · 253 MB | 8.8% · 114 MB |
| Backing track playing on a USB interface | 15% · 202 MB | — | — |
| Band settings, idle | 3.1% · 205 MB | 7.2% · 186 MB | — |

Even playing a track, Stagebook uses less than 2% of the whole processor. Essential mode barely changes these numbers:
its gain is a simpler screen.

## Install
Download `stagebook.apk` from the [Releases](../../releases). After that the app tells you when a new version is out
and updates itself. Needs Android 9 or newer.

## Support
Stagebook is free and ad-free. If it helps your rehearsals and gigs, how about buying me a coffee on
[Buy Me a Coffee](https://buymeacoffee.com/julianoaffonso)? Any amount helps keep the app going.

## Translate
The app ships in English and Brazilian Portuguese. Do you play and speak another language? The how-to is in
[docs/TRANSLATING.md](docs/TRANSLATING.md). No coding needed.

## For developers
Kotlin, Jetpack Compose and an audio engine in C. `./gradlew assembleDebug` builds and `./gradlew testDebugUnitTest`
runs the tests (Java 21). Releases are published by GitHub Actions. Everyone here follows the
[Code of Conduct](CODE_OF_CONDUCT.md).

## License
Source available under **PolyForm Shield 1.0.0** ([LICENSE.md](LICENSE.md)). Use it as you like, paid gigs included,
change it and share it. You just can't use the code to make a competing app.

`Required Notice: Copyright Stagebook (https://stagebook.app.br)`

Third-party audio decoders, in `app/src/main/cpp/third_party`, each with its license in the file itself: stb_vorbis
(public domain or MIT), dr_mp3 and dr_wav (public domain or MIT-0).
