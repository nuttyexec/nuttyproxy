package dev.nutty.proxy.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.nutty.proxy.MainActivity
import dev.nutty.proxy.R

/**
 * The ongoing notification — for an always-on agent, this is the primary UI.
 *
 * Most people will see the shade far more often than they open the app, so the
 * design gives it the same rules as the Home status pill:
 *
 *  * **State, count, one action.** Every variant names the state, says how many
 *    connections are live, and offers exactly one verb — Pause, Cancel, Resume,
 *    or Fix. Two competing actions in a shade row is how people tap the wrong one.
 *  * **Colour only in the icon tile.** [NotificationCompat.Builder.setColor] tints
 *    the small icon and nothing else; the row keeps the system's own text colours
 *    so it stays legible under any OEM shade theme.
 *  * **Never dismissible while serving.** An ongoing proxy that can be swiped away
 *    would leave traffic flowing with no visible trace of it.
 */
object ProxyNotifications {

    const val CHANNEL_ID = "proxy_status"
    const val NOTIFICATION_ID = 1001

    const val ACTION_PAUSE = "dev.nutty.proxy.action.PAUSE"
    const val ACTION_RESUME = "dev.nutty.proxy.action.RESUME"
    const val ACTION_CANCEL_RETRY = "dev.nutty.proxy.action.CANCEL_RETRY"
    const val ACTION_FIX = "dev.nutty.proxy.action.FIX"
    const val ACTION_DETAILS = "dev.nutty.proxy.action.DETAILS"

    /** The four shade states, matching frame 15 of the design. */
    enum class State { Connected, Attention, Reconnecting, Paused }

    /**
     * Low importance on purpose: this notification is a persistent readout, not
     * an alert. It must never make a sound — the agent running normally is not
     * news.
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_status_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * @param connections active connection count — shown in the title for every
     *   state, because "is anything actually using it?" is the question the shade
     *   is asked most.
     * @param detail the single supporting line: which server and how much today,
     *   or what went wrong.
     */
    fun build(
        context: Context,
        state: State,
        connections: Int,
        detail: String,
        retrySeconds: Int = 4,
    ): Notification {
        val title = when (state) {
            State.Connected -> context.getString(R.string.notif_connected_title, connections)
            State.Attention -> context.getString(R.string.notif_attention_title)
            State.Reconnecting -> context.getString(R.string.notif_reconnecting_title, retrySeconds)
            State.Paused -> context.getString(R.string.notif_paused_title)
        }

        val accent = ContextCompat.getColor(
            context,
            when (state) {
                State.Connected -> R.color.nutty_green
                State.Attention, State.Reconnecting -> R.color.nutty_amber
                State.Paused -> R.color.nutty_grey
            },
        )

        val primary = when (state) {
            State.Connected -> Action(R.string.action_pause, ACTION_PAUSE)
            State.Attention -> Action(R.string.action_fix, ACTION_FIX)
            State.Reconnecting -> Action(R.string.action_cancel, ACTION_CANCEL_RETRY)
            State.Paused -> Action(R.string.action_resume, ACTION_RESUME)
        }
        // Attention already spends its primary slot on "Fix", so its second slot
        // is Pause rather than Details — the escape hatch beats the explanation.
        val secondary = when (state) {
            State.Attention -> Action(R.string.action_pause, ACTION_PAUSE)
            else -> Action(R.string.action_details, ACTION_DETAILS)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nutty)
            .setColor(accent)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pendingIntent(context, ACTION_DETAILS))
            .setOngoing(state != State.Paused)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Visible on the lock screen but without the detail line: which hosts
            // a phone is proxying is not something to publish to a locked screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_nutty)
                    .setColor(accent)
                    .setContentTitle(title)
                    .build()
            )
            .addAction(0, context.getString(primary.label), pendingIntent(context, primary.action))
            .addAction(0, context.getString(secondary.label), pendingIntent(context, secondary.action))
            .build()
    }

    private data class Action(val label: Int, val action: String)

    /**
     * Routed through [MainActivity] for now. When the foreground service lands,
     * Pause/Resume/Cancel should become service intents so they act without
     * bringing the app to the front.
     */
    private fun pendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
