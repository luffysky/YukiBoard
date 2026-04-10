-- 使用者 AI API Key 雲端同步表
-- Key 在裝置端用 AES-256-CBC 加密後上傳，Supabase 只存密文

CREATE TABLE IF NOT EXISTS user_ai_keys (
    user_id TEXT PRIMARY KEY,
    encrypted_keys TEXT NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- RLS: 只有本人可以讀寫自己的 Key
ALTER TABLE user_ai_keys ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own keys"
    ON user_ai_keys FOR SELECT
    USING (auth.uid()::text = user_id);

CREATE POLICY "Users can insert own keys"
    ON user_ai_keys FOR INSERT
    WITH CHECK (auth.uid()::text = user_id);

CREATE POLICY "Users can update own keys"
    ON user_ai_keys FOR UPDATE
    USING (auth.uid()::text = user_id);
