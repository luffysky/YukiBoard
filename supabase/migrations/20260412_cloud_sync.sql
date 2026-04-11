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

-- ────────────────────────────────────────────
-- 4. 學習資料同步（7 種智慧輸入系統）
-- ────────────────────────────────────────────
-- adaptive_dict    — 自適應字典（詞頻學習）
-- personal_dict    — 個人詞庫（三層自動學詞）
-- phrase_predict   — 語句預測（bigram/trigram）
-- sentence_predict — 整句預測（句子 bigram + 片段接續）
-- style_profile    — 語氣風格（語尾詞 + App 維度風格分數）
-- typing_habits    — 打字習慣（時段/App 詞頻 + 速度）
-- correction_learn — 校正學習（接受/拒絕統計）
CREATE TABLE IF NOT EXISTS user_learning_data (
    user_id           TEXT PRIMARY KEY,
    adaptive_dict     JSONB NOT NULL DEFAULT '{}',
    personal_dict     JSONB NOT NULL DEFAULT '{}',
    phrase_predict    JSONB NOT NULL DEFAULT '{}',
    sentence_predict  JSONB NOT NULL DEFAULT '{}',
    style_profile     JSONB NOT NULL DEFAULT '{}',
    typing_habits     JSONB NOT NULL DEFAULT '{}',
    correction_learn  JSONB NOT NULL DEFAULT '{}',
    updated_at        TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE user_learning_data ENABLE ROW LEVEL SECURITY;

CREATE POLICY "user_learning_data_select" ON user_learning_data FOR SELECT
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_learning_data_insert" ON user_learning_data FOR INSERT
    WITH CHECK (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');

CREATE POLICY "user_learning_data_update" ON user_learning_data FOR UPDATE
    USING (auth.uid()::text = user_id OR user_id = current_setting('request.jwt.claims', true)::json->>'userId');
