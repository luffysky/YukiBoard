# SPEC 範例（YukiBoard / Android 版）

**版本**：v1.0  
**負責人**：Claude Code / Codex  
**狀態**：範例

---

## 功能描述

將 `SettingsActivity` 的登入區塊抽離成 `AuthSettingsSection`，讓 Activity 本體只保留容器與 lifecycle。

---

## 範圍

### In Scope

- 抽離登入/登出按鈕 UI
- 抽離使用者名稱顯示
- 保持既有登入流程不變
- 保持現有行為與文案不變

### Out of Scope

- 重寫 `SnowRealmAuthClient`
- 修改 OAuth 流程
- 改動雲同步邏輯

---

## 設計規則

1. `SettingsActivity` 不再持有登入區塊的大段 UI 建構細節
2. 新區塊應可被重用或獨立調整
3. 共享狀態優先由 `SettingsScreenCoordinator` / `SettingsUiState` 提供
4. 不得因此把更多邏輯塞回 `SettingsActivity`

---

## 相關檔案

- 主要修改：
  - `/app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt`
- 新增檔案：
  - `/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/AuthSettingsSection.kt`

---

## 驗證標準

- 設定頁可正常打開
- 登入 / 登出按鈕顯示正常
- 使用者名稱顯示邏輯不回歸
- `SettingsActivity` 行數與責任下降
- 沒有新增跨層 network / auth 耦合

---

## 備註

這個 SPEC 只是格式範例。  
真正任務應搭配 `TASK_TEMPLATE.md` 一起使用，明確限制 file scope。
