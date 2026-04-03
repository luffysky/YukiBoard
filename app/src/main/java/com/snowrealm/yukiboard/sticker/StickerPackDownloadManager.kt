package com.snowrealm.yukiboard.sticker

import android.content.Context
import kotlinx.coroutines.*

/**
 * 貼圖包下載管理 — 追蹤下載進度，提供 UI 回呼。
 *
 * 透過 [StickerRepository] 取得貼圖包清單，
 * 逐張下載圖片到 [StickerImageCache]。
 */
class StickerPackDownloadManager(context: Context) {

    private val repo = StickerRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 下載狀態 */
    enum class DownloadState { IDLE, DOWNLOADING, COMPLETED, FAILED }

    data class DownloadProgress(
        val packId: String,
        val packName: String,
        val state: DownloadState,
        val downloaded: Int,
        val total: Int
    ) {
        val progressPercent: Int
            get() = if (total > 0) (downloaded * 100 / total) else 0
    }

    private val progressMap = mutableMapOf<String, DownloadProgress>()

    var onProgressUpdated: ((DownloadProgress) -> Unit)? = null
    var onAllCompleted: (() -> Unit)? = null

    /** 下載指定貼圖包的所有圖片 */
    fun downloadPack(pack: RemoteStickerPack) {
        val total = pack.stickers.size + 1 // +1 for cover
        val progress = DownloadProgress(pack.id, pack.name, DownloadState.DOWNLOADING, 0, total)
        progressMap[pack.id] = progress
        onProgressUpdated?.invoke(progress)

        scope.launch {
            try {
                var count = 0

                // 下載封面
                if (pack.coverUrl.isNotEmpty()) {
                    repo.imageCache.getBitmap(pack.coverUrl)
                }
                count++
                updateProgress(pack.id, count, total)

                // 下載每張貼圖
                for (sticker in pack.stickers) {
                    if (!sticker.url.startsWith("local:")) {
                        repo.imageCache.getBitmap(sticker.url)
                    }
                    count++
                    updateProgress(pack.id, count, total)
                }

                markCompleted(pack.id, total)
            } catch (_: Exception) {
                markFailed(pack.id)
            }
        }
    }

    /** 下載所有貼圖包 */
    fun downloadAllPacks() {
        scope.launch {
            // 先同步貼圖包清單
            repo.syncInBackground()
            delay(500) // 等同步完成
            repo.packs.forEach { downloadPack(it) }
        }
    }

    /** 查詢指定包的下載進度 */
    fun getProgress(packId: String): DownloadProgress? = progressMap[packId]

    /** 是否正在下載中 */
    fun isDownloading(): Boolean =
        progressMap.values.any { it.state == DownloadState.DOWNLOADING }

    /** 取得已下載的貼圖包 ID 列表 */
    fun getCompletedPackIds(): Set<String> =
        progressMap.filter { it.value.state == DownloadState.COMPLETED }.keys

    fun destroy() {
        scope.cancel()
    }

    private fun updateProgress(packId: String, downloaded: Int, total: Int) {
        val existing = progressMap[packId] ?: return
        val updated = existing.copy(downloaded = downloaded)
        progressMap[packId] = updated
        onProgressUpdated?.invoke(updated)
    }

    private fun markCompleted(packId: String, total: Int) {
        val existing = progressMap[packId] ?: return
        val updated = existing.copy(state = DownloadState.COMPLETED, downloaded = total)
        progressMap[packId] = updated
        onProgressUpdated?.invoke(updated)

        if (progressMap.values.all { it.state != DownloadState.DOWNLOADING }) {
            onAllCompleted?.invoke()
        }
    }

    private fun markFailed(packId: String) {
        val existing = progressMap[packId] ?: return
        val updated = existing.copy(state = DownloadState.FAILED)
        progressMap[packId] = updated
        onProgressUpdated?.invoke(updated)
    }
}
