package dev.nutty.proxy.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val store = AgentStore(context)
            store.setBootObserved()
            if (store.isServingEnabled()) ProxyAgentService.start(context)
        }
    }
}
