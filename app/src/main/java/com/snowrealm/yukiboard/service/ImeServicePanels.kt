package com.snowrealm.yukiboard.service

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.snowrealm.yukiboard.animation.gles.KeyboardGLSurfaceView
import com.snowrealm.yukiboard.app.ime.DestroyCleanup
import com.snowrealm.yukiboard.app.ime.FinishInputCleanup
import com.snowrealm.yukiboard.app.ime.FinishInputViewCleanup
import com.snowrealm.yukiboard.app.ime.ImeCandidateInteractionHandler
import com.snowrealm.yukiboard.app.ime.ImePanelCleanup
import com.snowrealm.yukiboard.app.ime.ImePanelBindings
import com.snowrealm.yukiboard.app.ime.ImePanelCoordinator
import com.snowrealm.yukiboard.app.ime.ImePanelCreation
import com.snowrealm.yukiboard.app.ime.ImePanelFactory
import com.snowrealm.yukiboard.app.ime.ImeFloatingModeHelper
import com.snowrealm.yukiboard.app.ime.ImeFraudModelInit
import com.snowrealm.yukiboard.app.ime.ImeGameLauncher
import com.snowrealm.yukiboard.app.ime.ImeImageCommitter
import com.snowrealm.yukiboard.app.ime.ImePanelControlBar
import com.snowrealm.yukiboard.app.ime.ImeTransparencyOverlay
import com.snowrealm.yukiboard.app.ime.ImePanelHost
import com.snowrealm.yukiboard.app.ime.ImePanelRouter
import com.snowrealm.yukiboard.app.ime.ImeSessionCoordinator
import com.snowrealm.yukiboard.app.ime.ImeSessionHooks
import com.snowrealm.yukiboard.app.ime.ImeThemeRenderDelegate
import com.snowrealm.yukiboard.app.ime.ImeVoiceController
import com.snowrealm.yukiboard.data.CloudSyncManager
import com.snowrealm.yukiboard.game.*
import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.input.router.InputRouter
import com.snowrealm.yukiboard.input.router.InputResult
import com.snowrealm.yukiboard.input.snippet.SnippetManager
import com.snowrealm.yukiboard.sensor.AppModeDetector
import com.snowrealm.yukiboard.sensor.CursorDirection
import com.snowrealm.yukiboard.sensor.GyroscopeCursorController
import com.snowrealm.yukiboard.sensor.ShakeDetector
import com.snowrealm.yukiboard.remote.BluetoothHidKeyboard
import com.snowrealm.yukiboard.remote.ConnectionState
import com.snowrealm.yukiboard.remote.KeyAction
import com.snowrealm.yukiboard.remote.RemoteKeyEvent
import com.snowrealm.yukiboard.remote.RemoteKeyboardService
import com.snowrealm.yukiboard.remote.WifiKeyboardServer
import com.snowrealm.yukiboard.speech.AndroidSpeechEngine
import com.snowrealm.yukiboard.speech.SpeechInputEngine
import com.snowrealm.yukiboard.speech.FasterWhisperSpeechEngine
import com.snowrealm.yukiboard.speech.WhisperSpeechEngine
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.theme.ThemeManager
import com.snowrealm.yukiboard.ui.keyboard.KeyModel
import com.snowrealm.yukiboard.ui.keyboard.RadialLanguageMenu
import com.snowrealm.yukiboard.ui.keyboard.CharacterView
import com.snowrealm.yukiboard.ui.keyboard.FloatingBallView
import com.snowrealm.yukiboard.ui.keyboard.FloatingKeyboardWrapper
import com.snowrealm.yukiboard.ui.keyboard.KeyboardMode
import com.snowrealm.yukiboard.ui.keyboard.KeyboardResizeHandle
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState
import com.snowrealm.yukiboard.ui.keyboard.KeyboardView
import com.snowrealm.yukiboard.ui.panel.*
import com.snowrealm.yukiboard.ui.settings.SettingsActivity
import com.snowrealm.yukiboard.claw.ClawClient
import com.snowrealm.yukiboard.ui.settings.YukiPreferences
import com.snowrealm.yukiboard.gif.GiphyApiClient
import com.snowrealm.yukiboard.translate.TranslateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Extension functions extracted from YukiBoardIMEService

internal fun YukiBoardIMEService.buildHiddenPanelSet(): Set<PanelType> {
    val hidden = mutableSetOf<PanelType>()
    if (!prefs.toolbarVoice) hidden += PanelType.VOICE
    if (!prefs.toolbarTranslate) hidden += PanelType.TRANSLATE
    if (!prefs.toolbarGif) hidden += PanelType.GIF
    if (!prefs.toolbarEmoji) hidden += PanelType.EMOJI
    if (!prefs.toolbarKaomoji) hidden += PanelType.KAOMOJI
    if (!prefs.toolbarSticker) hidden += PanelType.STICKER
    if (!prefs.toolbarClipboard) hidden += PanelType.CLIPBOARD
    if (!prefs.toolbarRemote) hidden += PanelType.REMOTE
    if (!prefs.toolbarClaw) hidden += PanelType.CLAW
    if (!prefs.toolbarCalculator) hidden += PanelType.CALCULATOR
    if (!prefs.toolbarPhrase) hidden += PanelType.PHRASE
    if (!prefs.toolbarUserDict) hidden += PanelType.USER_DICT
    if (!prefs.toolbarDecrypt) hidden += PanelType.DECRYPT
    if (!prefs.toolbarColorPicker) hidden += PanelType.COLOR_PICKER
    if (!prefs.toolbarUnitConverter) hidden += PanelType.UNIT_CONVERTER
    if (!prefs.toolbarHandwriting) hidden += PanelType.HANDWRITING
    if (!prefs.toolbarMusic) hidden += PanelType.MUSIC
    if (!prefs.toolbarDictionary) hidden += PanelType.DICTIONARY
    if (!prefs.toolbarLangLearn) hidden += PanelType.LANG_LEARN
    if (!prefs.toolbarTheme) hidden += PanelType.THEME
    return hidden
}

internal fun YukiBoardIMEService.handlePanelToggle(panel: PanelType) {
    imePanelRouter.routeToggle(
        currentActivePanel = activePanel,
        requestedPanel = panel,
        updatePanelSelection = ::updatePanelSelection,
        openVoiceInput = ::toggleVoiceInput,
        openSettings = ::openSettings,
        showPanel = ::showPanel,
        closePanel = ::closePanel,
    )
}

internal fun YukiBoardIMEService.showPanel(panel: PanelType) {
    imePanelRouter.preparePanelOpen(
        panel = panel,
        updatePanelSelection = ::updatePanelSelection,
        commitPendingComposition = {
            if (inputRouter.composingText.isNotEmpty()) {
                val ic = currentInputConnection
                val composing = inputRouter.composingText
                ic?.commitText(composing, 1)
                ic?.finishComposingText()
            }
        },
        clearPanelBindings = ::clearActivePanelBindings,
        resetInputUiState = ::resetInputUiStateForPanelTransition,
    )

    val theme = ThemeManager.getInstance(this).currentTheme
    val factoryPanel = imePanelFactory.createOrNull(
        context = this,
        panel = panel,
        theme = theme,
        commitText = { text -> currentInputConnection?.commitText(text, 1) },
        showDialog = ::showImeDialog,
        commitImageContent = ::commitImageContent,
        translateEngine = translateEngine,
        gifClient = giphyClient,
        stickerRepository = stickerRepository,
        stickerDownloadManager = stickerDownloadManager,
        shouldSyncStickerRepository = !stickerSyncDone,
        onStickerRepositorySynced = { stickerSyncDone = true },
        scope = serviceScope,
        sourceTextProvider = ::extractPanelSourceText,
        onTranslateSpeakRequest = ::onTranslateSpeakRequest,
        inputTextProvider = {
            val ic = currentInputConnection
            val selected = ic?.getSelectedText(0)?.toString()
            if (!selected.isNullOrEmpty()) selected
            else ic?.getTextBeforeCursor(200, 0)?.toString()?.trim()?.split("\\s+".toRegex())?.lastOrNull() ?: ""
        },
        onSpeak = { text, locale -> ttsSpeak(text, locale) },
    )
    bindFactoryPanelCreation(factoryPanel)

    val panelView: View = factoryPanel?.panelView
        ?: createHighDependencyPanelOrNull(panel, theme)
        ?: return

    imePanelRouter.attachPanel(
        panel = panel,
        panelView = panelView,
        onOverlayPanelShown = ::showPanelControlBar,
        onOverlayPanelAttached = {
            if (isPanelBehindKeyboard) {
                isPanelBehindKeyboard = false
                imeCoordinator.setPanelBehindKeyboard(false)
                panelContainer.bringToFront()
            }
        },
    )
}

internal fun YukiBoardIMEService.closePanel() {
    imePanelRouter.closePanel(
        updatePanelSelection = ::updatePanelSelection,
        clearPanelBindings = ::clearActivePanelBindings,
        resetInputUiState = ::resetInputUiStateForPanelTransition,
        finishComposingText = { currentInputConnection?.finishComposingText() },
        restoreOverlayOrder = ::restorePanelOverlayOrder,
        restoreKeyboardFocus = ::restoreKeyboardFocusAfterPanelClose,
        restoreKeyboardBackground = ::restoreKeyboardBackgroundAfterPanelClose,
        onOverlayPanelHidden = ::hidePanelControlBar,
    )
}

internal fun YukiBoardIMEService.createHighDependencyPanelOrNull(panel: PanelType, theme: KeyboardTheme): View? {
    return when (panel) {
        PanelType.REMOTE -> {
            val p = RemoteKeyboardPanelView(this).also { imePanelBindings.remotePanel = it }
            p.theme = theme
            p.btState = btKeyboard?.connectionState ?: ConnectionState.IDLE
            p.wifiState = wifiServer?.connectionState ?: ConnectionState.IDLE
            p.wifiUrl = wifiServer?.getConnectionUrl() ?: ""
            p.connectedDeviceName = btKeyboard?.connectedDevice?.name
                ?: wifiServer?.connectedDevice?.name ?: ""
            p.onBluetoothToggle = { toggleBluetooth() }
            p.onWifiToggle = { toggleWifi() }
            p
        }
        PanelType.THEME -> {
            val tm = ThemeManager.getInstance(this)
            val p = ThemePanelView(this).also { imePanelBindings.themePanel = it }
            p.theme = theme
            p.currentThemeId = tm.currentTheme.id
            p.availableThemes = tm.availableThemes
            p.reloadPreviewBitmap()
            p.onThemeSelected = { selected ->
                tm.currentTheme = selected
                prefs.useCustomBackground = false
                applyTheme(selected)
                p.previewBgBitmap = null
            }
            p.onAnimThemeSelected = { styleId ->
                toolbarView.setAnimStyle(styleId)
                keyboardView.setAnimStyle(styleId)
            }
            val themeListener: (com.snowrealm.yukiboard.theme.KeyboardTheme) -> Unit = { newTheme ->
                p.currentThemeId = newTheme.id
                p.theme = newTheme
                p.invalidate()
            }
            tm.addOnThemeChangedListener(themeListener)
            p.addOnDetachListener {
                tm.removeOnThemeChangedListener(themeListener)
            }
            p.onPickBackground = {
                try {
                    val intent = Intent(this, com.snowrealm.yukiboard.ui.settings.BackgroundPickerActivity::class.java).apply {
                        putExtra(com.snowrealm.yukiboard.ui.settings.BackgroundPickerActivity.EXTRA_PICK_TYPE, "image")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            p.onPickVideo = {
                try {
                    val intent = Intent(this, com.snowrealm.yukiboard.ui.settings.BackgroundPickerActivity::class.java).apply {
                        putExtra(com.snowrealm.yukiboard.ui.settings.BackgroundPickerActivity.EXTRA_PICK_TYPE, "video")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            p.onClearBackground = {
                val dir = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getBackgroundDir(this)
                dir.listFiles()?.forEach { it.delete() }
                prefs.useCustomBackground = false
                backgroundView?.reloadFromStorage()
                keyboardView.hasCustomBackground = false
                keyboardView.invalidate()
                p.previewBgBitmap = null
            }
            p.onOverlayAlphaChanged = { alpha ->
                backgroundView?.overlayAlpha = alpha
                keyboardView.bgOverlayAlpha = alpha
                keyboardView.invalidate()
            }
            p.onLabelColorChanged = { color ->
                keyboardView.customKeyLabelColor = color
                keyboardView.invalidate()
            }
            p.onBackgroundSettingsChanged = {
                backgroundView?.let { bg ->
                    bg.scaleMode = prefs.bgScaleMode
                    bg.offsetX = prefs.bgOffsetX
                    bg.offsetY = prefs.bgOffsetY
                    bg.bgRotation = prefs.bgRotation
                    bg.reloadFromStorage()
                }
                keyboardView.invalidate()
                sendBroadcast(android.content.Intent("com.snowrealm.yukiboard.ACTION_BG_UPDATED").setPackage(packageName))
            }
            p.onKeySettingsChanged = {
                keyboardView.keyScalePercent = prefs.keyScalePercent
                keyboardView.keyOpacity = prefs.keyOpacity
                keyboardView.keyTextOpacity = prefs.keyTextOpacity
                applyTransparentMode()
                val bgHasNow = backgroundView?.reloadFromStorage() ?: false
                val glassKeysNow = (prefs.useCustomBackground && bgHasNow) || prefs.transparentKeyboard
                keyboardView.hasCustomBackground = glassKeysNow
                keyboardView.invalidate()
            }
            p.onPerKeySizeChanged = {
                keyboardView.perKeySizes = prefs.getAllPerKeySizes()
                keyboardView.invalidate()
            }
            p.onKeyboardHeightChanged = {
                closePanel()
                backgroundView?.destroy()
                backgroundView = null
                setInputView(onCreateInputView())
            }
            p
        }
        PanelType.CLAW -> {
            val p = ClawPanelView(this)
            p.theme = theme
            p.clawClient = clawClient
            p.prefs = prefs
            p.setCurrentMode(
                when (keyboardView.currentMode) {
                    KeyboardMode.DEVELOPER -> "developer"
                    else -> "normal"
                }
            )
            p.inputTextProvider = {
                val ic = currentInputConnection
                val selected = ic?.getSelectedText(0)?.toString()
                if (!selected.isNullOrEmpty()) selected
                else ic?.getTextBeforeCursor(500, 0)?.toString() ?: ""
            }
            p.appPackageProvider = {
                inputRouter.currentAppPackage ?: ""
            }
            p.keyboardModeProvider = {
                keyboardView.currentMode
            }
            p.onSuggestionSelected = { text ->
                val ic = currentInputConnection
                if (ic != null) {
                    ic.performContextMenuAction(android.R.id.selectAll)
                    ic.commitText(text, 1)
                }
                clawResultFloatingPanel?.showResult("AI", listOf(text))
            }
            p.onClose = { closePanel() }
            p.onLockTapped = {
                startActivity(
                    Intent(this, com.snowrealm.yukiboard.ui.auth.InviteCodeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            p
        }
        PanelType.USER_DICT -> {
            val p = UserDictionaryPanelView(this).also { imePanelBindings.userDictPanel = it }
            p.theme = theme
            p.userDictionary = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)
            p.onShowDialog = { dialog -> showImeDialog(dialog) }
            p.onExportRequest = { data ->
                try {
                    val file = java.io.File(getExternalFilesDir(null), "yukiboard_user_dict.txt")
                    file.writeText(data)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this, "$packageName.fileprovider", file
                    )
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "YukiBoard 個人詞庫")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(android.content.Intent.createChooser(shareIntent, "匯出詞庫").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (e: Exception) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(this, "匯出失敗: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            p.onImportRequest = {
                try {
                    val file = java.io.File(getExternalFilesDir(null), "yukiboard_user_dict.txt")
                    if (file.exists()) {
                        val count = p.importEntries(file.readText())
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(this, "已匯入 $count 筆詞彙", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(this, "找不到匯入檔案：${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(this, "匯入失敗: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            p.enterInputMode()
            p
        }
        PanelType.MUSIC -> {
            val p = MusicPanelView(this)
            p.theme = theme
            p.soundEngine = keySoundEngine
            p.onImportMidi = {
                com.snowrealm.yukiboard.ui.MidiImportActivity.onMidiImported = { midiSong ->
                    val song = MusicPanelView.Song(
                        name = midiSong.name,
                        artist = "MIDI",
                        bpm = 120,
                        notes = midiSong.notes.map { n ->
                            MusicPanelView.SongNote(n.semitone, n.startMs, n.durationMs)
                        }
                    )
                    android.os.Handler(android.os.Looper.getMainLooper()).post { p.addImportedSong(song) }
                }
                try {
                    val intent = android.content.Intent(this, com.snowrealm.yukiboard.ui.MidiImportActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            p
        }
        PanelType.HANDWRITING -> {
            if (handwritingEngine == null) {
                handwritingEngine = com.snowrealm.yukiboard.input.handwriting.HandwritingEngine(this).also { it.init() }
            }
            val p = HandwritingPanelView(this, handwritingEngine!!).also { imePanelBindings.handwritingPanel = it }
            p.theme = theme
            p.onCandidateSelected = { text ->
                currentInputConnection?.commitText(text, 1)
                keyboardView.updateCandidates(emptyList())
                inputRouter.onHandwritingCommit(text)
            }
            p.onRecognized = { rawCandidates ->
                if (rawCandidates.isNotEmpty()) {
                    val boosted = rawCandidates.sortedByDescending { word ->
                        inputRouter.smartInput.adaptiveDictionary.getFrequency(word)
                    }
                    val sourceMap = boosted.associateWith {
                        com.snowrealm.yukiboard.input.router.CandidateSource.DICTIONARY
                    }
                    keyboardView.updateCandidates(boosted, sourceMap)
                }
            }
            p.onBackspace = {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
            p.onConfirm = {
                sendDefaultEditorAction(true)
            }
            p.ensureReady { ready ->
                if (!ready) {
                    android.widget.Toast.makeText(this, "手寫模型下載中…", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            p
        }
        else -> null
    }
}

internal fun YukiBoardIMEService.extractPanelSourceText(): String {
    val ic = currentInputConnection
    val selected = ic?.getSelectedText(0)?.toString()?.trim().orEmpty()
    if (selected.isNotEmpty()) {
        return selected.take(200)
    }
    val before = ic?.getTextBeforeCursor(200, 0)?.toString()?.trim().orEmpty()
    if (before.isNotEmpty()) {
        return before.takeLast(100)
    }
    val after = ic?.getTextAfterCursor(200, 0)?.toString()?.trim().orEmpty()
    return after.take(100)
}

internal fun YukiBoardIMEService.bindFactoryPanelCreation(creation: ImePanelCreation?) {
    imePanelBindings.bindCreation(creation)
}

internal fun YukiBoardIMEService.clearActivePanelBindings() {
    imePanelBindings.clearInputModeBindings()
}

internal fun YukiBoardIMEService.resetInputUiStateForPanelTransition() {
    inputRouter.reset()
    keyboardView.updateComposingText("")
    keyboardView.updateCandidates(emptyList())
}

internal fun YukiBoardIMEService.restoreKeyboardFocusAfterPanelClose() {
    keyboardView.isEnabled = true
    keyboardView.requestFocus()
    keyboardView.resetTouchState()
}

internal fun YukiBoardIMEService.showPanelControlBar() {
    imePanelControlBar.show(rootContainer, toolbarView, panelContainer, ::togglePanelLayer)
}

internal fun YukiBoardIMEService.hidePanelControlBar() {
    imePanelControlBar.hide(rootContainer)
    panelContainer.alpha = 1f
}

