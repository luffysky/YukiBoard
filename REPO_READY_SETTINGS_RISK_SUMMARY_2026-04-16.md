# Settings 側最終風險摘要（2026-04-16）

日期: 2026-04-16  
角色: `Claude Code A`  
用途: 作為 settings 區 repo-ready 收尾的最終風險文件，供 `CodeX A` 審核

---

## 1. 結論

Settings 區目前處於**健康且穩定**的狀態。

- SettingsActivity 455 行（警戒線 500）
- 22 個 section 全部獨立運作
- 狀態來源已收斂到 coordinator / repository
- 無違規的反向引用或散落存取
- 無阻塞性風險

---

## 2. 結構數據

### 2.1 核心檔案

| 檔案 | 行數 | 角色 |
|------|------|------|
| `SettingsActivity.kt` | 455 | 容器頁（lifecycle + section 接線） |
| `SettingsScreenCoordinator.kt` | 84 | 統一狀態管理（verifySessionAsync / getApiKey） |
| `SettingsUiState.kt` | 55 | 狀態資料類別 |
| `UserSessionRepository.kt` | 78 | 登入驗證 / API key 提供 |

### 2.2 Section 行數（前 5 大）

| Section | 行數 | 備註 |
|---------|------|------|
| `BackgroundSettingsSection.kt` | 1235 | 最大，已評估不需切分 |
| `TtsSettingsSection.kt` | 447 | 1 個 Activity callback（合規） |
| `SpeechSettingsSection.kt` | 402 | 已從 SpeechSettingsHelper 轉換 |
| `AnimationSettingsSection.kt` | 186 | 正常範圍 |
| `SnippetSettingsSection.kt` | 182 | 正常範圍 |

其餘 17 個 section 均在 180 行以下。

---

## 3. 已知風險

### 3.1 低風險（已控制）

| 風險 | 說明 | 處置 |
|------|------|------|
| BackgroundSettingsSection 過大 | 1235 行，為全 section 最大 | 已評估：職責邊界清晰、切分成本 > 收益、無人正在修改。維持現狀 |
| TTS Google Cloud callback | SettingsActivity 內 ~20 行 lambda 閉包 | SETTINGS_BOUNDARY.md 已標為「已知灰色地帶」。門檻為 3 個 callback 才考慮 interface，目前僅 1 個 |
| SettingsActivity 行數接近警戒線 | 455 / 500 = 91% | 無新增壓力。所有新設定走 section pattern，不會回填 Activity |

### 3.2 待觀察（非阻塞）

| 項目 | 說明 |
|------|------|
| 登入 bug | 登入成功後設定頁不顯示帳號/方案/角色，待 Logcat 實機調查。已由使用者指示延後 |
| Runtime 未驗證 | 因模擬器不可用，以下 4 項僅通過 compile 驗證：coordinator 統一 fetchMe 後 UI 更新、SpeechSettingsSection 卡片呈現、LoginActivity 返回 recreate、TTS 測試按鈕 lifecycle |

### 3.3 不存在的風險（已清零確認）

| 檢查項 | 結果 |
|--------|------|
| Section 反向引用 SettingsActivity | 0（僅 doc comment 提及來源） |
| Section 內 `auth.fetchMe()` | 0（統一由 coordinator 發起） |
| Section 內 `AIProviderManager.getInstance()` | 0（改為 lambda 注入） |
| Activity 內 SeekBar / Switch / Radio / Checkbox | 0 |
| Activity 內 prefs 直接業務存取 | 0（lambda 閉包除外） |

---

## 4. 邊界灰色地帶

目前僅 1 處，且已記錄於 `RULE/SETTINGS_BOUNDARY.md` Section 7：

**Google Cloud TTS 測試 lambda**

```
位置：SettingsActivity.kt:226-247（~20 行）
原因：需要 runOnUiThread + Activity context 建構 GoogleCloudTtsEngine
處置：目前可接受。門檻為 Activity-level callback > 3 個才升級為 interface
```

---

## 5. 狀態來源收斂現況

### 已收斂

| 狀態 | 收斂入口 |
|------|---------|
| 登入驗證 | `SettingsScreenCoordinator.verifySessionAsync()` → 一次 fetchMe |
| 使用者資料 | `UserSessionRepository.verifySession()` → VerifiedSession |
| Whisper API Key | `settingsCoordinator.getApiKey()` → lambda 注入 SpeechSettingsSection |

### 合理邊界（不需收斂）

| 狀態 | 位置 | 理由 |
|------|------|------|
| AI Provider 設定 | `AISettingsActivity` | 該 Activity 即 provider 終端頁 |
| Fraud Model | `AISettingsSection` 內 | 建構成本低、不跨 section |
| Sync 操作 | `SyncSettingsSection` 持有 `CloudSyncManager` | singleton、section 核心業務 |
| TTS callback | `SettingsActivity` lambda | 需 Activity context，僅 1 處 |

---

## 6. 監督指標快照

| 指標 | 警戒值 | 2026-04-16 實測 | 狀態 |
|------|--------|----------------|------|
| SettingsActivity 行數 | > 500 | 455 | OK |
| Activity 內 SeekBar/Switch/Radio/Checkbox | > 0 | 0 | OK |
| Activity 內 prefs 直接業務存取 | > 0 | 0 | OK |
| Section 反向引用 Activity | > 0 | 0 | OK |
| Activity-level callback 數量 | > 3 | 1 | OK |

---

## 7. 對後續工作的建議

1. **Settings 區不需要進一步重構** — 結構已穩定，指標全部在安全範圍內
2. **登入 bug 待實機調查** — 這是功能 bug 不是架構問題，需要 Logcat
3. **若未來新增設定項** — 遵守 `RULE/SETTINGS_BOUNDARY.md` 的 section pattern 即可
4. **BackgroundSettingsSection** — 若未來有重大功能變更（如新增背景特效），屆時再評估切分

---

## 8. Claude Code A 正式交接

### A. 變更摘要

本輪 Claude Code A 完成：
- Settings 整合入口收斂（coordinator / repository 統一驗證）
- SpeechSettingsHelper → SpeechSettingsSection 轉換 + lambda 注入
- SETTINGS_SMOKE_CHECKLIST.md repo-ready 最終版
- SETTINGS_BOUNDARY.md 邊界規則
- 3 項可選優化評估（BackgroundSection 切分 / TTS callback / header 抽取）→ 全部維持現狀
- 監督指標行數校正（442→455）
- 本份風險摘要

### B. 風險

無阻塞性風險。唯一待處理項為登入 bug（使用者指示延後）。

### C. 判定

Settings 區 repo-ready 收尾**已完成**。

### D. 下一步建議

Claude Code A 的 settings 工作已完結。後續：
- 維持監督 SettingsActivity 不反彈即可
- 等待 CodeX A 對 Sprint 3 Phase 2 的二次拍板
- 登入 bug 待使用者安排實機調查時再展開

### E. CodeX A 審核需求

請 CodeX A 確認：
- Settings 區 repo-ready 是否正式結案
- Claude Code A 是否可進入待命狀態（等待新指派）
