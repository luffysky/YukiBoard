-- YukiBoard 雲端同步表
-- 使用者帳號綁定 Insight Engine（user_id 從 SnowRealmAuthClient 取得）

-- ────────────────────────────────────────────
-- 1. 偏好設定同步
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_settings (
    user_id    TEXT PRIMARY KEY,
    settings   JSONB NOT NULL DEFAULT '{}',  -- SharedPreferences 全量 JSON 快照
    updated_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "user_settings_select" ON user_settings FOR SELECT
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_settings_insert" ON user_settings FOR INSERT
    WITH CHECK (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_settings_update" ON user_settings FOR UPDATE
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

-- ────────────────────────────────────────────
-- 2. 自訂詞庫同步
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_dictionary (
    user_id    TEXT NOT NULL,
    word       TEXT NOT NULL,
    bopomofo   TEXT NOT NULL DEFAULT '',
    category   TEXT NOT NULL DEFAULT '一般',
    updated_at TIMESTAMPTZ DEFAULT now(),
    deleted    BOOLEAN DEFAULT false,         -- 軟刪除（同步用）
    PRIMARY KEY (user_id, word)
);

CREATE INDEX idx_user_dict_user ON user_dictionary(user_id);
CREATE INDEX idx_user_dict_updated ON user_dictionary(user_id, updated_at);

ALTER TABLE user_dictionary ENABLE ROW LEVEL SECURITY;

CREATE POLICY "user_dictionary_select" ON user_dictionary FOR SELECT
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_dictionary_insert" ON user_dictionary FOR INSERT
    WITH CHECK (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_dictionary_update" ON user_dictionary FOR UPDATE
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_dictionary_delete" ON user_dictionary FOR DELETE
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

-- ────────────────────────────────────────────
-- 3. 主題 / 背景設定同步
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_themes (
    user_id       TEXT PRIMARY KEY,
    theme_config  JSONB NOT NULL DEFAULT '{}',  -- 主題偏好 JSON
    updated_at    TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE user_themes ENABLE ROW LEVEL SECURITY;

CREATE POLICY "user_themes_select" ON user_themes FOR SELECT
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_themes_insert" ON user_themes FOR INSERT
    WITH CHECK (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_themes_update" ON user_themes FOR UPDATE
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');
