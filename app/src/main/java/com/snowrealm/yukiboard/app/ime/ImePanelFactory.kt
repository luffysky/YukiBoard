package com.snowrealm.yukiboard.app.ime

import android.app.AlertDialog
import android.content.Context
import com.snowrealm.yukiboard.gif.GiphyApiClient
import com.snowrealm.yukiboard.sticker.StickerPackDownloadManager
import com.snowrealm.yukiboard.sticker.StickerRepository
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.translate.TranslateEngine
import com.snowrealm.yukiboard.ui.panel.DecryptPanelView
import com.snowrealm.yukiboard.ui.panel.DictionaryPanelView
import com.snowrealm.yukiboard.ui.panel.GifPanelView
import com.snowrealm.yukiboard.ui.panel.LangLearnPanelView
import com.snowrealm.yukiboard.ui.panel.PanelType
import com.snowrealm.yukiboard.ui.panel.StickerPanelView
import com.snowrealm.yukiboard.ui.panel.TranslatePanelView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 第二批 panel factory。
 *
 * 先委派給 basic factory；若 basic 無法處理，再嘗試建立中等依賴的 panel。
 * 建不到仍由 service fallback。
 */
class ImePanelFactory(
    private val basicFactory: ImeBasicPanelFactory = ImeBasicPanelFactory(),
) {

    fun createOrNull(
        context: Context,
        panel: PanelType,
        theme: KeyboardTheme,
        commitText: (String) -> Unit,
        showDialog: (AlertDialog) -> Unit,
        commitImageContent: (String) -> Unit,
        translateEngine: TranslateEngine?,
        gifClient: GiphyApiClient?,
        stickerRepository: StickerRepository?,
        stickerDownloadManager: StickerPackDownloadManager?,
        shouldSyncStickerRepository: Boolean,
        onStickerRepositorySynced: () -> Unit,
        scope: CoroutineScope,
        sourceTextProvider: () -> String,
        onTranslateSpeakRequest: (String, String) -> Unit,
        inputTextProvider: () -> String,
        onSpeak: (String, String?) -> Unit,
    ): ImePanelCreation? {
        basicFactory.createOrNull(
            context = context,
            panel = panel,
            theme = theme,
            commitText = commitText,
        )?.let { return it }

        return when (panel) {
            PanelType.TRANSLATE -> {
                val panelView = TranslatePanelView(context).apply {
                    this.theme = theme
                    onShowDialog = showDialog
                    val source = sourceTextProvider()
                    if (source.isNotEmpty()) {
                        sourceText = source
                    }
                    enterInputMode()
                    onTranslatedSelected = commitText
                    onInsertToApp = commitText
                    onSpeakRequest = onTranslateSpeakRequest
                    if (translateEngine != null) {
                        translationProvider = { src, fromLang, toLang, callback ->
                            scope.launch {
                                try {
                                    callback(translateEngine.translate(src, fromLang, toLang))
                                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    callback("翻譯失敗: ${e.message}")
                                }
                            }
                        }
                    }
                }
                ImePanelCreation(
                    panelView = panelView,
                    translatePanel = panelView,
                )
            }
            PanelType.GIF -> {
                val panelView = GifPanelView(context).apply {
                    this.theme = theme
                    onShowDialog = showDialog
                    onGifSelected = { gif -> commitImageContent(gif.fullUrl) }
                    if (gifClient != null && gifClient.isAvailable()) {
                        onSearch = { query, callback ->
                            scope.launch {
                                try {
                                    val results = if (query.isBlank()) gifClient.fetchTrending() else gifClient.search(query)
                                    callback(results)
                                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    callback(emptyList())
                                }
                            }
                        }
                    }
                    enterInputMode()
                }
                ImePanelCreation(
                    panelView = panelView,
                    gifPanel = panelView,
                )
            }
            PanelType.DECRYPT -> {
                val panelView = DecryptPanelView(context).apply {
                    this.theme = theme
                    onEncryptedResult = commitText
                    enterInputMode()
                }
                ImePanelCreation(
                    panelView = panelView,
                    decryptPanel = panelView,
                )
            }
            PanelType.STICKER -> {
                val panelView = StickerPanelView(context).apply {
                    this.theme = theme
                    repository = stickerRepository
                    downloadManager = stickerDownloadManager
                    if (shouldSyncStickerRepository) {
                        stickerRepository?.syncInBackground()
                        onStickerRepositorySynced()
                    }
                    onStickerSelected = { stickerText ->
                        if (stickerText.startsWith("[STICKER]:")) {
                            commitImageContent(stickerText.removePrefix("[STICKER]:"))
                        } else {
                            commitText(stickerText)
                        }
                    }
                }
                ImePanelCreation(
                    panelView = panelView,
                    stickerPanel = panelView,
                )
            }
            PanelType.DICTIONARY -> {
                val panelView = DictionaryPanelView(context).apply {
                    this.theme = theme
                    this.inputTextProvider = inputTextProvider
                    this.onInsertText = commitText
                    this.onSpeak = onSpeak
                }
                ImePanelCreation(
                    panelView = panelView,
                    dictionaryPanel = panelView,
                )
            }
            PanelType.LANG_LEARN -> {
                val panelView = LangLearnPanelView(context).apply {
                    this.theme = theme
                    this.inputTextProvider = inputTextProvider
                    this.onInsertText = commitText
                    this.onSpeak = onSpeak
                }
                ImePanelCreation(
                    panelView = panelView,
                    langLearnPanel = panelView,
                )
            }
            else -> null
        }
    }
}
