# Sprint 14 All Round Gate

日期: 2026-04-18  
身份: 玄樞  
狀態: `PLANNED`

## 1. Sprint 主題

Sprint 14 主題定義為：

- 詞庫擴充
- 詞庫基礎設施
- 長線輸入品質提升

## 2. Sprint 14 正式範圍

1. 拼音詞庫外部化
2. 倉頡詞組化
3. 日韓詞庫擴充
4. 詞庫版本管理
5. 詞庫匯入工具
6. 使用者詞庫同步

## 3. Round Gate

### S14-0 Dictionary Infra Design

狀態: `LOCKED`

目標：

1. 定義字典格式、外部化策略、版本欄位與匯入邊界
2. 切出拼音、倉頡、日韓與 user dictionary sync 的最小 infra contract
3. 先做 design / contract，不直接擴大詞庫內容

指定負責：

- `雪鑰`
- `鍵律`

任務指派：

#### 雪鑰

- 任務 ID：`S14-0-CCA-DICTIONARY-INFRA-CONTRACT-DESIGN`

目標：

- 補設定面、同步面、版本說明、匯入匯出契約與 migration note

#### 鍵律

- 任務 ID：`S14-0-CCB-DICTIONARY-INFRA-RUNTIME-DESIGN`

目標：

- 補字典格式、載入方式、版本欄位、增量更新與 runtime 邊界

提交鏈：

1. `雪鑰` 完成 `S14-0-CCA-DICTIONARY-INFRA-CONTRACT-DESIGN`
2. `鍵律` 完成 `S14-0-CCB-DICTIONARY-INFRA-RUNTIME-DESIGN`
3. 兩份收齊後提交給：`玄樞`
4. 只有 `玄樞` 判定 `READY`，才可開 `S14-1`

### S14-1 Pinyin External Dictionary

狀態: `LOCKED`

目標：

- 讓拼音詞庫完成第一條外部化路徑，成為後續 dictionary infra 樣板

指定負責：

- `鍵律`
- `雪鑰`

任務指派：

#### 鍵律

- 任務 ID：`S14-1-CCB-PINYIN-EXTERNAL-DICTIONARY-IMPLEMENTATION`

目標：

- 落地拼音字典的外部檔 / 載入 / fallback 路徑

#### 雪鑰

- 任務 ID：`S14-1-CCA-PINYIN-EXTERNAL-DICTIONARY-CONSUME-REVIEW`

目標：

- 審設定、版本顯示、更新說明與 migration 行為

提交鏈：

1. `鍵律` 完成 `S14-1-CCB-PINYIN-EXTERNAL-DICTIONARY-IMPLEMENTATION`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S14-1-CCA-PINYIN-EXTERNAL-DICTIONARY-CONSUME-REVIEW`
4. 提交給：`玄樞`
5. 只有 `玄樞` 判定 `READY`，才可開 `S14-2`

### S14-2 Cangjie Phrase Support

狀態: `LOCKED`

目標：

- 讓倉頡從單字根映射走向詞組 / phrase support 的第一條正式路徑

指定負責：

- `鍵律`
- `雪鑰` 審核

任務指派：

#### 鍵律

- 任務 ID：`S14-2-CCB-CANGJIE-PHRASE-SUPPORT-IMPLEMENTATION`

目標：

- 落地倉頡 phrase support 的最小可用模型

#### 雪鑰

- 任務 ID：`S14-2-CCA-CANGJIE-PHRASE-SUPPORT-CONSUME-REVIEW`

目標：

- 檢查設定、說明、fallback 與升級行為

提交鏈：

1. `鍵律` 完成 `S14-2-CCB-CANGJIE-PHRASE-SUPPORT-IMPLEMENTATION`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S14-2-CCA-CANGJIE-PHRASE-SUPPORT-CONSUME-REVIEW`
4. 提交給：`玄樞`
5. 只有 `玄樞` 判定 `READY`，本門才可結案

### S14-3 Japanese / Korean Expansion

狀態: `LOCKED`

目標：

- 補日韓詞庫擴充的第一輪結構化內容，不做無邊界大堆檔

指定負責：

- `鍵律`
- `雪鑰`

任務指派：

#### 鍵律

- 任務 ID：`S14-3-CCB-JP-KR-DICTIONARY-EXPANSION`

目標：

- 落地日韓詞庫擴充與 fallback 邊界

#### 雪鑰

- 任務 ID：`S14-3-CCA-JP-KR-DICTIONARY-MIGRATION-NOTE`

目標：

- 補版本、升級、使用者可見差異與設定面說明

提交鏈：

1. `鍵律` 完成 `S14-3-CCB-JP-KR-DICTIONARY-EXPANSION`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S14-3-CCA-JP-KR-DICTIONARY-MIGRATION-NOTE`
4. 提交給：`玄樞`
5. 只有 `玄樞` 判定 `READY`，本門才可結案

### S14-4 Dictionary Versioning / Incremental Update

狀態: `LOCKED`

目標：

- 建立字典版本管理與增量更新基線，不再只靠口頭規劃

指定負責：

- `雪鑰`
- `鍵律`

任務指派：

#### 雪鑰

- 任務 ID：`S14-4-CCA-DICTIONARY-VERSIONING-POLICY`

目標：

- 補版本顯示、更新說明、回滾 / 兼容性政策

#### 鍵律

- 任務 ID：`S14-4-CCB-DICTIONARY-INCREMENTAL-UPDATE-PLAN`

目標：

- 補版本欄位、增量更新路徑、下載 / 套用的 runtime contract

提交鏈：

1. `雪鑰` 完成 `S14-4-CCA-DICTIONARY-VERSIONING-POLICY`
2. `鍵律` 完成 `S14-4-CCB-DICTIONARY-INCREMENTAL-UPDATE-PLAN`
3. 兩份收齊後提交給：`玄樞`
4. 只有 `玄樞` 判定 `READY`，本門才可結案

### S14-5 User Dictionary Sync

狀態: `LOCKED`

目標：

- 在 dictionary infra 成熟後，定義 user dictionary sync 的最小實作與契約

指定負責：

- `雪鑰`
- `鍵律`

任務指派：

#### 雪鑰

- 任務 ID：`S14-5-CCA-USERDICT-SYNC-CONSUME-MIGRATION`

目標：

- 補設定面、migration note、使用者資料責任與衝突策略

#### 鍵律

- 任務 ID：`S14-5-CCB-USERDICT-SYNC-RUNTIME-IMPLEMENTATION`

目標：

- 落地 user dictionary sync 的 runtime / storage / merge contract

提交鏈：

1. `鍵律` 完成 `S14-5-CCB-USERDICT-SYNC-RUNTIME-IMPLEMENTATION`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S14-5-CCA-USERDICT-SYNC-CONSUME-MIGRATION`
4. 提交給：`玄樞`
5. 只有 `玄樞` 判定 `READY`，本門才可結案

## 4. 解鎖規則

1. `S14-0` 完成前不得直接擴詞庫檔與格式。
2. 先定格式，再做內容擴充。
3. 先做基礎字典 contract，再做雲同步。

## 5. Sprint 14 完成條件

1. 至少一條主要語言字典完成外部化或結構升級。
2. 詞庫版本管理不再停留在口頭規劃。
3. 後續詞庫擴充可持續而不是一次性堆檔。

## 6. 玄樞預設判定

`LOCKED`

Reason:
1. Sprint 14 是長線品質提升，不應搶在安全與合規之前。
