-- YukiBoard 邀請碼表
CREATE TABLE IF NOT EXISTS yukiboard_invite_codes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT UNIQUE NOT NULL,
  type TEXT NOT NULL DEFAULT 'standard',       -- standard / kol / permanent
  daily_token_limit INT NOT NULL DEFAULT 5000, -- 每日 token 額度
  max_uses INT NOT NULL DEFAULT 1,             -- 最大使用次數
  used_count INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by TEXT,                              -- 產生者 user id
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ,                       -- NULL = 不過期
  note TEXT
);

CREATE INDEX IF NOT EXISTS idx_yb_invite_code ON yukiboard_invite_codes (code);
CREATE INDEX IF NOT EXISTS idx_yb_invite_active ON yukiboard_invite_codes (is_active, type);

-- 邀請碼使用紀錄
CREATE TABLE IF NOT EXISTS yukiboard_invite_usages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invite_code_id UUID REFERENCES yukiboard_invite_codes(id),
  user_id TEXT NOT NULL,
  used_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- AI 代理使用量追蹤
CREATE TABLE IF NOT EXISTS yukiboard_ai_usage (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invite_code_id UUID REFERENCES yukiboard_invite_codes(id),
  device_id TEXT NOT NULL,
  tokens_used INT NOT NULL DEFAULT 0,
  provider TEXT NOT NULL,             -- openai / anthropic
  model TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_yb_ai_usage_daily
  ON yukiboard_ai_usage (invite_code_id, device_id, created_at);
