# YukiBoard 產品決策文件

> 給 Claude Code 讀取，補充 todo_list_v2.md 的決策細節。
> 最後更新：2026-04-03

---

## 1. 資料庫 — 獨立 Supabase 專案

YukiBoard 在 Zeabur **新開獨立專案**，不與 Insight 共用資料庫。

- Zeabur 專案名稱：`SnowRealmYukiBoard`
- 在此專案內加 Supabase 服務
- URL 和 anon key 已填入 `.env`（`YUKIBOARD_SUPABASE_URL` / `YUKIBOARD_SUPABASE_ANON_KEY`）

**與 Insight 的關係：**
- 帳號透過 JWT 互通（打 Insight API 驗證，不共用資料表）
- 兩邊資料完全隔離，只有帳號 ID 共用

---

## 2. Z幣系統 — 參考 Insight 現有實作

**直接參考 Insight 的 Z幣系統**，不重新設計。

- Z幣兌換比率、儲值邏輯、錢包結構：參考 Insight 的現有實作
- 金流：接**藍新金流（Newebpay）**，用儲值方式購買 Z幣
- API 串接：參考 Insight 的藍新整合方式

**YukiBoard Z幣消費場景：**
- AI Skill token 用量
- 鍵盤主題皮膚解鎖
- 貼圖包購買
- 表情包解鎖

---

## 3. 帳號系統 — 使用 Insight 現有帳號

**直接使用 SnowRealm（Insight）的帳號系統**，不重新建立 Auth。

- 登入方式：Email / LINE Login / Google（Insight 已有）
- Insight API base URL：`https://insight.snowrealm.pet`
- YukiBoard 打 `/api/auth/login`、`/api/auth/me` 驗證身份
- **登入介面另外做**（YukiBoard 自己的 Android 登入畫面，但後端打 Insight API）

**登入流程：**
```
用戶在 YukiBoard 輸入帳號密碼
      ↓
POST https://insight.snowrealm.pet/api/auth/login
      ↓
取得 JWT token → 存入 EncryptedSharedPreferences
      ↓
後續 API 呼叫帶 Authorization: Bearer <token>
```

---

## 4. VRM 3D 角色 — 參考 Insight 現有模型

- 雪凜 VRM 模型已在 Insight 專案中
- YukiBoard 的 VRM viewer 框架**參考 Insight 的 VRM 實作方式**
- 先建好 VRM 渲染框架，模型素材從 Insight 取用

---

## 5. 皮膚商城

**第一階段範圍：**
- 鍵盤主題皮膚（背景顏色、按鍵風格、流光顏色）
- 貼圖包（接 Insight 貼圖 API，已有 `InsightStickerApi.kt`）
- 表情包（Emoji 風格包、顔文字包）

**定價策略：**
- 免費皮膚：3 款預設主題（深空藍、雪域白、凜空紫）
- Z幣皮膚：角色主題、節日限定
- 貼圖包：Z幣購買，參考 Insight Z幣定價

**暫不包含：**
- 雪凜衣服（VRM 換裝，留待 VRM 整合完成後）

---

## 6. AR 雪凜

- 用途：雪凜出現在現實桌面上（ARCore 平面偵測）
- 3D 模型與 VRM 共用（同一個雪凜模型）
- 先建 ARCore session + 錨點放置框架，模型後補

---

## 7. CoinBeast / 毛行天下 跨 App 聯動

**目前兩個 App 都還在開發中，先預留接口。**

聯動方式：Deep Link
```kotlin
// 預留 Deep Link scheme
// coinbeast://yukiboard/reward?zcoin=xxx
// maoxing://yukiboard/distance?km=xxx
```

- CoinBeast 聯動：遊戲獎勵 Z幣同步（Deep Link 預留，API 之後接）
- 毛行天下聯動：鍵盤旅行者打字距離同步（Deep Link 預留）

---

## 8. Insight 貼圖 + 情緒採礦週報

- **貼圖**：`InsightStickerApi.kt` 已存在，直接串接即可
- **情緒採礦週報**：先存本地 Room DB，之後再考慮送到 Insight 分析
- Insight API base URL：`https://insight.snowrealm.pet`（已在 `.env`）

---

## 環境變數補充（加入 .env）

```
# YukiBoard 獨立 Supabase
YUKIBOARD_SUPABASE_URL=（Zeabur 新專案的 Supabase URL）
YUKIBOARD_SUPABASE_ANON_KEY=（Zeabur 新專案的 anon key）

# SnowRealm Insight API
INSIGHT_API_BASE_URL=https://insight.snowrealm.pet

# 藍新金流（Z幣儲值）
NEWEBPAY_MERCHANT_ID=（藍新商店代號）
NEWEBPAY_HASH_KEY=（參考 Insight 藍新設定）
NEWEBPAY_HASH_IV=（參考 Insight 藍新設定）
```

---

## 可直接實作的（不需等待素材）

以下項目 Claude Code 可以直接開始做，素材/後端之後補：

| 項目 | 說明 |
|------|------|
| 打字競速 v2.0 框架 | UI + 本地模擬，Supabase 連線層後接 |
| 皮膚商城 UI | 商城頁面 + SkinManager，皮膚圖用 placeholder |
| VRM viewer 框架 | 用 filament 或 sceneform，模型後補 |
| AR 雪凜框架 | ARCore session + 錨點放置 |
| Z幣本地錢包 | ZCoinManager + UI，藍新充值 API 後接 |
| 帳號登入 UI | Android 登入頁面，後端打 Insight API |
| 情緒採礦週報 | MoodMinerGame + Room DB + 週報頁面 |
| 所有 14 款新遊戲 | Batch 6 全部實作 |
| SnowRealmVR 鍵盤框架 | VR keyboard UI 框架 |
