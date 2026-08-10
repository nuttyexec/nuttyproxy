package dev.nutty.proxy.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.component.AccentPillButton
import dev.nutty.proxy.ui.component.CardDivider
import dev.nutty.proxy.ui.component.GhostPillButton
import dev.nutty.proxy.ui.component.InfoButton
import dev.nutty.proxy.ui.component.NuttyChip
import dev.nutty.proxy.ui.component.OutlineButton
import dev.nutty.proxy.ui.component.PrimaryButton
import dev.nutty.proxy.ui.component.ReadinessMarker
import dev.nutty.proxy.ui.component.SectionLabel
import dev.nutty.proxy.ui.component.StepDots
import dev.nutty.proxy.ui.component.QrScanner
import dev.nutty.proxy.ui.component.tapText
import dev.nutty.proxy.ui.model.DemoData
import dev.nutty.proxy.ui.model.ReadinessState
import dev.nutty.proxy.ui.model.SheetKey
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyShape
import dev.nutty.proxy.ui.theme.NuttyType
import dev.nutty.proxy.agent.PairingParser

/**
 * Shared frame for the four onboarding steps.
 *
 * Wider gutters than the tabbed screens (22dp vs 16dp) and a single column: each
 * step asks for exactly one thing, and the step ticks are the only chrome.
 */
@Composable
private fun OnboardScaffold(
    step: Int,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dim.OnboardPadding)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        StepDots(step, modifier = Modifier.padding(top = 10.dp, bottom = 30.dp))
        content()
    }
}

/** Headline + optional "(i)" into the pairing sheet. */
@Composable
private fun OnboardTitle(
    title: String,
    modifier: Modifier = Modifier,
    onInfo: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(title, style = NuttyType.OnboardTitle, color = NuttyColor.TextPrimary)
        if (onInfo != null) {
            InfoButton(onClick = onInfo, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * The blinking text caret.
 *
 * A hard on/off blink (step timing), not a fade — a fading caret reads as a
 * loading state, and these fields are ready for input, not busy.
 */
@Composable
private fun Caret(height: androidx.compose.ui.unit.Dp = 20.dp) {
    val transition = rememberInfiniteTransition(label = "caret")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "caretPhase",
    )
    Box(
        Modifier
            .size(width = 2.dp, height = height)
            .alpha(if (phase < 0.5f) 1f else 0f)
            .background(NuttyColor.TextDim)
    )
}

// ── 10 · Pair by QR ───────────────────────────────────────────────────────────

@Composable
fun PairScreen(
    modifier: Modifier = Modifier,
    onManual: () -> Unit,
    onOpenSheet: (SheetKey) -> Unit,
    onPaired: (String) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var handled by remember { mutableStateOf(false) }
    OnboardScaffold(step = 1, modifier = modifier) {
        OnboardTitle("Scan the\npairing QR") { onOpenSheet(SheetKey.Pairing) }
        Text(
            text = "The server shows it when you run pairing.",
            style = NuttyType.Body,
            color = NuttyColor.TextDim,
            modifier = Modifier.padding(bottom = 28.dp),
        )

        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            QrScanner(
                modifier = Modifier.fillMaxSize().clip(NuttyShape.Well),
                onPayload = { raw ->
                    if (!handled) {
                        PairingParser.parse(raw).onSuccess {
                            handled = true
                            onPaired(raw)
                        }.onFailure { error = "Invalid or expired pairing QR" }
                    }
                },
                onUnavailable = { error = "Camera permission is needed to scan" },
            )
            QrViewfinder(overlay = true)
        }
        error?.let {
            Text(it, style = NuttyType.Hint, color = NuttyColor.Amber, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Enter address manually", onManual)
            Text(
                text = "Use this if the server has no screen",
                style = NuttyType.Hint,
                color = NuttyColor.TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The QR viewfinder.
 *
 * Four corner brackets rather than a full frame — the brackets say "aim here"
 * without covering the code, and the sweeping green line is the only motion on
 * the screen, so it reads as "actively looking".
 */
@Composable
private fun QrViewfinder(modifier: Modifier = Modifier, overlay: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "scan")
    val scanAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scanAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(NuttyShape.Well)
            .background(if (overlay) androidx.compose.ui.graphics.Color.Transparent else NuttyColor.SurfaceInset, NuttyShape.Well)
            .border(1.dp, NuttyColor.Outline, NuttyShape.Well),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 34.dp.toPx()
            val arm = 34.dp.toPx()
            val stroke = 2.dp.toPx()
            val w = size.width
            val h = size.height

            fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(
                    color = NuttyColor.TextPrimary,
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x + dx * arm, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = NuttyColor.TextPrimary,
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x, y + dy * arm),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            bracket(inset, inset, 1f, 1f)
            bracket(w - inset, inset, -1f, 1f)
            bracket(inset, h - inset, 1f, -1f)
            bracket(w - inset, h - inset, -1f, -1f)

            // Scan line
            drawLine(
                color = NuttyColor.Green.copy(alpha = scanAlpha),
                start = androidx.compose.ui.geometry.Offset(inset, h / 2f),
                end = androidx.compose.ui.geometry.Offset(w - inset, h / 2f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        Text(
            text = "ALIGN QR INSIDE FRAME",
            style = NuttyType.Meta,
            color = NuttyColor.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 22.dp),
        )
    }
}

// ── 11 · Manual entry ─────────────────────────────────────────────────────────

@Composable
fun ManualScreen(
    modifier: Modifier = Modifier,
    onBackToQr: () -> Unit,
    onOpenSheet: (SheetKey) -> Unit,
    onPaired: (String) -> Unit,
) {
    var payload by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OnboardScaffold(step = 1, modifier = modifier) {
        Text(
            text = "‹ Scan QR instead",
            style = NuttyType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = NuttyColor.TextMuted,
            modifier = Modifier
                .tapText(onBackToQr)
                .padding(bottom = 14.dp),
        )
        OnboardTitle("Connect to\nyour server") { onOpenSheet(SheetKey.Pairing) }
        Text(
            text = "Paste the one-time pairing payload the server printed.",
            style = NuttyType.Body,
            color = NuttyColor.TextDim,
            modifier = Modifier.padding(bottom = 30.dp),
        )

        SectionLabel("PAIRING PAYLOAD", modifier = Modifier.padding(bottom = 9.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dim.FieldHeight)
                .clip(NuttyShape.Button)
                .background(NuttyColor.Surface, NuttyShape.Button)
                .border(1.dp, NuttyColor.OutlineFocus, NuttyShape.Button)
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = payload,
                onValueChange = { payload = it; error = null },
                textStyle = NuttyType.Field.copy(color = NuttyColor.TextPrimary),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (payload.isBlank()) Text("Paste pairing JSON", style = NuttyType.Field, color = NuttyColor.TextFaint)
                    inner()
                },
            )
        }
        Text(
            text = "Includes the gateway address, key pin, and one-time code.",
            style = NuttyType.Hint,
            color = NuttyColor.TextFaint,
            modifier = Modifier.padding(top = 8.dp),
        )

        error?.let { Text(it, style = NuttyType.Hint, color = NuttyColor.Amber, modifier = Modifier.padding(top = 14.dp)) }

        Spacer(Modifier.weight(1f))
        PrimaryButton("Connect", onClick = {
            PairingParser.parse(payload).onSuccess { onPaired(payload) }.onFailure { error = "Invalid or expired pairing payload" }
        })
    }
}

@Composable
private fun CodeCell(char: String?, focused: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(Dim.FieldHeight)
            .clip(NuttyShape.Cell)
            .background(NuttyColor.Surface, NuttyShape.Cell)
            .border(
                width = 1.dp,
                color = if (focused) NuttyColor.OutlineFocus else NuttyColor.OutlineMuted,
                shape = NuttyShape.Cell,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            char != null -> Text(char, style = NuttyType.InputCode, color = NuttyColor.TextPrimary)
            focused -> Caret()
        }
    }
}

// ── 12 · Device name ──────────────────────────────────────────────────────────

@Composable
fun NameScreen(modifier: Modifier = Modifier, initialName: String, onContinue: (String) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(initialName) }
    val suggestions = listOf("P1", "P2", "P3")

    OnboardScaffold(step = 2, modifier = modifier) {
        Text(
            text = "Name this\ndevice",
            style = NuttyType.OnboardTitle,
            color = NuttyColor.TextPrimary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Text(
            text = "Servers will see this name.",
            style = NuttyType.Body,
            color = NuttyColor.TextDim,
            modifier = Modifier.padding(bottom = 30.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(NuttyShape.Card)
                .background(NuttyColor.Surface, NuttyShape.Card)
                .border(1.dp, NuttyColor.OutlineFocus, NuttyShape.Card)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                textStyle = NuttyType.InputLarge.copy(color = NuttyColor.TextPrimary),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                NuttyChip(
                    text = suggestion,
                    selected = index == selected,
                    onClick = { selected = index; name = suggestions[index] },
                    mono = true,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", { onContinue(name.ifBlank { suggestions[selected] }) })
    }
}

// ── 13 · Always-on checklist ──────────────────────────────────────────────────

@Composable
fun ReadyScreen(
    readiness: List<dev.nutty.proxy.ui.model.ReadinessItem>,
    modifier: Modifier = Modifier,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
    onData: () -> Unit,
    onAppSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardScaffold(step = 3, modifier = modifier) {
        Text(
            text = "Keep it\nalways on",
            style = NuttyType.OnboardTitle,
            color = NuttyColor.TextPrimary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Text(
            text = "Three grants. Restart after boot is built in.",
            style = NuttyType.Body,
            color = NuttyColor.TextDim,
            modifier = Modifier.padding(bottom = 26.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            readiness.forEach { item ->
                val warning = item.state == ReadinessState.Warning
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NuttyShape.Button)
                        .background(
                            if (warning) NuttyColor.AmberContainerSoft else NuttyColor.Surface,
                            NuttyShape.Button,
                        )
                        .border(
                            width = 1.dp,
                            color = if (warning) NuttyColor.AmberOutlineSoft else NuttyColor.Outline,
                            shape = NuttyShape.Button,
                        )
                        .padding(
                            start = 15.dp,
                            end = if (item.action == "DONE") 15.dp else 11.dp,
                            top = if (item.action == "DONE") 15.dp else 11.dp,
                            bottom = if (item.action == "DONE") 15.dp else 11.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ReadinessMarker(
                        state = item.state,
                        size = 18.dp,
                        // The last grant is OEM-specific and cannot be automated,
                        // so it is numbered rather than ticked.
                        pendingLabel = item.marker,
                    )
                    Text(
                        text = item.label,
                        style = NuttyType.Row,
                        color = if (warning) NuttyColor.AmberText else NuttyColor.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        item.state == ReadinessState.Done -> Text(
                            text = "READY",
                            style = NuttyType.ValueSmall,
                            color = NuttyColor.TextFaint,
                        )
                        warning -> AccentPillButton(item.action.orEmpty(), onClick = when (item.label) {
                            "Notifications" -> onNotifications
                            "Battery background access" -> onBattery
                            "Background data" -> onData
                            else -> onAppSettings
                        })
                        item.action != null -> GhostPillButton(
                            text = item.action.orEmpty(),
                            onClick = when (item.label) {
                                "Notifications" -> onNotifications
                                "Battery background access" -> onBattery
                                "Background data" -> onData
                                else -> ({})
                            },
                            textColor = NuttyColor.TextPrimary,
                            strong = true,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("Start proxy", onSkip)
    }
}

// ── 14 · Connection test ──────────────────────────────────────────────────────

@Composable
fun TestScreen(
    deviceName: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    OnboardScaffold(step = 4, modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(NuttyColor.GreenContainer, CircleShape)
                    .border(1.dp, NuttyColor.GreenOutline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = NuttyType.GlyphHero, color = NuttyColor.Green)
            }

            Text(
                text = "$deviceName is serving",
                style = NuttyType.ResultTitle,
                color = NuttyColor.TextPrimary,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NuttyShape.Card)
                    .background(NuttyColor.Surface, NuttyShape.Card)
                    .border(1.dp, NuttyColor.Outline, NuttyShape.Card)
                    .padding(horizontal = 15.dp, vertical = 2.dp),
            ) {
                TestRow("Handshake", "OK · 210 ms", NuttyColor.Green, divider = false)
                TestRow("Proxy test", "200 OK", NuttyColor.Green)
                TestRow("Gateway", "gw-sel-01", NuttyColor.TextPrimary)
            }
        }

        PrimaryButton("Go to home", onDone)
    }
}

@Composable
private fun TestRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    divider: Boolean = true,
) {
    if (divider) CardDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = NuttyType.Label, color = NuttyColor.TextMuted)
        Text(value, style = NuttyType.Value, color = valueColor)
    }
}
