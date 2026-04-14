package com.snowrealm.yukiboard.app.ime

import android.content.Context
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.panel.CalculatorPanelView
import com.snowrealm.yukiboard.ui.panel.ClipboardPanelView
import com.snowrealm.yukiboard.ui.panel.ColorPickerPanelView
import com.snowrealm.yukiboard.ui.panel.EmojiPanelView
import com.snowrealm.yukiboard.ui.panel.KaomojiPanelView
import com.snowrealm.yukiboard.ui.panel.PanelType
import com.snowrealm.yukiboard.ui.panel.PhrasePanelView
import com.snowrealm.yukiboard.ui.panel.UnitConverterPanelView
import com.snowrealm.yukiboard.ui.panel.StickerPanelView

/**
 * Sprint 2 第一批：只收低依賴 panel 的建立。
 *
 * 若 factory 無法建立，呼叫端仍可 fallback 回 service 內原本的實作。
 */
class ImeBasicPanelFactory {

    fun createOrNull(
        context: Context,
        panel: PanelType,
        theme: KeyboardTheme,
        commitText: (String) -> Unit,
    ): ImePanelCreation? = when (panel) {
        PanelType.EMOJI -> {
            val panelView = EmojiPanelView(context).apply {
                this.theme = theme
                onEmojiSelected = commitText
            }
            ImePanelCreation(
                panelView = panelView,
                emojiPanel = panelView,
            )
        }
        PanelType.KAOMOJI -> {
            val panelView = KaomojiPanelView(context).apply {
                this.theme = theme
                onKaomojiSelected = commitText
            }
            ImePanelCreation(
                panelView = panelView,
                kaomojiPanel = panelView,
            )
        }
        PanelType.CLIPBOARD -> {
            val panelView = ClipboardPanelView(context).apply {
                this.theme = theme
                captureCurrentClip()
                onClipSelected = commitText
            }
            ImePanelCreation(
                panelView = panelView,
                clipboardPanel = panelView,
            )
        }
        PanelType.CALCULATOR -> {
            val panelView = CalculatorPanelView(context).apply {
                this.theme = theme
                onInsertResult = commitText
            }
            ImePanelCreation(panelView = panelView)
        }
        PanelType.PHRASE -> {
            val panelView = PhrasePanelView(context).apply {
                this.theme = theme
                onInsertPhrase = commitText
            }
            ImePanelCreation(panelView = panelView)
        }
        PanelType.COLOR_PICKER -> {
            val panelView = ColorPickerPanelView(context).apply {
                this.theme = theme
                onInsertColor = commitText
            }
            ImePanelCreation(panelView = panelView)
        }
        PanelType.UNIT_CONVERTER -> {
            val panelView = UnitConverterPanelView(context).apply {
                this.theme = theme
                onInsertResult = commitText
            }
            ImePanelCreation(panelView = panelView)
        }
        else -> null
    }
}

data class ImePanelCreation(
    val panelView: View,
    val emojiPanel: EmojiPanelView? = null,
    val kaomojiPanel: KaomojiPanelView? = null,
    val clipboardPanel: ClipboardPanelView? = null,
    val translatePanel: com.snowrealm.yukiboard.ui.panel.TranslatePanelView? = null,
    val gifPanel: com.snowrealm.yukiboard.ui.panel.GifPanelView? = null,
    val decryptPanel: com.snowrealm.yukiboard.ui.panel.DecryptPanelView? = null,
    val stickerPanel: StickerPanelView? = null,
    val dictionaryPanel: com.snowrealm.yukiboard.ui.panel.DictionaryPanelView? = null,
    val langLearnPanel: com.snowrealm.yukiboard.ui.panel.LangLearnPanelView? = null,
)
