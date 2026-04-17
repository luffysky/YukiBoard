# Sprint 9 Workstream A — CCB Insight SSE Stream Fix 回交

身份：Claude Code B
任務 ID：`S9-A-CCB-INSIGHT-SSE-FIX`
日期：2026-04-18

對應 `Sprint9-All-Round-Gate.md` Workstream A：修 `InsightApiClient.stream()` 的 SSE 解析，正確處理 chunked / partial line / partial JSON。不改 ai-proxy 協定、不擴到 provider business logic。

---

## A. 變更摘要

### 修改檔案（1 個，僅在 S9-A 允許 write scope 內）

- `app/src/main/java/com/snowrealm/yukiboard/data/InsightApiClient.kt`

### 問題盤點（舊版 `stream()` bugs）

1. **UTF-8 字節邊界切斷**：`source.read(buffer, 8192)` 按字節讀最多 8192 byte，`buffer.readUtf8()` 對剛好落在 multi-byte codepoint 中段的字節會解碼失敗或產生 replacement char。中文／日文／韓文 streaming output 受影響最大。
2. **Partial line 當完整 JSON 解析**：每次 read 後做 `line.split("\n").forEach`，最後一個 fragment 通常是未收完的下一行；仍然會 `startsWith("data:")` 比對並嘗試 `json.parseToJsonElement(data)`，失敗時被 `catch (_: Exception) {}` 靜默吞掉 → 整包 chunk 掉。
3. **無 SSE event 邊界語意**：SSE spec 規定以 blank line（`\n\n`）當 event 終止符，多個連續 `data:` 欄位應以 `\n` 串成一個 event payload。舊版每遇到一行 `data:` 就立刻 dispatch，multi-line data 欄位會被當成獨立 chunk 各自解析，JSON 全數失敗。
4. **`[DONE]` 未終止循環**：`if (data == "[DONE]") return@forEach` 只跳出 `forEach` block，外層 `while (!source.exhausted())` 繼續讀，導致伺服器若在 `[DONE]` 後還送了東西（keep-alive、註解、其它 event）會被當成正常 chunk 處理。
5. **Response 未 close**：`if (!response.isSuccessful)` 與 `run { onError("empty body") }` 兩條 early-return 都沒關 response，OkHttp connection pool 漏 socket。

### Fix 策略（minimal scope，僅 `stream()` 方法 + 一個私有 helper）

- 改用 `BufferedSource.readUtf8Line()`：okio 保證按 `\n` / `\r\n` 切行、正確處理 UTF-8 codepoint 邊界；EOF 回 `null`。
- 維護 `StringBuilder dataBuffer`：遇 `data:` 欄位追加到 buffer（多行以 `\n` 連接），遇 blank line 才 flush 成一個完整 event payload 並 JSON parse。
- `[DONE]`：在 flush 時若 payload 是 `[DONE]`，設 `done = true` 跳出外層 while。
- Response 以 `httpClient.newCall(request).execute().use { }` 包住，任何退出路徑（early-return / 例外 / 正常結束）都會 close。
- SSE comment 行（`:`-prefix）與未用的 `event:`/`id:`/`retry:` 欄位直接略過。

### Fix 細節

```kotlin
httpClient.newCall(request).execute().use { response ->
    if (!response.isSuccessful) {
        onError("HTTP ${response.code}")
        return@use
    }
    val source = response.body?.source() ?: run {
        onError("empty body")
        return@use
    }
    val dataBuffer = StringBuilder()
    var done = false
    while (!done) {
        val line = source.readUtf8Line() ?: break
        if (line.isEmpty()) {
            if (dataBuffer.isEmpty()) continue
            val data = dataBuffer.toString()
            dataBuffer.setLength(0)
            if (data == "[DONE]") { done = true; continue }
            dispatchSseData(data, onChunk)
            continue
        }
        if (line.startsWith(":")) continue
        if (line.startsWith("data:")) {
            val value = line.substring(5).let {
                if (it.startsWith(" ")) it.substring(1) else it
            }
            if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
            dataBuffer.append(value)
        }
    }
    if (dataBuffer.isNotEmpty() && !done) {
        val data = dataBuffer.toString()
        if (data != "[DONE]") dispatchSseData(data, onChunk)
    }
    onDone()
}
```

```kotlin
private fun dispatchSseData(data: String, onChunk: (String) -> Unit) {
    try {
        val chunk = json.parseToJsonElement(data).jsonObject
        val text = chunk["text"]?.jsonPrimitive?.content
        if (!text.isNullOrEmpty()) onChunk(text)
    } catch (_: Exception) {
        // 單一 event payload malformed 不中斷 stream
    }
}
```

---

## B. 風險

1. **ai-proxy 契約未變**：仍從 event payload 讀 `"text"` 欄位，仍認 `[DONE]` sentinel，對 server 端零變更。
2. **行為更嚴格於 SSE spec 的子集**：只 dispatch `text` 欄位，其他 JSON 欄位（role / function_call / tool_use 等）仍會靜默略過（與舊版一致）。若 server 未來新增 event type，須另開 round 擴協定，不屬本輪 scope。
3. **`dispatchSseData` 吞 parse 例外**：與舊版行為一致，避免單一 malformed chunk 中斷整串 stream。若要區分「某 chunk 壞掉」與「整個 stream 壞掉」，需新增 error 分類，本輪不擴。
4. **`readUtf8Line()` blocks 直到見到 `\n` 或 EOF**：與舊版 `while (!exhausted)` 同屬阻塞式讀取，caller 已在 `withContext(Dispatchers.IO)`，無 thread starvation 疑慮。Socket timeout 由 `AppHttpClient` 配置，行為與舊版一致。
5. **response.use 關閉路徑**：若 `onChunk` / `onDone` / `onError` 回呼本身拋例外，`use` 的 finally 仍會 close response，但例外會冒泡到外層 `catch (e: Exception)` 觸發第二次 `onError`。目前 caller 的回呼都只更新 UI state，不會拋例外；若未來有 caller 回呼可能拋，需額外包裝，非本輪 scope。
6. **write scope 嚴守**：本輪未碰 `CloudSyncManager.kt` / `SnowRealmAuthClient.kt` / `AppHttpClient`；未動其他 caller；未改任何 `Request.Builder` / `buildRequest()` 邏輯。

---

## C. 下一步建議

**不自排後續 workstream**，候選給 `CodeX A` 決定：

- **候選 A — S9-A 結案**：本輪 fix 僅限 `stream()`，若 `CodeX B` tech review 與 `Claude Code A` impact note 均通過即可 gate READY。
- **候選 B — 非 blocking 強化**：
  - `AppHttpClient` 的 read timeout 對 SSE 長 stream 是否適當，可在 Sprint 10+ 另行檢視
  - SSE event 欄位擴協定（處理 `event:` 讓 server 能推送 tool-use / metadata chunk），需 ai-proxy 契約變更，不在 S9-A scope
- **候選 C — S9-B / S9-C / S9-D 平行進度**：CCB 另外兩個 task（S9-B crosscheck 等 CXB 實作；S9-C 實作等 CCA design note + CodeX A 核可）本輪仍在等相依輸入。

---

## D. 架構決策

- **Scope 內聚在 `InsightApiClient.stream()` 與一個私有 helper**：不抽出獨立 SSE parser class；目前僅一個 caller、一個 payload shape，獨立類別會是過度設計。若未來出現第二個 SSE caller，再評估抽取。
- **沿用既有 `ignoreUnknownKeys = true` Json instance**：不新增 serializer / domain model。
- **未新增 coordinator / repository / interface**：符合 `CLAUDE.md`「單一狀態來源、依賴更可追蹤」與「不擴結構」。

---

## E. 驗證

1. `./gradlew.bat :app:compileDebugKotlin --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL in 57s`。
2. 無新 warning / error（log 中的 deprecation / `ImeServiceLifecycle.kt:109` 均為 pre-existing）。
3. 行為驗證（靜態推導）：
   - **UTF-8 多字節字元跨 read 邊界**：`readUtf8Line()` 會等到見到 `\n` 才切行，codepoint 不會被腰斬 ✓
   - **Partial line**：舊版會把最後一段未完 fragment 當完整行處理；新版 `readUtf8Line()` 只會在見到 `\n` 時 return 該行，未完的留在 buffer 等下一次 read，不會誤判 ✓
   - **Multi-line `data:` event**：
     - server 送 `data: hello\ndata: world\n\n` → 新版累積成 `hello\nworld` 才 dispatch；舊版會把 `hello`、`world` 各自當 JSON 解析失敗 → 全數掉 chunk
   - **`[DONE]` 終止**：新版 `done = true` 跳出外層 while，不再讀；舊版 `return@forEach` 僅跳出內層，外層繼續 drain ✓
   - **HTTP 非 2xx**：`response.use` 自動 close；舊版漏 close ✓
   - **空 body**：早退路徑也走 `use`，會 close ✓
   - **JSON malformed 的單一 event**：`dispatchSseData` 吞例外，不中斷 stream，與舊版一致 ✓
   - **SSE comment / 其他欄位**：`:`-prefix 行略過；`event:` / `id:` / `retry:` 不 match `data:` 分支也略過 ✓
4. 只動到 S9-A 允許 scope 內且為最小子集：
   - `data/InsightApiClient.kt` ✅
   - 未碰 `CloudSyncManager.kt` ✅
   - 未碰 `SnowRealmAuthClient.kt` ✅
   - 未碰 `AppHttpClient` / `UserSessionRepository` ✅
5. 未觸碰其他 workstream 的 write scope（與 S9-B / S9-C / S9-D 零檔案重疊）✅

---

## CodeX A 審核需求

- 需 `CodeX A` gate：`S9-A` 是否可結案；最終以 `CodeX B` tech review + `Claude Code A` impact note + 本回交三文件為準。
- Claude Code B 不自排後續 workstream；其他兩條 CCB 負責線（S9-B crosscheck / S9-C impl）持續等相依輸入。
