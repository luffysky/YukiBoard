package com.snowrealm.yukiboard.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.snowrealm.yukiboard.music.MidiParser

/**
 * 透明 Activity — 從 IME Service 啟動檔案選擇器匯入 MIDI。
 *
 * 因為 InputMethodService 無法 startActivityForResult，
 * 所以用這個輕量 Activity 做中介。
 */
class MidiImportActivity : Activity() {

    companion object {
        private const val REQUEST_MIDI = 7001
        /** 匯入結果回呼（靜態，因為 IME Service 設定後此 Activity 讀取） */
        var onMidiImported: ((MidiParser.MidiSong) -> Unit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/midi", "audio/x-midi", "application/x-midi",
                "application/octet-stream" // 部分檔案管理器將 .mid 歸類為此
            ))
        }
        try {
            startActivityForResult(intent, REQUEST_MIDI)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "無法開啟檔案選擇器", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MIDI && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val song = MidiParser.parse(stream, fileName)
                        if (song != null) {
                            // 轉成 MusicPanelView.Song 格式
                            val panelSong = com.snowrealm.yukiboard.ui.panel.MusicPanelView.Song(
                                name = song.name,
                                artist = "MIDI 匯入",
                                bpm = 120,
                                notes = song.notes.map { n ->
                                    com.snowrealm.yukiboard.ui.panel.MusicPanelView.SongNote(
                                        n.semitone, n.startMs, n.durationMs
                                    )
                                }
                            )
                            onMidiImported?.invoke(song)
                            android.widget.Toast.makeText(this, "已匯入: ${song.name} (${song.notes.size} 音符)", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(this, "MIDI 解析失敗", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "匯入失敗: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        finish()
    }
}
