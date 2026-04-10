# YukiBoard 工作日誌 — 2026-04-10

> 起始版本：v0.9.42 (108) → 結束版本：v0.9.46 (112)
> 全部已推送 GitHub + Firebase App Distribution (testers)

---

## 今日完成項目

### 1. 手寫辨識面板 — ML Kit Digital Ink（v0.9.43~v0.9.45）

**PR1 — 基礎手寫辨識（v0.9.43）**
- 新增 `HandwritingEngine.kt` — ML Kit Digital Ink 封裝，支援繁中/英/日/簡中 + 混合辨識
- 新增 `StrokeCollector.kt` — MotionEvent → Ink 轉換器
- 新增 `HandwritingPanelView.kt` — Canvas 手寫面板
  - 控制列：4 語言按鈕（中/英/日/混合）+ 3 操作按鈕（清除/⌫/↩）
  - 候選列：辨識結果候選字卡片
  - 手寫區域：十字虛線參考線 + 筆畫渲染
  - 停筆 1.2s 自動辨識
- PanelType 新增 `HANDWRITING("✍", "手寫")`
- ToolbarView AI 工具群組加入手寫按鈕
- IMEService 接線：lazy init 引擎、模型自動下載、commitText/退格/確認
- `build.gradle` 新增 `com.google.mlkit:digital-ink-recognition:18.1.0`

**PR2 — 手寫進階功能（v0.9.45）**
- StrokeCollector 支援 undo（保留每筆 stroke 可回退重建 Ink）
- 撤銷按鈕（↶）移除最後一筆並重新辨識
- 連續手寫模式：辨識後 2.5s 自動送出第一候選 + 清畫布繼續寫
- 壓感筆畫粗細：根據 pressure 值調整 2.5~7dp 筆寬
- 筆畫計數顯示（右下角小字）
- 第一候選字高亮（連續模式下藍底白字）

**PR3 — 語言模型管理（v0.9.45）**
- HandwritingEngine 新增 `deleteModel()` + `checkAllModelStatus()`
- 控制列 ⚙ 按鈕展開模型管理列
- 已下載（✓ 綠色）/ 下載中（⏳ 橘色）/ 未下載（↓ 灰色）
- 點擊已下載模型 → 刪除，點擊未下載 → 下載

### 2. 選圖片/影片改用系統內建相簿（v0.9.44）
- `BackgroundPickerActivity` — `ACTION_GET_CONTENT` + Chooser → `ACTION_PICK` + MediaStore URI
- `SettingsActivity` — 同步改用 `ACTION_PICK`
- 不再跳檔案瀏覽器，直接開手機相簿 App

### 3. Bug 修復（v0.9.46）
- **面板收回後自訂背景消失** — closePanel 結尾重新 reloadFromStorage + 恢復 hasCustomBackground
- **切 App 再回來候選字殘留** — onStartInputView 加入 `updateCandidates(emptyList())` + `updateComposingText("")`

---

## 修改檔案一覽

| 檔案 | 動作 | 說明 |
|------|------|------|
| `input/handwriting/HandwritingEngine.kt` | 新增 | ML Kit 手寫引擎封裝 |
| `input/handwriting/StrokeCollector.kt` | 新增 | 筆跡收集 + undo 支援 |
| `ui/panel/HandwritingPanelView.kt` | 新增 | 手寫面板 Canvas UI |
| `ui/panel/PanelType.kt` | 修改 | +HANDWRITING |
| `ui/panel/ToolbarView.kt` | 修改 | 手寫加入 AI 工具群組 |
| `service/YukiBoardIMEService.kt` | 修改 | 手寫接線 + 背景/候選修復 |
| `ui/settings/BackgroundPickerActivity.kt` | 修改 | ACTION_PICK + MediaStore |
| `ui/settings/SettingsActivity.kt` | 修改 | 同步改 ACTION_PICK |
| `app/build.gradle` | 修改 | ML Kit 依賴 + 版號 |

---

## 版本紀錄

| 版本 | versionCode | 內容 |
|------|-------------|------|
| v0.9.43 | 109 | 手寫 PR1 — 基礎手寫辨識 |
| v0.9.44 | 110 | 選圖片/影片改用系統內建相簿 |
| v0.9.45 | 111 | 手寫 PR2+PR3 撤銷/連續/壓感/模型管理 |
| v0.9.46 | 112 | 背景消失 + 候選字殘留修復 |

---

## 待追蹤
- 背景消失問題如果 v0.9.46 修復不完全，需進一步查 backgroundView z-order 或 reloadFromStorage 時機
- 手寫面板後續可加：手勢辨識（圈選刪除）、多字連寫分割、手寫鍵盤混合模式
