package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 功能列 section（20 個工具列項目開關）。
 */
class ToolbarSettingsSection(
    private val prefs: YukiPreferences,
    private val tr: (String) -> String
) {
    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("功能列"), null, null, children) {
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDC3E Claw AI", tr("AI 對話助手"), prefs.toolbarClaw) { prefs.toolbarClaw = it }
            children += SettingsUiHelper.switchRow(context, "\uD83C\uDFA4 ${tr("語音")}", tr("語音輸入"), prefs.toolbarVoice) { prefs.toolbarVoice = it }
            children += SettingsUiHelper.switchRow(context, "\uD83C\uDF10 ${tr("翻譯")}", tr("即時翻譯面板"), prefs.toolbarTranslate) { prefs.toolbarTranslate = it }
            children += SettingsUiHelper.switchRow(context, "\u270D ${tr("手寫")}", tr("手寫辨識輸入"), prefs.toolbarHandwriting) { prefs.toolbarHandwriting = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDCDA ${tr("字典")}", tr("字典查詢面板"), prefs.toolbarDictionary) { prefs.toolbarDictionary = it }
            children += SettingsUiHelper.switchRow(context, "\uD83C\uDF93 ${tr("學語言")}", tr("語言學習面板"), prefs.toolbarLangLearn) { prefs.toolbarLangLearn = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDE00 ${tr("表情")}", tr("Emoji 表情面板"), prefs.toolbarEmoji) { prefs.toolbarEmoji = it }
            children += SettingsUiHelper.switchRow(context, "\u989C ${tr("顔文字")}", tr("日式顔文字面板"), prefs.toolbarKaomoji) { prefs.toolbarKaomoji = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDDBC ${tr("貼圖")}", tr("貼圖面板"), prefs.toolbarSticker) { prefs.toolbarSticker = it }
            children += SettingsUiHelper.switchRow(context, "GIF", tr("GIF 搜尋面板"), prefs.toolbarGif) { prefs.toolbarGif = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDCCB ${tr("剪貼簿")}", tr("剪貼簿歷史面板"), prefs.toolbarClipboard) { prefs.toolbarClipboard = it }
            children += SettingsUiHelper.switchRow(context, "\uD83E\uDDEE ${tr("計算機")}", tr("快速計算機"), prefs.toolbarCalculator) { prefs.toolbarCalculator = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDCAC ${tr("常用語")}", tr("常用語快貼"), prefs.toolbarPhrase) { prefs.toolbarPhrase = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDCD6 ${tr("詞庫")}", tr("使用者自訂詞庫"), prefs.toolbarUserDict) { prefs.toolbarUserDict = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDD10 ${tr("解密")}", tr("私房話解密面板"), prefs.toolbarDecrypt) { prefs.toolbarDecrypt = it }
            children += SettingsUiHelper.switchRow(context, "\uD83C\uDFAF ${tr("色碼")}", tr("色碼選取器"), prefs.toolbarColorPicker) { prefs.toolbarColorPicker = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDCCF ${tr("換算")}", tr("單位換算工具"), prefs.toolbarUnitConverter) { prefs.toolbarUnitConverter = it }
            children += SettingsUiHelper.switchRow(context, "\uD83C\uDFB9 ${tr("音樂")}", tr("音樂鍵盤"), prefs.toolbarMusic) { prefs.toolbarMusic = it }
            children += SettingsUiHelper.switchRow(context, "\uD83D\uDD17 ${tr("遠端")}", tr("遠端鍵盤連線"), prefs.toolbarRemote) { prefs.toolbarRemote = it }
            children += SettingsUiHelper.switchRow(context, "\uD83C\uDFA8 ${tr("主題")}", tr("主題瀏覽切換"), prefs.toolbarTheme) { prefs.toolbarTheme = it }
        }
    }
}
