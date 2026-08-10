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

/** Production host for the persistent proxy agent. */
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
