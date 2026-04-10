package com.snowrealm.yukiboard.music

import java.io.InputStream

/**
 * 輕量 MIDI 檔案解析器 — 從 .mid 檔提取音符序列。
 *
 * 只支援 Standard MIDI File (SMF) format 0/1。
 * 解析 Note On/Off 事件，回傳 (semitone relative to A4, startMs, durationMs) 列表。
 */
object MidiParser {

    data class MidiNote(val semitone: Int, val startMs: Long, val durationMs: Long)
    data class MidiSong(val name: String, val notes: List<MidiNote>, val durationMs: Long)

    /**
     * 解析 MIDI 檔案輸入流，回傳音符列表。
     * semitone 以 A4(440Hz)=0 為基準，即 MIDI note 69 = 0。
     */
    fun parse(input: InputStream, fileName: String = "匯入曲目"): MidiSong? {
        return try {
            val data = input.readBytes()
            parseMidi(data, fileName)
        } catch (e: Exception) {
            android.util.Log.e("MidiParser", "Parse failed: ${e.message}")
            null
        }
    }

    private fun parseMidi(data: ByteArray, fileName: String): MidiSong? {
        if (data.size < 14) return null

        // 檢查 MThd header
        val header = String(data, 0, 4)
        if (header != "MThd") return null

        val headerLen = readInt32(data, 4)
        val format = readInt16(data, 8)
        val numTracks = readInt16(data, 10)
        val division = readInt16(data, 12)

        if (format > 1) return null // 不支援 format 2

        // 解析所有 track
        var offset = 8 + headerLen
        val allEvents = mutableListOf<RawEvent>()

        for (t in 0 until numTracks) {
            if (offset + 8 > data.size) break
            val trackHeader = String(data, offset, 4)
            if (trackHeader != "MTrk") break
            val trackLen = readInt32(data, offset + 4)
            offset += 8
            val trackEnd = offset + trackLen

            val events = parseTrack(data, offset, trackEnd)
            allEvents.addAll(events)
            offset = trackEnd
        }

        if (allEvents.isEmpty()) return null

        // 排序事件並轉換 tick → ms
        allEvents.sortBy { it.tick }

        // 處理 tempo 事件
        val tempoChanges = allEvents.filter { it.type == EventType.TEMPO }
            .sortedBy { it.tick }
        val defaultTempo = 500000 // 120 BPM in microseconds per beat

        // 建立 tick → ms 轉換
        val ticksPerBeat = if (division > 0) division else 480

        // 配對 Note On/Off → MidiNote
        val noteOnMap = mutableMapOf<Int, MutableList<Pair<Long, Int>>>() // key → [(tick, velocity)]
        val notes = mutableListOf<MidiNote>()

        for (event in allEvents) {
            when (event.type) {
                EventType.NOTE_ON -> {
                    if (event.velocity > 0) {
                        noteOnMap.getOrPut(event.note) { mutableListOf() }
                            .add(event.tick to event.velocity)
                    } else {
                        // velocity 0 的 Note On = Note Off
                        handleNoteOff(event.note, event.tick, noteOnMap, notes, ticksPerBeat, tempoChanges, defaultTempo)
                    }
                }
                EventType.NOTE_OFF -> {
                    handleNoteOff(event.note, event.tick, noteOnMap, notes, ticksPerBeat, tempoChanges, defaultTempo)
                }
                else -> {}
            }
        }

        // 未關閉的音符也加入
        for ((note, onList) in noteOnMap) {
            for ((onTick, _) in onList) {
                val startMs = tickToMs(onTick, ticksPerBeat, tempoChanges, defaultTempo)
                notes.add(MidiNote(note - 69, startMs, 200))
            }
        }

        if (notes.isEmpty()) return null
        notes.sortBy { it.startMs }

        // 正規化：讓第一個音符從 0ms 開始
        val firstMs = notes.first().startMs
        val normalized = notes.map { it.copy(startMs = it.startMs - firstMs) }
        val totalDur = normalized.maxOf { it.startMs + it.durationMs }

        val displayName = fileName.removeSuffix(".mid").removeSuffix(".midi")
            .replace("_", " ").replace("-", " ")

        return MidiSong(displayName, normalized, totalDur)
    }

    private fun handleNoteOff(
        note: Int, offTick: Long,
        noteOnMap: MutableMap<Int, MutableList<Pair<Long, Int>>>,
        notes: MutableList<MidiNote>,
        ticksPerBeat: Int,
        tempoChanges: List<RawEvent>,
        defaultTempo: Int
    ) {
        val onList = noteOnMap[note] ?: return
        if (onList.isEmpty()) return
        val (onTick, _) = onList.removeAt(0)
        val startMs = tickToMs(onTick, ticksPerBeat, tempoChanges, defaultTempo)
        val endMs = tickToMs(offTick, ticksPerBeat, tempoChanges, defaultTempo)
        val durMs = (endMs - startMs).coerceAtLeast(50)
        notes.add(MidiNote(note - 69, startMs, durMs)) // MIDI note 69 = A4 = semitone 0
    }

    private fun tickToMs(tick: Long, ticksPerBeat: Int, tempoChanges: List<RawEvent>, defaultTempo: Int): Long {
        var ms = 0.0
        var lastTick = 0L
        var tempo = defaultTempo.toDouble()

        for (tc in tempoChanges) {
            if (tc.tick >= tick) break
            ms += (tc.tick - lastTick) * tempo / ticksPerBeat / 1000.0
            lastTick = tc.tick
            tempo = tc.tempoUs.toDouble()
        }
        ms += (tick - lastTick) * tempo / ticksPerBeat / 1000.0
        return ms.toLong()
    }

    // ── 內部資料結構 ──

    private enum class EventType { NOTE_ON, NOTE_OFF, TEMPO, OTHER }

    private data class RawEvent(
        val tick: Long,
        val type: EventType,
        val note: Int = 0,
        val velocity: Int = 0,
        val tempoUs: Int = 500000
    )

    private fun parseTrack(data: ByteArray, start: Int, end: Int): List<RawEvent> {
        val events = mutableListOf<RawEvent>()
        var pos = start
        var tick = 0L
        var runningStatus = 0

        while (pos < end) {
            // 讀 delta time (variable length)
            val (delta, deltaLen) = readVarLen(data, pos)
            pos += deltaLen
            tick += delta

            if (pos >= end) break

            var statusByte = data[pos].toInt() and 0xFF

            // Running status：如果不是 status byte，使用上一個
            if (statusByte < 0x80) {
                statusByte = runningStatus
            } else {
                runningStatus = statusByte
                pos++
            }

            val channel = statusByte and 0x0F
            val msgType = statusByte and 0xF0

            when (msgType) {
                0x90 -> { // Note On
                    if (pos + 1 >= end) break
                    val note = data[pos].toInt() and 0xFF
                    val velocity = data[pos + 1].toInt() and 0xFF
                    pos += 2
                    events.add(RawEvent(tick, EventType.NOTE_ON, note, velocity))
                }
                0x80 -> { // Note Off
                    if (pos + 1 >= end) break
                    val note = data[pos].toInt() and 0xFF
                    val velocity = data[pos + 1].toInt() and 0xFF
                    pos += 2
                    events.add(RawEvent(tick, EventType.NOTE_OFF, note, velocity))
                }
                0xA0, 0xB0, 0xE0 -> pos += 2 // Aftertouch, CC, Pitch Bend
                0xC0, 0xD0 -> pos += 1        // Program Change, Channel Pressure
                0xF0 -> {
                    if (statusByte == 0xFF) { // Meta event
                        if (pos + 1 >= end) break
                        val metaType = data[pos].toInt() and 0xFF
                        pos++
                        val (metaLen, metaLenBytes) = readVarLen(data, pos)
                        pos += metaLenBytes
                        if (metaType == 0x51 && metaLen >= 3 && pos + 2 < end) {
                            // Tempo event
                            val tempoUs = ((data[pos].toInt() and 0xFF) shl 16) or
                                    ((data[pos + 1].toInt() and 0xFF) shl 8) or
                                    (data[pos + 2].toInt() and 0xFF)
                            events.add(RawEvent(tick, EventType.TEMPO, tempoUs = tempoUs))
                        }
                        pos += metaLen.toInt()
                    } else if (statusByte == 0xF0 || statusByte == 0xF7) {
                        // SysEx
                        val (sysLen, sysLenBytes) = readVarLen(data, pos)
                        pos += sysLenBytes + sysLen.toInt()
                    }
                }
            }
        }
        return events
    }

    private fun readVarLen(data: ByteArray, start: Int): Pair<Long, Int> {
        var value = 0L
        var pos = start
        var bytesRead = 0
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F).toLong()
            bytesRead++
            pos++
            if (b and 0x80 == 0) break
            if (bytesRead > 4) break
        }
        return value to bytesRead
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun readInt16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
    }
}
