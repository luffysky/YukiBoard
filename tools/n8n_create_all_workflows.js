const https = require('https');
const fs = require('fs');
const path = require('path');

// ── 讀取 .env ──
const envPath = path.join(__dirname, '..', '.env');
const envContent = fs.readFileSync(envPath, 'utf8');
function getEnv(key) {
  const m = envContent.match(new RegExp(`${key}=(.+)`));
  return m ? m[1].trim() : '';
}

const apiKey = getEnv('N8N_API_KEY');
if (!apiKey) { console.error('N8N_API_KEY not found in .env'); process.exit(1); }

const supabaseUrl = 'https://yukiboard.zeabur.app';
const supabaseKey = getEnv('YUKIBOARD_SUPABASE_ANON_KEY');
const insightUrl = 'https://insight.snowrealm.pet';
const platformApiKey = getEnv('PLATFORM_API_KEY');
const openaiKey = getEnv('OPENAI_API_KEY');
const anthropicKey = getEnv('ANTHROPIC_API_KEY');

// ══════════════════════════════════════════════════════════
// Helper: 發送 n8n API
// ══════════════════════════════════════════════════════════
function n8nRequest(method, apiPath, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = https.request({
      hostname: 'n8n.snowrealm.pet',
      path: apiPath,
      method,
      headers: {
        'X-N8N-API-KEY': apiKey,
        'Content-Type': 'application/json',
        ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {}),
      },
    }, res => {
      let buf = '';
      res.on('data', d => buf += d);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, data: JSON.parse(buf) }); }
        catch { resolve({ status: res.statusCode, data: buf }); }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function createAndActivate(workflow) {
  const name = workflow.name;
  console.log(`\n━━━ Creating: ${name} ━━━`);

  const res = await n8nRequest('POST', '/api/v1/workflows', workflow);
  if (res.status >= 400) {
    console.log(`  ✗ Failed (${res.status}):`, typeof res.data === 'string' ? res.data.substring(0, 200) : JSON.stringify(res.data).substring(0, 200));
    return null;
  }

  const id = res.data.id;
  console.log(`  ✓ Created: ID=${id}`);

  // Activate
  const act = await n8nRequest('PATCH', `/api/v1/workflows/${id}`, { active: true });
  console.log(`  ✓ Activated: ${act.data.active}`);
  console.log(`  → https://n8n.snowrealm.pet/workflow/${id}`);
  return id;
}

// ══════════════════════════════════════════════════════════
// Workflow 1: 政府防詐資料全面同步（增強版）
// ══════════════════════════════════════════════════════════
const workflow1_fraudSync = {
  name: '🛡️ 防詐資料全面同步 (政府 + 國際)',
  nodes: [
    {
      id: 'sched1', name: '每6小時觸發', type: 'n8n-nodes-base.scheduleTrigger',
      typeVersion: 1.2, position: [0, 0],
      parameters: { rule: { interval: [{ field: 'hours', hoursInterval: 6 }] } },
    },
    // ── 台灣政府來源 ──
    {
      id: 'moda', name: 'MODA 詐騙網域', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, -400],
      parameters: { url: 'https://www-api.moda.gov.tw/OpenData/Files/16352', method: 'GET', options: { timeout: 30000 } },
    },
    {
      id: 'npa165', name: '165 涉詐網站', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, -200],
      parameters: { url: 'https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/29E8E643-88ED-4952-B21E-BD42A3B7108C/resource/18211AF8-3248-4AF7-A50D-1B3F5777CCB9/download', method: 'GET', options: { timeout: 30000 } },
    },
    {
      id: 'debunk', name: '165 詐騙闢謠', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 0],
      parameters: { url: 'https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/4F4DF9A5-DF4C-4EE8-A50D-869347D38D9E/resource/7AABA1C9-86CB-4FD7-B837-00FA1427CB5C/download', method: 'GET', options: { timeout: 30000 } },
    },
    // ── 金管會投資警示 ──
    {
      id: 'fsc', name: '金管會 投資警示', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 200],
      parameters: { url: 'https://www.sfb.gov.tw/opendata/api/IllegalForeignFutures', method: 'GET', options: { timeout: 30000 } },
    },
    // ── 國際來源 ──
    {
      id: 'openphish', name: 'OpenPhish', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 400],
      parameters: { url: 'https://raw.githubusercontent.com/openphish/public_feed/refs/heads/main/feed.txt', method: 'GET', options: { timeout: 30000 } },
    },
    {
      id: 'urlhaus', name: 'URLhaus 惡意URL', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 600],
      parameters: { url: 'https://urlhaus.abuse.ch/downloads/text_online/', method: 'GET', options: { timeout: 30000 } },
    },
    {
      id: 'certpl', name: 'CERT.PL 釣魚', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 800],
      parameters: { url: 'https://hole.cert.pl/domains/domains.txt', method: 'GET', options: { timeout: 30000 } },
    },
    // ── 合併處理 ──
    {
      id: 'merge_all', name: '合併所有來源', type: 'n8n-nodes-base.code',
      typeVersion: 2, position: [700, 200],
      parameters: {
        jsCode: `
function extractDomain(input) {
  let s = input.trim().toLowerCase();
  s = s.replace(/^https?:\\/\\//, '').replace(/^www\\./, '');
  s = s.split('/')[0].split(':')[0];
  if (!s.includes('.') || s.length < 4 || s.length > 100) return null;
  if (/[^a-z0-9.\\-]/.test(s)) return null;
  return s;
}

const domains = new Set();
const allItems = $input.all();
const sources = {
  MODA: 0, '165_blocked': 0, '165_debunk': 0, FSC: 0,
  OpenPhish: 0, URLhaus: 0, 'CERT_PL': 0
};

for (const item of allItems) {
  const text = typeof item.json === 'string' ? item.json : JSON.stringify(item.json || '');

  // JSON array parsing
  try {
    let parsed = typeof item.json === 'string' ? JSON.parse(item.json) : item.json;
    if (parsed?.data) parsed = parsed.data;
    if (Array.isArray(parsed)) {
      for (const row of parsed) {
        const val = row.Domain || row.domain || row.url || row.URL || row['網域'] || row['網址'];
        if (val) {
          const d = extractDomain(String(val));
          if (d) domains.add(d);
        }
      }
      continue;
    }
  } catch {}

  // Line-based parsing (CSV, TXT)
  text.split('\\n').forEach(line => {
    const trimmed = line.trim();
    // URL line
    if (trimmed.startsWith('http')) {
      const d = extractDomain(trimmed);
      if (d) domains.add(d);
    }
    // Domain line (CERT.PL, URLhaus)
    else if (trimmed.includes('.') && !trimmed.startsWith('#') && !trimmed.includes(' ') && trimmed.length < 100) {
      const d = extractDomain(trimmed);
      if (d) domains.add(d);
    }
    // CSV fields
    else if (trimmed.includes(',')) {
      trimmed.split(',').forEach(f => {
        const clean = f.trim().replace(/['"]/g, '');
        if (clean.includes('.') && !clean.includes(' ') && clean.length < 100) {
          const d = extractDomain(clean);
          if (d) domains.add(d);
        }
      });
    }
    // Extract URLs from text (debunk)
    const urlRegex = /(?:https?:\\/\\/)?(?:www\\.)?([a-zA-Z0-9][-a-zA-Z0-9]*\\.)+[a-zA-Z]{2,}/g;
    let match;
    while ((match = urlRegex.exec(trimmed)) !== null) {
      const d = extractDomain(match[0]);
      if (d) domains.add(d);
    }
  });
}

return [{
  json: {
    domains: [...domains],
    count: domains.size,
    sources: Object.keys(sources),
    syncTime: new Date().toISOString()
  }
}];
`
      },
    },
    // ── 分批 + Upsert ──
    {
      id: 'split1', name: '分批 100', type: 'n8n-nodes-base.code',
      typeVersion: 2, position: [1000, 100],
      parameters: {
        jsCode: `
const domains = $json.domains || [];
const batches = [];
for (let i = 0; i < domains.length; i += 100) {
  batches.push({
    json: {
      batch: domains.slice(i, i + 100).map(d => ({
        domain: d, source: 'n8n_gov_sync', is_active: true
      })),
      batchIndex: Math.floor(i / 100),
      totalDomains: domains.length
    }
  });
}
return batches.length > 0 ? batches : [{ json: { batch: [], totalDomains: 0 } }];
`
      },
    },
    {
      id: 'upsert1', name: 'Supabase Upsert', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [1300, 100],
      parameters: {
        url: `${supabaseUrl}/rest/v1/fraud_blocklist`,
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
            { name: 'Prefer', value: 'resolution=merge-duplicates' },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: '={{ JSON.stringify($json.batch) }}',
      },
    },
    {
      id: 'log1', name: '同步結果', type: 'n8n-nodes-base.set',
      typeVersion: 3.4, position: [1000, 400],
      parameters: {
        assignments: { assignments: [
          { id: 'a1', name: 'total_domains', value: '={{ $json.count }}', type: 'number' },
          { id: 'a2', name: 'sync_time', value: '={{ $json.syncTime }}', type: 'string' },
          { id: 'a3', name: 'status', value: 'success', type: 'string' },
        ] },
      },
    },
  ],
  connections: {
    '每6小時觸發': { main: [[
      { node: 'MODA 詐騙網域', type: 'main', index: 0 },
      { node: '165 涉詐網站', type: 'main', index: 0 },
      { node: '165 詐騙闢謠', type: 'main', index: 0 },
      { node: '金管會 投資警示', type: 'main', index: 0 },
      { node: 'OpenPhish', type: 'main', index: 0 },
      { node: 'URLhaus 惡意URL', type: 'main', index: 0 },
      { node: 'CERT.PL 釣魚', type: 'main', index: 0 },
    ]] },
    'MODA 詐騙網域': { main: [[ { node: '合併所有來源', type: 'main', index: 0 } ]] },
    '165 涉詐網站': { main: [[ { node: '合併所有來源', type: 'main', index: 0 } ]] },
    '165 詐騙闢謠': { main: [[ { node: '合併所有來源', type: 'main', index: 0 } ]] },
    '金管會 投資警示': { main: [[ { node: '合併所有來源', type: 'main', index: 0 } ]] },
    'OpenPhish': { main: [[ { node: '合併所有來源', type: 'main', index: 0 } ]] },
    'URLhaus 惡意URL': { main: [[ { node: '合併所有來源', type: 'main', index: 0 } ]] },
    'CERT.PL 釣魚': { main: [[ { node: '合併所有來源', type: 'main', index: 0 } ]] },
    '合併所有來源': { main: [[
      { node: '分批 100', type: 'main', index: 0 },
      { node: '同步結果', type: 'main', index: 0 },
    ]] },
    '分批 100': { main: [[ { node: 'Supabase Upsert', type: 'main', index: 0 } ]] },
  },
  settings: { executionOrder: 'v1' },
};

// ══════════════════════════════════════════════════════════
// Workflow 2: AI 模型健康檢查
// ══════════════════════════════════════════════════════════
const workflow2_aiHealth = {
  name: '🏥 AI 模型健康檢查 (每30分鐘)',
  nodes: [
    {
      id: 'sched2', name: '每30分鐘', type: 'n8n-nodes-base.scheduleTrigger',
      typeVersion: 1.2, position: [0, 0],
      parameters: { rule: { interval: [{ field: 'minutes', minutesInterval: 30 }] } },
    },
    {
      id: 'check_anthropic', name: 'Anthropic Ping', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, -100],
      parameters: {
        url: 'https://api.anthropic.com/v1/messages',
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'x-api-key', value: anthropicKey },
            { name: 'anthropic-version', value: '2023-06-01' },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: JSON.stringify({
          model: 'claude-haiku-4-5-20251001',
          max_tokens: 5,
          messages: [{ role: 'user', content: 'ping' }],
        }),
        options: { timeout: 15000 },
      },
    },
    {
      id: 'check_openai', name: 'OpenAI Ping', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 100],
      parameters: {
        url: 'https://api.openai.com/v1/chat/completions',
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'Authorization', value: `Bearer ${openaiKey}` },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: JSON.stringify({
          model: 'gpt-4o-mini',
          max_tokens: 5,
          messages: [{ role: 'user', content: 'ping' }],
        }),
        options: { timeout: 15000 },
      },
    },
    {
      id: 'eval_health', name: '判斷健康狀態', type: 'n8n-nodes-base.code',
      typeVersion: 2, position: [600, 0],
      parameters: {
        jsCode: `
const items = $input.all();
const results = [];
const alerts = [];
const now = new Date().toISOString();

for (const item of items) {
  const statusCode = item.json?.statusCode || item.json?.status || 200;
  const isOk = statusCode >= 200 && statusCode < 300;
  const name = item.json?.model || item.json?.content?.[0]?.text ? 'Anthropic' : 'OpenAI';

  results.push({ provider: name, status: isOk ? 'healthy' : 'down', statusCode, time: now });

  if (!isOk) {
    alerts.push(\`⚠️ \${name} API 異常 (HTTP \${statusCode}) — \${now}\`);
  }
}

// 記錄到 Supabase（可選）
return [{
  json: {
    results,
    alerts,
    allHealthy: alerts.length === 0,
    checkTime: now
  }
}];
`
      },
    },
    {
      id: 'filter_alert', name: '有異常?', type: 'n8n-nodes-base.if',
      typeVersion: 2.2, position: [900, 0],
      parameters: {
        conditions: {
          options: { caseSensitive: true, leftValue: '', typeValidation: 'strict' },
          combinator: 'and',
          conditions: [{
            id: 'c1',
            leftValue: '={{ $json.allHealthy }}',
            rightValue: 'false',
            operator: { type: 'string', operation: 'equals' },
          }],
        },
      },
    },
    {
      id: 'log_health', name: '記錄健康狀態', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [1200, -100],
      parameters: {
        url: `${supabaseUrl}/rest/v1/system_health_log`,
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: '={{ JSON.stringify({ service: "ai_models", status: $json.allHealthy ? "healthy" : "degraded", details: JSON.stringify($json.results), checked_at: $json.checkTime }) }}',
      },
    },
  ],
  connections: {
    '每30分鐘': { main: [[
      { node: 'Anthropic Ping', type: 'main', index: 0 },
      { node: 'OpenAI Ping', type: 'main', index: 0 },
    ]] },
    'Anthropic Ping': { main: [[ { node: '判斷健康狀態', type: 'main', index: 0 } ]] },
    'OpenAI Ping': { main: [[ { node: '判斷健康狀態', type: 'main', index: 0 } ]] },
    '判斷健康狀態': { main: [[ { node: '有異常?', type: 'main', index: 0 } ]] },
    '有異常?': { main: [
      [ { node: '記錄健康狀態', type: 'main', index: 0 } ],  // true
      [ { node: '記錄健康狀態', type: 'main', index: 0 } ],  // false (also log)
    ] },
  },
  settings: { executionOrder: 'v1' },
};

// ══════════════════════════════════════════════════════════
// Workflow 3: 使用量統計日報
// ══════════════════════════════════════════════════════════
const workflow3_usageReport = {
  name: '📊 AI 使用量日報',
  nodes: [
    {
      id: 'sched3', name: '每日 23:50', type: 'n8n-nodes-base.scheduleTrigger',
      typeVersion: 1.2, position: [0, 0],
      parameters: {
        rule: { interval: [{ field: 'cronExpression', expression: '50 23 * * *' }] },
      },
    },
    {
      id: 'fetch_usage', name: '查詢今日使用量', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 0],
      parameters: {
        url: `${supabaseUrl}/rest/v1/rpc/get_daily_ai_usage`,
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: '{}',
        options: { timeout: 15000 },
      },
    },
    {
      id: 'fallback_usage', name: '直接查 usage 表', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 200],
      parameters: {
        url: `${supabaseUrl}/rest/v1/yukiboard_ai_usage?select=provider,model,tokens_used,created_at&created_at=gte.{{ new Date().toISOString().split('T')[0] }}&order=created_at.desc`,
        method: 'GET',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
          ],
        },
        options: { timeout: 15000 },
      },
    },
    {
      id: 'build_report', name: '彙整報表', type: 'n8n-nodes-base.code',
      typeVersion: 2, position: [600, 100],
      parameters: {
        jsCode: `
const items = $input.all();
const rows = items.flatMap(i => Array.isArray(i.json) ? i.json : [i.json]);

const byProvider = {};
let totalTokens = 0;
let totalRequests = rows.length;

for (const row of rows) {
  const provider = row.provider || 'unknown';
  const tokens = parseInt(row.tokens_used || '0', 10);
  if (!byProvider[provider]) byProvider[provider] = { tokens: 0, requests: 0 };
  byProvider[provider].tokens += tokens;
  byProvider[provider].requests++;
  totalTokens += tokens;
}

const today = new Date().toISOString().split('T')[0];
let report = \`📊 YukiBoard AI 使用日報 — \${today}\\n\\n\`;
report += \`總請求數：\${totalRequests}\\n\`;
report += \`總 Token 數：\${totalTokens.toLocaleString()}\\n\\n\`;

for (const [provider, stats] of Object.entries(byProvider)) {
  report += \`• \${provider}: \${stats.requests} 次, \${stats.tokens.toLocaleString()} tokens\\n\`;
}

// 預估成本
const costs = {
  anthropic: totalTokens * 0.000001, // ~$1/M tokens (haiku)
  openai: totalTokens * 0.0000005,   // ~$0.5/M tokens (4o-mini)
};

report += \`\\n💰 預估成本：~$\${(costs.anthropic + costs.openai).toFixed(4)}\\n\`;

return [{ json: { report, totalTokens, totalRequests, byProvider, date: today } }];
`
      },
    },
    {
      id: 'save_report', name: '儲存日報', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [900, 100],
      parameters: {
        url: `${supabaseUrl}/rest/v1/daily_usage_reports`,
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
            { name: 'Prefer', value: 'resolution=merge-duplicates' },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: '={{ JSON.stringify({ report_date: $json.date, total_tokens: $json.totalTokens, total_requests: $json.totalRequests, breakdown: JSON.stringify($json.byProvider), report_text: $json.report }) }}',
      },
    },
  ],
  connections: {
    '每日 23:50': { main: [[
      { node: '直接查 usage 表', type: 'main', index: 0 },
    ]] },
    '直接查 usage 表': { main: [[ { node: '彙整報表', type: 'main', index: 0 } ]] },
    '彙整報表': { main: [[ { node: '儲存日報', type: 'main', index: 0 } ]] },
  },
  settings: { executionOrder: 'v1' },
};

// ══════════════════════════════════════════════════════════
// Workflow 4: 邀請碼到期提醒
// ══════════════════════════════════════════════════════════
const workflow4_inviteExpiry = {
  name: '🎟️ 邀請碼到期提醒 (每日檢查)',
  nodes: [
    {
      id: 'sched4', name: '每日 09:00', type: 'n8n-nodes-base.scheduleTrigger',
      typeVersion: 1.2, position: [0, 0],
      parameters: {
        rule: { interval: [{ field: 'cronExpression', expression: '0 9 * * *' }] },
      },
    },
    {
      id: 'check_codes', name: '查詢快到期邀請碼', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 0],
      parameters: {
        url: `${supabaseUrl}/rest/v1/yukiboard_invite_codes?select=code,type,expires_at,daily_token_limit,used_count,max_uses&is_active=eq.true&expires_at=not.is.null&order=expires_at.asc`,
        method: 'GET',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
          ],
        },
        options: { timeout: 15000 },
      },
    },
    {
      id: 'filter_expiring', name: '篩選 7 天內到期', type: 'n8n-nodes-base.code',
      typeVersion: 2, position: [600, 0],
      parameters: {
        jsCode: `
const items = $input.all();
const codes = items.flatMap(i => Array.isArray(i.json) ? i.json : [i.json]);
const now = new Date();
const sevenDays = 7 * 24 * 60 * 60 * 1000;
const alerts = [];

for (const code of codes) {
  if (!code.expires_at) continue;
  const expires = new Date(code.expires_at);
  const daysLeft = Math.ceil((expires - now) / (24 * 60 * 60 * 1000));

  if (daysLeft <= 0) {
    alerts.push({ code: code.code, status: '已過期', daysLeft, type: code.type, usage: code.used_count + '/' + code.max_uses });
  } else if (daysLeft <= 7) {
    alerts.push({ code: code.code, status: daysLeft + '天後到期', daysLeft, type: code.type, usage: code.used_count + '/' + code.max_uses });
  }
}

let report = '';
if (alerts.length === 0) {
  report = '✅ 所有邀請碼狀態正常，無即將到期的碼';
} else {
  report = '🎟️ 邀請碼到期提醒\\n\\n';
  for (const a of alerts) {
    const icon = a.daysLeft <= 0 ? '🔴' : a.daysLeft <= 3 ? '🟡' : '🟢';
    report += icon + ' ' + a.code + ' — ' + a.status + ' (類型: ' + a.type + ', 使用: ' + a.usage + ')\\n';
  }
}

return [{ json: { alerts, report, hasExpiring: alerts.length > 0 } }];
`
      },
    },
    {
      id: 'log_invite', name: '記錄檢查結果', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [900, 0],
      parameters: {
        url: `${supabaseUrl}/rest/v1/system_health_log`,
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: '={{ JSON.stringify({ service: "invite_codes", status: $json.hasExpiring ? "warning" : "healthy", details: $json.report, checked_at: new Date().toISOString() }) }}',
      },
    },
  ],
  connections: {
    '每日 09:00': { main: [[ { node: '查詢快到期邀請碼', type: 'main', index: 0 } ]] },
    '查詢快到期邀請碼': { main: [[ { node: '篩選 7 天內到期', type: 'main', index: 0 } ]] },
    '篩選 7 天內到期': { main: [[ { node: '記錄檢查結果', type: 'main', index: 0 } ]] },
  },
  settings: { executionOrder: 'v1' },
};

// ══════════════════════════════════════════════════════════
// Workflow 5: 開源詞庫自動更新
// ══════════════════════════════════════════════════════════
const workflow5_dictSync = {
  name: '📚 開源詞庫自動更新 (注音/英文/倉頡)',
  nodes: [
    {
      id: 'sched5', name: '每週一 04:00', type: 'n8n-nodes-base.scheduleTrigger',
      typeVersion: 1.2, position: [0, 0],
      parameters: {
        rule: { interval: [{ field: 'cronExpression', expression: '0 4 * * 1' }] },
      },
    },
    // ── 開源詞庫來源 ──
    {
      id: 'rime_terra', name: 'RIME 地球拼音詞庫', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, -300],
      parameters: {
        url: 'https://raw.githubusercontent.com/rime/rime-terra-pinyin/master/terra_pinyin.dict.yaml',
        method: 'GET', options: { timeout: 60000 },
      },
    },
    {
      id: 'rime_luna', name: 'RIME 朙月拼音', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, -100],
      parameters: {
        url: 'https://raw.githubusercontent.com/rime/rime-luna-pinyin/master/luna_pinyin.dict.yaml',
        method: 'GET', options: { timeout: 60000 },
      },
    },
    {
      id: 'rime_bopomofo', name: 'RIME 注音', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 100],
      parameters: {
        url: 'https://raw.githubusercontent.com/rime/rime-bopomofo/master/bopomofo.dict.yaml',
        method: 'GET', options: { timeout: 60000 },
      },
    },
    {
      id: 'rime_cangjie', name: 'RIME 倉頡', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 300],
      parameters: {
        url: 'https://raw.githubusercontent.com/rime/rime-cangjie/master/cangjie5.dict.yaml',
        method: 'GET', options: { timeout: 60000 },
      },
    },
    {
      id: 'moedict', name: '萌典 (教育部) 詞條', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 500],
      parameters: {
        url: 'https://raw.githubusercontent.com/g0v/moedict-data/master/dict-revised.json',
        method: 'GET', options: { timeout: 120000 },
      },
    },
    {
      id: 'en_10k', name: '英文高頻 10K', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 700],
      parameters: {
        url: 'https://raw.githubusercontent.com/first20hours/google-10000-english/master/google-10000-english-usa-no-swears.txt',
        method: 'GET', options: { timeout: 30000 },
      },
    },
    // ── 處理合併 ──
    {
      id: 'process_dict', name: '解析轉換格式', type: 'n8n-nodes-base.code',
      typeVersion: 2, position: [700, 200],
      parameters: {
        jsCode: `
const items = $input.all();
const stats = { rime_bopomofo: 0, rime_cangjie: 0, rime_pinyin: 0, moedict: 0, english: 0 };

// ── 注音詞庫（RIME YAML → 我們的 tab-separated 格式）──
const bopomofoPairs = new Map(); // bopomofo -> Set<word>
const cangjiePairs = new Map();  // cangjie_code -> Set<word>
const englishWords = new Set();

for (const item of items) {
  const text = typeof item.json === 'string' ? item.json : JSON.stringify(item.json || '');

  // RIME YAML 格式: word\\tcode\\tfreq
  if (text.includes('---')) {
    const lines = text.split('\\n');
    let inData = false;
    for (const line of lines) {
      if (line.trim() === '...') break;
      if (line.trim() === '---') { inData = true; continue; }
      if (!inData) continue;
      if (line.startsWith('#') || !line.trim()) continue;

      const parts = line.split('\\t');
      if (parts.length >= 2) {
        const word = parts[0].trim();
        const code = parts[1].trim();
        if (!word || !code) continue;

        // 判斷是注音還是倉頡
        if (/[ㄅ-ㄩ]/.test(code) || /[a-z]+ ?[a-z]*/.test(code)) {
          if (/[ㄅ-ㄩ]/.test(code)) {
            if (!bopomofoPairs.has(code)) bopomofoPairs.set(code, new Set());
            bopomofoPairs.get(code).add(word);
            stats.rime_bopomofo++;
          } else if (code.length <= 5 && /^[a-z]+$/.test(code)) {
            if (!cangjiePairs.has(code)) cangjiePairs.set(code, new Set());
            cangjiePairs.get(code).add(word);
            stats.rime_cangjie++;
          } else {
            stats.rime_pinyin++;
          }
        }
      }
    }
  }

  // 萌典 JSON (教育部辭典)
  try {
    let moeData = typeof item.json === 'string' ? JSON.parse(item.json) : item.json;
    if (Array.isArray(moeData)) {
      for (const entry of moeData) {
        if (entry.title && entry.bopomofo) {
          const word = entry.title;
          const reading = entry.bopomofo.replace(/[，、。！？]/g, ' ').trim();
          if (reading && /[ㄅ-ㄩ]/.test(reading)) {
            if (!bopomofoPairs.has(reading)) bopomofoPairs.set(reading, new Set());
            bopomofoPairs.get(reading).add(word);
            stats.moedict++;
          }
        }
      }
    }
  } catch {}

  // 英文（純文字，一行一詞）
  if (!text.includes('---') && !text.includes('{')) {
    const words = text.split('\\n').map(w => w.trim().toLowerCase()).filter(w => /^[a-z]{2,}$/.test(w));
    if (words.length > 100) {
      words.forEach(w => englishWords.add(w));
      stats.english = words.length;
    }
  }
}

return [{
  json: {
    bopomofo_count: bopomofoPairs.size,
    cangjie_count: cangjiePairs.size,
    english_count: englishWords.size,
    stats,
    syncTime: new Date().toISOString(),
    // 儲存摘要（實際詞庫檔太大，由 App 端直接拉取 raw）
    sources: ['rime-bopomofo', 'rime-cangjie', 'rime-terra-pinyin', 'rime-luna-pinyin', 'moedict', 'google-10000-english'],
  }
}];
`
      },
    },
    {
      id: 'save_dict_meta', name: '儲存詞庫版本', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [1000, 200],
      parameters: {
        url: `${supabaseUrl}/rest/v1/dictionary_versions`,
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
            { name: 'Prefer', value: 'resolution=merge-duplicates' },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: '={{ JSON.stringify({ dict_type: "all", bopomofo_count: $json.bopomofo_count, cangjie_count: $json.cangjie_count, english_count: $json.english_count, sources: JSON.stringify($json.sources), synced_at: $json.syncTime }) }}',
      },
    },
  ],
  connections: {
    '每週一 04:00': { main: [[
      { node: 'RIME 地球拼音詞庫', type: 'main', index: 0 },
      { node: 'RIME 朙月拼音', type: 'main', index: 0 },
      { node: 'RIME 注音', type: 'main', index: 0 },
      { node: 'RIME 倉頡', type: 'main', index: 0 },
      { node: '萌典 (教育部) 詞條', type: 'main', index: 0 },
      { node: '英文高頻 10K', type: 'main', index: 0 },
    ]] },
    'RIME 地球拼音詞庫': { main: [[ { node: '解析轉換格式', type: 'main', index: 0 } ]] },
    'RIME 朙月拼音': { main: [[ { node: '解析轉換格式', type: 'main', index: 0 } ]] },
    'RIME 注音': { main: [[ { node: '解析轉換格式', type: 'main', index: 0 } ]] },
    'RIME 倉頡': { main: [[ { node: '解析轉換格式', type: 'main', index: 0 } ]] },
    '萌典 (教育部) 詞條': { main: [[ { node: '解析轉換格式', type: 'main', index: 0 } ]] },
    '英文高頻 10K': { main: [[ { node: '解析轉換格式', type: 'main', index: 0 } ]] },
    '解析轉換格式': { main: [[ { node: '儲存詞庫版本', type: 'main', index: 0 } ]] },
  },
  settings: { executionOrder: 'v1' },
};

// ══════════════════════════════════════════════════════════
// Workflow 6: 貼圖/主題包同步
// ══════════════════════════════════════════════════════════
const workflow6_stickerSync = {
  name: '🎨 貼圖主題包同步檢查',
  nodes: [
    {
      id: 'sched6', name: '每12小時', type: 'n8n-nodes-base.scheduleTrigger',
      typeVersion: 1.2, position: [0, 0],
      parameters: { rule: { interval: [{ field: 'hours', hoursInterval: 12 }] } },
    },
    {
      id: 'check_stickers', name: '查詢 Insight 貼圖清單', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, -100],
      parameters: {
        url: `${insightUrl}/api/yukiboard/stickers`,
        method: 'GET',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'X-Platform-Key', value: platformApiKey },
          ],
        },
        options: { timeout: 15000 },
      },
    },
    {
      id: 'check_themes', name: '查詢 Insight 主題清單', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [300, 100],
      parameters: {
        url: `${insightUrl}/api/yukiboard/themes`,
        method: 'GET',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'X-Platform-Key', value: platformApiKey },
          ],
        },
        options: { timeout: 15000 },
      },
    },
    {
      id: 'eval_assets', name: '比對版本', type: 'n8n-nodes-base.code',
      typeVersion: 2, position: [600, 0],
      parameters: {
        jsCode: `
const items = $input.all();
const now = new Date().toISOString();

let stickerCount = 0, themeCount = 0;
let newStickers = 0, newThemes = 0;

for (const item of items) {
  const data = Array.isArray(item.json) ? item.json : (item.json?.data || []);
  if (data.length > 0) {
    // 簡單統計
    if (data[0]?.pack_id || data[0]?.sticker_id) {
      stickerCount = data.length;
    } else {
      themeCount = data.length;
    }
  }
}

return [{
  json: {
    sticker_packs: stickerCount,
    themes: themeCount,
    check_time: now,
    status: 'checked'
  }
}];
`
      },
    },
    {
      id: 'log_assets', name: '記錄同步狀態', type: 'n8n-nodes-base.httpRequest',
      typeVersion: 4.2, position: [900, 0],
      parameters: {
        url: `${supabaseUrl}/rest/v1/system_health_log`,
        method: 'POST',
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: 'apikey', value: supabaseKey },
            { name: 'Authorization', value: `Bearer ${supabaseKey}` },
            { name: 'Content-Type', value: 'application/json' },
          ],
        },
        sendBody: true, specifyBody: 'json',
        jsonBody: '={{ JSON.stringify({ service: "assets_sync", status: "healthy", details: JSON.stringify($json), checked_at: $json.check_time }) }}',
      },
    },
  ],
  connections: {
    '每12小時': { main: [[
      { node: '查詢 Insight 貼圖清單', type: 'main', index: 0 },
      { node: '查詢 Insight 主題清單', type: 'main', index: 0 },
    ]] },
    '查詢 Insight 貼圖清單': { main: [[ { node: '比對版本', type: 'main', index: 0 } ]] },
    '查詢 Insight 主題清單': { main: [[ { node: '比對版本', type: 'main', index: 0 } ]] },
    '比對版本': { main: [[ { node: '記錄同步狀態', type: 'main', index: 0 } ]] },
  },
  settings: { executionOrder: 'v1' },
};

// ══════════════════════════════════════════════════════════
// 主流程：依序建立所有 workflow
// ══════════════════════════════════════════════════════════
async function main() {
  console.log('🚀 開始建立所有 n8n workflows...\n');

  const workflows = [
    workflow1_fraudSync,
    workflow2_aiHealth,
    workflow3_usageReport,
    workflow4_inviteExpiry,
    workflow5_dictSync,
    workflow6_stickerSync,
  ];

  const results = [];
  for (const wf of workflows) {
    try {
      const id = await createAndActivate(wf);
      results.push({ name: wf.name, id, status: id ? '✓' : '✗' });
    } catch (err) {
      console.log(`  ✗ Error: ${err.message}`);
      results.push({ name: wf.name, id: null, status: '✗' });
    }
  }

  console.log('\n\n══════════════════════════════════════');
  console.log('📋 建立結果：');
  console.log('══════════════════════════════════════');
  for (const r of results) {
    console.log(`  ${r.status} ${r.name}${r.id ? ` → https://n8n.snowrealm.pet/workflow/${r.id}` : ''}`);
  }
  console.log(`\n完成：${results.filter(r => r.id).length}/${results.length} 個 workflow`);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
