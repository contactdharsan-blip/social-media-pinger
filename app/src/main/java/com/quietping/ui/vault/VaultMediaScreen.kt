package com.quietping.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.drawable.ColorDrawable
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.placeholder
import com.quietping.domain.model.MediaItem
import com.quietping.ui.components.EmptyState
import com.quietping.ui.components.GlassButton
import com.quietping.ui.components.GlassButtonStyle
import com.quietping.ui.components.ShimmerBlock
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.BgPrimary
import com.quietping.ui.theme.BgTertiary
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.Motion
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.glass
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import java.io.File

/**
 * Captured-media gallery (PRD §6D follow-up): a thumbnail grid over the on-device
 * [com.quietping.capture.MediaVault], with a full-screen pan/zoom viewer on tap.
 *
 * This is a full-screen route that draws edge-to-edge, so it consumes the status-bar
 * inset itself (on the header and the viewer's scrim row) rather than via a nav-graph
 * wrapper — that lets the zoom viewer's image stay full-bleed while the back header
 * still clears the system bar (see the inset/occlusion lesson in `tasks/lessons.md`).
 *
 * Thumbnails load from local `java.io.File`s via Coil3 (core only — no network).
 * The viewer uses Telephoto's base [zoomable] modifier; the image's intrinsic size
 * is fed to [ZoomableContentLocation] so pan/zoom clamps to the real content bounds.
 * The viewer's zoom gestures are user-driven; the overlay only cross-fades in/out
 * and degrades to a short fade under reduced motion (no gratuitous entrance).
 *
 * @param onNavigate generic destination navigation (mandated screen contract).
 * @param onBack     pops back to the Vault list.
 * @param viewModel  the Hilt-provided [VaultMediaViewModel].
 */
@Composable
fun VaultMediaScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: VaultMediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    val motion = LocalQuietPingTheme.current.motionEnabled

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            MediaHeader(count = uiState.items.size, onBack = onBack)

            val bodyState = when {
                uiState.errorMessage != null -> MediaBodyState.ERROR
                uiState.isLoading -> MediaBodyState.LOADING
                uiState.isEmpty -> MediaBodyState.EMPTY
                else -> MediaBodyState.CONTENT
            }
            Crossfade(
                targetState = bodyState,
                animationSpec = if (motion) MotionTokens.signatureSpring() else Motion.ReducedFade,
                label = "mediaBody"
            ) { state ->
                when (state) {
                    MediaBodyState.LOADING -> MediaLoadingGrid()
                    MediaBodyState.EMPTY -> MediaEmpty()
                    MediaBodyState.ERROR -> MediaError(
                        message = uiState.errorMessage,
                        onRetry = viewModel::refresh
                    )
                    MediaBodyState.CONTENT -> MediaGrid(
                        items = uiState.items,
                        onOpen = { selected = it }
                    )
                }
            }
        }

        // Full-screen pan/zoom viewer overlay for the tapped item.
        val motionEnabled = LocalQuietPingTheme.current.motionEnabled
        val overlaySpec = if (motionEnabled) MotionTokens.signatureSpring() else ReducedFade
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn(overlaySpec),
            exit = fadeOut(overlaySpec)
        ) {
            selected?.let { item ->
                MediaViewer(item = item, onClose = { selected = null })
            }
        }
    }
}

/** The mutually exclusive body states the gallery crossfades between. */
private enum class MediaBodyState { LOADING, EMPTY, ERROR, CONTENT }

/** Top bar: back affordance, title, and a captured-count subtitle. */
@Composable
private fun MediaHeader(count: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Captured Media",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (count == 1) "1 file, on-device only" else "$count files, on-device only",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = items, key = { it.path }) { item ->
            MediaThumbnail(item = item, onClick = { onOpen(item) })
        }
    }
}

/** A single rounded, glass-framed thumbnail loaded from its local file. */
@Composable
private fun MediaThumbnail(item: MediaItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(GlassDefaults.CornerRadiusMd)
    val interaction = remember { MutableInteractionSource() }
    val motion = LocalQuietPingTheme.current.motionEnabled
    val context = LocalPlatformContext.current
    // Local File only (no network); motion-gated fade-in + a subtle surface placeholder
    // so thumbnails reveal smoothly instead of popping in.
    val request = remember(item.path, motion) {
        ImageRequest.Builder(context)
            .data(File(item.path))
            .crossfade(motion)
            .placeholder(ColorDrawable(BgTertiary.toArgb()))
            .build()
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .glass(cornerRadius = GlassDefaults.CornerRadiusMd)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun MediaLoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(count = 12, key = { it }) {
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                cornerRadius = GlassDefaults.CornerRadiusMd
            )
        }
    }
}

@Composable
private fun MediaEmpty() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        EmptyState(
            icon = Icons.Filled.PermMedia,
            title = "No captured media",
            message = "Photos, videos, and voice notes QuietPing saves from your chats " +
                "before they're deleted will appear here. Everything stays on this device.",
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

/**
 * Error state when the media scan fails. Re-scanning is a real, idempotent operation
 * ([VaultMediaViewModel.refresh]), so this offers a working Retry.
 */
@Composable
private fun MediaError(message: String?, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        EmptyState(
            icon = Icons.Filled.CloudOff,
            title = "Something went wrong",
            message = message,
            action = {
                GlassButton(
                    text = "Retry",
                    onClick = onRetry,
                    style = GlassButtonStyle.Secondary
                )
            },
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

/**
 * Full-screen pan/zoom viewer for one [item]. Dims the canvas, renders the local
 * file via a Coil painter, and makes it zoomable with Telephoto's base modifier.
 * The painter's intrinsic size is fed to [ZoomableContentLocation] once decoded so
 * gestures clamp to the actual image bounds. A close button and the file's name +
 * source-app label float over the top. System back closes the overlay first.
 */
@Composable
private fun MediaViewer(item: MediaItem, onClose: () -> Unit) {
    BackHandler(enabled = true, onBack = onClose)

    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f))
    val painter = rememberAsyncImagePainter(model = File(item.path))
    val painterState by painter.state.collectAsStateWithLifecycle()

    // Tell Telephoto the content bounds as soon as the image is decoded so pan/zoom
    // is correctly clamped (the base zoomable module is content-size aware).
    LaunchedEffect(painterState) {
        (painterState as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize?.let { size ->
            zoomableState.setContentLocation(
                ZoomableContentLocation.scaledInsideAndCenterAligned(size)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary.copy(alpha = 0.97f))
    ) {
        if (painterState is AsyncImagePainter.State.Error) {
            ViewerMessage(
                icon = Icons.Filled.BrokenImage,
                text = "This file can't be previewed."
            )
        } else {
            Image(
                painter = painter,
                contentDescription = item.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(zoomableState),
                contentScale = ContentScale.Inside
            )
        }

        // Top scrim row: close + file metadata.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.appLabel.isNotBlank()) {
                    Text(
                        text = item.appLabel.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

/** A centered icon + line for the viewer's error / non-previewable state. */
@Composable
private fun ViewerMessage(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/** Fade timing for the viewer's reduced-motion fallback (mirrors [com.quietping.ui.theme.Motion]). */
private val ReducedFade = tween<Float>(durationMillis = 120)
