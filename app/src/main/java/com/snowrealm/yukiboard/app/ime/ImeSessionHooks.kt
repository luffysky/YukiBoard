package com.snowrealm.yukiboard.app.ime

import android.view.inputmethod.EditorInfo
import com.snowrealm.yukiboard.data.CloudSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sprint 2: 將 IME session lifecycle 的清理與收尾順序從 service 本體抽出。
 *
 * 目前仍以薄封裝為主，不改變既有行為，只集中 session hook 的責任。
 */
class ImeSessionHooks(
    private val imeCoordinator: ImeSessionCoordinator,
    private val cloudSyncManager: CloudSyncManager,
) {

    fun onStartInputView(
        info: EditorInfo?,
        restarting: Boolean,
        resetSessionState: () -> Unit,
    ) {
        imeCoordinator.onStartInputView(info, restarting)
        resetSessionState()
    }

    fun onFinishInputView(
        finishingInput: Boolean,
        scope: CoroutineScope,
        cleanup: FinishInputViewCleanup,
    ) {
        imeCoordinator.onFinishInputView(finishingInput)
        cleanup.cancelAsyncWork()
        cleanup.stopClipboardMonitoring()
        cleanup.persistSmartInput()
        cleanup.resetInputState()
        cleanup.stopSensors()
        cleanup.resetManualModeOverride()
        cleanup.pauseVisualEffects()
        cleanup.flushSentenceBuffer()
        cleanup.pauseGameLoop()
        scope.launch {
            try {
                cloudSyncManager.onSessionEnd()
            } catch (_: Exception) {
            }
        }
    }

    fun onFinishInput(cleanup: FinishInputCleanup) {
        imeCoordinator.onFinishInput()
        cleanup.resetInputState()
        cleanup.stopSensors()
        cleanup.resetTouchState()
    }

    fun onDestroy(cleanup: DestroyCleanup) {
        imeCoordinator.onDestroy()
        cleanup.unregisterReceivers()
        cleanup.stopSensors()
        cleanup.stopGameLoop()
        cleanup.destroyAudioAndRemote()
        cleanup.cancelScope()
    }
}

data class FinishInputViewCleanup(
    val cancelAsyncWork: () -> Unit,
    val stopClipboardMonitoring: () -> Unit,
    val persistSmartInput: () -> Unit,
    val resetInputState: () -> Unit,
    val stopSensors: () -> Unit,
    val resetManualModeOverride: () -> Unit,
    val pauseVisualEffects: () -> Unit,
    val flushSentenceBuffer: () -> Unit,
    val pauseGameLoop: () -> Unit,
)

data class FinishInputCleanup(
    val resetInputState: () -> Unit,
    val stopSensors: () -> Unit,
    val resetTouchState: () -> Unit,
)

data class DestroyCleanup(
    val unregisterReceivers: () -> Unit,
    val stopSensors: () -> Unit,
    val stopGameLoop: () -> Unit,
    val destroyAudioAndRemote: () -> Unit,
    val cancelScope: () -> Unit,
)
