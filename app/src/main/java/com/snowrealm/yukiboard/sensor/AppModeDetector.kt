package com.snowrealm.yukiboard.sensor

import com.snowrealm.yukiboard.ui.keyboard.KeyboardMode

/**
 * App 感知自動模式切換器。
 *
 * 根據目前前景 App 的 package name 自動判斷應使用哪種鍵盤模式。
 * IME 的 onStartInputView 拿到 EditorInfo.packageName 後傳入此處。
 *
 * ## 分類規則
 * - 設計者模式：Figma, Canva, Adobe 系列, Photopea, Sketch 等
 * - 開發者模式：Spck Editor, Acode, Termux, GitHub, VS Code, CodeSandbox 等
 * - 一般模式：其餘所有 App
 *
 * 使用者可在設定頁面開關自動切換功能。
 */
class AppModeDetector {

    private val designerPackages = setOf(
        // Figma
        "com.figma.mirror",
        "com.figma",
        // Canva
        "com.canva.editor",
        // Adobe 系列
        "com.adobe.psmobile",           // Photoshop Express
        "com.adobe.photoshopmix",
        "com.adobe.photoshopfix",
        "com.adobe.lrmobile",           // Lightroom
        "com.adobe.spark.post",         // Adobe Express
        "com.adobe.creativeapps.gather", // Creative Cloud
        "com.adobe.xd",
        "com.adobe.illustrator",
        // 其他設計工具
        "io.photopea",
        "com.byjus.thelearningapp.designtools",
        "com.picsart.studio",
        "com.sketch",
        "com.InVisionApp.Inspect",
        "com.polarr.editor",
        "com.pixlr.express",
        "com.google.android.apps.photos",   // 相片編輯
    )

    private val developerPackages = setOf(
        // 程式碼編輯器
        "io.spck",                       // Spck Editor
        "com.foxdebug.acodefree",       // Acode Free
        "com.foxdebug.acode",           // Acode Pro
        "com.aide.ui",                  // AIDE
        "com.ninesecured.dcoder",       // Dcoder
        "com.termux",                   // Termux
        "com.termux.api",
        "com.github.android",           // GitHub
        "com.microsoft.vscode",         // VS Code (if available)
        "in.nickeditor",
        "com.niceditor",
        "com.rhmsoft.edit",             // QuickEdit
        "jp.nicewill.cmaker",           // C/C++ Maker
        "com.apedroid.hwkeyboardhelper",
        "com.google.android.apps.fireball", // Firebase Console
        // 終端 & 工具
        "com.jrummyapps.terminal",
        "jackpal.androidterm",
        "com.server.auditor",           // Server Auditor (SSH)
        "com.sonelli.juicessh",
        "tech.ula",
        "com.nutomic.syncthingandroid",
        "com.cpuid.cpu_z",
        // Git
        "com.manichord.mgit",
        // Note apps in code context
        "com.rfc2822.noted",
    )

    // 部分匹配前綴（某些 App 有多個變體）
    private val designerPrefixes = listOf(
        "com.adobe.",
        "com.canva.",
    )

    private val developerPrefixes = listOf(
        "com.termux",
        "com.foxdebug.",
    )

    /**
     * 根據 package name 推薦鍵盤模式。
     *
     * @param packageName 前景 App 的 package name
     * @return 推薦模式；若無特殊匹配則回傳 NORMAL
     */
    fun detectMode(packageName: String?): KeyboardMode {
        if (packageName.isNullOrEmpty()) return KeyboardMode.NORMAL

        // 精確匹配
        if (packageName in designerPackages) return KeyboardMode.DESIGNER
        if (packageName in developerPackages) return KeyboardMode.DEVELOPER

        // 前綴匹配
        if (designerPrefixes.any { packageName.startsWith(it) }) return KeyboardMode.DESIGNER
        if (developerPrefixes.any { packageName.startsWith(it) }) return KeyboardMode.DEVELOPER

        return KeyboardMode.NORMAL
    }
}
