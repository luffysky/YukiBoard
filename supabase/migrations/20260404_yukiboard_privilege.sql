-- YukiBoard 特權欄位
ALTER TABLE admin_users
ADD COLUMN IF NOT EXISTS yukiboard_privilege BOOLEAN DEFAULT FALSE;

ALTER TABLE admin_users
ADD COLUMN IF NOT EXISTS yukiboard_privilege_note TEXT;
