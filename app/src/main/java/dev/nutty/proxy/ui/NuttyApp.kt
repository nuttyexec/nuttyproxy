package dev.nutty.proxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import android.app.Activity
import dev.nutty.proxy.ProxyViewModel
import androidx.compose.ui.Modifier
import dev.nutty.proxy.ui.component.BottomTabs
import dev.nutty.proxy.ui.component.FakeStatusBar
import dev.nutty.proxy.ui.component.HomeIndicator
import dev.nutty.proxy.ui.component.SheetHost
import dev.nutty.proxy.ui.model.DemoData
import dev.nutty.proxy.ui.model.HomeState
import dev.nutty.proxy.ui.model.Screen
import dev.nutty.proxy.ui.model.SheetKey
import dev.nutty.proxy.ui.model.ServerInfo
import dev.nutty.proxy.ui.screen.ActivityScreen
import dev.nutty.proxy.ui.screen.HomeScreen
import dev.nutty.proxy.ui.screen.ManualScreen
import dev.nutty.proxy.ui.screen.NameScreen
import dev.nutty.proxy.ui.screen.NotificationShadeScreen
import dev.nutty.proxy.ui.screen.PairScreen
import dev.nutty.proxy.ui.screen.ReadyScreen
import dev.nutty.proxy.ui.screen.ServerDetailScreen
import dev.nutty.proxy.ui.screen.ServersScreen
import dev.nutty.proxy.ui.screen.SettingsScreen
import dev.nutty.proxy.ui.screen.TestScreen
import dev.nutty.proxy.ui.theme.NuttyColor

/**
 * The app shell: status bar, screen, tab bar, and the sheet layer above all three.
 *
 * State is deliberately held here as plain `remember`, mirroring the prototype's
 * own logic — this is a design implementation, so the seam for a real ViewModel
 * is a single swap at this level and nothing below it changes.
 *
 * The sheet lives *inside* the content Box rather than above the tab bar, exactly
 * as the design has it: the tabs stay reachable and the scrim reads as belonging
 * to the screen, not to the whole app.
 */
@Composable
fun NuttyApp(
    viewModel: ProxyViewModel? = null,
    modifier: Modifier = Modifier,
    initialScreen: Screen = Screen.Home,
    initialHomeState: HomeState = HomeState.Connected,
    deviceName: String = DemoData.DEVICE_NAME,
) {
    val model: ProxyViewModel = viewModel ?: composeViewModel()
    val snapshot by model.snapshot.collectAsState()
    val activity = LocalContext.current as? Activity
    val resolvedDeviceName = snapshot.deviceName.ifBlank { deviceName }
    val servers = model.servers(snapshot)
    val logs = model.logs(snapshot)
    val requests = model.requests(snapshot)
    val readiness = model.readiness()
    var screen by remember { mutableStateOf(if (snapshot.profiles.isEmpty()) Screen.Pair else initialScreen) }
    var sheet by remember { mutableStateOf<SheetKey?>(null) }
    var pendingPairing by remember { mutableStateOf<String?>(null) }
    var selectedServer by remember { mutableStateOf<ServerInfo?>(null) }

    val openSheet: (SheetKey) -> Unit = { sheet = it }
    val go: (Screen) -> Unit = { screen = it; sheet = null }
    val homeState = model.homeState(snapshot)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NuttyColor.Bg)
    ) {
        FakeStatusBar(network = DemoData.NETWORK)

        Box(modifier = Modifier.weight(1f)) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    state = homeState,
                    deviceName = resolvedDeviceName,
                    serverCount = servers.size,
                    activeStreams = snapshot.activeStreams,
                    traffic = model.traffic(snapshot),
                    tunnelCaption = snapshot.statuses.values.firstOrNull()?.detail ?: "Waiting for a server",
                    recent = logs,
                    modifier = Modifier.fillMaxSize(),
                    onOpenSheet = openSheet,
                    onPause = model::pause,
                    onResume = model::resume,
                    onRetry = model::retry,
                    onViewActivity = { go(Screen.Activity) },
                )

                Screen.Servers -> ServersScreen(
                    servers = servers,
                    modifier = Modifier.fillMaxSize(),
                    onOpenServer = { server -> selectedServer = server; go(Screen.ServerDetail) },
                    onAddServer = { go(Screen.Pair) },
                )

                Screen.ServerDetail -> ServerDetailScreen(
                    server = selectedServer ?: servers.firstOrNull()
                        ?: ServerInfo(name = "No server selected", state = dev.nutty.proxy.ui.model.ServerState.Paused, lastSeen = "—", streams = "0", today = "0 B", errors = "0"),
                    modifier = Modifier.fillMaxSize(),
                    onBack = { go(Screen.Servers) },
                    onOpenSheet = openSheet,
                    onPauseOrResume = {
                        selectedServer?.let { server ->
                            if (server.state == dev.nutty.proxy.ui.model.ServerState.Paused) model.resumeServer(server.id)
                            else model.pauseServer(server.id)
                        }
                    },
                    onRevoke = { selectedServer?.let { model.revokeServer(it.id); go(Screen.Servers) } },
                )

                Screen.Activity -> ActivityScreen(
                    traffic = model.traffic(snapshot),
                    requests = requests,
                    logs = logs,
                    modifier = Modifier.fillMaxSize(),
                    onOpenSheet = openSheet,
                )

                Screen.Settings -> SettingsScreen(
                    deviceName = resolvedDeviceName,
                    readiness = readiness,
                    onNotifications = { activity?.let { model.requestNotifications(it) } },
                    onBattery = { activity?.let { model.requestBatteryUnrestricted(it) } },
                    onData = { activity?.let { model.openDataSettings(it) } },
                    onAppSettings = { activity?.let { model.openAppSettings(it) } },
                    modifier = Modifier.fillMaxSize(),
                )

                Screen.Pair -> PairScreen(
                    modifier = Modifier.fillMaxSize(),
                    onManual = { go(Screen.Manual) },
                    onOpenSheet = openSheet,
                    onPaired = { raw -> pendingPairing = raw; go(Screen.Name) },
                )

                Screen.Manual -> ManualScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackToQr = { go(Screen.Pair) },
                    onOpenSheet = openSheet,
                    onPaired = { raw -> pendingPairing = raw; go(Screen.Name) },
                )

                Screen.Name -> NameScreen(
                    modifier = Modifier.fillMaxSize(),
                    initialName = resolvedDeviceName,
                    onContinue = { name ->
                        model.saveDeviceName(name)
                        go(Screen.Ready)
                    },
                )

                Screen.Ready -> ReadyScreen(
                    readiness = readiness,
                    modifier = Modifier.fillMaxSize(),
                    onNotifications = { activity?.let { model.requestNotifications(it) } },
                    onBattery = { activity?.let { model.requestBatteryUnrestricted(it) } },
                    onData = { activity?.let { model.openDataSettings(it) } },
                    onAppSettings = { activity?.let { model.openAppSettings(it) } },
                    onSkip = {
                        // A foreground service must be started only after the
                        // user has had a chance to grant its notification
                        // permission.  Starting it from the naming screen made
                        // first-run failures look like a pairing/UI crash.
                        pendingPairing?.let(model::enroll)
                        pendingPairing = null
                        go(Screen.Test)
                    },
                )

                Screen.Test -> TestScreen(
                    deviceName = resolvedDeviceName,
                    modifier = Modifier.fillMaxSize(),
                    onDone = { go(Screen.Home) },
                )

                Screen.NotificationShade -> NotificationShadeScreen(Modifier.fillMaxSize())
            }

            SheetHost(
                spec = sheet?.let { DemoData.sheet(it) },
                onDismiss = { sheet = null },
            )
        }

        when {
            screen.isTabbed -> BottomTabs(current = screen, onSelect = go)
            // The shade mock owns its whole frame; everything else still reserves
            // the gesture inset so buttons never sit under the system pill.
            screen != Screen.NotificationShade -> HomeIndicator()
        }
    }
}
