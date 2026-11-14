package br.app.stagebook.ui.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import br.app.stagebook.R
import br.app.stagebook.data.device.DeviceTweak
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageMode
import br.app.stagebook.data.model.essential
import br.app.stagebook.domain.TextKey

/** What a control is: the guard test counts the switches and the sliders the screen draws against the index. */
internal enum class ItemKind { SWITCH, SLIDER, OTHER }

/** What the visible items depend on: the same things the cards check when they draw (profile, parts, device). */
internal data class IndexCtx(
    val look: Look = Look(),
    val midiSupported: Boolean = false,
    val advanced: AdvancedUi = AdvancedUi(),
    val nationalTags: Boolean = false,   // Portuguese only (Tags card)
)

/** Why an item isn't on screen now, and the item to open to turn it on (null: open the item itself). */
internal class Hide(@StringRes val reason: Int, val opens: String? = null)

/**
 * One searchable thing in Settings: a control (or a card with no control), the card it lives in and its group. The texts
 * are string resources, so the index is always in the app's current language. [hide] says why the item is not on
 * screen with [IndexCtx] (null: it is). The screen tags each control with the same [id] (`Modifier.settingsItem`), and
 * a guard test holds the two together.
 */
internal class SettingsItem(
    val id: String,
    val group: SettingsSection,
    @StringRes val card: Int,
    @StringRes val title: Int,
    @StringRes val detail: Int? = null,
    @StringRes val keywords: Int? = null,
    val kind: ItemKind = ItemKind.OTHER,
    val hide: (IndexCtx) -> Hide? = { null },
) {
    fun visible(ctx: IndexCtx) = hide(ctx) == null
}

private val ItemKind.slider get() = this == ItemKind.SLIDER

/** Every item, hidden or not (the search shows the hidden ones dimmed, with the reason). */
internal fun allSettingsItems(): List<SettingsItem> = allItems + tweakItems

/** The items that are on screen with [ctx]: what an essential mode, a profile or a device leaves out is not here. */
internal fun settingsItems(ctx: IndexCtx): List<SettingsItem> = allSettingsItems().filter { it.visible(ctx) }

internal fun settingsItemById(id: String): SettingsItem? = allSettingsItems().firstOrNull { it.id == id }

private fun item(
    id: String, group: SettingsSection, card: Int, title: Int, detail: Int? = null, kw: Int? = null, kind: ItemKind = ItemKind.OTHER,
    hide: (IndexCtx) -> Hide? = { null },
) = SettingsItem(id, group, card, title, detail, kw, kind, hide)

private val allItems: List<SettingsItem> by lazy {
    val sw = ItemKind.SWITCH
    val sl = ItemKind.SLIDER
    val L = SettingsSection.LYRICS
    val P = SettingsSection.PAGE
    val C = SettingsSection.CHORDS
    val M = SettingsSection.PLAYER
    val B = SettingsSection.LIBRARY
    val N = SettingsSection.BAND
    val D = SettingsSection.DEVICE
    val A = SettingsSection.ABOUT
    // the reasons, each with the item that turns the thing back on
    val noChords: (IndexCtx) -> Hide? = { if (!it.look.chords) Hide(R.string.hide_chords, "profile") else null }
    val noExtras: (IndexCtx) -> Hide? = { if (!it.look.extrasOn) Hide(R.string.hide_extras, "essential_extras") else null }
    val noPlayer: (IndexCtx) -> Hide? = { if (!it.look.playerMode) Hide(R.string.hide_player, "essential_player") else null }
    val noMidi: (IndexCtx) -> Hide? = { if (!it.midiSupported) Hide(R.string.hide_midi) else null }
    val noMetro: (IndexCtx) -> Hide? = { noExtras(it) ?: if (!it.look.metroShown) Hide(R.string.hide_metro_shown, "metro_shown") else null }
    val noClick: (IndexCtx) -> Hide? = { noMetro(it) ?: if (!it.look.metroSound) Hide(R.string.hide_metro_sound, "metro_sound") else null }
    val noSections: (IndexCtx) -> Hide? = { if (!it.look.showSections) Hide(R.string.hide_sections, "show_sections") else null }
    val noLan: (IndexCtx) -> Hide? = { if (!it.look.bandOn) Hide(R.string.hide_band, "essential_band") else null }
    val noBandOrNet: (IndexCtx) -> Hide? = { if (!it.look.bandOn && !it.look.internetOn) Hide(R.string.hide_band_or_internet, "essential_band") else null }
    val noInternet: (IndexCtx) -> Hide? = { if (!it.look.internetOn) Hide(R.string.hide_internet, "essential_internet") else null }
    listOf(
        // Lyrics and chords
        item("font_size", L, R.string.settings_lyrics_card, R.string.settings_font_size, R.string.settings_font_help, R.string.kw_size, sl),
        item("font_lyrics", L, R.string.settings_lyrics_card, R.string.settings_font_lyrics, kw = R.string.kw_font),
        item("bold", L, R.string.settings_lyrics_card, R.string.settings_bold, kw = R.string.kw_bold, kind = sw),
        item("text_color", L, R.string.settings_lyrics_card, R.string.settings_text_color, kw = R.string.kw_color),
        item("highlight_color", L, R.string.settings_lyrics_card, R.string.settings_highlight_color, kw = R.string.kw_color),
        item("background", L, R.string.settings_lyrics_card, R.string.settings_background, kw = R.string.kw_background),
        item("profile", L, R.string.settings_chords_card, R.string.settings_chords_show, R.string.settings_chords_show_help, R.string.kw_profile),
        item("chords_size", L, R.string.settings_chords_card, R.string.settings_chords_size, kw = R.string.kw_size, kind = sl, hide = noChords),
        item("font_chords", L, R.string.settings_chords_card, R.string.settings_font_chords, kw = R.string.kw_font, hide = noChords),
        item("chords_color", L, R.string.settings_chords_card, R.string.settings_chords_color, kw = R.string.kw_color, hide = noChords),
        item("show_sections", L, R.string.settings_sections_look, R.string.settings_show_sections, R.string.settings_sections_help, R.string.kw_sections, sw),
        item("sections_size", L, R.string.settings_sections_look, R.string.settings_sections_size, kw = R.string.kw_size, kind = sl, hide = noSections),
        item("font_sections", L, R.string.settings_sections_look, R.string.settings_font_sections, kw = R.string.kw_font, hide = noSections),
        item("section_color", L, R.string.settings_sections_look, R.string.settings_section_color, kw = R.string.kw_color, hide = noSections),
        item("translate_sections", L, R.string.settings_sections_look, R.string.settings_translate_sections, R.string.settings_translate_sections_help, R.string.kw_sections, sw),

        // Turn the page
        item("stage_mode", P, R.string.settings_page_mode, R.string.settings_stage_mode, R.string.settings_stage_mode_help, R.string.kw_mode),
        item("auto_scroll", P, R.string.settings_page_mode, R.string.settings_auto_scroll, R.string.settings_auto_scroll_help, R.string.kw_scroll, sl) {
            if (it.look.stageMode != StageMode.SCROLL) Hide(R.string.hide_scroll, "stage_mode") else null
        },
        item("columns", P, R.string.settings_page_mode, R.string.settings_columns, R.string.settings_columns_help, R.string.kw_columns) {
            if (it.look.stageMode != StageMode.PAGES) Hide(R.string.hide_pages, "stage_mode") else null
        },
        item("pedal_connect", P, R.string.settings_pedal, R.string.settings_pedal_connect, R.string.settings_pedal_nothing, R.string.kw_pedal),
        item("pedal_common", P, R.string.settings_pedal, R.string.settings_pedal_add_common, R.string.settings_pedal_kinds_help, R.string.kw_pedal),
        item("pedal_midi", P, R.string.settings_pedal, R.string.settings_pedal_add_midi, R.string.settings_pedal_kinds_help, R.string.kw_midi, hide = noMidi),
        item("midi_usb", P, R.string.settings_pedal, R.string.settings_midi_usb_switch, R.string.settings_midi_usb_help, R.string.kw_midi, sw, noMidi),
        item("pedal_buttons", P, R.string.settings_pedal, R.string.settings_pedal_buttons, R.string.settings_pedal_press, R.string.kw_pedal),
        item("midi_out", P, R.string.settings_midi_out, R.string.settings_midi_out_device, R.string.settings_midi_out_help, R.string.kw_midi) { noMidi(it) ?: noExtras(it) },
        item("keep_screen_on", P, R.string.settings_during_show, R.string.settings_keep_screen_on, R.string.settings_keep_screen_on_help, R.string.kw_screen, sw),
        item("stage_dnd", P, R.string.settings_during_show, R.string.settings_stage_dnd, R.string.settings_stage_dnd_detail, R.string.kw_dnd, sw),
        item("track_stops", P, R.string.settings_during_show, R.string.settings_track_stops, R.string.settings_track_stops_detail, R.string.kw_track, sw, noPlayer),

        // Chords
        item("instrument", C, R.string.settings_chord_shapes, R.string.settings_instrument, R.string.settings_instrument_help, R.string.kw_instrument, hide = noChords),
        item("chord_tap", C, R.string.settings_chord_shapes, R.string.settings_chord_tap, kw = R.string.kw_diagram, kind = sw, hide = noChords),
        item("chord_play", C, R.string.settings_chord_shapes, R.string.settings_chord_play, kw = R.string.kw_sound) { noChords(it) ?: noExtras(it) },
        item("chord_list", C, R.string.settings_chord_shapes, R.string.settings_chord_list, R.string.settings_chord_list_help, R.string.kw_list, hide = noChords),

        // Player and metronome
        item("metro_shown", M, R.string.metro_title, R.string.metro_shown, R.string.metro_help, R.string.kw_metro, sw, noExtras),
        item("metro_count", M, R.string.metro_title, R.string.metro_count, kw = R.string.kw_metro, kind = sw, hide = noMetro),
        item("metro_sound", M, R.string.metro_title, R.string.metro_sound, kw = R.string.kw_metro, kind = sw, hide = noMetro),
        item("metro_volume", M, R.string.metro_title, R.string.metro_volume, kw = R.string.kw_volume, kind = sl, hide = noClick),
        item("metro_out", M, R.string.metro_title, R.string.metro_out, kw = R.string.kw_output) { noClick(it) ?: noPlayer(it) },
        item("metro_test", M, R.string.metro_title, R.string.settings_metronome_test, kw = R.string.kw_metro, hide = noClick),
        item("metro_glow", M, R.string.metro_title, R.string.metro_glow, kw = R.string.kw_glow, kind = sw, hide = noMetro),
        item("metro_glow_color", M, R.string.metro_title, R.string.metro_glow_color, kw = R.string.kw_glow) {
            noMetro(it) ?: if (!it.look.metroGlow) Hide(R.string.hide_metro_glow, "metro_glow") else null
        },
        item("usb_via_android", M, R.string.settings_usb_card, R.string.usb_via_android, R.string.usb_via_android_detail, R.string.kw_usb, sw, noPlayer),
        item("battery_allow", M, R.string.advanced_play, R.string.advanced_battery_allow, R.string.advanced_play_help, R.string.kw_battery) {
            noPlayer(it) ?: if (it.advanced.batteryFree) Hide(R.string.hide_battery_free) else null
        },

        // Import and backup
        item("library", B, R.string.library_card, R.string.library_card, kw = R.string.kw_library),
        item("sites_chords", B, R.string.settings_chord_sites, R.string.settings_chord_sites, R.string.settings_sites_help, R.string.kw_sites) {
            if (!it.look.chords && !it.look.autoProfile) Hide(R.string.hide_sites_chords, "profile") else null
        },
        item("sites_lyrics", B, R.string.settings_lyric_sites, R.string.settings_lyric_sites, R.string.settings_sites_help, R.string.kw_sites) {
            if (it.look.chords && !it.look.autoProfile) Hide(R.string.hide_sites_lyrics, "profile") else null
        },
        item("tags", B, R.string.tags_title, R.string.tags_auto, R.string.tags_auto_info, R.string.kw_tags) {
            if (!it.nationalTags) Hide(R.string.hide_pt, "language") else null
        },
        item("normalize", B, R.string.settings_normalize_card, R.string.settings_normalize, R.string.settings_normalize_help, R.string.kw_normalize),
        item("backup_library", B, R.string.backup_save_card, R.string.backup_part_library, R.string.backup_part_library_detail, R.string.kw_backup, sw),
        item("backup_tracks", B, R.string.backup_save_card, R.string.backup_part_tracks, R.string.backup_part_tracks_detail, R.string.kw_backup, sw),
        item("backup_settings", B, R.string.backup_save_card, R.string.backup_part_settings, R.string.backup_part_settings_detail, R.string.kw_backup, sw),
        item("backup_make", B, R.string.backup_save_card, R.string.backup_make, R.string.backup_save_info, R.string.kw_backup),
        item("backup_open", B, R.string.backup_open_card, R.string.backup_open, R.string.backup_open_info, R.string.kw_restore),
        item("share", B, R.string.backup_share_card, R.string.backup_share_card, R.string.backup_share_info, R.string.kw_share),
        item("reset", B, R.string.reset_zone, R.string.reset_title, R.string.reset_info, R.string.kw_reset),

        // Band and computer
        item("band_rename", N, R.string.band_this_device, R.string.band_rename, R.string.band_help, R.string.kw_band, hide = noBandOrNet),
        item("band_nearby", N, R.string.band_nearby, R.string.band_nearby, kw = R.string.kw_band, hide = noLan),
        item("band_members", N, R.string.band_members, R.string.band_members, kw = R.string.kw_band, hide = noBandOrNet),
        item("band_invite", N, R.string.band_members, R.string.band_invite, R.string.band_invite_detail, R.string.kw_internet, hide = noInternet),
        item("announce", N, R.string.announce_title, R.string.announce_switch, R.string.announce_help, R.string.kw_announce, sw, noExtras),
        item("web_editor", N, R.string.settings_web_editor_card, R.string.settings_web_editor_switch, R.string.settings_web_editor_switch_detail, R.string.kw_computer, sw) {
            if (it.look.essential) Hide(R.string.hide_essential, "essential") else null
        },
        item("web_devices", N, R.string.settings_web_editor_devices, R.string.settings_web_editor_devices, kw = R.string.kw_computer) {
            if (it.look.essential) Hide(R.string.hide_essential, "essential") else null
        },

        // Device
        item("essential", D, R.string.essential_title, R.string.essential_switch, R.string.essential_keeps, R.string.kw_essential, sw),
        item("essential_band", D, R.string.essential_parts, R.string.essential_band, R.string.essential_band_detail, R.string.kw_essential, sw),
        item("essential_internet", D, R.string.essential_parts, R.string.essential_internet, R.string.essential_internet_detail, R.string.kw_essential, sw),
        item("essential_player", D, R.string.essential_parts, R.string.essential_player, R.string.essential_player_detail, R.string.kw_essential, sw),
        item("essential_extras", D, R.string.essential_parts, R.string.essential_extras, R.string.essential_extras_detail, R.string.kw_essential, sw),
        item("language", D, R.string.settings_language, R.string.settings_language, R.string.settings_language_help, R.string.kw_language),
        item("permission", D, R.string.advanced_permission, R.string.advanced_permission_copy, R.string.advanced_permission_text, R.string.kw_adb) {
            if (it.advanced.canWrite) Hide(R.string.hide_has_permission) else null
        },

        // About (apart from the groups)
        item("about_help", A, R.string.about_help, R.string.about_help_open, R.string.about_help_text, R.string.kw_help),
        item("about_update", A, R.string.settings_update, R.string.settings_update, R.string.settings_update_help, R.string.kw_update, hide = noInternet),
        item("about_privacy", A, R.string.about_privacy, R.string.about_privacy, R.string.about_privacy_text, R.string.kw_privacy),
        item("about_code", A, R.string.about_open_source, R.string.about_see_code, R.string.about_open_source_text, R.string.kw_code),
        item("about_support", A, R.string.about_support, R.string.about_support, R.string.about_support_text, R.string.kw_support),
    )
}

/** The Android settings, from [DeviceTweak] itself: a new one is searchable without anyone remembering to add it. */
private val tweakItems: List<SettingsItem> by lazy {
    DeviceTweak.entries.filter { it.shown }.map { t ->
        val (group, card) = when {
            t.group == DeviceTweak.Group.PLAY -> SettingsSection.PLAYER to R.string.advanced_play
            t == DeviceTweak.SCREEN_TIMEOUT -> SettingsSection.PAGE to R.string.settings_during_show
            t.group == DeviceTweak.Group.BATTERY -> SettingsSection.DEVICE to R.string.advanced_battery
            else -> SettingsSection.DEVICE to R.string.advanced_computer
        }
        SettingsItem(tweakId(t), group, card, t.label, t.detail, R.string.kw_android, ItemKind.SWITCH) { c ->
            when {
                t.group == DeviceTweak.Group.PLAY && !c.look.playerMode -> Hide(R.string.hide_player, "essential_player")
                t !in c.advanced.on -> Hide(R.string.hide_device)
                else -> null
            }
        }
    }
}

/** The id a tweak's switch carries on the screen and in the index. */
internal fun tweakId(t: DeviceTweak) = "tweak_${t.name.lowercase()}"

/** An item as the person reads it, in the language the app is showing now. */
internal class SettingsHit(
    val item: SettingsItem, val title: String, val card: String, val groupName: String, val detail: String?, val score: Int,
    val hide: Hide? = null, val reason: String? = null,
) {
    /** Only the explanation matched: the result says so. */
    val inHelp get() = score == HELP
}

private const val SENTINEL = -7777
private const val HELP = 3

/** The texts of [item]; a slider's title loses the number ("Font size: 72" is found as "Font size"). */
private fun Resources.read(item: SettingsItem): SettingsHit {
    val raw = if (item.kind.slider) getString(item.title, SENTINEL) else getString(item.title)
    val title = raw.substringBefore(SENTINEL.toString()).trim().trimEnd(':', ' ').ifEmpty { raw }
    return SettingsHit(item, title, getString(item.card), getString(item.group.title), item.detail?.let(::getString), 0)
}

/** What a search reads of one item, normalized once per language: a keystroke only compares against it. */
private class Entry(
    val read: SettingsHit, val name: String, val nameWords: List<String>, val kw: String, val where: String, val help: String,
)

/**
 * The settings' texts read and normalized once for a language ([Resources]); [search] then only compares and checks
 * what is hidden. Built apart from the query so a keystroke does no string reading or normalizing.
 */
internal class SettingsSearchIndex(private val resources: Resources) {
    private val entries = allSettingsItems().map { item ->
        val read = resources.read(item)
        val name = TextKey.of(read.title)
        Entry(
            read, name, name.split(' '), TextKey.of(item.keywords?.let(resources::getString).orEmpty()),
            TextKey.of(read.card + " " + read.groupName), TextKey.of(read.detail.orEmpty()),
        )
    }
    private val reasons = HashMap<Int, String>()

    /**
     * The items that answer [query], hidden ones too (they come dimmed, with the reason). Case and accents don't matter
     * ([TextKey]); every word of the query has to hit, in any order, in the name (best), the synonyms, the card or group,
     * or the explanation (last). An item ranks by the worst place its words needed; ties keep the visible ones first.
     */
    fun search(query: String, ctx: IndexCtx): List<SettingsHit> {
        val words = TextKey.of(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            var score = 0
            for (w in words) {
                val rank = when {
                    e.nameWords.any { it.startsWith(w) } -> 0
                    w in e.name -> 0
                    w in e.kw -> 1
                    w in e.where -> 2
                    w in e.help -> HELP
                    else -> return@mapNotNull null
                }
                score = maxOf(score, rank)
            }
            val item = e.read.item
            val hide = item.hide(ctx)
            val r = e.read
            SettingsHit(item, r.title, r.card, r.groupName, r.detail, score, hide, hide?.let { reasons.getOrPut(it.reason) { resources.getString(it.reason) } })
        }.sortedWith(compareBy({ it.score }, { it.hide != null }))
    }
}

/** One-off search (tests, callers without a cached index); the screen keeps a [SettingsSearchIndex] instead. */
internal fun searchSettings(query: String, ctx: IndexCtx, resources: Resources): List<SettingsHit> =
    SettingsSearchIndex(resources).search(query, ctx)
