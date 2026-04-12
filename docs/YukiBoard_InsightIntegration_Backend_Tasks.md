# YukiBoard × Insight 全面整合 — Insight 後端任務包

> 給 Claude Code 在 Insight Engine 專案執行
> 新增 YukiBoard 專用 API endpoint
> 基於現有 Insight 的 auth、AI proxy、sticker 基礎擴充

---

## 需要新增的 Endpoint 總覽

```
已有（不需改）：
  POST /api/auth/login
  POST /api/auth/register
  POST /api/auth/oauth
  GET  /api/auth/me
  POST /api/yukiboard/ai-proxy
  GET  /api/public/stickers

需要新增或修改：
  PATCH /api/auth/me              → 補回傳 avatarUrl + plan
  GET   /api/yukiboard/survey/trigger     → 情境問卷觸發
  POST  /api/yukiboard/survey/answer      → 提交答案
  GET   /api/yukiboard/themes             → 使用者擁有的主題清單
  POST  /api/yukiboard/settings/backup    → 備份鍵盤設定
  GET   /api/yukiboard/settings/restore   → 恢復設定
  POST  /api/yukiboard/social/share       → 分享到貼文
  POST  /api/yukiboard/ai-proxy           → 補充串流支援（SSE）
```

---

## 第一批 — /api/auth/me 補充 plan + avatarUrl

### 修改現有 `/api/auth/me` 回應

在現有 `GET /api/auth/me` 的 response 加入：

```typescript
// 現有 response 補充這幾個欄位
{
  userId: string,
  userName: string,
  displayName: string,
  role: string,
  tenantId: string,
  tenantName: string,
  adminTitle: string,
  // ── 新增 ──
  avatarUrl: string,        // 頭像 URL（空字串表示無）
  plan: "free" | "plus" | "pro" | "enterprise"  // 訂閱等級
}
```

**實作邏輯：**

```typescript
// 在 /api/auth/me handler 的 user 查詢加入
// plan 從 user 的 subscription / role 判斷

function getUserPlan(user: User): string {
  if (user.role === "enterprise" || user.tenantId) return "enterprise"
  if (user.subscription?.plan === "pro")  return "pro"
  if (user.subscription?.plan === "plus") return "plus"
  return "free"
}

// response 加入
return {
  ...existingFields,
  avatarUrl: user.avatarUrl ?? "",
  plan: getUserPlan(user)
}
```

---

## 第二批 — /api/yukiboard/ai-proxy 補充 SSE 串流

### 修改現有 ai-proxy 加入串流支援

**Request body 新增 `stream` 欄位：**

```typescript
// Request
{
  invite_code?: string,
  auth_token?: string,
  device_id: string,
  provider: string,     // "anthropic" | "openai"
  model: string,
  prompt: string,
  system_prompt?: string,
  max_tokens?: number,
  stream?: boolean      // ← 新增，true = SSE 串流
}
```

**非串流（原有）回傳：**
```json
{ "text": "...", "tokens_used": 123 }
```

**串流回傳（SSE）：**
```
Content-Type: text/event-stream
Cache-Control: no-cache

data: {"text": "你好"}
data: {"text": "，我是"}
data: {"text": "雪凜"}
data: {"tokens_used": 15}
data: [DONE]
```

**實作邏輯（Next.js App Router）：**

```typescript
// app/api/yukiboard/ai-proxy/route.ts

export async function POST(req: Request) {
  const body = await req.json()
  const { stream = false, ...rest } = body

  // 驗證 invite_code 或 auth_token（現有邏輯）
  const verified = await verifyYukiBoardAuth(body)
  if (!verified) {
    return Response.json({ error: "未授權" }, { status: 401 })
  }

  if (!stream) {
    // 原有非串流邏輯不變
    return handleNonStream(rest)
  }

  // SSE 串流
  const encoder = new TextEncoder()
  const readable = new ReadableStream({
    async start(controller) {
      try {
        // 呼叫對應的 AI provider（Anthropic / OpenAI）
        const aiStream = await callAIStream({
          provider: rest.provider,
          model: rest.model,
          prompt: rest.prompt,
          systemPrompt: rest.system_prompt,
          maxTokens: rest.max_tokens ?? 1024
        })

        let totalTokens = 0
        for await (const chunk of aiStream) {
          const text = extractChunkText(chunk)
          if (text) {
            controller.enqueue(
              encoder.encode(`data: ${JSON.stringify({ text })}\n\n`)
            )
            totalTokens += estimateTokens(text)
          }
        }

        // 送總 token 數
        controller.enqueue(
          encoder.encode(`data: ${JSON.stringify({ tokens_used: totalTokens })}\n\n`)
        )
        controller.enqueue(encoder.encode("data: [DONE]\n\n"))

        // 扣除用戶額度
        await deductUserTokens(verified.userId, totalTokens, rest.provider)

      } catch (err) {
        controller.enqueue(
          encoder.encode(`data: ${JSON.stringify({ error: String(err) })}\n\n`)
        )
      } finally {
        controller.close()
      }
    }
  })

  return new Response(readable, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive"
    }
  })
}
```

---

## 第三批 — 情境問卷 API

### 建立 Survey 資料表（Supabase）

```sql
-- 問卷定義表
CREATE TABLE yukiboard_surveys (
  id           UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  question     TEXT NOT NULL,
  options      JSONB NOT NULL,    -- ["選項A", "選項B", "選項C"]
  trigger_emotions TEXT[],        -- ["SAD", "ANGRY", "EXCITED"]
  reward_zcoin INTEGER DEFAULT 1, -- 回答獎勵
  is_active    BOOLEAN DEFAULT true,
  created_at   TIMESTAMPTZ DEFAULT now()
);

-- 問卷回答記錄
CREATE TABLE yukiboard_survey_answers (
  id           UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  survey_id    UUID REFERENCES yukiboard_surveys(id),
  user_id      TEXT,
  device_hash  TEXT,
  option_index INTEGER NOT NULL,
  app_package  TEXT,
  emotion      TEXT,
  created_at   TIMESTAMPTZ DEFAULT now()
);

-- 預設插入幾筆問卷
INSERT INTO yukiboard_surveys (question, options, trigger_emotions, reward_zcoin) VALUES
  ('你最近壓力大嗎？', '["很大", "還好", "不大"]', ARRAY['SAD', 'ANGRY'], 1),
  ('你現在的心情如何？', '["開心", "普通", "不好"]', ARRAY['SAD'], 1),
  ('今天發生什麼好事了嗎？', '["有！", "沒有", "不確定"]', ARRAY['EXCITED'], 1);
```

### `GET /api/yukiboard/survey/trigger`

**Request（Query params）：**
```
emotion=SAD&app_package=com.linecorp.LINE
Authorization: Bearer {token}  // 或 X-Platform-Key（未登入也可觸發）
```

**Response：**
```json
{
  "survey_id": "uuid",
  "question": "你最近壓力大嗎？",
  "options": ["很大", "還好", "不大"],
  "reward_zcoin": 1
}
```

若無對應問卷回傳 `204 No Content`。

**實作：**

```typescript
// app/api/yukiboard/survey/trigger/route.ts
export async function POST(req: Request) {
  const body = await req.json()
  const { emotion, app_package } = body

  if (!emotion) return Response.json({ error: "missing emotion" }, { status: 400 })

  // 查詢符合情緒的啟用中問卷
  const survey = await supabase
    .from("yukiboard_surveys")
    .select("*")
    .contains("trigger_emotions", [emotion.toUpperCase()])
    .eq("is_active", true)
    .order("created_at", { ascending: false })
    .limit(1)
    .single()

  if (!survey.data) return new Response(null, { status: 204 })

  return Response.json({
    survey_id: survey.data.id,
    question: survey.data.question,
    options: survey.data.options,
    reward_zcoin: survey.data.reward_zcoin
  })
}
```

### `POST /api/yukiboard/survey/answer`

**Request：**
```json
{
  "survey_id": "uuid",
  "option_index": 0,
  "emotion": "SAD",
  "app_package": "com.linecorp.LINE"
}
```

**Response：**
```json
{ "reward_zcoin": 1 }
```

**實作：**

```typescript
export async function POST(req: Request) {
  const body = await req.json()
  const { survey_id, option_index, emotion, app_package } = body
  const userId = await getUserIdFromToken(req) // 可為 null（未登入）
  const deviceHash = req.headers.get("X-Device-Hash") ?? ""

  // 檢查是否已回答過（24小時內）
  const existing = await supabase
    .from("yukiboard_survey_answers")
    .select("id")
    .eq("survey_id", survey_id)
    .eq("device_hash", deviceHash)
    .gte("created_at", new Date(Date.now() - 86400000).toISOString())
    .single()

  if (existing.data) {
    return Response.json({ reward_zcoin: 0 })  // 已回答，不重複給
  }

  // 儲存答案
  await supabase.from("yukiboard_survey_answers").insert({
    survey_id,
    user_id: userId,
    device_hash: deviceHash,
    option_index,
    app_package,
    emotion
  })

  // 取得獎勵金額
  const survey = await supabase
    .from("yukiboard_surveys")
    .select("reward_zcoin")
    .eq("id", survey_id)
    .single()

  const reward = survey.data?.reward_zcoin ?? 0

  // 若已登入，發放 Z 幣
  if (userId && reward > 0) {
    await grantZCoin(userId, reward, `問卷回答獎勵 #${survey_id.slice(0, 8)}`)
  }

  return Response.json({ reward_zcoin: reward })
}
```

---

## 第四批 — 主題同步 API

### 建立主題備份資料表

```sql
CREATE TABLE yukiboard_user_settings (
  user_id        TEXT PRIMARY KEY,
  theme_id       TEXT,
  keyboard_height_ratio FLOAT DEFAULT 0.35,
  keyboard_opacity      FLOAT DEFAULT 1.0,
  updated_at     TIMESTAMPTZ DEFAULT now()
);
```

### `GET /api/yukiboard/themes`

回傳使用者擁有的主題（購買的 + 免費的）：

```typescript
export async function GET(req: Request) {
  const userId = await requireAuth(req)

  // 取得用戶購買的主題
  const purchases = await supabase
    .from("skin_purchases")
    .select("skin_id")
    .eq("user_id", userId)

  const purchasedIds = purchases.data?.map(p => p.skin_id) ?? []

  // 取得所有主題（含是否已購買）
  const skins = await supabase
    .from("skins")
    .select("id, name, preview_url, price, is_free")

  const themes = skins.data?.map(skin => ({
    id: skin.id,
    name: skin.name,
    preview_url: skin.preview_url,
    is_free: skin.is_free,
    is_purchased: skin.is_free || purchasedIds.includes(skin.id)
  })) ?? []

  return Response.json({ themes })
}
```

### `POST /api/yukiboard/settings/backup`

```typescript
export async function POST(req: Request) {
  const userId = await requireAuth(req)
  const { theme_id, keyboard_height_ratio, keyboard_opacity } = await req.json()

  await supabase
    .from("yukiboard_user_settings")
    .upsert({
      user_id: userId,
      theme_id,
      keyboard_height_ratio,
      keyboard_opacity,
      updated_at: new Date().toISOString()
    }, { onConflict: "user_id" })

  return Response.json({ success: true })
}
```

### `GET /api/yukiboard/settings/restore`

```typescript
export async function GET(req: Request) {
  const userId = await requireAuth(req)

  const settings = await supabase
    .from("yukiboard_user_settings")
    .select("*")
    .eq("user_id", userId)
    .single()

  if (!settings.data) return Response.json({ restored: false })

  return Response.json({
    restored: true,
    theme_id: settings.data.theme_id,
    keyboard_height_ratio: settings.data.keyboard_height_ratio,
    keyboard_opacity: settings.data.keyboard_opacity
  })
}
```

---

## 第五批 — 社群分享 API

### `POST /api/yukiboard/social/share`

**Request：**
```json
{
  "content": "AI 幫我寫的文字...",
  "source": "yukiboard",
  "skill": "writer"
}
```

**Response：**
```json
{
  "post_id": "uuid",
  "post_url": "https://insight.snowrealm.pet/post/uuid"
}
```

**實作：**

```typescript
export async function POST(req: Request) {
  const userId = await requireAuth(req)
  if (!userId) return Response.json({ error: "需要登入" }, { status: 401 })

  const { content, source, skill } = await req.json()

  if (!content || content.length > 500) {
    return Response.json({ error: "內容不合法" }, { status: 400 })
  }

  // 建立貼文（使用現有 Insight 的 post 系統）
  const post = await createInsightPost({
    authorId: userId,
    content: content,
    tags: ["yukiboard", skill].filter(Boolean),
    metadata: { source: "yukiboard", skill }
  })

  return Response.json({
    post_id: post.id,
    post_url: `${process.env.NEXT_PUBLIC_SITE_URL}/post/${post.id}`
  })
}
```

---

## 共用工具函式（加在 Insight 後端）

### `lib/yukiboard-auth.ts`

```typescript
import { headers } from "next/headers"

/**
 * 驗證 YukiBoard 請求的身份。
 * 接受兩種方式：
 * 1. Bearer token（已登入用戶）
 * 2. invite_code + device_id（邀請碼用戶）
 */
export async function verifyYukiBoardAuth(body: any): Promise<{
  userId: string | null,
  inviteCode: string | null,
  isLoggedIn: boolean
} | null> {
  const headersList = headers()
  const platformKey = headersList.get("X-Platform-Key")

  // 驗證 Platform Key
  if (platformKey !== process.env.PLATFORM_API_KEY) return null

  const authToken = body.auth_token ?? headersList.get("Authorization")?.replace("Bearer ", "")
  const inviteCode = body.invite_code

  if (authToken) {
    const user = await verifyJWT(authToken)
    if (user) return { userId: user.id, inviteCode: null, isLoggedIn: true }
  }

  if (inviteCode) {
    const valid = await validateInviteCode(inviteCode)
    if (valid) return { userId: null, inviteCode, isLoggedIn: false }
  }

  return null
}

/**
 * 要求已登入（拋出 401 或回傳 userId）
 */
export async function requireAuth(req: Request): Promise<string | null> {
  const token = req.headers.get("Authorization")?.replace("Bearer ", "")
  if (!token) return null
  const user = await verifyJWT(token)
  return user?.id ?? null
}
```

---

## 環境變數（確認已設定）

```env
PLATFORM_API_KEY=          # 跟 YukiBoard BuildConfig 相同
NEXT_PUBLIC_SITE_URL=https://insight.snowrealm.pet
JWT_SECRET=                # 跟 Supabase jwt_secret 相同
```

---

## 執行順序

```
第一批：修改 /api/auth/me 補 plan + avatarUrl
第二批：/api/yukiboard/ai-proxy 加 SSE 串流
第三批：建 survey 資料表 + trigger + answer endpoint
第四批：建 settings 資料表 + themes + backup + restore endpoint
第五批：/api/yukiboard/social/share endpoint
共用：lib/yukiboard-auth.ts
部署後更新 Android 端的 endpoint 測試
```

---

## 測試驗證清單

```
□ /api/auth/me 回傳有 avatarUrl 和 plan 欄位
□ /api/yukiboard/ai-proxy stream=true 回傳 SSE 格式
□ /api/yukiboard/survey/trigger 對 SAD emotion 回傳問卷
□ /api/yukiboard/survey/answer 回傳 reward_zcoin
□ /api/yukiboard/themes 回傳用戶主題清單
□ /api/yukiboard/settings/backup 成功儲存
□ /api/yukiboard/settings/restore 成功回傳
□ /api/yukiboard/social/share 建立貼文回傳 URL
□ 未登入 + 有效 invite_code 可呼叫 ai-proxy 和 survey/trigger
□ 無 X-Platform-Key 呼叫所有 endpoint 回傳 401
```
