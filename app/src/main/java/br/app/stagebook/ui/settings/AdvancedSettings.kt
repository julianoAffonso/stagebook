package br.app.stagebook.ui.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.R
import br.app.stagebook.data.device.DeviceTweak
import br.app.stagebook.data.device.DeviceTweaks
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface
import br.app.stagebook.ui.components.SettingsCard
import br.app.stagebook.ui.components.StatusNote
import br.app.stagebook.ui.components.StatusTone
import br.app.stagebook.ui.components.SwitchRow
import br.app.stagebook.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings → Advanced: the Android settings as they are on the device now. */
@Immutable
data class AdvancedUi(
    val canWrite: Boolean = false,
    val on: Map<DeviceTweak, Boolean> = emptyMap(),   // only the tweaks this device has
    val batteryFree: Boolean = true,
)

@HiltViewModel
class AdvancedViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    private val _ui = MutableStateFlow(AdvancedUi())
    val ui: StateFlow<AdvancedUi> = _ui.asStateFlow()

    /** Reads the device again: something may have changed in Android's settings meanwhile. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { _ui.value = read() }
    }

    fun set(tweak: DeviceTweak, on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            DeviceTweaks.set(context, tweak, on)
            _ui.value = read()
        }
    }

    /** Android's request to free Stagebook from battery saving (one tap on Allow). */
    @SuppressLint("BatteryLife")   // a music player that must keep playing with the screen off; not on a store that forbids it
    fun batteryRequest(): Intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    private fun read() = AdvancedUi(
        canWrite = DeviceTweaks.canWrite(context),
        on = DeviceTweak.entries.filter { DeviceTweaks.available(context, it) }.associateWith { DeviceTweaks.isOn(context, it) },
        batteryFree = DeviceTweaks.batteryFree(context),
    )
}

@Composable
fun AdvancedDetail(ui: AdvancedUi, onTweak: (DeviceTweak, Boolean) -> Unit, onBattery: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (!ui.canWrite) PermissionCard()
        SettingsCard(stringResource(R.string.advanced_play), help = stringResource(R.string.advanced_play_help)) {
            // freeing the app from battery saving only Android does, with the user's tap: a button, not a switch
            StatusNote(
                stringResource(if (ui.batteryFree) R.string.advanced_battery_free else R.string.advanced_battery_restricted),
                if (ui.batteryFree) StatusTone.OK else StatusTone.PROBLEM,
            ) {
                if (!ui.batteryFree) ActionButton(stringResource(R.string.advanced_battery_allow), onClick = onBattery, icon = AppIcons.Eco, tone = ButtonTone.Tonal)
            }
            Tweaks(ui, DeviceTweak.Group.PLAY, onTweak)
        }
        SettingsCard(stringResource(R.string.advanced_battery)) { Tweaks(ui, DeviceTweak.Group.BATTERY, onTweak) }
        SettingsCard(stringResource(R.string.advanced_computer)) { Tweaks(ui, DeviceTweak.Group.COMPUTER, onTweak) }
    }
}

@Composable
private fun Tweaks(ui: AdvancedUi, group: DeviceTweak.Group, onTweak: (DeviceTweak, Boolean) -> Unit) {
    DeviceTweak.entries.filter { it.group == group && it in ui.on }.forEach { t ->
        SwitchRow(
            stringResource(t.label),
            ui.on[t] == true,
            { onTweak(t, it) },
            detail = stringResource(t.detail),
            enabled = ui.canWrite,   // without the permission Android refuses the change: shown, not switchable
        )
    }
}

// without the permission nothing here can be written: say how to grant it, once, from a computer
@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    SettingsCard(stringResource(R.string.advanced_permission)) {
        StatusNote(stringResource(R.string.advanced_permission_text), StatusTone.PROBLEM) {
            Text(DeviceTweaks.GRANT, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            ActionButton(stringResource(R.string.advanced_permission_copy), onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Stagebook", DeviceTweaks.GRANT))
            }, icon = AppIcons.Copy, tone = ButtonTone.Tonal)
        }
    }
}

@get:StringRes
private val DeviceTweak.label: Int
    get() = when (this) {
        DeviceTweak.CHARGE_MODES -> R.string.tweak_charge
        DeviceTweak.USB_ROUTING -> R.string.tweak_usb_routing
        DeviceTweak.BATTERY_MANAGER -> R.string.tweak_battery_manager
        DeviceTweak.ANIMATIONS -> R.string.tweak_animations
        DeviceTweak.SCREEN_TIMEOUT -> R.string.tweak_screen_timeout
        DeviceTweak.NO_BACKGROUND_SCANS -> R.string.tweak_scans
        DeviceTweak.NO_MOBILE_DATA_ALWAYS_ON -> R.string.tweak_mobile_data
        DeviceTweak.DEBUGGING -> R.string.tweak_debugging
    }

@get:StringRes
private val DeviceTweak.detail: Int
    get() = when (this) {
        DeviceTweak.CHARGE_MODES -> R.string.tweak_charge_detail
        DeviceTweak.USB_ROUTING -> R.string.tweak_usb_routing_detail
        DeviceTweak.BATTERY_MANAGER -> R.string.tweak_battery_manager_detail
        DeviceTweak.ANIMATIONS -> R.string.tweak_animations_detail
        DeviceTweak.SCREEN_TIMEOUT -> R.string.tweak_screen_timeout_detail
        DeviceTweak.NO_BACKGROUND_SCANS -> R.string.tweak_scans_detail
        DeviceTweak.NO_MOBILE_DATA_ALWAYS_ON -> R.string.tweak_mobile_data_detail
        DeviceTweak.DEBUGGING -> R.string.tweak_debugging_detail
    }

/** A device with every tweak at its default (as a TCL with the permission looks on first open). */
internal val sampleAdvanced = AdvancedUi(canWrite = true, on = DeviceTweak.entries.associateWith { it.default }, batteryFree = false)

@LightPreview
@Composable
private fun AdvancedLightPreview() = PreviewSurface(dark = false) { AdvancedDetail(sampleAdvanced, { _, _ -> }, {}) }

@DarkPreview
@Composable
private fun AdvancedDarkPreview() = PreviewSurface(dark = true) { AdvancedDetail(sampleAdvanced.copy(canWrite = false), { _, _ -> }, {}) }
