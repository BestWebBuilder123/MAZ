package com.maz.blurrr

import android.content.ContentValues
import android.content.Intent
import android.provider.Settings
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import android.widget.ImageView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.key
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val bg = Color(0xFF1D1B1E)
private val panel = Color(0xFF29272A)
private val panel2 = Color(0xFF353236)
private val lime = Color(0xFFD5FF35)
private val muted = Color(0xFF969197)

data class MediaClip(
    val uri: String,
    val name: String,
    val mimeType: String,
    val kind: String,
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val volume: Float = 1f,
    val speed: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

data class TextLayer(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val sizeSp: Float = 48f,
    val position: String = "Center",
    val bold: Boolean = true,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f
)

data class Project(
    val name: String,
    val ratio: String,
    val resolution: String,
    val fps: Int,
    val clips: List<MediaClip> = emptyList(),
    val textLayers: List<TextLayer> = emptyList(),
    val hueAdjustment: Float = 0f,
    val saturationAdjustment: Float = 0f,
    val lightnessAdjustment: Float = 0f,
    val id: String = UUID.randomUUID().toString()
)

class MainActivity : ComponentActivity() {
    private var currentTransformer: Transformer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var projects by remember { mutableStateOf(loadProjects()) }
            var screen by remember { mutableStateOf("home") }
            var current by remember { mutableStateOf<Project?>(null) }
            var newDialog by remember { mutableStateOf(false) }
            var accent by remember { mutableStateOf(getAccent()) }
            var updateText by remember { mutableStateOf("Checking GitHub…") }
            var updateUrl by remember { mutableStateOf<String?>(null) }
            var isDownloadingUpdate by remember { mutableStateOf(false) }
            var exporting by remember { mutableStateOf(false) }
            var exportStatus by remember { mutableStateOf("Ready to export") }

            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                val p = current
                if (p != null && uris.isNotEmpty()) {
                    val added = uris.mapNotNull { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        readClip(uri)
                    }
                    val changed = p.copy(clips = p.clips + added)
                    current = changed
                    projects = projects.map { if (it.id == p.id) changed else it }
                    saveProjects(projects)
                }
            }

            fun launchPicker(kind: String) {
                val types = when (kind) {
                    "audio" -> arrayOf("audio/*")
                    "visual" -> arrayOf("video/*", "image/*")
                    else -> arrayOf("video/*", "image/*", "audio/*")
                }
                picker.launch(types)
            }

            LaunchedEffect(Unit) {
                updateUrl = latestUpdate()
                updateText = if (updateUrl == null) "You're using the latest published version." else "A newer version is available."
            }

            MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = panel)) {
                Surface(Modifier.fillMaxSize(), color = bg) {
                    when (screen) {
                        "editor" -> {
                            val project = current
                            if (project != null) {
                                EditorScreen(
                                    project = project,
                                    exporting = exporting,
                                    exportStatus = exportStatus,
                                    back = { screen = "home" },
                                    import = { launchPicker(it) },
                                    remove = { index ->
                                        val changed = project.copy(clips = project.clips.filterIndexed { i, _ -> i != index })
                                        current = changed
                                        projects = projects.map { if (it.id == project.id) changed else it }
                                        saveProjects(projects)
                                    },
                                    updateClip = { index, clip ->
                                        val changed = project.copy(clips = project.clips.mapIndexed { i, old -> if (i == index) clip else old })
                                        current = changed
                                        projects = projects.map { if (it.id == project.id) changed else it }
                                        saveProjects(projects)
                                    },
                                    moveClip = { index, direction ->
                                        val next = index + direction
                                        if (next in project.clips.indices) {
                                            val reordered = project.clips.toMutableList()
                                            val item = reordered.removeAt(index)
                                            reordered.add(next, item)
                                            val changed = project.copy(clips = reordered)
                                            current = changed
                                            projects = projects.map { if (it.id == project.id) changed else it }
                                            saveProjects(projects)
                                        }
                                    },
                                    addTextLayer = { layer ->
                                        val changed = project.copy(textLayers = project.textLayers + layer)
                                        current = changed
                                        projects = projects.map { if (it.id == project.id) changed else it }
                                        saveProjects(projects)
                                    },
                                    removeTextLayer = { layerId ->
                                        val changed = project.copy(textLayers = project.textLayers.filterNot { it.id == layerId })
                                        current = changed
                                        projects = projects.map { if (it.id == project.id) changed else it }
                                        saveProjects(projects)
                                    },
                                    updateTextLayer = { layer ->
                                        val changed = project.copy(textLayers = project.textLayers.map { if (it.id == layer.id) layer else it })
                                        current = changed
                                        projects = projects.map { if (it.id == project.id) changed else it }
                                        saveProjects(projects)
                                    },
                                    updateColorEffects = { hue, saturation, lightness ->
                                        val changed = project.copy(
                                            hueAdjustment = hue,
                                            saturationAdjustment = saturation,
                                            lightnessAdjustment = lightness
                                        )
                                        current = changed
                                        projects = projects.map { if (it.id == project.id) changed else it }
                                        saveProjects(projects)
                                    },
                                    onExport = {
                                        if (!exporting) {
                                            exporting = true
                                            exportStatus = "Preparing export…"
                                            exportProject(project) { status ->
                                                exportStatus = status
                                                if (status.startsWith("Saved") || status.startsWith("Export failed")) exporting = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        "settings" -> SettingsScreen(
                            accent = accent,
                            setAccent = { accent = it; saveAccent(it) },
                            update = updateText,
                            updateAvailable = updateUrl != null,
                            downloading = isDownloadingUpdate,
                            download = {
                                val asset = updateUrl
                                if (asset != null && !isDownloadingUpdate) {
                                    isDownloadingUpdate = true
                                    updateText = "Downloading update…"
                                    downloadAndInstallUpdate(asset) { status ->
                                        updateText = status
                                        isDownloadingUpdate = false
                                    }
                                }
                            },
                            back = { screen = "home" }
                        )
                        else -> HomeScreen(
                            projects = projects,
                            onNew = { newDialog = true },
                            onOpen = { current = it; screen = "editor" },
                            onSettings = { screen = "settings" },
                            onImport = {
                                val importedProject = Project("Imported Project", "16:9", "1080p", 30)
                                projects = listOf(importedProject) + projects
                                saveProjects(projects)
                                current = importedProject
                                screen = "editor"
                                launchPicker("all")
                            },
                            onRename = { project, name ->
                                projects = projects.map { if (it.id == project.id) it.copy(name = name) else it }
                                saveProjects(projects)
                            },
                            onDelete = { project ->
                                projects = projects.filterNot { it.id == project.id }
                                saveProjects(projects)
                            }
                        )
                    }

                    if (newDialog) {
                        NewProjectDialog(
                            close = { newDialog = false },
                            create = { p ->
                                projects = listOf(p) + projects
                                saveProjects(projects)
                                current = p
                                newDialog = false
                                screen = "editor"
                            }
                        )
                    }
                }
            }
        }
    }

    private fun readClip(uri: Uri): MediaClip {
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        val kind = when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("audio/") -> "audio"
            else -> "video"
        }
        val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled media"
        val duration = if (kind == "image") 5_000L else runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(this, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
        return MediaClip(uri.toString(), displayName, mime, kind, duration)
    }

    private fun loadProjects(): List<Project> = runCatching {
        val array = JSONArray(getSharedPreferences("blurrr", MODE_PRIVATE).getString("projects", "[]"))
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val rawClips = obj.optJSONArray("clips") ?: JSONArray()
            val clips = (0 until rawClips.length()).map { j ->
                val raw = rawClips.get(j)
                if (raw is JSONObject) {
                    MediaClip(
                        uri = raw.optString("uri"),
                        name = raw.optString("name", "Media"),
                        mimeType = raw.optString("mimeType", "application/octet-stream"),
                        kind = raw.optString("kind", "video"),
                        durationMs = raw.optLong("durationMs", 0L),
                        trimStartMs = raw.optLong("trimStartMs", 0L),
                        trimEndMs = raw.optLong("trimEndMs", 0L),
                        volume = raw.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
                        speed = raw.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4f),
                        fadeInMs = raw.optLong("fadeInMs", 0L).coerceAtLeast(0L),
                        fadeOutMs = raw.optLong("fadeOutMs", 0L).coerceAtLeast(0L)
                    )
                } else {
                    val legacyUri = raw.toString()
                    val inferred = when {
                        legacyUri.contains(".png", true) || legacyUri.contains(".jpg", true) || legacyUri.contains(".webp", true) -> "image"
                        legacyUri.contains(".mp3", true) || legacyUri.contains(".m4a", true) || legacyUri.contains(".wav", true) -> "audio"
                        else -> "video"
                    }
                    MediaClip(legacyUri, "Imported media", inferred + "/*", inferred)
                }
            }
            val rawTextLayers = obj.optJSONArray("textLayers") ?: JSONArray()
            val textLayers = (0 until rawTextLayers.length()).map { j ->
                val layer = rawTextLayers.getJSONObject(j)
                TextLayer(
                    id = layer.optString("id", UUID.randomUUID().toString()),
                    text = layer.optString("text", ""),
                    colorArgb = layer.optInt("colorArgb", 0xFFFFFFFF.toInt()),
                    sizeSp = layer.optDouble("sizeSp", 48.0).toFloat(),
                    position = layer.optString("position", "Center"),
                    bold = layer.optBoolean("bold", true),
                    scale = layer.optDouble("scale", 1.0).toFloat().coerceIn(0.25f, 3f),
                    rotationDegrees = layer.optDouble("rotationDegrees", 0.0).toFloat().coerceIn(-180f, 180f),
                    opacity = layer.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f)
                )
            }
            Project(
                name = obj.optString("name", "Untitled"),
                ratio = obj.optString("ratio", "16:9"),
                resolution = obj.optString("resolution", "1080p"),
                fps = obj.optInt("fps", 30),
                clips = clips,
                textLayers = textLayers,
                hueAdjustment = obj.optDouble("hueAdjustment", 0.0).toFloat(),
                saturationAdjustment = obj.optDouble("saturationAdjustment", 0.0).toFloat(),
                lightnessAdjustment = obj.optDouble("lightnessAdjustment", 0.0).toFloat(),
                id = obj.optString("id", UUID.randomUUID().toString())
            )
        }
    }.getOrDefault(emptyList())

    private fun saveProjects(list: List<Project>) {
        val array = JSONArray()
        list.forEach { p ->
            val clips = JSONArray()
            p.clips.forEach { c ->
                clips.put(
                    JSONObject()
                        .put("uri", c.uri)
                        .put("name", c.name)
                        .put("mimeType", c.mimeType)
                        .put("kind", c.kind)
                        .put("durationMs", c.durationMs)
                        .put("trimStartMs", c.trimStartMs)
                        .put("trimEndMs", c.trimEndMs)
                        .put("volume", c.volume.toDouble())
                        .put("speed", c.speed.toDouble())
                        .put("fadeInMs", c.fadeInMs)
                        .put("fadeOutMs", c.fadeOutMs)
                )
            }
            val textLayers = JSONArray()
            p.textLayers.forEach { layer ->
                textLayers.put(
                    JSONObject()
                        .put("id", layer.id)
                        .put("text", layer.text)
                        .put("colorArgb", layer.colorArgb)
                        .put("sizeSp", layer.sizeSp.toDouble())
                        .put("position", layer.position)
                        .put("bold", layer.bold)
                        .put("scale", layer.scale.toDouble())
                        .put("rotationDegrees", layer.rotationDegrees.toDouble())
                        .put("opacity", layer.opacity.toDouble())
                )
            }
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("ratio", p.ratio)
                    .put("resolution", p.resolution)
                    .put("fps", p.fps)
                    .put("clips", clips)
                    .put("textLayers", textLayers)
                    .put("hueAdjustment", p.hueAdjustment.toDouble())
                    .put("saturationAdjustment", p.saturationAdjustment.toDouble())
                    .put("lightnessAdjustment", p.lightnessAdjustment.toDouble())
            )
        }
        getSharedPreferences("blurrr", MODE_PRIVATE).edit().putString("projects", array.toString()).apply()
    }

    private fun getAccent(): Color {
        val value = getSharedPreferences("blurrr", MODE_PRIVATE).getLong("accent", 0xFFD5FF35)
        return Color(value)
    }

    private fun saveAccent(color: Color) {
        getSharedPreferences("blurrr", MODE_PRIVATE).edit().putLong("accent", color.value.toLong()).apply()
    }

    @OptIn(UnstableApi::class)
    private fun gainProcessorFor(clip: MediaClip): GainProcessor {
        val clipEnd = if (clip.trimEndMs > 0L) clip.trimEndMs else clip.durationMs
        val trimmedDurationMs = (clipEnd - clip.trimStartMs).coerceAtLeast(0L)
        val fadeIn = clip.fadeInMs.coerceIn(0L, trimmedDurationMs)
        val fadeOut = clip.fadeOutMs.coerceIn(0L, trimmedDurationMs)
        val builder = DefaultGainProvider.Builder(clip.volume.coerceIn(0f, 1f))
        if (fadeIn > 0L) {
            builder.addFadeAt(0L, fadeIn * 1_000L, DefaultGainProvider.FADE_IN_LINEAR)
        }
        if (fadeOut > 0L) {
            builder.addFadeAt(
                (trimmedDurationMs - fadeOut).coerceAtLeast(0L) * 1_000L,
                fadeOut * 1_000L,
                DefaultGainProvider.FADE_OUT_LINEAR
            )
        }
        return GainProcessor(builder.build())
    }

    @OptIn(UnstableApi::class)
    private fun constantSpeed(speed: Float): SpeedProvider {
        val safeSpeed = speed.coerceIn(0.25f, 4f)
        return object : SpeedProvider {
            override fun getSpeed(timeUs: Long): Float = safeSpeed
            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
        }
    }

    @OptIn(UnstableApi::class)
    private fun exportProject(project: Project, onStatus: (String) -> Unit) {
        val visualClips = project.clips.filter { it.kind == "video" || it.kind == "image" }
        val audioClips = project.clips.filter { it.kind == "audio" }
        if (visualClips.isEmpty()) {
            onStatus("Export failed: Add at least one video or photo first.")
            return
        }

        val visualItems = visualClips.map { clip ->
            val builder = MediaItem.Builder().setUri(Uri.parse(clip.uri))
            if (clip.kind == "image") {
                builder.setImageDurationMs(clip.durationMs.coerceAtLeast(1_000L))
            } else if (clip.trimStartMs > 0L || clip.trimEndMs > 0L) {
                val end = if (clip.trimEndMs > 0L) clip.trimEndMs else clip.durationMs
                if (end > clip.trimStartMs) {
                    builder.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs)
                            .setEndPositionMs(end)
                            .build()
                    )
                }
            }
            val editedBuilder = EditedMediaItem.Builder(builder.build())
                .setFrameRate(project.fps.coerceIn(1, 120))
                .setEffects(
                    Effects(
                        listOf(gainProcessorFor(clip)),
                        emptyList()
                    )
                )
            if (clip.kind == "video") editedBuilder.setSpeed(constantSpeed(clip.speed))
            editedBuilder.build()
        }

        val visualSequence = EditedMediaItemSequence.withAudioAndVideoFrom(visualItems)
        val compositionBuilder = if (audioClips.isNotEmpty()) {
            val audioItems = audioClips.map { clip ->
                val builder = MediaItem.Builder().setUri(Uri.parse(clip.uri))
                if (clip.trimStartMs > 0L || clip.trimEndMs > 0L) {
                    val end = if (clip.trimEndMs > 0L) clip.trimEndMs else clip.durationMs
                    if (end > clip.trimStartMs) {
                        builder.setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(clip.trimStartMs)
                                .setEndPositionMs(end)
                                .build()
                        )
                    }
                }
                EditedMediaItem.Builder(builder.build())
                    .setSpeed(constantSpeed(clip.speed))
                    .setEffects(
                        Effects(
                            listOf(gainProcessorFor(clip)),
                            emptyList()
                        )
                    )
                    .build()
            }
            val audioSequence = EditedMediaItemSequence.withAudioFrom(audioItems)
                .buildUpon()
                .setIsLooping(true)
                .build()
            Composition.Builder(visualSequence, audioSequence)
        } else {
            Composition.Builder(visualSequence)
        }

        val textOverlays = project.textLayers
            .filter { it.text.isNotBlank() }
            .map { layer ->
                val styledText = SpannableString(layer.text)
                styledText.setSpan(
                    ForegroundColorSpan(layer.colorArgb),
                    0,
                    styledText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                styledText.setSpan(
                    AbsoluteSizeSpan(layer.sizeSp.toInt().coerceIn(16, 160), true),
                    0,
                    styledText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (layer.bold) {
                    styledText.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        styledText.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                val anchorY = when (layer.position) {
                    "Top" -> 0.72f
                    "Bottom" -> -0.72f
                    else -> 0f
                }
                val overlaySettings = StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(0f, anchorY)
                    .setOverlayFrameAnchor(0f, 0f)
                    .setScale(layer.scale, layer.scale)
                    .setRotationDegrees(layer.rotationDegrees)
                    .setAlphaScale(layer.opacity)
                    .build()
                TextOverlay.createStaticTextOverlay(styledText, overlaySettings)
            }
        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        val longSide = when (project.resolution) {
            "480p" -> 854
            "720p" -> 1280
            else -> 1920
        }
        val shortSide = when (project.resolution) {
            "480p" -> 480
            "720p" -> 720
            else -> 1080
        }
        val (outputWidth, outputHeight) = when (project.ratio) {
            "9:16" -> shortSide to longSide
            "1:1" -> longSide to longSide
            "4:5" -> shortSide to (shortSide * 5 / 4)
            else -> longSide to shortSide
        }
        videoEffects.add(
            Presentation.createForWidthAndHeight(
                outputWidth,
                outputHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        )
        if (project.hueAdjustment != 0f || project.saturationAdjustment != 0f || project.lightnessAdjustment != 0f) {
            videoEffects.add(
                HslAdjustment.Builder()
                    .adjustHue(project.hueAdjustment)
                    .adjustSaturation(project.saturationAdjustment)
                    .adjustLightness(project.lightnessAdjustment)
                    .build()
            )
        }
        if (textOverlays.isNotEmpty()) {
            videoEffects.add(OverlayEffect(textOverlays))
        }
        if (videoEffects.isNotEmpty()) {
            compositionBuilder.setEffects(Effects(emptyList(), videoEffects))
        }
        val composition = compositionBuilder.build()

        val folder = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: cacheDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val output = File(folder, "Blurrr_" + stamp + ".mp4")
        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()
        var transformer: Transformer? = null
        val progressRunnable = object : Runnable {
            override fun run() {
                val active = transformer ?: return
                if (active.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onStatus("Exporting " + progressHolder.progress + "%")
                }
                handler.postDelayed(this, 500L)
            }
        }

        try {
            transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(progressRunnable)
                        Thread {
                            val result = runCatching { publishVideo(output) }
                            handler.post {
                                if (result.isSuccess) {
                                    onStatus("Saved to Movies/Blurrr")
                                } else {
                                    onStatus("Export failed: " + (result.exceptionOrNull()?.message ?: "Could not save the video"))
                                }
                                output.delete()
                            }
                        }
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacks(progressRunnable)
                        output.delete()
                        onStatus("Export failed: " + (exportException.message ?: "Rendering error"))
                    }
                })
                .build()
            currentTransformer = transformer
            transformer.start(composition, output.absolutePath)
            handler.postDelayed(progressRunnable, 500L)
        } catch (error: Exception) {
            handler.removeCallbacks(progressRunnable)
            output.delete()
            onStatus("Export failed: " + (error.message ?: "Could not start renderer"))
        }
    }

    private fun publishVideo(file: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Blurrr")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val destination = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create a video in Movies")
        try {
            val stream = contentResolver.openOutputStream(destination)
                ?: throw IllegalStateException("Could not open the exported video")
            stream.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
            if (Build.VERSION.SDK_INT >= 29) {
                contentResolver.update(destination, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
            }
            return destination
        } catch (error: Exception) {
            contentResolver.delete(destination, null, null)
            throw error
        }
    }

    private fun downloadAndInstallUpdate(assetUrl: String, onStatus: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    val updatesDir = File(cacheDir, "updates").apply { mkdirs() }
                    val destination = File(updatesDir, "blurrr-update.apk")
                    val connection = (URL(assetUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout = 60_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Blurrr-Android-Updater")
                        setRequestProperty("Accept", "application/octet-stream")
                    }
                    try {
                        val code = connection.responseCode
                        if (code !in 200..299) {
                            throw IllegalStateException("GitHub download returned HTTP $code")
                        }
                        connection.inputStream.use { input ->
                            destination.outputStream().buffered().use { output -> input.copyTo(output) }
                        }
                    } finally {
                        connection.disconnect()
                    }
                    if (!destination.exists() || destination.length() < 1024L) {
                        destination.delete()
                        throw IllegalStateException("The downloaded APK is empty or incomplete")
                    }
                    destination
                }
                getSharedPreferences("blurrr", MODE_PRIVATE).edit()
                    .putString("pending_update_apk", apkFile.absolutePath)
                    .apply()
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                        onStatus("Allow installs from Blurrr, then return to install the update.")
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                        )
                    } else {
                        onStatus("Downloaded. Waiting for Android's install confirmation…")
                        launchApkInstaller(apkFile)
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    onStatus("Update download failed: " + (error.message ?: "Unknown error"))
                }
            }
        }
    }

    private fun launchApkInstaller(apkFile: File) {
        getSharedPreferences("blurrr", MODE_PRIVATE).edit().remove("pending_update_apk").apply()
        if (!apkFile.exists() || apkFile.length() < 1024L) {
            return
        }
        val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(installIntent)
    }

    override fun onResume() {
        super.onResume()
        val preferences = getSharedPreferences("blurrr", MODE_PRIVATE)
        val pendingPath = preferences.getString("pending_update_apk", null) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) return
        preferences.edit().remove("pending_update_apk").apply()
        val apkFile = File(pendingPath)
        if (apkFile.exists()) {
            runCatching { launchApkInstaller(apkFile) }
        }
    }

    private suspend fun latestUpdate(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("https://api.github.com/repos/BestWebBuilder123/MAZ/releases/latest").openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            connection.disconnect()
            val tag = json.optString("tag_name").removePrefix("v")
            val installed = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
            fun parts(version: String) = version.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = parts(tag)
            val installedParts = parts(installed)
            val newer = (0..2).firstNotNullOfOrNull { i ->
                val latestNumber = latestParts.getOrElse(i) { 0 }
                val installedNumber = installedParts.getOrElse(i) { 0 }
                if (latestNumber != installedNumber) latestNumber > installedNumber else null
            } ?: false
            if (newer) {
                json.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name") == "blurrr-android.apk" }
                        ?.optString("browser_download_url")
                }
            } else null
        }.getOrNull()
    }

    override fun onDestroy() {
        currentTransformer?.cancel()
        currentTransformer = null
        super.onDestroy()
    }
}

@Composable
private fun HomeScreen(
    projects: List<Project>,
    onNew: () -> Unit,
    onOpen: (Project) -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onRename: (Project, String) -> Unit,
    onDelete: (Project) -> Unit
) {
    var menuProjectId by remember { mutableStateOf<String?>(null) }
    var renameProject by remember { mutableStateOf<Project?>(null) }
    var deleteProject by remember { mutableStateOf<Project?>(null) }
    var renameValue by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AddBox, null, tint = Color.White)
            Text("Blurrr", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.SportsEsports, null, tint = Color.White)
                Icon(Icons.Default.Diamond, null, tint = Color.Cyan)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp).clip(RoundedCornerShape(28.dp))
                .background(panel2).clickable { onNew() }.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.MovieCreation, null, tint = lime, modifier = Modifier.size(52.dp))
            Text("Start Creating", fontWeight = FontWeight.Bold)
            Text("New Project / Tools", fontSize = 11.sp, color = muted)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniTool("FPS Changer", Icons.Default.FastForward)
            MiniTool("V-FPS changer", Icons.Default.Speed)
            MiniTool("Anime", Icons.Default.Hd)
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your project", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Text("Cloud project", color = muted)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55F58A), contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Download, null)
                Text(" Import")
            }
        }
        if (projects.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Your projects will appear here", color = muted)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(projects, key = { it.id }) { project ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(project) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(panel2),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, null, tint = muted, modifier = Modifier.size(32.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(project.name, fontWeight = FontWeight.Bold)
                            Text(project.ratio + " • " + project.fps + " FPS", color = muted, fontSize = 11.sp)
                            Text(project.resolution + " • " + project.clips.size + " media", color = muted, fontSize = 11.sp)
                        }
                        Box {
                            Icon(
                                Icons.Default.MoreVert,
                                null,
                                tint = muted,
                                modifier = Modifier.clickable { menuProjectId = project.id }
                            )
                            DropdownMenu(
                                expanded = menuProjectId == project.id,
                                onDismissRequest = { menuProjectId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        renameValue = project.name
                                        renameProject = project
                                        menuProjectId = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        deleteProject = project
                                        menuProjectId = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Nav("Playground", Icons.Default.VideoLibrary, false) {}
            Nav("Creation", Icons.Default.AddBox, true, onNew)
            Nav("My", Icons.Default.AccountCircle, false, onSettings)
        }
    }
    renameProject?.let { project ->
        AlertDialog(
            onDismissRequest = { renameProject = null },
            containerColor = panel,
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Project name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = renameValue.trim()
                        if (name.isNotEmpty()) onRename(project, name)
                        renameProject = null
                    },
                    enabled = renameValue.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = Color.Black)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameProject = null }) { Text("Cancel") } }
        )
    }
    deleteProject?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteProject = null },
            containerColor = panel,
            title = { Text("Delete project?") },
            text = { Text("“" + project.name + "” will be removed from this device.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(project); deleteProject = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteProject = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MiniTool(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.LightGray)
        Text(title, fontSize = 10.sp, color = muted)
    }
}

@Composable
private fun Nav(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, click: () -> Unit) {
    Column(Modifier.clickable { click() }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (active) Color.White else muted)
        Text(title, fontSize = 11.sp, color = if (active) Color.White else muted)
    }
}

@Composable
private fun NewProjectDialog(close: () -> Unit, create: (Project) -> Unit) {
    var name by remember { mutableStateOf("New Project") }
    var ratio by remember { mutableStateOf("16:9") }
    var resolution by remember { mutableStateOf("1080p") }
    var fps by remember { mutableIntStateOf(30) }
    AlertDialog(
        onDismissRequest = close,
        containerColor = panel,
        title = { Text("New Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true)
                Text("Choose frame", fontSize = 12.sp, color = muted)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("16:9", "9:16", "1:1", "4:5").forEach {
                        FilterChip(selected = ratio == it, onClick = { ratio = it }, label = { Text(it) })
                    }
                }
                Text("Resolution", fontSize = 12.sp, color = muted)
                Row {
                    listOf("480p", "720p", "1080p").forEach {
                        FilterChip(selected = resolution == it, onClick = { resolution = it }, label = { Text(it) })
                    }
                }
                Text("Select frame rate", fontSize = 12.sp, color = muted)
                Row {
                    listOf(24, 30, 60).forEach {
                        FilterChip(selected = fps == it, onClick = { fps = it }, label = { Text(it.toString() + " FPS") })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { create(Project(name.ifBlank { "New Project" }, ratio, resolution, fps)) },
                colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = Color.Black)
            ) { Text("+ New Project") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun EditorScreen(
    project: Project,
    exporting: Boolean,
    exportStatus: String,
    back: () -> Unit,
    import: (String) -> Unit,
    remove: (Int) -> Unit,
    updateClip: (Int, MediaClip) -> Unit,
    moveClip: (Int, Int) -> Unit,
    addTextLayer: (TextLayer) -> Unit,
    removeTextLayer: (String) -> Unit,
    updateTextLayer: (TextLayer) -> Unit,
    updateColorEffects: (Float, Float, Float) -> Unit,
    onExport: () -> Unit
) {
    var selected by remember(project.id) { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var previewPosition by remember(project.id) { mutableLongStateOf(0L) }
    val active = project.clips.getOrNull(selected)
    val context = LocalContext.current
    val previewPlayer = remember(project.id) {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }
    LaunchedEffect(
        active?.uri,
        active?.trimStartMs,
        active?.trimEndMs,
        project.hueAdjustment,
        project.saturationAdjustment,
        project.lightnessAdjustment
    ) {
        previewPlayer.pause()
        val previewEffects = if (
            project.hueAdjustment != 0f ||
            project.saturationAdjustment != 0f ||
            project.lightnessAdjustment != 0f
        ) {
            listOf(
                HslAdjustment.Builder()
                    .adjustHue(project.hueAdjustment)
                    .adjustSaturation(project.saturationAdjustment)
                    .adjustLightness(project.lightnessAdjustment)
                    .build()
            )
        } else {
            emptyList()
        }
        previewPlayer.setVideoEffects(previewEffects)
        if (active != null && active.kind != "image") {
            val mediaBuilder = MediaItem.Builder().setUri(Uri.parse(active.uri))
            if (active.trimStartMs > 0L || active.trimEndMs > 0L) {
                val clipEnd = if (active.trimEndMs > 0L) active.trimEndMs else active.durationMs
                if (clipEnd > active.trimStartMs) {
                    mediaBuilder.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(active.trimStartMs)
                            .setEndPositionMs(clipEnd)
                            .build()
                    )
                }
            }
            previewPlayer.setMediaItem(mediaBuilder.build())
            previewPlayer.prepare()
            previewPosition = 0L
            previewPlayer.seekTo(0L)
            previewPlayer.playWhenReady = playing
        } else {
            previewPlayer.clearMediaItems()
        }
    }
    LaunchedEffect(playing) {
        previewPlayer.playWhenReady = playing
        while (playing) {
            previewPosition = previewPlayer.currentPosition.coerceAtLeast(0L)
            delay(120L)
        }
    }
    LaunchedEffect(active?.speed, active?.volume) {
        previewPlayer.setPlaybackSpeed(active?.speed ?: 1f)
        previewPlayer.volume = active?.volume ?: 1f
    }
    var showTextDialog by remember { mutableStateOf(false) }
    var editingTextLayer by remember { mutableStateOf<TextLayer?>(null) }
    var showEffectsDialog by remember { mutableStateOf(false) }
    var toolNotice by remember { mutableStateOf<String?>(null) }
    val duration = active?.durationMs ?: 0L
    val endValue = active?.let { if (it.trimEndMs > 0L) it.trimEndMs else it.durationMs } ?: 0L

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Close, null, Modifier.clickable { back() }, tint = Color.White)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(project.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(project.resolution + " • " + project.fps + " FPS", color = muted, fontSize = 10.sp)
            }
            Button(
                onClick = onExport,
                enabled = !exporting && project.clips.any { it.kind == "video" || it.kind == "image" },
                colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = Color.Black)
            ) {
                if (exporting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.FileUpload, null)
                Text(if (exporting) " Exporting" else " Export")
            }
        }

        Box(
            Modifier.fillMaxWidth().weight(0.42f).padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF080808)),
            contentAlignment = Alignment.Center
        ) {
            if (active == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Movie, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                    Text("No Display", color = Color.Gray)
                    Text("Import a video or photo to begin", fontSize = 11.sp, color = muted)
                }
            } else if (active.kind != "image") {
                AndroidView(
                    factory = { playerContext ->
                        PlayerView(playerContext).apply {
                            player = previewPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { view -> view.player = previewPlayer },
                    modifier = Modifier.fillMaxSize()
                )
                if (active.kind == "audio") {
                    Column(
                        Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp))
                            .background(Color(0xE6222024)).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = lime, modifier = Modifier.size(42.dp))
                        Text(active.name, color = Color.White, maxLines = 2)
                        Text("Audio preview", color = muted, fontSize = 11.sp)
                    }
                }
            } else if (active.kind == "image") {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageURI(Uri.parse(active.uri))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, null, tint = lime, modifier = Modifier.size(46.dp))
                    Text(active.name, color = Color.White)
                    Text("Audio track", color = muted, fontSize = 11.sp)
                }
            }
            if (active?.kind == "video" || active?.kind == "image") {
                project.textLayers.forEach { layer ->
                    val alignment = when (layer.position) {
                        "Top" -> Alignment.TopCenter
                        "Bottom" -> Alignment.BottomCenter
                        else -> Alignment.Center
                    }
                    Text(
                        text = layer.text,
                        color = Color(layer.colorArgb.toLong()),
                        fontSize = layer.sizeSp.sp,
                        fontWeight = if (layer.bold) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.align(alignment).padding(12.dp)
                    )
                }
            }
        }

        if (active != null && active.kind != "image" && duration > 0L) {
            val previewDuration = (endValue - active.trimStartMs).coerceAtLeast(1L)
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(formatDuration(previewPosition + active.trimStartMs), color = muted, fontSize = 10.sp)
                    Text(formatDuration(endValue), color = muted, fontSize = 10.sp)
                }
                Slider(
                    value = previewPosition.coerceIn(0L, previewDuration).toFloat(),
                    onValueChange = { value ->
                        previewPosition = value.toLong()
                        previewPlayer.seekTo(value.toLong())
                    },
                    valueRange = 0f..previewDuration.toFloat()
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Undo, null, tint = muted)
            Icon(Icons.Default.Redo, null, tint = muted)
            Icon(
                Icons.Default.SkipPrevious, null, tint = Color.White,
                modifier = Modifier.clickable { selected = (selected - 1).coerceAtLeast(0); playing = false }
            )
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                null,
                tint = Color.White,
                modifier = Modifier.size(30.dp).clickable { playing = !playing }
            )
            Icon(
                Icons.Default.SkipNext, null, tint = Color.White,
                modifier = Modifier.clickable { selected = (selected + 1).coerceAtMost((project.clips.size - 1).coerceAtLeast(0)); playing = false }
            )
        }

        Column(
            Modifier.fillMaxWidth().weight(0.40f).background(Color(0xFF171619))
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TIMELINE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(exportStatus, color = if (exporting) lime else muted, fontSize = 10.sp, maxLines = 1)
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 150.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimelineTrack(
                    title = "VIDEO",
                    clips = project.clips.withIndex().filter { it.value.kind != "audio" }.toList(),
                    selected = selected,
                    onSelect = { selected = it; playing = false },
                    moveClip = moveClip,
                    removeClip = { index ->
                        remove(index)
                        selected = when {
                            selected > index -> selected - 1
                            selected == index -> index.coerceAtMost(project.clips.lastIndex - 1).coerceAtLeast(0)
                            else -> selected
                        }
                    }
                )
                TimelineTrack(
                    title = "AUDIO",
                    clips = project.clips.withIndex().filter { it.value.kind == "audio" }.toList(),
                    selected = selected,
                    onSelect = { selected = it; playing = false },
                    moveClip = moveClip,
                    removeClip = { index ->
                        remove(index)
                        selected = when {
                            selected > index -> selected - 1
                            selected == index -> index.coerceAtMost(project.clips.lastIndex - 1).coerceAtLeast(0)
                            else -> selected
                        }
                    }
                )
                if (project.textLayers.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TEXT", color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            project.textLayers.forEach { layer ->
                                Row(
                                    Modifier.width(150.dp).height(48.dp).clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xFF3A3025))
                                        .clickable { editingTextLayer = layer; showTextDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.TextFields, null, tint = Color(0xFFFFC45E), modifier = Modifier.size(17.dp))
                                    Text(layer.text, color = Color.White, fontSize = 11.sp, maxLines = 2, modifier = Modifier.weight(1f).padding(horizontal = 7.dp))
                                    Icon(Icons.Default.Close, null, tint = muted, modifier = Modifier.clickable { removeTextLayer(layer.id) }.size(19.dp))
                                }
                            }
                        }
                    }
                }
                if (project.clips.isEmpty() && project.textLayers.isEmpty()) {
                    Text("Add video, photo, music, or text below", color = muted, fontSize = 11.sp, modifier = Modifier.padding(14.dp))
                }
            }
            if (active != null && active.kind != "image") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("Volume  " + (active.volume * 100f).toInt() + "%", color = muted, fontSize = 10.sp)
                    Slider(
                        value = active.volume,
                        onValueChange = { value -> updateClip(selected, active.copy(volume = value.coerceIn(0f, 1f))) },
                        valueRange = 0f..1f
                    )
                    if (active.kind == "video" || active.kind == "audio") {
                        Text("Speed  " + String.format(Locale.US, "%.2fx", active.speed), color = muted, fontSize = 10.sp)
                        Slider(
                            value = active.speed,
                            onValueChange = { value -> updateClip(selected, active.copy(speed = value.coerceIn(0.25f, 4f))) },
                            valueRange = 0.25f..4f
                        )
                        if (duration > 0L) {
                            val maxFade = duration.coerceAtMost(5_000L).toFloat()
                            Text("Fade in  " + String.format(Locale.US, "%.1fs", active.fadeInMs / 1000f), color = muted, fontSize = 10.sp)
                            Slider(
                                value = active.fadeInMs.coerceIn(0L, maxFade.toLong()).toFloat(),
                                onValueChange = { value -> updateClip(selected, active.copy(fadeInMs = value.toLong())) },
                                valueRange = 0f..maxFade
                            )
                            Text("Fade out  " + String.format(Locale.US, "%.1fs", active.fadeOutMs / 1000f), color = muted, fontSize = 10.sp)
                            Slider(
                                value = active.fadeOutMs.coerceIn(0L, maxFade.toLong()).toFloat(),
                                onValueChange = { value -> updateClip(selected, active.copy(fadeOutMs = value.toLong())) },
                                valueRange = 0f..maxFade
                            )
                        }
                    }
                }
            }
            if (active != null && duration > 0L) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text((if (active.kind == "audio") "Audio trim start  " else "Trim start  ") + formatDuration(active.trimStartMs), color = muted, fontSize = 10.sp)
                    Slider(
                        value = active.trimStartMs.coerceIn(0L, (duration - 250L).coerceAtLeast(0L)).toFloat(),
                        onValueChange = { value ->
                            val start = value.toLong()
                            val end = if (active.trimEndMs > start) active.trimEndMs else duration
                            updateClip(selected, active.copy(trimStartMs = start, trimEndMs = end))
                        },
                        valueRange = 0f..(duration - 250L).coerceAtLeast(0L).toFloat()
                    )
                    Text((if (active.kind == "audio") "Audio trim end  " else "Trim end  ") + formatDuration(endValue), color = muted, fontSize = 10.sp)
                    Slider(
                        value = endValue.coerceIn((active.trimStartMs + 250L).coerceAtMost(duration), duration).toFloat(),
                        onValueChange = { value ->
                            updateClip(selected, active.copy(trimEndMs = value.toLong()))
                        },
                        valueRange = (active.trimStartMs + 250L).coerceAtMost(duration).toFloat()..duration.toFloat()
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(panel).padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Video/Photo", "Music", "Fx", "Text", "Shapes", "3D").forEach { tool ->
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (tool == "Video/Photo") panel2 else panel)
                        .clickable {
                            when (tool) {
                                "Video/Photo" -> import("visual")
                                "Music" -> import("audio")
                                "Text" -> showTextDialog = true
                                "Fx" -> showEffectsDialog = true
                                else -> toolNotice = "$tool editing is still being built."
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        when (tool) {
                            "Music" -> Icons.Default.MusicNote
                            "Fx" -> Icons.Default.AutoAwesome
                            "Text" -> Icons.Default.TextFields
                            "Shapes" -> Icons.Default.Category
                            "3D" -> Icons.Default.ViewInAr
                            else -> Icons.Default.VideoLibrary
                        },
                        null,
                        tint = Color.White
                    )
                    Text(tool, fontSize = 11.sp, color = muted)
                }
            }
        }
    }

    if (showTextDialog) {
        TextLayerDialog(
            initial = editingTextLayer,
            close = { showTextDialog = false; editingTextLayer = null },
            save = { layer ->
                if (editingTextLayer == null) addTextLayer(layer) else updateTextLayer(layer)
                showTextDialog = false
                editingTextLayer = null
            }
        )
    }
    if (showEffectsDialog) {
        ColorEffectsDialog(
            initialHue = project.hueAdjustment,
            initialSaturation = project.saturationAdjustment,
            initialLightness = project.lightnessAdjustment,
            close = { showEffectsDialog = false },
            apply = { hue, saturation, lightness ->
                updateColorEffects(hue, saturation, lightness)
                showEffectsDialog = false
            }
        )
    }
    toolNotice?.let { message ->
        AlertDialog(
            onDismissRequest = { toolNotice = null },
            containerColor = panel,
            title = { Text("In development") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { toolNotice = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun TimelineTrack(
    title: String,
    clips: List<IndexedValue<MediaClip>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    moveClip: (Int, Int) -> Unit,
    removeClip: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
        if (clips.isEmpty()) {
            Text("＋ Add media", color = muted, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
        } else {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                clips.forEach { entry ->
                    val index = entry.index
                    val clip = entry.value
                    val cardWidth = (clip.durationMs / 1000f * 24f).coerceIn(104f, 184f).dp
                    Column(
                        Modifier.width(cardWidth).height(58.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (index == selected) Color(0xFF514D55) else panel)
                            .border(if (index == selected) 1.dp else 0.dp, if (index == selected) lime else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { onSelect(index) }
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (clip.kind) {
                                    "audio" -> Icons.Default.MusicNote
                                    "image" -> Icons.Default.Image
                                    else -> Icons.Default.Movie
                                },
                                null,
                                tint = lime,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(clip.name, color = Color.White, fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 5.dp))
                            Icon(Icons.Default.Close, null, tint = muted, modifier = Modifier.size(15.dp).clickable { removeClip(index) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(clip.durationMs), color = muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowUp, null, tint = muted, modifier = Modifier.size(16.dp).clickable { moveClip(index, -1) })
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = muted, modifier = Modifier.size(16.dp).clickable { moveClip(index, 1) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextLayerDialog(
    initial: TextLayer? = null,
    close: () -> Unit,
    save: (TextLayer) -> Unit
) {
    var value by remember(initial?.id) { mutableStateOf(initial?.text ?: "Your text") }
    var colorArgb by remember(initial?.id) { mutableIntStateOf(initial?.colorArgb ?: 0xFFFFFFFF.toInt()) }
    var size by remember(initial?.id) { mutableFloatStateOf(initial?.sizeSp ?: 48f) }
    var position by remember(initial?.id) { mutableStateOf(initial?.position ?: "Center") }
    var bold by remember(initial?.id) { mutableStateOf(initial?.bold ?: true) }
    var scale by remember(initial?.id) { mutableFloatStateOf(initial?.scale ?: 1f) }
    var rotation by remember(initial?.id) { mutableFloatStateOf(initial?.rotationDegrees ?: 0f) }
    var opacity by remember(initial?.id) { mutableFloatStateOf(initial?.opacity ?: 1f) }
    val colors = listOf(
        0xFFFFFFFF.toInt(),
        0xFFFFD84D.toInt(),
        0xFFFF6F91.toInt(),
        0xFF57D8FF.toInt()
    )

    AlertDialog(
        onDismissRequest = close,
        containerColor = panel,
        title = { Text(if (initial == null) "Add text layer" else "Edit text layer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Text") },
                    minLines = 2,
                    maxLines = 4
                )
                Text("Color", color = muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.forEach { color ->
                        Box(
                            Modifier.size(30.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(color.toLong()))
                                .border(if (colorArgb == color) 2.dp else 0.dp, Color.White, RoundedCornerShape(50))
                                .clickable { colorArgb = color }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", color = muted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(size.toInt().toString() + " sp", color = Color.White, fontSize = 12.sp)
                }
                Slider(value = size, onValueChange = { size = it }, valueRange = 20f..96f)
                Text("Position", color = muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Top", "Center", "Bottom").forEach { option ->
                        FilterChip(
                            selected = position == option,
                            onClick = { position = option },
                            label = { Text(option) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = bold, onCheckedChange = { bold = it })
                    Text("Bold")
                }
                Text("Scale  " + String.format(Locale.US, "%.2fx", scale), color = muted, fontSize = 12.sp)
                Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.25f..3f)
                Text("Rotation  " + rotation.toInt() + "°", color = muted, fontSize = 12.sp)
                Slider(value = rotation, onValueChange = { rotation = it }, valueRange = -180f..180f)
                Text("Opacity  " + (opacity * 100).toInt() + "%", color = muted, fontSize = 12.sp)
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    save(
                        TextLayer(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            text = value.trim(),
                            colorArgb = colorArgb,
                            sizeSp = size,
                            position = position,
                            bold = bold,
                            scale = scale,
                            rotationDegrees = rotation,
                            opacity = opacity
                        )
                    )
                },
                enabled = value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = Color.Black)
            ) { Text(if (initial == null) "Add to timeline" else "Save changes") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
private fun ColorEffectsDialog(
    initialHue: Float,
    initialSaturation: Float,
    initialLightness: Float,
    close: () -> Unit,
    apply: (Float, Float, Float) -> Unit
) {
    var hue by remember { mutableFloatStateOf(initialHue) }
    var saturation by remember { mutableFloatStateOf(initialSaturation) }
    var lightness by remember { mutableFloatStateOf(initialLightness) }

    AlertDialog(
        onDismissRequest = close,
        containerColor = panel,
        title = { Text("Color effects") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick looks", color = muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(
                        selected = hue == 0f && saturation == 0f && lightness == 0f,
                        onClick = { hue = 0f; saturation = 0f; lightness = 0f },
                        label = { Text("Original") }
                    )
                    FilterChip(
                        selected = saturation == 35f,
                        onClick = { hue = 0f; saturation = 35f; lightness = 0f },
                        label = { Text("Vivid") }
                    )
                    FilterChip(
                        selected = saturation == -100f,
                        onClick = { hue = 0f; saturation = -100f; lightness = 0f },
                        label = { Text("Mono") }
                    )
                }
                Text("Hue  " + hue.toInt() + "°", color = muted, fontSize = 12.sp)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = -180f..180f)
                Text("Saturation  " + saturation.toInt(), color = muted, fontSize = 12.sp)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = -100f..100f)
                Text("Lightness  " + lightness.toInt(), color = muted, fontSize = 12.sp)
                Slider(value = lightness, onValueChange = { lightness = it }, valueRange = -100f..100f)
                Text("These adjustments are applied when the video is exported.", color = muted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = { apply(hue, saturation, lightness) },
                colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = Color.Black)
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsScreen(
    accent: Color,
    setAccent: (Color) -> Unit,
    update: String,
    updateAvailable: Boolean,
    downloading: Boolean,
    download: () -> Unit,
    back: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, null, Modifier.clickable { back() }.padding(end = 12.dp))
            Text("My", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Text("App theme", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(lime, Color(0xFF7B8CFF), Color(0xFFFF79B8), Color(0xFF57D8C3), Color.White).forEach { color ->
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(color)
                        .border(if (accent == color) 2.dp else 0.dp, Color.White, RoundedCornerShape(50))
                        .clickable { setAccent(color) }
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("App updates", fontWeight = FontWeight.Bold)
        Text(update, color = muted)
        Button(onClick = download, enabled = updateAvailable && !downloading) { Text(if (downloading) "Downloading…" else "Download & install update") }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000L
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
