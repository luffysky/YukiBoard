package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.ui.panel.ClipboardPanelView
import com.snowrealm.yukiboard.ui.panel.DecryptPanelView
import com.snowrealm.yukiboard.ui.panel.DictionaryPanelView
import com.snowrealm.yukiboard.ui.panel.EmojiPanelView
import com.snowrealm.yukiboard.ui.panel.GifPanelView
import com.snowrealm.yukiboard.ui.panel.HandwritingPanelView
import com.snowrealm.yukiboard.ui.panel.KaomojiPanelView
import com.snowrealm.yukiboard.ui.panel.LangLearnPanelView
import com.snowrealm.yukiboard.ui.panel.PanelInputTarget
import com.snowrealm.yukiboard.ui.panel.RemoteKeyboardPanelView
import com.snowrealm.yukiboard.ui.panel.StickerPanelView
import com.snowrealm.yukiboard.ui.panel.ThemePanelView
import com.snowrealm.yukiboard.ui.panel.TranslatePanelView
import com.snowrealm.yukiboard.ui.panel.UserDictionaryPanelView

/**
 * 收斂 IME panel reference 與 input-mode 判斷，避免 service 內散落多份 panel 清單。
 *
 * 這一層只整理 bindings，不改變既有 panel 建立方式。
 */
class ImePanelBindings {

    var emojiPanel: EmojiPanelView? = null
    var kaomojiPanel: KaomojiPanelView? = null
    var clipboardPanel: ClipboardPanelView? = null
    var translatePanel: TranslatePanelView? = null
    var gifPanel: GifPanelView? = null
    var stickerPanel: StickerPanelView? = null
    var themePanel: ThemePanelView? = null
    var remotePanel: RemoteKeyboardPanelView? = null
    var userDictPanel: UserDictionaryPanelView? = null
    var decryptPanel: DecryptPanelView? = null
    var dictionaryPanel: DictionaryPanelView? = null
    var langLearnPanel: LangLearnPanelView? = null
    var handwritingPanel: HandwritingPanelView? = null

    fun bindCreation(creation: ImePanelCreation?) {
        if (creation == null) return
        emojiPanel = creation.emojiPanel
        kaomojiPanel = creation.kaomojiPanel
        clipboardPanel = creation.clipboardPanel
        translatePanel = creation.translatePanel
        gifPanel = creation.gifPanel
        decryptPanel = creation.decryptPanel
        stickerPanel = creation.stickerPanel
        dictionaryPanel = creation.dictionaryPanel
        langLearnPanel = creation.langLearnPanel
    }

    fun activeRawCommitTarget(): PanelInputTarget? = listOfNotNull(
        translatePanel,
        gifPanel,
        userDictPanel,
        decryptPanel,
        dictionaryPanel,
        langLearnPanel,
    ).firstOrNull { it.isInInputMode }

    fun activeInputTarget(): PanelInputTarget? = listOfNotNull(
        translatePanel,
        gifPanel,
        userDictPanel,
        decryptPanel,
        dictionaryPanel,
        langLearnPanel,
        clipboardPanel,
    ).firstOrNull { it.isInInputMode }

    fun hasActiveInputTarget(): Boolean = activeInputTarget() != null

    fun clearInputModeBindings() {
        emojiPanel = null
        kaomojiPanel = null
        stickerPanel = null
        translatePanel?.exitInputMode()
        gifPanel?.exitInputMode()
        userDictPanel?.exitInputMode()
        decryptPanel?.exitInputMode()
        dictionaryPanel?.exitInputMode()
        langLearnPanel?.exitInputMode()
        clipboardPanel?.exitInputMode()
        translatePanel = null
        gifPanel = null
        userDictPanel = null
        decryptPanel = null
        dictionaryPanel = null
        langLearnPanel = null
        clipboardPanel = null
        handwritingPanel = null
    }
}
