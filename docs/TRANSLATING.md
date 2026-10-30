# Translating Stagebook

**English** · [Português (Brasil)](TRANSLATING.pt-BR.md)

Stagebook is made by a musician for musicians. If you play and speak another language, your translation puts the app
on more stages. No coding needed: it's one text file.

## How to
1. Copy `app/src/main/res/values/strings.xml` (English, the base) to `app/src/main/res/values-<code>/strings.xml`.
   The code is the language's: `es` (Spanish), `fr` (French), `de` (German). For a variant, `es-rMX`, `fr-rCA`.
2. Translate only the text between the tags. Leave `name="…"`, `%1$s` and `%1$d` (a song name, a number…) and `\n`
   (line break) as they are.
3. Open a pull request. Half done is fine: the language shows up in the app once it's complete, and someone else can
   pick up where you stopped.

No GitHub? Open an issue with the file attached and we'll put it in place.

## How to write
Think of a bandmate explaining at rehearsal, not a manual.

- **Short sentences.** One idea each. Use a period instead of colons, dashes or brackets.
- **Everyday words.** "Delete", not "permanently remove". Keep the words musicians already use: setlist, backing
  track, BPM, capo, chords.
- **Talk to the person.** "You", plain, no "please" and no "the user".
- **Buttons start with a verb** and have one to three words: "Save", "Scan QR", "Ask the band".
- **Explanations in two sentences at most.** If it needs more, the text is trying to do too much.
- **No ad talk** ("amazing", "powerful", "experience").
- **Don't translate names:** Stagebook, Now Playing, OBS, ChordPro.
- **Lyrics sections** (Chorus, Verse, Bridge…) use the word musicians in that language write on their charts.

## The editor on the computer
The texts starting with `web_` are the web editor's page (Settings → Edit on the computer). They are in the same
`strings.xml` and the page follows the app's language, so translating the file translates the editor too.
