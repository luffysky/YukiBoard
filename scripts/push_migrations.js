#!/usr/bin/env node
/**
 * YukiBoard Supabase Migration Push Script
 *
 * 從 .env 讀取連線資訊，將 supabase/migrations/ 下的 SQL 推到對應的 Supabase 實例。
 *
 * 用法：
 *   node scripts/push_migrations.js              # 推全部
 *   node scripts/push_migrations.js --dry-run    # 只顯示要推什麼，不執行
 *   node scripts/push_migrations.js --target yukiboard   # 只推 YukiBoard
 *   node scripts/push_migrations.js --target insight      # 只推 Insight
 */

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

// ── 讀取 .env ────────────────────────────────────────────
function loadEnv(envPath) {
  const vars = {};
  if (!fs.existsSync(envPath)) return vars;
  const lines = fs.readFileSync(envPath, 'utf-8').split('\n');
  for (const raw of lines) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const idx = line.indexOf('=');
    if (idx < 0) continue;
    const key = line.substring(0, idx).trim();
    let val = line.substring(idx + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    vars[key] = val;
  }
  return vars;
}

// ── 連線設定 ─────────────────────────────────────────────
// YukiBoard: .env → YUKIBOARD_SUPABASE_PG（被註解）→ 手動組合
// Insight:   insight-engine/.env.local → DATABASE_URL
const yukiboardEnv = loadEnv(path.join(__dirname, '..', '.env'));
const insightEnvPath = path.join(__dirname, '..', '..', 'snowrealm-insight-engine', 'insight-engine', '.env.local');
const insightEnv = loadEnv(insightEnvPath);

const TARGETS = {
  yukiboard: {
    connectionString: 'postgresql://postgres:17gmC3IDij985LVP6r4BhsX02YKJtQnl@43.167.237.37:30183/postgres',
    label: 'YukiBoard Supabase',
  },
  insight: {
    connectionString: insightEnv.DATABASE_URL || 'postgresql://postgres:3n25zPNSZyU64T9QrAmxX817WGk0RYwc@43.167.237.37:30465/postgres',
    label: 'Insight Engine Supabase',
  },
};

// ── Migration 歸屬 ──────────────────────────────────────
// key = 檔名, value = 'yukiboard' | 'insight'
const MIGRATION_TARGET = {
  '20260404_yukiboard_invite.sql': 'yukiboard',
  '20260404_yukiboard_privilege.sql': 'insight',    // ALTER TABLE admin_users → Insight DB
  '20260411_user_ai_keys.sql': 'yukiboard',
  '20260412_cloud_sync.sql': 'yukiboard',
};

// ── 建立 migration 追蹤表 ────────────────────────────────
const TRACKING_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS _migrations (
  id SERIAL PRIMARY KEY,
  filename TEXT UNIQUE NOT NULL,
  applied_at TIMESTAMPTZ DEFAULT now()
);
`;

async function pushMigrations(targetFilter, dryRun) {
  const migrationsDir = path.join(__dirname, '..', 'supabase', 'migrations');
  if (!fs.existsSync(migrationsDir)) {
    console.error('❌ supabase/migrations/ 目錄不存在');
    process.exit(1);
  }

  const files = fs.readdirSync(migrationsDir)
    .filter(f => f.endsWith('.sql'))
    .sort();

  if (files.length === 0) {
    console.log('沒有 migration 檔案');
    return;
  }

  // 按 target 分組
  const grouped = { yukiboard: [], insight: [] };
  for (const f of files) {
    const target = MIGRATION_TARGET[f];
    if (!target) {
      console.warn(`⚠️  ${f} 未定義歸屬，跳過`);
      continue;
    }
    if (targetFilter && target !== targetFilter) continue;
    grouped[target].push(f);
  }

  for (const [target, migrations] of Object.entries(grouped)) {
    if (migrations.length === 0) continue;

    const config = TARGETS[target];
    console.log(`\n${'═'.repeat(60)}`);
    console.log(`📦 ${config.label} (${target})`);
    console.log(`${'═'.repeat(60)}`);

    if (dryRun) {
      for (const m of migrations) {
        console.log(`  📄 [DRY-RUN] ${m}`);
      }
      continue;
    }

    const pool = new Pool({
      connectionString: config.connectionString,
      ssl: false,
      connectionTimeoutMillis: 10000,
    });

    try {
      // 確保追蹤表存在
      await pool.query(TRACKING_TABLE_SQL);

      // 查已套用的 migration
      const { rows: applied } = await pool.query('SELECT filename FROM _migrations');
      const appliedSet = new Set(applied.map(r => r.filename));

      for (const m of migrations) {
        if (appliedSet.has(m)) {
          console.log(`  ✅ ${m} — 已套用，跳過`);
          continue;
        }

        const sql = fs.readFileSync(path.join(migrationsDir, m), 'utf-8');
        console.log(`  ⏳ ${m} — 執行中...`);

        try {
          await pool.query('BEGIN');
          await pool.query(sql);
          await pool.query('INSERT INTO _migrations (filename) VALUES ($1)', [m]);
          await pool.query('COMMIT');
          console.log(`  ✅ ${m} — 成功`);
        } catch (err) {
          await pool.query('ROLLBACK');
          console.error(`  ❌ ${m} — 失敗: ${err.message}`);
        }
      }
    } catch (err) {
      console.error(`  ❌ 連線失敗: ${err.message}`);
    } finally {
      await pool.end();
    }
  }

  console.log('\n完成！');
}

// ── CLI ──────────────────────────────────────────────────
const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
let targetFilter = null;
const targetIdx = args.indexOf('--target');
if (targetIdx >= 0 && args[targetIdx + 1]) {
  targetFilter = args[targetIdx + 1].toLowerCase();
  if (!TARGETS[targetFilter]) {
    console.error(`❌ 未知 target: ${targetFilter}（可選: yukiboard / insight）`);
    process.exit(1);
  }
}

pushMigrations(targetFilter, dryRun);
