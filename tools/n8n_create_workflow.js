const https = require('https');
const fs = require('fs');
const path = require('path');

// Read API key from .env
const envPath = path.join(__dirname, '..', '.env');
const envContent = fs.readFileSync(envPath, 'utf8');
const apiKeyMatch = envContent.match(/N8N_API_KEY=(.+)/);
if (!apiKeyMatch) { console.error('N8N_API_KEY not found in .env'); process.exit(1); }
const apiKey = apiKeyMatch[1].trim();

const supabaseKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyAgCiAgICAicm9sZSI6ICJhbm9uIiwKICAgICJpc3MiOiAic3VwYWJhc2UtZGVtbyIsCiAgICAiaWF0IjogMTY0MTc2OTIwMCwKICAgICJleHAiOiAxNzk5NTM1NjAwCn0.dc_X5iR_VP_qT0zsiyj_I_OZ2T9FtRU2BBNWN8Bu4GE';

const codeNodeJs = `
// 從各來源提取域名
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

// 1. MODA (JSON) - first item
try {
  const modaItem = allItems[0];
  let modaData = modaItem?.json;
  if (typeof modaData === 'string') modaData = JSON.parse(modaData);
  if (modaData?.data) modaData = modaData.data;
  if (Array.isArray(modaData)) {
    for (const item of modaData) {
      const val = item.Domain || item.domain || item.url || item.URL;
      if (val) { const d = extractDomain(String(val)); if (d) domains.add(d); }
    }
  }
} catch(e) { /* skip */ }

// 2. 165 (CSV) - second item
try {
  const item165 = allItems[1];
  const text = typeof item165?.json === 'string' ? item165.json : JSON.stringify(item165?.json || '');
  text.split('\\n').forEach(line => {
    line.split(',').forEach(field => {
      const f = field.trim().replace(/['"]/g, '');
      if (f.includes('.') && !f.includes(' ') && f.length < 100) {
        const d = extractDomain(f);
        if (d) domains.add(d);
      }
    });
  });
} catch(e) { /* skip */ }

// 3. OpenPhish (text URLs) - third item
try {
  const itemOP = allItems[2];
  const text = typeof itemOP?.json === 'string' ? itemOP.json : JSON.stringify(itemOP?.json || '');
  text.split('\\n').forEach(line => {
    const url = line.trim();
    if (url.startsWith('http')) {
      const d = extractDomain(url);
      if (d) domains.add(d);
    }
  });
} catch(e) { /* skip */ }

return [{
  json: {
    domains: [...domains],
    count: domains.size,
    sources: ['MODA', '165', 'OpenPhish'],
    syncTime: new Date().toISOString()
  }
}];
`;

const workflow = {
  name: "防詐資料自動同步 (MODA + 165 + OpenPhish)",
  nodes: [
    {
      id: "schedule",
      name: "每6小時觸發",
      type: "n8n-nodes-base.scheduleTrigger",
      typeVersion: 1.2,
      position: [0, 0],
      parameters: {
        rule: { interval: [{ field: "hours", hoursInterval: 6 }] }
      }
    },
    {
      id: "moda_fetch",
      name: "MODA 詐騙網域",
      type: "n8n-nodes-base.httpRequest",
      typeVersion: 4.2,
      position: [300, -200],
      parameters: {
        url: "https://www-api.moda.gov.tw/OpenData/Files/16352",
        method: "GET",
        options: { timeout: 30000 }
      }
    },
    {
      id: "npa165_fetch",
      name: "165 涉詐網站",
      type: "n8n-nodes-base.httpRequest",
      typeVersion: 4.2,
      position: [300, 0],
      parameters: {
        url: "https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/29E8E643-88ED-4952-B21E-BD42A3B7108C/resource/18211AF8-3248-4AF7-A50D-1B3F5777CCB9/download",
        method: "GET",
        options: { timeout: 30000 }
      }
    },
    {
      id: "openphish_fetch",
      name: "OpenPhish 釣魚URL",
      type: "n8n-nodes-base.httpRequest",
      typeVersion: 4.2,
      position: [300, 200],
      parameters: {
        url: "https://raw.githubusercontent.com/openphish/public_feed/refs/heads/main/feed.txt",
        method: "GET",
        options: { timeout: 30000 }
      }
    },
    {
      id: "process_domains",
      name: "解析合併域名",
      type: "n8n-nodes-base.code",
      typeVersion: 2,
      position: [600, 0],
      parameters: {
        jsCode: codeNodeJs
      }
    },
    {
      id: "split_batches",
      name: "分批處理",
      type: "n8n-nodes-base.code",
      typeVersion: 2,
      position: [900, -100],
      parameters: {
        jsCode: `
// 把域名分成每批 100 筆的 Supabase upsert 請求
const domains = $json.domains || [];
const batches = [];
for (let i = 0; i < domains.length; i += 100) {
  batches.push({
    json: {
      batch: domains.slice(i, i + 100).map(d => ({
        domain: d,
        source: 'n8n_gov_sync',
        is_active: true
      })),
      batchIndex: Math.floor(i / 100),
      totalDomains: domains.length
    }
  });
}
return batches.length > 0 ? batches : [{ json: { batch: [], totalDomains: 0 } }];
`
      }
    },
    {
      id: "upsert_supabase",
      name: "Supabase Upsert",
      type: "n8n-nodes-base.httpRequest",
      typeVersion: 4.2,
      position: [1200, -100],
      parameters: {
        url: "https://yukiboard.zeabur.app/rest/v1/fraud_blocklist",
        method: "POST",
        sendHeaders: true,
        headerParameters: {
          parameters: [
            { name: "apikey", value: supabaseKey },
            { name: "Authorization", value: "Bearer " + supabaseKey },
            { name: "Prefer", value: "resolution=merge-duplicates" },
            { name: "Content-Type", value: "application/json" }
          ]
        },
        sendBody: true,
        specifyBody: "json",
        jsonBody: "={{ JSON.stringify($json.batch) }}"
      }
    },
    {
      id: "log_result",
      name: "同步結果",
      type: "n8n-nodes-base.set",
      typeVersion: 3.4,
      position: [900, 200],
      parameters: {
        assignments: {
          assignments: [
            { id: "a1", name: "total_domains", value: "={{ $json.count }}", type: "number" },
            { id: "a2", name: "sync_time", value: "={{ $json.syncTime }}", type: "string" },
            { id: "a3", name: "status", value: "success", type: "string" }
          ]
        }
      }
    }
  ],
  connections: {
    "每6小時觸發": { main: [[
      { node: "MODA 詐騙網域", type: "main", index: 0 },
      { node: "165 涉詐網站", type: "main", index: 0 },
      { node: "OpenPhish 釣魚URL", type: "main", index: 0 }
    ]] },
    "MODA 詐騙網域": { main: [[ { node: "解析合併域名", type: "main", index: 0 } ]] },
    "165 涉詐網站": { main: [[ { node: "解析合併域名", type: "main", index: 0 } ]] },
    "OpenPhish 釣魚URL": { main: [[ { node: "解析合併域名", type: "main", index: 0 } ]] },
    "解析合併域名": { main: [[
      { node: "分批處理", type: "main", index: 0 },
      { node: "同步結果", type: "main", index: 0 }
    ]] },
    "分批處理": { main: [[ { node: "Supabase Upsert", type: "main", index: 0 } ]] }
  },
  settings: {
    executionOrder: "v1"
  }
};

const data = JSON.stringify(workflow);

const req = https.request({
  hostname: 'n8n.snowrealm.pet',
  path: '/api/v1/workflows',
  method: 'POST',
  headers: {
    'X-N8N-API-KEY': apiKey,
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(data)
  }
}, res => {
  let body = '';
  res.on('data', d => body += d);
  res.on('end', () => {
    console.log('Status:', res.statusCode);
    try {
      const result = JSON.parse(body);
      console.log('Workflow ID:', result.id);
      console.log('Name:', result.name);
      console.log('Active:', result.active);

      // Activate the workflow
      if (result.id) {
        const activateData = JSON.stringify({ active: true });
        const activateReq = https.request({
          hostname: 'n8n.snowrealm.pet',
          path: `/api/v1/workflows/${result.id}`,
          method: 'PATCH',
          headers: {
            'X-N8N-API-KEY': apiKey,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(activateData)
          }
        }, activateRes => {
          let abody = '';
          activateRes.on('data', d => abody += d);
          activateRes.on('end', () => {
            const ar = JSON.parse(abody);
            console.log('Activated:', ar.active);
            console.log('\nWorkflow created and activated!');
            console.log('View: https://n8n.snowrealm.pet/workflow/' + result.id);
          });
        });
        activateReq.write(activateData);
        activateReq.end();
      }
    } catch(e) {
      console.log('Response:', body.substring(0, 500));
    }
  });
});
req.on('error', e => console.log('Error:', e.message));
req.write(data);
req.end();
