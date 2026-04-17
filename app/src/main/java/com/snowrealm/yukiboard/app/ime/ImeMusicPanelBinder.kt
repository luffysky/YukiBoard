package com.snowrealm.yukiboard.app.ime

import android.content.Context
import com.snowrealm.yukiboard.service.KeyboardSoundEngine
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.panel.MusicPanelView

/**
 * Sprint 6-1：MUSIC panel 最小邊界 binder。
 *
 * 只負責 [MusicPanelView] 的建立與 theme / soundEngine / onImportMidi 線路掛接。
 * 不觸碰 MidiImportActivity、KeyboardSoundEngine、或其他 media stack 細節：
 * 匯入流程與 Activity 啟動由 service 端透過 [onImportMidiClick] 注入，
 * 確保 binder 不反向依賴 service / activity。
 */
object ImeMusicPanelBinder {

    fun bind(
        context: Context,
        theme: KeyboardTheme,
        soundEngine: KeyboardSoundEngine?,
        onImportMidiClick: (MusicPanelView) -> Unit,
    ): MusicPanelView {
        val panel = MusicPanelView(context)
        panel.theme = theme
        panel.soundEngine = soundEngine
        panel.onImportMidi = { onImportMidiClick(panel) }
        return panel
    }
}
