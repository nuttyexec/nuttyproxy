package dev.nutty.proxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nutty.proxy.ui.NuttyApp
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyTheme

/**
 * Design host.
 *
 * This activity currently renders the design implementation against [DemoData];
 * the agent, the tunnel and the foreground service are not wired up. Everything
 * below `ui/` is production-shaped, so wiring is a matter of replacing the state
 * in `NuttyApp` with a ViewModel.
 *
 * Note the app draws its own status bar (`FakeStatusBar`) so a rendered frame
 * matches the 412 × 916 design canvas. Going edge-to-edge for real means
 * dropping that composable and applying `WindowInsets` instead.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NuttyTheme {
                val proxyViewModel: ProxyViewModel = viewModel()
                NuttyApp(
                    viewModel = proxyViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NuttyColor.Bg)
                )
            }
        }
    }
}
