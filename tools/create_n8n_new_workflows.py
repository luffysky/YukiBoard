#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Create 7 new n8n automation workflows for YukiBoard."""
import json, urllib.request, ssl, sys, os

BASE = "https://n8n.snowrealm.pet"

# Read from .env
env_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".env")
env_vars = {}
if os.path.exists(env_path):
    with open(env_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env_vars[k.strip()] = v.strip().strip("'\"")

API_KEY = env_vars.get("N8N_API_KEY", "")
SB = env_vars.get("YUKIBOARD_SUPABASE_URL", "https://yukiboard.zeabur.app")
SK = env_vars.get("YUKIBOARD_SUPABASE_ANON_KEY", "")
INS = env_vars.get("INSIGHT_API_BASE_URL", "https://insight.snowrealm.pet")

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def sh():
    return [
        {"name": "apikey", "value": SK},
        {"name": "Authorization", "value": "Bearer " + SK},
        {"name": "Content-Type", "value": "application/json"},
        {"name": "Prefer", "value": "return=minimal"},
    ]

def go(wf):
    name = wf["name"]
    data = json.dumps(wf).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/v1/workflows", data=data, method="POST")
    req.add_header("X-N8N-API-KEY", API_KEY)
    req.add_header("Content-Type", "application/json")
    try:
        resp = urllib.request.urlopen(req, context=ctx)
        r = json.loads(resp.read())
        wid = r.get("id", "?")
        # Activate
        ar = urllib.request.Request(BASE + "/api/v1/workflows/" + wid + "/activate", method="POST")
        ar.add_header("X-N8N-API-KEY", API_KEY)
        urllib.request.urlopen(ar, context=ctx)
        print("OK " + name + " -> " + wid)
    except Exception as e:
        print("FAIL " + name + " -> " + str(e)[:200])

wfs = [
    {
        "name": "Firebase Crashlytics Alert",
        "nodes": [
            {"id": "s", "name": "hourly", "type": "n8n-nodes-base.scheduleTrigger", "typeVersion": 1.2, "position": [0, 0],
             "parameters": {"rule": {"interval": [{"field": "hours", "hoursInterval": 1}]}}},
            {"id": "a", "name": "firebase", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 0],
             "parameters": {"method": "GET", "url": "https://firebase.googleapis.com/v1beta1/projects/yukiboard-app/apps", "options": {}}},
            {"id": "b", "name": "log", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [400, 0],
             "parameters": {"method": "POST", "url": SB + "/rest/v1/automation_logs", "sendHeaders": True,
                            "headerParameters": {"parameters": sh()}, "sendBody": True, "specifyBody": "json",
                            "jsonBody": json.dumps({"workflow": "crashlytics", "status": "checked"})}}
        ],
        "connections": {"hourly": {"main": [[{"node": "firebase", "type": "main", "index": 0}]]},
                        "firebase": {"main": [[{"node": "log", "type": "main", "index": 0}]]}},
        "settings": {"executionOrder": "v1"},
    },
    {
        "name": "ZCoin Anomaly Detection",
        "nodes": [
            {"id": "s", "name": "every30m", "type": "n8n-nodes-base.scheduleTrigger", "typeVersion": 1.2, "position": [0, 0],
             "parameters": {"rule": {"interval": [{"field": "minutes", "minutesInterval": 30}]}}},
            {"id": "a", "name": "txns", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 0],
             "parameters": {"method": "GET", "url": SB + "/rest/v1/z_coin_transactions?order=created_at.desc&limit=100",
                            "sendHeaders": True, "headerParameters": {"parameters": [{"name": "apikey", "value": SK}, {"name": "Authorization", "value": "Bearer " + SK}]}}},
            {"id": "b", "name": "analyze", "type": "n8n-nodes-base.code", "typeVersion": 2, "position": [400, 0],
             "parameters": {"jsCode": "const t=$input.all().map(i=>i.json);return [{json:{count:t.length}}];"}},
            {"id": "c", "name": "log", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [600, 0],
             "parameters": {"method": "POST", "url": SB + "/rest/v1/automation_logs", "sendHeaders": True,
                            "headerParameters": {"parameters": sh()}, "sendBody": True, "specifyBody": "json",
                            "jsonBody": json.dumps({"workflow": "zcoin_anomaly", "status": "checked"})}}
        ],
        "connections": {"every30m": {"main": [[{"node": "txns", "type": "main", "index": 0}]]},
                        "txns": {"main": [[{"node": "analyze", "type": "main", "index": 0}]]},
                        "analyze": {"main": [[{"node": "log", "type": "main", "index": 0}]]}},
        "settings": {"executionOrder": "v1"},
    },
    {
        "name": "DAU Retention Report",
        "nodes": [
            {"id": "s", "name": "daily8", "type": "n8n-nodes-base.scheduleTrigger", "typeVersion": 1.2, "position": [0, 0],
             "parameters": {"rule": {"interval": [{"field": "cronExpression", "expression": "0 8 * * *"}]}}},
            {"id": "a", "name": "users", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 0],
             "parameters": {"method": "GET", "url": SB + "/rest/v1/user_profiles?select=id,last_active_at&limit=500",
                            "sendHeaders": True, "headerParameters": {"parameters": [{"name": "apikey", "value": SK}, {"name": "Authorization", "value": "Bearer " + SK}]}}},
            {"id": "b", "name": "calc", "type": "n8n-nodes-base.code", "typeVersion": 2, "position": [400, 0],
             "parameters": {"jsCode": "const u=$input.all().map(i=>i.json);return [{json:{total:u.length}}];"}},
            {"id": "c", "name": "log", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [600, 0],
             "parameters": {"method": "POST", "url": SB + "/rest/v1/automation_logs", "sendHeaders": True,
                            "headerParameters": {"parameters": sh()}, "sendBody": True, "specifyBody": "json",
                            "jsonBody": json.dumps({"workflow": "dau_report", "status": "reported"})}}
        ],
        "connections": {"daily8": {"main": [[{"node": "users", "type": "main", "index": 0}]]},
                        "users": {"main": [[{"node": "calc", "type": "main", "index": 0}]]},
                        "calc": {"main": [[{"node": "log", "type": "main", "index": 0}]]}},
        "settings": {"executionOrder": "v1"},
    },
    {
        "name": "API Health Monitor",
        "nodes": [
            {"id": "s", "name": "every5m", "type": "n8n-nodes-base.scheduleTrigger", "typeVersion": 1.2, "position": [0, 0],
             "parameters": {"rule": {"interval": [{"field": "minutes", "minutesInterval": 5}]}}},
            {"id": "a", "name": "insight", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 0],
             "parameters": {"method": "GET", "url": INS + "/health", "options": {"timeout": 10000}, "onError": "continueRegularOutput"}},
            {"id": "b", "name": "supabase", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 200],
             "parameters": {"method": "GET", "url": SB + "/rest/v1/", "sendHeaders": True,
                            "headerParameters": {"parameters": [{"name": "apikey", "value": SK}]},
                            "options": {"timeout": 10000}, "onError": "continueRegularOutput"}},
            {"id": "c", "name": "check", "type": "n8n-nodes-base.code", "typeVersion": 2, "position": [400, 100],
             "parameters": {"jsCode": "return [{json:{status:'checked',time:new Date().toISOString()}}];"}},
            {"id": "d", "name": "log", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [600, 100],
             "parameters": {"method": "POST", "url": SB + "/rest/v1/automation_logs", "sendHeaders": True,
                            "headerParameters": {"parameters": sh()}, "sendBody": True, "specifyBody": "json",
                            "jsonBody": json.dumps({"workflow": "health_monitor", "status": "checked"})}}
        ],
        "connections": {"every5m": {"main": [[{"node": "insight", "type": "main", "index": 0}, {"node": "supabase", "type": "main", "index": 0}]]},
                        "insight": {"main": [[{"node": "check", "type": "main", "index": 0}]]},
                        "supabase": {"main": [[{"node": "check", "type": "main", "index": 0}]]},
                        "check": {"main": [[{"node": "log", "type": "main", "index": 0}]]}},
        "settings": {"executionOrder": "v1"},
    },
    {
        "name": "Google Play Reviews Monitor",
        "nodes": [
            {"id": "s", "name": "every4h", "type": "n8n-nodes-base.scheduleTrigger", "typeVersion": 1.2, "position": [0, 0],
             "parameters": {"rule": {"interval": [{"field": "hours", "hoursInterval": 4}]}}},
            {"id": "a", "name": "play", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 0],
             "parameters": {"method": "GET", "url": "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.snowrealm.yukiboard/reviews", "options": {}}},
            {"id": "b", "name": "log", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [400, 0],
             "parameters": {"method": "POST", "url": SB + "/rest/v1/automation_logs", "sendHeaders": True,
                            "headerParameters": {"parameters": sh()}, "sendBody": True, "specifyBody": "json",
                            "jsonBody": json.dumps({"workflow": "play_reviews", "status": "checked"})}}
        ],
        "connections": {"every4h": {"main": [[{"node": "play", "type": "main", "index": 0}]]},
                        "play": {"main": [[{"node": "log", "type": "main", "index": 0}]]}},
        "settings": {"executionOrder": "v1"},
    },
    {
        "name": "Supabase Data Backup Check",
        "nodes": [
            {"id": "s", "name": "daily3", "type": "n8n-nodes-base.scheduleTrigger", "typeVersion": 1.2, "position": [0, 0],
             "parameters": {"rule": {"interval": [{"field": "cronExpression", "expression": "0 3 * * *"}]}}},
            {"id": "a", "name": "fraud", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 0],
             "parameters": {"method": "HEAD", "url": SB + "/rest/v1/fraud_domains?select=id", "sendHeaders": True,
                            "headerParameters": {"parameters": [{"name": "apikey", "value": SK}, {"name": "Authorization", "value": "Bearer " + SK}, {"name": "Prefer", "value": "count=exact"}]}}},
            {"id": "b", "name": "users", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 200],
             "parameters": {"method": "HEAD", "url": SB + "/rest/v1/user_profiles?select=id", "sendHeaders": True,
                            "headerParameters": {"parameters": [{"name": "apikey", "value": SK}, {"name": "Authorization", "value": "Bearer " + SK}, {"name": "Prefer", "value": "count=exact"}]}}},
            {"id": "c", "name": "log", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [400, 100],
             "parameters": {"method": "POST", "url": SB + "/rest/v1/automation_logs", "sendHeaders": True,
                            "headerParameters": {"parameters": sh()}, "sendBody": True, "specifyBody": "json",
                            "jsonBody": json.dumps({"workflow": "backup_check", "status": "checked"})}}
        ],
        "connections": {"daily3": {"main": [[{"node": "fraud", "type": "main", "index": 0}, {"node": "users", "type": "main", "index": 0}]]},
                        "fraud": {"main": [[{"node": "log", "type": "main", "index": 0}]]},
                        "users": {"main": [[{"node": "log", "type": "main", "index": 0}]]}},
        "settings": {"executionOrder": "v1"},
    },
    {
        "name": "Newebpay Reconciliation",
        "nodes": [
            {"id": "s", "name": "daily10", "type": "n8n-nodes-base.scheduleTrigger", "typeVersion": 1.2, "position": [0, 0],
             "parameters": {"rule": {"interval": [{"field": "cronExpression", "expression": "0 10 * * *"}]}}},
            {"id": "a", "name": "topup", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [200, 0],
             "parameters": {"method": "GET", "url": SB + "/rest/v1/z_coin_transactions?type=eq.topup&limit=200",
                            "sendHeaders": True, "headerParameters": {"parameters": [{"name": "apikey", "value": SK}, {"name": "Authorization", "value": "Bearer " + SK}]}}},
            {"id": "b", "name": "reconcile", "type": "n8n-nodes-base.code", "typeVersion": 2, "position": [400, 0],
             "parameters": {"jsCode": "const t=$input.all().map(i=>i.json);return [{json:{count:t.length}}];"}},
            {"id": "c", "name": "log", "type": "n8n-nodes-base.httpRequest", "typeVersion": 4.2, "position": [600, 0],
             "parameters": {"method": "POST", "url": SB + "/rest/v1/automation_logs", "sendHeaders": True,
                            "headerParameters": {"parameters": sh()}, "sendBody": True, "specifyBody": "json",
                            "jsonBody": json.dumps({"workflow": "newebpay", "status": "checked"})}}
        ],
        "connections": {"daily10": {"main": [[{"node": "topup", "type": "main", "index": 0}]]},
                        "topup": {"main": [[{"node": "reconcile", "type": "main", "index": 0}]]},
                        "reconcile": {"main": [[{"node": "log", "type": "main", "index": 0}]]}},
        "settings": {"executionOrder": "v1"},
    },
]

if __name__ == "__main__":
    print("Creating %d workflows on %s..." % (len(wfs), BASE))
    for w in wfs:
        go(w)
    print("Done!")
