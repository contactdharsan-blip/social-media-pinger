package com.quietping.capture

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.RawEvent

/**
 * Opt-in Accessibility booster (PRD §4 / §6A / §13 V3). Scoped by
 * @xml/accessibility_service_config to the target messaging packages and a minimal
 * event set, it extends Vault coverage to silenced / never-notified content the
 * notification stream misses.
 *
 * Like the listener, the [onAccessibilityEvent] callback returns immediately: it
 * extracts visible text and offloads a [RawEvent.AccessibilityCaptured] to the
 * shared [CapturePipeline] (non-blocking). No parsing or DB work runs here.
 */
class PingAccessibilityService : AccessibilityService() {

    private val pipeline: CapturePipeline by lazy { applicationContext.capturePipeline() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val packageName = e.packageName?.toString() ?: return

        // Defensive double-filter: the xml config already restricts packages, but we
        // refuse anything outside the supported set so no unrelated app is ever read.
        if (AppPackage.fromPackageId(packageName) == null) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> handleNotificationState(e, packageName)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContent(e, packageName)
            else -> Unit
        }
    }

    override fun onInterrupt() {
        // No long-running work to interrupt.
    }

    /**
     * Notification-state events carry their text directly in [AccessibilityEvent.getText],
     * which is the cheapest reliable signal — prefer it before walking the tree.
     */
    private fun handleNotificationState(e: AccessibilityEvent, packageName: String) {
        val text = e.text.joinToNonBlank()
        if (text.isBlank()) return
        emit(packageName, title = null, text = text, postedAt = eventTime(e))
    }

    /**
     * Window-content changes may not populate event text; fall back to reading the
     * focused/source node's visible text. We read at most the source subtree to stay
     * battery-light and never crawl the whole window.
     */
    private fun handleWindowContent(e: AccessibilityEvent, packageName: String) {
        val inlineText = e.text.joinToNonBlank()
        val text = if (inlineText.isNotBlank()) {
            inlineText
        } else {
            val source: AccessibilityNodeInfo = e.source ?: return
            try {
                extractNodeText(source)
            } finally {
                @Suppress("DEPRECATION")
                source.recycle()
            }
        }
        if (text.isBlank()) return
        emit(packageName, title = null, text = text, postedAt = eventTime(e))
    }

    private fun emit(packageName: String, title: String?, text: String, postedAt: Long) {
        pipeline.offer(
            RawEvent.AccessibilityCaptured(
                packageName = packageName,
                title = title,
                text = text,
                postedAt = postedAt,
            ),
        )
    }

    /**
     * Collect non-blank text from a node and its immediate descendants (bounded
     * depth) into a single string. Keeps the read shallow and deterministic.
     */
    private fun extractNodeText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > MAX_NODE_DEPTH) return ""
        val builder = StringBuilder()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append('\n') }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            try {
                builder.append(extractNodeText(child, depth + 1))
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
        return builder.toString().trim()
    }

    private fun eventTime(e: AccessibilityEvent): Long =
        if (e.eventTime > 0L) e.eventTime else System.currentTimeMillis()

    private companion object {
        const val TAG = "PingA11yService"
        const val MAX_NODE_DEPTH = 4
    }
}

private fun List<CharSequence>.joinToNonBlank(): String =
    asSequence()
        .map { it.toString() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n")
        .trim()
