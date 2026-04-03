#!/usr/bin/env python3
"""
YukiBoard 邀請碼產生器
用法：python generate_invite_codes.py --type standard --count 100 --month 202604
"""

import hmac
import hashlib
import argparse
import csv
from datetime import datetime

TYPE_MAP = {
    'standard': 'S',
    'kol': 'K',
    'permanent': 'P'
}

def generate_code(type_char: str, year_month: str, index: int, secret: str) -> str:
    payload = f"{type_char}{year_month}{index:04d}"
    signature = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()[:8].upper()
    return f"YUKI-{payload}-{signature}"

def main():
    parser = argparse.ArgumentParser(description='YukiBoard 邀請碼產生器')
    parser.add_argument('--type', choices=['standard', 'kol', 'permanent'], required=True)
    parser.add_argument('--count', type=int, default=10)
    parser.add_argument('--month', default=datetime.now().strftime('%Y%m'))
    parser.add_argument('--secret', default='yukiboard_dev_2026')
    args = parser.parse_args()

    type_char = TYPE_MAP[args.type]
    codes = []
    for i in range(args.count):
        code = generate_code(type_char, args.month, i, args.secret)
        codes.append({
            'code': code,
            'type': args.type,
            'month': args.month,
            'index': i
        })

    filename = f"invite_codes_{args.type}_{args.month}.csv"
    with open(filename, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['code', 'type', 'month', 'index'])
        writer.writeheader()
        writer.writerows(codes)

    print(f"✅ 產生 {len(codes)} 個 {args.type} 邀請碼")
    print(f"📄 儲存至 {filename}")
    print("\n前 5 個預覽：")
    for c in codes[:5]:
        print(f"  {c['code']}")

if __name__ == '__main__':
    main()
