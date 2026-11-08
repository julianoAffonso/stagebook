package br.app.stagebook.data.device

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import br.app.stagebook.MainActivity
import br.app.stagebook.R

/**
 * Do not disturb while the stage is open (user, 07/11: a notification mid-song can throw a musician). Android 10+: a
 * rule of the app's own, on while the stage is in front and off when it leaves, so the person's own Do not disturb
 * settings are never touched. Starred contacts and repeat callers still ring; media plays (the click and the Android
 * route go through it). Android 9: the device's priority mode, put back as it was. Needs Android's permission
 * (Do not disturb access), asked once on the stage.
 */
object StageQuiet {
    private const val PREFS = "stage_quiet"
    private const val KEY_RULE = "rule"
    private const val KEY_ASKED = "asked"
    private const val KEY_BEFORE = "before"   // Android 9: the filter in place before the stage, to put back
    private val CONDITION: Uri = Uri.parse("condition://br.app.stagebook/stage")

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun granted(c: Context): Boolean = nm(c)?.isNotificationPolicyAccessGranted == true

    /** The first time on stage without the permission: ask (once; Settings can still turn it on later). */
    fun shouldAsk(c: Context): Boolean = !granted(c) && !prefs(c).getBoolean(KEY_ASKED, false)

    fun asked(c: Context) = prefs(c).edit { putBoolean(KEY_ASKED, true) }

    fun accessScreen(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    // what the stage last asked for in this process: the startup clean-up must not undo it (audit 07/11)
    @Volatile private var asked = false

    /**
     * Startup: a rule left on by a process killed on stage is switched off. It runs on another thread, so if the stage
     * already asked for quiet in this process, the stage wins.
     */
    @Synchronized fun clearStale(c: Context) {
        if (!asked) set(c, false)
    }

    /** On (stage in front) or off (stage closed or app out of sight). Without the permission it does nothing. */
    @Synchronized fun set(c: Context, on: Boolean) {
        asked = on
        val nm = nm(c) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) rule(c, nm, on) else priority(c, nm, on)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun rule(c: Context, nm: NotificationManager, on: Boolean) {
        val saved = prefs(c).getString(KEY_RULE, null)
        val id = saved?.takeIf { nm.getAutomaticZenRule(it) != null } ?: run {
            if (!on) return
            nm.addAutomaticZenRule(newRule(c)).also { prefs(c).edit { putString(KEY_RULE, it) } }
        }
        val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
        nm.setAutomaticZenRuleState(id, Condition(CONDITION, c.getString(R.string.settings_stage_dnd), state))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun newRule(c: Context): AutomaticZenRule {
        val policy = ZenPolicy.Builder()
            .allowCalls(ZenPolicy.PEOPLE_TYPE_STARRED)
            .allowRepeatCallers(true)
            .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
            .allowMedia(true)
            .allowAlarms(false)
            .allowReminders(false)
            .allowEvents(false)
            .allowSystem(false)
            .showPeeking(false)
            .showFullScreenIntent(false)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE) }
            .build()
        return AutomaticZenRule(
            c.getString(R.string.settings_stage_dnd), null, ComponentName(c, MainActivity::class.java), CONDITION,
            policy, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true,
        )
    }

    // Android 9 has no rule state from apps: the device's own mode, and what it was goes back afterwards
    private fun priority(c: Context, nm: NotificationManager, on: Boolean) {
        val p = prefs(c)
        if (on) {
            if (!p.contains(KEY_BEFORE)) p.edit { putInt(KEY_BEFORE, nm.currentInterruptionFilter) }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else if (p.contains(KEY_BEFORE)) {
            nm.setInterruptionFilter(p.getInt(KEY_BEFORE, NotificationManager.INTERRUPTION_FILTER_ALL))
            p.edit { remove(KEY_BEFORE) }
        }
    }
}
