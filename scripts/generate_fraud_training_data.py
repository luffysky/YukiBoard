#!/usr/bin/env python3
"""
FraudAI Training Data Generator
================================
Uses Claude API (anthropic SDK) to batch-generate synthetic training conversations
for the YukiBoard fraud detector model.

8 fraud types + safe conversations → JSONL output.

Usage:
    python scripts/generate_fraud_training_data.py \
        --output data/fraud_training.jsonl \
        --api-key $ANTHROPIC_API_KEY

    # Merge with real Supabase export:
    python scripts/generate_fraud_training_data.py \
        --output data/fraud_training.jsonl \
        --api-key $ANTHROPIC_API_KEY \
        --merge-real data/supabase_reports.csv

Requirements:
    pip install anthropic tqdm
"""

import argparse
import csv
import json
import os
import sys
import time
from pathlib import Path
from typing import Optional

try:
    import anthropic
except ImportError:
    print("ERROR: anthropic SDK not installed. Run: pip install anthropic")
    sys.exit(1)

try:
    from tqdm import tqdm
except ImportError:
    # Fallback: no progress bar
    def tqdm(iterable, **kwargs):
        total = kwargs.get("total", None)
        desc = kwargs.get("desc", "")
        for i, item in enumerate(iterable):
            if total:
                print(f"\r{desc} {i+1}/{total}", end="", flush=True)
            yield item
        print()

# ─── Fraud Types ───────────────────────────────────────────────────────────────

FRAUD_TYPES = {
    "investment":    "投資詐騙",
    "romance":       "感情詐騙",
    "impersonation": "假冒官方",
    "shopping":      "購物詐騙",
    "job":           "求職詐騙",
    "loan":          "貸款詐騙",
    "lottery":       "中獎詐騙",
    "phishing":      "釣魚連結",
}

FRAUD_PROMPTS = {
    "investment": """生成一段台灣常見的投資詐騙對話（繁體中文）。
場景：詐騙者透過 LINE 或社群媒體聯繫受害者，推銷假投資平台。
包含：保證獲利話術、要求入金、假投資群組、老師帶單等元素。
對話至少 6 輪，雙方各說 3 句以上。格式為「角色：對話內容」，每輪一行。""",

    "romance": """生成一段台灣常見的感情詐騙對話（繁體中文）。
場景：詐騙者在交友軟體認識受害者，建立感情後以各種理由要求匯款。
包含：甜言蜜語、突發困難（住院/被困海外/急用錢）、要求匯款或買禮品卡。
對話至少 6 輪。格式為「角色：對話內容」。""",

    "impersonation": """生成一段台灣常見的假冒官方詐騙對話（繁體中文）。
場景：詐騙者冒充警察/檢察官/銀行客服，聲稱受害者涉案或帳戶異常。
包含：恐嚇話術、要求轉帳到「安全帳戶」、不能告訴別人、偽造文書號碼。
對話至少 6 輪。格式為「角色：對話內容」。""",

    "shopping": """生成一段台灣常見的購物詐騙對話（繁體中文）。
場景：詐騙者在社群/拍賣平台以超低價商品吸引買家，要求私下交易。
包含：超低價吸引、限時特價壓力、要求先付訂金、匯款後失聯。
對話至少 6 輪。格式為「角色：對話內容」。""",

    "job": """生成一段台灣常見的求職詐騙對話（繁體中文）。
場景：詐騙者以高薪兼職/在家工作為誘餌，實際上要求刷單、收簿或繳保證金。
包含：不切實際的高薪承諾、簡單輕鬆的工作描述、要求提供帳戶或繳費。
對話至少 6 輪。格式為「角色：對話內容」。""",

    "loan": """生成一段台灣常見的貸款詐騙對話（繁體中文）。
場景：詐騙者以低利率貸款、免擔保審核為誘餌，實際上要求先繳手續費。
包含：保證過件、先繳手續費/前置利息、信用瑕疵也可辦、假冒銀行。
對話至少 6 輪。格式為「角色：對話內容」。""",

    "lottery": """生成一段台灣常見的中獎詐騙對話（繁體中文）。
場景：詐騙者通知受害者中獎（抽獎/彩券/活動），要求先繳稅金或手續費才能領獎。
包含：恭喜中獎、限時領取壓力、要求繳稅金/手續費/保證金。
對話至少 6 輪。格式為「角色：對話內容」。""",

    "phishing": """生成一段台灣常見的釣魚連結詐騙對話（繁體中文）。
場景：詐騙者透過簡訊或訊息發送假連結，誘騙受害者點擊並輸入個人資料。
包含：偽裝成銀行/電商/政府通知、可疑短網址、要求點擊驗證/更新資料。
對話至少 4 輪。格式為「角色：對話內容」。""",
}

SAFE_PROMPT = """生成一段台灣日常正常對話（繁體中文）。
場景從以下隨機選擇一種：
1. 朋友約吃飯聊天
2. 同事討論工作專案
3. 家人關心近況
4. 情侶日常閒聊
5. 討論旅遊計畫
6. 聊最近看的電影/劇
7. 討論運動/健身
8. 分享烹飪食譜
9. 討論寵物趣事
10. 聊購物心得（正常購物，非詐騙）

對話自然流暢，至少 4 輪，不包含任何詐騙元素。
格式為「角色：對話內容」，每輪一行。"""


# ─── Generation ────────────────────────────────────────────────────────────────

def create_client(api_key: str) -> anthropic.Anthropic:
    return anthropic.Anthropic(api_key=api_key)


def generate_conversation(
    client: anthropic.Anthropic,
    prompt: str,
    variation_hint: str = "",
    max_retries: int = 3,
) -> Optional[str]:
    """Generate a single conversation using Claude API."""
    full_prompt = prompt
    if variation_hint:
        full_prompt += f"\n\n變化提示：{variation_hint}"
    full_prompt += "\n\n直接輸出對話內容，不要加任何前言或說明。"

    for attempt in range(max_retries):
        try:
            response = client.messages.create(
                model="claude-sonnet-4-20250514",
                max_tokens=1024,
                messages=[{"role": "user", "content": full_prompt}],
            )
            text = response.content[0].text.strip()
            if len(text) > 20:
                return text
        except anthropic.RateLimitError:
            wait = 2 ** (attempt + 1)
            print(f"\n  Rate limited, waiting {wait}s...")
            time.sleep(wait)
        except Exception as e:
            print(f"\n  Error: {e}")
            time.sleep(1)

    return None


VARIATION_HINTS = [
    "受害者是年輕上班族",
    "受害者是退休長輩",
    "受害者是大學生",
    "受害者是家庭主婦",
    "對話發生在深夜",
    "詐騙者語氣非常急切",
    "詐騙者裝得很親切",
    "受害者一開始有些懷疑",
    "受害者完全沒有防備",
    "詐騙者使用很多專業術語",
    "對話中穿插語音訊息提及",
    "詐騙者提供假截圖或假證據",
    "受害者曾被朋友提醒但仍繼續",
    "詐騙者用限時優惠施壓",
    "對話涉及多個詐騙者（團體作案）",
    "受害者是小商家老闆",
    "受害者是新住民",
    "詐騙者假裝是朋友介紹的",
    "對話場景在 Instagram",
    "對話場景在 Facebook 社團",
]


def generate_fraud_data(
    client: anthropic.Anthropic,
    fraud_type: str,
    count: int,
) -> list[dict]:
    """Generate N fraud conversations of a given type."""
    label_zh = FRAUD_TYPES[fraud_type]
    prompt = FRAUD_PROMPTS[fraud_type]
    results = []

    desc = f"  {label_zh}"
    for i in tqdm(range(count), total=count, desc=desc):
        hint = VARIATION_HINTS[i % len(VARIATION_HINTS)]
        text = generate_conversation(client, prompt, variation_hint=hint)
        if text:
            results.append({
                "text": text,
                "label": fraud_type,
                "label_zh": label_zh,
                "source": "synthetic",
            })
        # Small delay to avoid rate limits
        if (i + 1) % 10 == 0:
            time.sleep(0.5)

    return results


def generate_safe_data(client: anthropic.Anthropic, count: int) -> list[dict]:
    """Generate N safe (non-fraud) conversations."""
    results = []

    for i in tqdm(range(count), total=count, desc="  安全對話"):
        hint = VARIATION_HINTS[i % len(VARIATION_HINTS)]
        text = generate_conversation(client, SAFE_PROMPT, variation_hint=hint)
        if text:
            results.append({
                "text": text,
                "label": "safe",
                "label_zh": "安全",
                "source": "synthetic",
            })
        if (i + 1) % 10 == 0:
            time.sleep(0.5)

    return results


# ─── Merge Real Data ───────────────────────────────────────────────────────────

def merge_supabase_export(csv_path: str) -> list[dict]:
    """
    Merge real fraud report data from a Supabase CSV export.

    Expected CSV columns:
      - text: conversation text
      - fraud_type: one of the 8 types or 'safe'
      - (optional) label_zh: Chinese label

    Returns list of dicts in the same format as synthetic data.
    """
    results = []
    type_map = {v: k for k, v in FRAUD_TYPES.items()}
    # Also map English keys directly
    type_map.update({k: k for k in FRAUD_TYPES.keys()})
    type_map["safe"] = "safe"
    type_map["安全"] = "safe"

    try:
        with open(csv_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                text = row.get("text", "").strip()
                raw_type = row.get("fraud_type", "").strip()

                if not text or len(text) < 10:
                    continue

                label = type_map.get(raw_type, None)
                if label is None:
                    print(f"  Warning: unknown fraud_type '{raw_type}', skipping")
                    continue

                label_zh = FRAUD_TYPES.get(label, "安全") if label != "safe" else "安全"
                results.append({
                    "text": text,
                    "label": label,
                    "label_zh": label_zh,
                    "source": "real_report",
                })

        print(f"  Merged {len(results)} real report records from {csv_path}")
    except FileNotFoundError:
        print(f"  ERROR: CSV file not found: {csv_path}")
    except Exception as e:
        print(f"  ERROR reading CSV: {e}")

    return results


# ─── Output ────────────────────────────────────────────────────────────────────

def write_jsonl(data: list[dict], output_path: str):
    """Write data to JSONL file."""
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)

    with open(output_path, "w", encoding="utf-8") as f:
        for item in data:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"\nWrote {len(data)} records to {output_path}")

    # Print stats
    from collections import Counter
    label_counts = Counter(item["label"] for item in data)
    source_counts = Counter(item["source"] for item in data)

    print("\nLabel distribution:")
    for label, count in sorted(label_counts.items()):
        zh = FRAUD_TYPES.get(label, "安全")
        print(f"  {label:20s} ({zh}): {count}")

    print("\nSource distribution:")
    for source, count in sorted(source_counts.items()):
        print(f"  {source}: {count}")


# ─── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Generate fraud detection training data using Claude API"
    )
    parser.add_argument(
        "--output", "-o",
        default="data/fraud_training.jsonl",
        help="Output JSONL file path (default: data/fraud_training.jsonl)",
    )
    parser.add_argument(
        "--api-key",
        default=os.environ.get("ANTHROPIC_API_KEY", ""),
        help="Anthropic API key (default: $ANTHROPIC_API_KEY env var)",
    )
    parser.add_argument(
        "--fraud-per-type",
        type=int,
        default=200,
        help="Number of fraud examples per type (default: 200)",
    )
    parser.add_argument(
        "--safe-count",
        type=int,
        default=1000,
        help="Number of safe conversation examples (default: 1000)",
    )
    parser.add_argument(
        "--merge-real",
        default=None,
        help="Path to Supabase export CSV to merge real report data",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print config and exit without generating",
    )

    args = parser.parse_args()

    if not args.api_key:
        print("ERROR: No API key provided.")
        print("  Set ANTHROPIC_API_KEY env var or use --api-key flag.")
        sys.exit(1)

    total_fraud = args.fraud_per_type * len(FRAUD_TYPES)
    total = total_fraud + args.safe_count

    print("=" * 60)
    print("FraudAI Training Data Generator")
    print("=" * 60)
    print(f"  Fraud types:      {len(FRAUD_TYPES)}")
    print(f"  Per type:         {args.fraud_per_type}")
    print(f"  Total fraud:      {total_fraud}")
    print(f"  Safe examples:    {args.safe_count}")
    print(f"  Total synthetic:  {total}")
    print(f"  Output:           {args.output}")
    if args.merge_real:
        print(f"  Merge real data:  {args.merge_real}")
    print()

    if args.dry_run:
        print("Dry run — exiting.")
        return

    client = create_client(args.api_key)
    all_data = []

    # Generate fraud conversations
    print("Generating fraud conversations...")
    for fraud_type in FRAUD_TYPES:
        data = generate_fraud_data(client, fraud_type, args.fraud_per_type)
        all_data.extend(data)
        print(f"    → {len(data)}/{args.fraud_per_type} generated")

    # Generate safe conversations
    print("\nGenerating safe conversations...")
    safe_data = generate_safe_data(client, args.safe_count)
    all_data.extend(safe_data)
    print(f"    → {len(safe_data)}/{args.safe_count} generated")

    # Merge real data if provided
    if args.merge_real:
        print("\nMerging real report data...")
        real_data = merge_supabase_export(args.merge_real)
        all_data.extend(real_data)

    # Write output
    write_jsonl(all_data, args.output)

    print("\nDone!")


if __name__ == "__main__":
    main()
