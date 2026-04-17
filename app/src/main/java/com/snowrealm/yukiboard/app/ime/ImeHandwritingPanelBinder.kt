package com.snowrealm.yukiboard.app.ime

import android.content.Context
import com.snowrealm.yukiboard.input.handwriting.HandwritingEngine
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.panel.HandwritingPanelView

/**
 * Sprint 6-5：HANDWRITING panel 最小邊界 binder。
 *
 * 只負責 [HandwritingPanelView] 建立、主題掛接與 4 個 callback wiring。
 *
 * 刻意不做：
 * - `HandwritingEngine` 的建立 / `init()` / 生命週期（仍由 service 端 lazy cache owner 掌控）
 * - `ensureReady` 的呼叫（side-effect 保留在 service 端）
 * - 不直接依賴 `KeyboardView.updateCandidates` / `InputRouter.onHandwritingCommit`
 *   的語意（皆由 service 端 lambdas 內部操作，binder 只接受封裝後的 callback）
 */
object ImeHandwritingPanelBinder {

    fun bind(
        context: Context,
        engine: HandwritingEngine,
        theme: KeyboardTheme,
        onCandidateSelected: (String) -> Unit,
        onRecognized: (List<String>) -> Unit,
        onBackspace: () -> Unit,
        onConfirm: () -> Unit,
    ): HandwritingPanelView {
        val panel = HandwritingPanelView(context, engine)
        panel.theme = theme
        panel.onCandidateSelected = onCandidateSelected
        panel.onRecognized = onRecognized
        panel.onBackspace = onBackspace
        panel.onConfirm = onConfirm
        return panel
    }
}
