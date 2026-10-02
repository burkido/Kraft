package com.burkido.kraft.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.voice.MicrophoneState
import com.burkido.kraft.effects.voice.VoiceBeam
import com.burkido.kraft.effects.voice.VoiceBeamType
import com.burkido.kraft.effects.voice.VoiceColorVariant
import com.burkido.kraft.effects.voice.VoiceTuning
import com.burkido.kraft.effects.voice.demoVoiceLevel
import com.burkido.kraft.effects.voice.rememberMicrophone
import kotlin.math.roundToInt

internal object VoicePage : LibraryPage {
    override val subtitle =
        "A colorful glow that rises with your voice from the bottom of a chat input or phone screen, then sweeps side to side while the reply is thought through."

    override val usageCode = """
        import com.burkido.kraft.effects.voice.VoiceBeam
        import com.burkido.kraft.effects.voice.rememberMicrophone

        val mic = rememberMicrophone()

        VoiceBeam(microphone = mic, background = { ChatInputSurface() }) {
            ChatInputControls()
        }
    """.trimIndent()

    override val prompt = """
        Add the Voice glow from Kraft to my Compose Multiplatform app.

        Install:
        Depend on the :effects module from commonMain. For the microphone, declare
        android.permission.RECORD_AUDIO (Android) and NSMicrophoneUsageDescription (iOS).

        Usage:
        val mic = rememberMicrophone()   // call mic.start() from a tap; it asks for permission
        VoiceBeam(
            type = VoiceBeamType.Default,   // Default (chat input) | Pill | Mobile
            microphone = mic,               // or level = { seconds -> myLevel } (0–1)
            processing = thinking,          // gathers into a beam that sweeps while you work
            background = { InputSurface() },
        ) { InputControls() }

        The glow sits along the bottom edge, rises with the voice's level and bands, bends its
        ceiling and traces it with a chromatic band. background draws under the glow, content over it.
    """.trimIndent()

    @Composable
    override fun Preview() {
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhoneExample()
                ExampleRowFull { ChatExample() }
            }
            Spacer(Modifier.height(12.dp))
            PlaygroundLabel()
            VoicePlayground()
        }
    }
}

/** The phone crop's glow runs at the crop's 0.68 of the phone's tuning. */
private const val PhoneScale = 1.25 * PhoneCrop
private val PhoneRadius = (66 * PhoneCrop).dp

/**
 * `.example-row-full--phone`: the crop flush with the card's top edge and 40 dp under it, the
 * transcript arriving as the demo voice speaks (half a phrase after the chat below).
 */
@Composable
private fun PhoneExample() {
    val transcript = remember { DemoTranscript() }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).cssSurface(16.dp) { Pg.Stage }.padding(bottom = 40.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        PhoneBeam(transcript, level = { t -> demoVoiceLevel(t + 4.5) })
    }
}

@Composable
private fun PhoneBeam(
    transcript: DemoTranscript,
    level: (Double) -> Float,
    processing: Boolean = false,
    paused: Boolean = false,
    microphone: com.burkido.kraft.effects.voice.Microphone? = null,
    variant: VoiceColorVariant = VoiceColorVariant.Colorful,
    tuning: VoiceTuning = VoiceTuning(),
) {
    VoiceBeam(
        type = VoiceBeamType.Mobile,
        level = level,
        microphone = microphone,
        processing = processing,
        paused = paused,
        colorVariant = variant,
        tuning = tuning,
        theme = EffectTheme.Dark,
        scale = PhoneScale,
        borderRadius = PhoneRadius,
        onLevel = transcript::onLevel,
        background = { PhoneSurface(transcript.runKey, transcript.words) },
    ) { PhoneForeground() }
}

/** The chat input: 371 dp where it fits, the card's width where not — the glow scales with it. */
@Composable
private fun ChatExample() {
    ChatBeam(level = { t -> demoVoiceLevel(t) })
}

@Composable
private fun ChatBeam(
    level: (Double) -> Float,
    processing: Boolean = false,
    paused: Boolean = false,
    microphone: com.burkido.kraft.effects.voice.Microphone? = null,
    variant: VoiceColorVariant = VoiceColorVariant.Colorful,
    tuning: VoiceTuning = VoiceTuning(),
    onLevel: ((Float) -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.widthIn(max = VoiceChatWidth.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, VoiceChatWidth.dp)
        val scale = (width.value / VoiceChatWidth).toDouble().coerceAtMost(1.0)
        VoiceBeam(
            type = VoiceBeamType.Default,
            level = level,
            microphone = microphone,
            processing = processing,
            paused = paused,
            colorVariant = variant,
            tuning = tuning,
            scale = scale,
            borderRadius = 20.dp,
            onLevel = onLevel,
            background = { VoiceChatSurface(width) },
        ) { VoiceChatForeground() }
    }
}

private enum class VoiceHost(val label: String, val child: String) {
    Chat("Chat input", "ChatInput()"),
    Mobile("Mobile", "VoiceScreen()"),
    Pill("Recording pill", "RecordingPill()"),
}

private enum class VoiceSource(val label: String) { Demo("Demo voice"), Mic("Microphone"), Processing("Processing") }

private val VoiceVariants = VoiceColorVariant.entries.map { it to it.name }

/** `MIC_STATUS`: what the microphone is doing, under the Source tabs. */
private fun micStatus(state: MicrophoneState): String? = when (state) {
    MicrophoneState.Idle -> null
    MicrophoneState.Requesting -> "Waiting for permission…"
    MicrophoneState.Live -> "Listening."
    MicrophoneState.Denied -> "Microphone blocked — allow it in the system settings."
    MicrophoneState.Unsupported -> "This device can't capture audio."
    MicrophoneState.Error -> "Couldn't open the microphone."
}

@Composable
private fun VoicePlayground() {
    val fonts = LocalKraftFonts.current
    var host by rememberSaveable { mutableStateOf(VoiceHost.Chat) }
    var source by rememberSaveable { mutableStateOf(VoiceSource.Demo) }
    var paused by rememberSaveable { mutableStateOf(true) }
    var variant by rememberSaveable { mutableStateOf(VoiceColorVariant.Colorful) }
    var sensitivity by rememberSaveable { mutableDoubleStateOf(3.1) }
    var reach by rememberSaveable { mutableStateOf<Double?>(null) }
    var bend by rememberSaveable { mutableStateOf<Double?>(null) }
    val mic = rememberMicrophone()
    val transcript = remember { DemoTranscript() }

    // Leaving the Microphone source releases the device; type and source changes restart the mock.
    LaunchedEffect(source) { if (source != VoiceSource.Mic) mic.stop() }
    LaunchedEffect(host, source) { transcript.restart() }

    val type = when (host) {
        VoiceHost.Chat -> VoiceBeamType.Default
        VoiceHost.Mobile -> VoiceBeamType.Mobile
        VoiceHost.Pill -> VoiceBeamType.Pill
    }
    val presetReach = when (type) {
        VoiceBeamType.Default -> 1.2
        VoiceBeamType.Pill -> 1.35
        VoiceBeamType.Mobile -> 3.0
    }
    val presetBend = when (type) {
        VoiceBeamType.Default -> 60.0
        VoiceBeamType.Pill -> 23.0
        VoiceBeamType.Mobile -> 70.0
    }
    val tuning = VoiceTuning(sensitivity = sensitivity, reach = reach, bend = bend)
    val processing = source == VoiceSource.Processing
    val microphone = if (source == VoiceSource.Mic) mic else null
    val level: (Double) -> Float = if (source == VoiceSource.Demo) ({ t -> demoVoiceLevel(t) }) else ({ 0f })

    Playground(
        stageMinHeight = 480.dp,
        stage = {
            Box(
                Modifier.matchParentSize().padding(horizontal = 40.dp, vertical = if (host == VoiceHost.Mobile) 0.dp else 48.dp),
                contentAlignment = if (host == VoiceHost.Mobile) Alignment.TopCenter else Alignment.Center,
            ) {
                when (host) {
                    VoiceHost.Chat -> ChatBeam(level, processing, paused, microphone, variant, tuning, transcript::onLevel)
                    VoiceHost.Mobile -> PhoneBeam(transcript, level, processing, paused, microphone, variant, tuning)
                    VoiceHost.Pill -> VoiceBeam(
                        type = VoiceBeamType.Pill,
                        level = level,
                        microphone = microphone,
                        processing = processing,
                        paused = paused,
                        colorVariant = variant,
                        tuning = tuning,
                        borderRadius = 22.dp,
                        background = { RecordingPillSurface() },
                    ) { RecordingPillForeground(running = !processing, paused = paused, resetKey = transcript.runKey) }
                }
            }
            PlayPauseButton(!paused) { paused = !paused }
        },
        controls = {
            PgField("Type") { PgTabs(VoiceHost.entries.map { it to it.label }, host) { host = it } }
            PgField("Source") {
                PgTabs(VoiceSource.entries.map { it to it.label }, source) {
                    source = it
                    // Choosing the microphone is the tap the platform wants: start listening and press Play.
                    if (it == VoiceSource.Mic) {
                        mic.start()
                        paused = false
                    }
                }
            }
            if (source == VoiceSource.Mic) {
                micStatus(mic.state)?.let {
                    BasicText(it, style = cssText(fonts.sans, 12f, 16f, FontWeight.Normal, Pg.Label))
                }
            }
            PgRule()
            PgGroup("Color theme") { PgTabs(VoiceVariants, variant) { variant = it } }
            PgGroup("Tuning") {
                PgSlider("Sensitivity", sensitivity.toFloat(), 0.2f..4f, 0.05f, "${num(sensitivity)}×") { sensitivity = it.toDouble() }
                val r = reach ?: presetReach
                PgSlider("Reach", r.toFloat(), 0f..3f, 0.05f, "${num(r)}×") { reach = it.toDouble() }
                val b = bend ?: presetBend
                PgSlider("Bend", b.toFloat(), 0f..80f, 1f, "${b.roundToInt()}px") { bend = it.toDouble() }
            }
        },
    )
    Spacer(Modifier.height(12.dp))
    CodeSnippet(voiceSnippet(host, source, paused, variant, sensitivity, reach, bend))
}

private fun voiceSnippet(
    host: VoiceHost,
    source: VoiceSource,
    paused: Boolean,
    variant: VoiceColorVariant,
    sensitivity: Double,
    reach: Double?,
    bend: Double?,
): String {
    val args = buildList {
        when (host) {
            VoiceHost.Chat -> Unit
            VoiceHost.Mobile -> add("type = VoiceBeamType.Mobile")
            VoiceHost.Pill -> add("type = VoiceBeamType.Pill")
        }
        when (source) {
            VoiceSource.Demo -> add("level = { yourLevel }")
            VoiceSource.Mic -> add("microphone = mic")
            VoiceSource.Processing -> add("processing = true")
        }
        if (variant != VoiceColorVariant.Colorful) add("colorVariant = VoiceColorVariant.${variant.name}")
        val knobs = buildList {
            if (sensitivity != 3.1) add("sensitivity = ${num(sensitivity)}")
            if (reach != null) add("reach = ${num(reach)}")
            if (bend != null) add("bend = ${num(bend)}")
        }
        if (knobs.isNotEmpty()) add("tuning = VoiceTuning(${knobs.joinToString()})")
        if (paused) add("paused = true")
    }
    val head = if (source == VoiceSource.Mic) "val mic = rememberMicrophone()\n\n" else ""
    val call = "VoiceBeam(\n" + args.joinToString("") { "    $it,\n" } + ") {\n    ${host.child}\n}"
    val tail = if (source == VoiceSource.Mic) "\n\nButton(onClick = mic::start) { Text(\"Listen\") }" else ""
    return head + call + tail
}
