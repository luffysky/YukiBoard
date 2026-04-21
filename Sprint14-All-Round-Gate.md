# Sprint 14 All Round Gate

日期: 2026-04-18  
身份: 玄樞  
狀態: `PLANNED`

## 0. 額度恢復後角色編成

自 2026-04-20 起，`Codex` 額度已恢復；`Sprint 14` 不再採額度不足時的縮編配置，正式恢復四角編成：

- `玄樞`：Sprint owner / gate owner / 架構切板 / 高風險仲裁 / 最終放行
- `衡鑑`：technical review owner / infra / runtime / `.kt` 實檔複核 / 測試與風險交叉審查
- `雪鑰`：contract / consume / settings / migration / import-export wording
- `鍵律`：implementation / runtime / engine / service / dictionary consume 實作

## 1. Sprint 主題

Sprint 14 主題定義為：

- 詞庫擴充
- 詞庫基礎設施
- 長線輸入品質提升
- `Sprint 12` 遞延的 `ZCoin / Newebpay` 收尾接線

## 2. Sprint 14 正式範圍

1. 拼音詞庫外部化
2. 倉頡詞組化
3. 日韓詞庫擴充
4. 詞庫版本管理
5. 詞庫匯入工具
6. 使用者詞庫同步
7. `Sprint 12` deferred 的 `ZCoin / Newebpay` 重新納入排程
8. `ZCoin` Android client scaffold 與 Insight payment / wallet 路徑正式接線
9. `Newebpay` payment secret remediation 閉環

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
- `衡鑑` technical cross-check
- `玄樞` final gate

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
3. 兩份收齊後提交給：`衡鑑`
4. `衡鑑` 完成 `S14-0-CXA-DICTIONARY-INFRA-TECHNICAL-CROSSCHECK`
5. 提交給：`玄樞`
6. 只有 `玄樞` 判定 `READY`，才可開 `S14-1`

### S14-1 Pinyin External Dictionary

狀態: `LOCKED`

目標：

- 讓拼音詞庫完成第一條外部化路徑，成為後續 dictionary infra 樣板

指定負責：

- `鍵律`
- `雪鑰`
- `衡鑑` technical review
- `玄樞` final gate

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
4. 提交給：`衡鑑`
5. `衡鑑` 完成 `S14-1-CXA-PINYIN-EXTERNAL-DICTIONARY-TECHNICAL-REVIEW`
6. 提交給：`玄樞`
7. 只有 `玄樞` 判定 `READY`，才可開 `S14-2`

### S14-2 Cangjie Phrase Support

狀態: `LOCKED`

目標：

- 讓倉頡從單字根映射走向詞組 / phrase support 的第一條正式路徑

指定負責：

- `鍵律`
- `雪鑰` 審核
- `衡鑑` technical review
- `玄樞` final gate

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
4. 提交給：`衡鑑`
5. `衡鑑` 完成 `S14-2-CXA-CANGJIE-PHRASE-SUPPORT-TECHNICAL-REVIEW`
6. 提交給：`玄樞`
7. 只有 `玄樞` 判定 `READY`，本門才可結案

### S14-3 Japanese / Korean Expansion

狀態: `LOCKED`

目標：

- 補日韓詞庫擴充的第一輪結構化內容，不做無邊界大堆檔

指定負責：

- `鍵律`
- `雪鑰`
- `衡鑑` technical review
- `玄樞` final gate

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
4. 提交給：`衡鑑`
5. `衡鑑` 完成 `S14-3-CXA-JP-KR-DICTIONARY-TECHNICAL-REVIEW`
6. 提交給：`玄樞`
7. 只有 `玄樞` 判定 `READY`，本門才可結案

### S14-4 Dictionary Versioning / Incremental Update

狀態: `LOCKED`

目標：

- 建立字典版本管理與增量更新基線，不再只靠口頭規劃

指定負責：

- `雪鑰`
- `鍵律`
- `衡鑑` technical cross-check
- `玄樞` final gate

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
3. 兩份收齊後提交給：`衡鑑`
4. `衡鑑` 完成 `S14-4-CXA-DICTIONARY-VERSIONING-TECHNICAL-CROSSCHECK`
5. 提交給：`玄樞`
6. 只有 `玄樞` 判定 `READY`，本門才可結案

### S14-5 User Dictionary Sync

狀態: `LOCKED`

目標：

- 在 dictionary infra 成熟後，定義 user dictionary sync 的最小實作與契約

指定負責：

- `雪鑰`
- `鍵律`
- `衡鑑` technical review
- `玄樞` final gate

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
4. 提交給：`衡鑑`
5. `衡鑑` 完成 `S14-5-CXA-USERDICT-SYNC-TECHNICAL-REVIEW`
6. 提交給：`玄樞`
7. 只有 `玄樞` 判定 `READY`，本門才可結案

### S14-6 ZCoin / Newebpay Re-entry Audit

狀態: `LOCKED`

目標：

- 把 `Sprint 12` deferred 的 `ZCoin / Newebpay` 工作正式收回 `Sprint 14`
- 對齊 Insight 既有 server-side payment 能力、Android client scaffold 現狀、auth 邊界與 file scope
- 先做 audit / contract / reopen cut，不直接偷進 implementation

指定負責：

- `雪鑰`
- `鍵律`
- `衡鑑` technical cross-check
- `玄樞` final gate

任務指派：

#### 雪鑰

- 任務 ID：`S14-6-CCA-ZCOIN-NEWEBPAY-REENTRY-CONTRACT-AUDIT`

目標：

- 補 `ZCoinWalletActivity` 對外路徑、Insight auth / consume 邊界、release / rollout 前提與 migration note

#### 鍵律

- 任務 ID：`S14-6-CCB-ZCOIN-NEWEBPAY-RUNTIME-SCOPE-AUDIT`

目標：

- 補 `NewebpayClient` / `ZCoinManager` / `ZCoinApi` / `app/build.gradle` 的 runtime scope、secret 清理邊界與 wire-up 切板

提交鏈：

1. `雪鑰` 完成 `S14-6-CCA-ZCOIN-NEWEBPAY-REENTRY-CONTRACT-AUDIT`
2. `鍵律` 完成 `S14-6-CCB-ZCOIN-NEWEBPAY-RUNTIME-SCOPE-AUDIT`
3. 兩份收齊後提交給：`衡鑑`
4. `衡鑑` 完成 `S14-6-CXA-ZCOIN-NEWEBPAY-TECHNICAL-CROSSCHECK`
5. 提交給：`玄樞`
6. 只有 `玄樞` 判定 `READY`，才可開 `S14-7`

### S14-7 ZCoin / Newebpay Wire-up and Secret Closure

狀態: `LOCKED`

目標：

- 完成 `ZCoin` Android client 與 Insight payment / wallet 正式接線
- 關閉 `Sprint 12` 留下的 `Newebpay` payment secret remediation 遺留
- 嚴禁 client 端保留 `Newebpay` AES signing 與直連 Supabase 寫 wallet

指定負責：

- `鍵律`
- `雪鑰`
- `衡鑑` technical review
- `玄樞` final gate

任務指派：

#### 鍵律

- 任務 ID：`S14-7-CCB-ZCOIN-NEWEBPAY-WIREUP-IMPLEMENTATION`

目標：

- 落地 Android 端最終 wire-up、client secret 清理、wallet / payment runtime 接線與 error handling

#### 雪鑰

- 任務 ID：`S14-7-CCA-ZCOIN-NEWEBPAY-CONSUME-REVIEW`

目標：

- 審 consume 路徑、登入 / tenant / Custom Tab 行為、使用者可見文案與 rollout 邊界

提交鏈：

1. `鍵律` 完成 `S14-7-CCB-ZCOIN-NEWEBPAY-WIREUP-IMPLEMENTATION`
2. 提交給：`雪鑰`
3. `雪鑰` 完成 `S14-7-CCA-ZCOIN-NEWEBPAY-CONSUME-REVIEW`
4. 提交給：`衡鑑`
5. `衡鑑` 完成 `S14-7-CXA-ZCOIN-NEWEBPAY-WIREUP-TECHNICAL-REVIEW`
6. 提交給：`玄樞`
7. 只有 `玄樞` 判定 `READY`，本門才可結案

## 4. 解鎖規則

1. `S14-0` 完成前不得直接擴詞庫檔與格式。
2. 先定格式，再做內容擴充。
3. 先做基礎字典 contract，再做雲同步。
4. `S14-6` / `S14-7` 屬 `Sprint 12` deferred payment carry-over，必須獨立成門，不得混入字典 infra round 一起交付。
5. `S14-7` 開門前，必須先由 `玄樞` 確認 `S14-6` 已把 `ZCoin / Newebpay` consume、runtime、auth、release 前提與 file scope 收乾淨。
6. `S14-7` 不得接受下列做法：
   - 在 Android client 端做 `Newebpay` AES signing
   - 讓 YukiBoard client 直連 Supabase 寫 `zcoin_wallets`
   - 只修 wallet URL 但不處理 payment secret remediation 閉環

## 5. Sprint 14 完成條件

1. 至少一條主要語言字典完成外部化或結構升級。
2. 詞庫版本管理不再停留在口頭規劃。
3. 後續詞庫擴充可持續而不是一次性堆檔。
4. `Sprint 12` deferred 的 `ZCoin / Newebpay` 已在本 sprint 重新納管並完成正式接線或明確 gate 裁定。
5. `Newebpay` payment secret remediation 不再停留在 deferred note。

## 6. 玄樞預設判定

`LOCKED`

Reason:
1. Sprint 14 現含字典 infra 與 `Sprint 12` payment carry-over 兩條主線，必須先切 contract 與 file scope，不能直接開做。
