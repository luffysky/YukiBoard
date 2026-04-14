package com.snowrealm.yukiboard.ui.settings.section

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.snowrealm.yukiboard.ui.settings.SettingsUiHelper

/**
 * 法律與隱私 section（隱私權政策 / 服務條款 / 開源授權）。
 */
class LegalSettingsSection(
    private val tr: (String) -> String,
    private val showLegalWebView: (fileName: String, title: String) -> Unit
) {
    fun buildCard(context: Context): LinearLayout {
        val children = mutableListOf<View>()
        return SettingsUiHelper.cardGroup(context, tr("法律與隱私"), null, null, children) {
            children += Button(context).apply {
                text = tr("隱私權政策")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                background = null
                setOnClickListener { showLegalWebView("privacy_policy.html", "隱私權政策") }
            }
            children += Button(context).apply {
                text = tr("服務條款")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                background = null
                setOnClickListener { showLegalWebView("terms_of_service.html", "服務條款") }
            }
            children += Button(context).apply {
                text = tr("開源授權")
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                background = null
                setOnClickListener { showLegalWebView("open_source.html", "開源授權") }
            }
        }
    }
}
