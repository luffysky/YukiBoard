#!/usr/bin/env python3
"""
RIME 詞庫 → SQLite 轉換工具

下載 RIME bopomofo_tw.dict.yaml 和 terra_pinyin.dict.yaml，
轉換為 SQLite 資料庫供 YukiBoard 注音輸入引擎使用。

用法：
  python convert_rime_dict.py              # 從網路下載
  python convert_rime_dict.py --local      # 使用 scripts/dicts/ 本地檔案
  python convert_rime_dict.py --local-dir /path/to/dicts  # 指定本地目錄
"""

import os
import sys
import sqlite3
import argparse
import urllib.request
from collections import defaultdict

# ---------------------------------------------------------------------------
# 拼音→注音 轉換（移植自 merge_rime_pinyin.py）
# ---------------------------------------------------------------------------

SYLLABLE_MAP = {}


def build_syllable_map():
    """建立完整的拼音→注音映射表"""
    initials = {
        'b': 'ㄅ', 'p': 'ㄆ', 'm': 'ㄇ', 'f': 'ㄈ',
        'd': 'ㄉ', 't': 'ㄊ', 'n': 'ㄋ', 'l': 'ㄌ',
        'g': 'ㄍ', 'k': 'ㄎ', 'h': 'ㄏ',
        'j': 'ㄐ', 'q': 'ㄑ', 'x': 'ㄒ',
        'zh': 'ㄓ', 'ch': 'ㄔ', 'sh': 'ㄕ', 'r': 'ㄖ',
        'z': 'ㄗ', 'c': 'ㄘ', 's': 'ㄙ',
    }

    finals = {
        'a': 'ㄚ', 'o': 'ㄛ', 'e': 'ㄜ', 'ai': 'ㄞ', 'ei': 'ㄟ',
        'ao': 'ㄠ', 'ou': 'ㄡ', 'an': 'ㄢ', 'en': 'ㄣ',
        'ang': 'ㄤ', 'eng': 'ㄥ', 'er': 'ㄦ',
        'i': 'ㄧ', 'ia': 'ㄧㄚ', 'ie': 'ㄧㄝ', 'iao': 'ㄧㄠ',
        'iu': 'ㄧㄡ', 'ian': 'ㄧㄢ', 'in': 'ㄧㄣ',
        'iang': 'ㄧㄤ', 'ing': 'ㄧㄥ',
        'u': 'ㄨ', 'ua': 'ㄨㄚ', 'uo': 'ㄨㄛ', 'uai': 'ㄨㄞ',
        'ui': 'ㄨㄟ', 'uan': 'ㄨㄢ', 'un': 'ㄨㄣ',
        'uang': 'ㄨㄤ', 'ong': 'ㄨㄥ',
        'v': 'ㄩ', 'ue': 'ㄩㄝ', 've': 'ㄩㄝ',
        'van': 'ㄩㄢ', 'vn': 'ㄩㄣ', 'iong': 'ㄩㄥ',
    }

    # 特殊完整音節
    specials = {
        'zhi': 'ㄓ', 'chi': 'ㄔ', 'shi': 'ㄕ', 'ri': 'ㄖ',
        'zi': 'ㄗ', 'ci': 'ㄘ', 'si': 'ㄙ',
        'yi': 'ㄧ', 'ya': 'ㄧㄚ', 'ye': 'ㄧㄝ', 'yao': 'ㄧㄠ',
        'you': 'ㄧㄡ', 'yan': 'ㄧㄢ', 'yin': 'ㄧㄣ',
        'yang': 'ㄧㄤ', 'ying': 'ㄧㄥ',
        'wu': 'ㄨ', 'wa': 'ㄨㄚ', 'wo': 'ㄨㄛ', 'wai': 'ㄨㄞ',
        'wei': 'ㄨㄟ', 'wan': 'ㄨㄢ', 'wen': 'ㄨㄣ',
        'wang': 'ㄨㄤ', 'weng': 'ㄨㄥ',
        'yu': 'ㄩ', 'yue': 'ㄩㄝ', 'yuan': 'ㄩㄢ',
        'yun': 'ㄩㄣ', 'yong': 'ㄩㄥ',
        'a': 'ㄚ', 'o': 'ㄛ', 'e': 'ㄜ', 'ai': 'ㄞ', 'ei': 'ㄟ',
        'ao': 'ㄠ', 'ou': 'ㄡ', 'an': 'ㄢ', 'en': 'ㄣ',
        'ang': 'ㄤ', 'eng': 'ㄥ', 'er': 'ㄦ',
    }
    SYLLABLE_MAP.update(specials)

    # 聲母 + 韻母 組合
    for ini, ini_bopo in initials.items():
        for fin, fin_bopo in finals.items():
            if ini in ('j', 'q', 'x') and fin == 'u':
                SYLLABLE_MAP[ini + fin] = ini_bopo + 'ㄩ'
            elif ini in ('j', 'q', 'x') and fin.startswith('u') and fin != 'u':
                actual_fin = 'v' + fin[1:] if fin[1:] else 'v'
                if actual_fin in finals:
                    SYLLABLE_MAP[ini + fin] = ini_bopo + finals[actual_fin]
                else:
                    SYLLABLE_MAP[ini + fin] = ini_bopo + fin_bopo
            else:
                SYLLABLE_MAP[ini + fin] = ini_bopo + fin_bopo

    # j/q/x + ü 系列
    for ini in ('j', 'q', 'x'):
        ini_b = initials[ini]
        SYLLABLE_MAP[ini + 'u'] = ini_b + 'ㄩ'
        SYLLABLE_MAP[ini + 'uan'] = ini_b + 'ㄩㄢ'
        SYLLABLE_MAP[ini + 'un'] = ini_b + 'ㄩㄣ'
        SYLLABLE_MAP[ini + 'ue'] = ini_b + 'ㄩㄝ'

    # n/l + ü
    for ini in ('n', 'l'):
        ini_b = initials[ini]
        SYLLABLE_MAP[ini + 'v'] = ini_b + 'ㄩ'
        SYLLABLE_MAP[ini + 've'] = ini_b + 'ㄩㄝ'
        SYLLABLE_MAP[ini + 'u'] = ini_b + 'ㄨ'
        SYLLABLE_MAP[ini + 'ue'] = ini_b + 'ㄩㄝ'
        SYLLABLE_MAP[ini + 'nv'] = ini_b + 'ㄩ'


def pinyin_to_bopomofo(pinyin: str) -> str:
    """將單個拼音音節轉換為注音（含聲調）"""
    py = pinyin.strip().lower()
    if not py:
        return ''

    # 去掉數字聲調
    tone = ''
    if py[-1] in '12345':
        tone_num = py[-1]
        py = py[:-1]
        tone_map = {'1': '', '2': 'ˊ', '3': 'ˇ', '4': 'ˋ', '5': '˙'}
        tone = tone_map.get(tone_num, '')

    # 查完整映射
    if py in SYLLABLE_MAP:
        return SYLLABLE_MAP[py] + tone

    # 嘗試拆分聲母+韻母
    for ini_len in (2, 1):
        ini = py[:ini_len]
        if ini in ('zh', 'ch', 'sh', 'b', 'p', 'm', 'f', 'd', 't', 'n', 'l',
                    'g', 'k', 'h', 'j', 'q', 'x', 'r', 'z', 'c', 's'):
            fin = py[ini_len:]
            if fin and ini + fin in SYLLABLE_MAP:
                return SYLLABLE_MAP[ini + fin] + tone

    return ''


# ---------------------------------------------------------------------------
# 注音聲調處理
# ---------------------------------------------------------------------------

TONE_MARKS = set('ˊˇˋ˙')


def strip_tones(bopomofo: str) -> str:
    """移除注音聲調符號，保留聲母韻母"""
    return ''.join(ch for ch in bopomofo if ch not in TONE_MARKS)


# ---------------------------------------------------------------------------
# RIME YAML 解析
# ---------------------------------------------------------------------------

def parse_rime_yaml(filepath, is_bopomofo=False):
    """
    解析 RIME dict YAML 檔案。

    回傳 list of (word, bopomofo_with_tones, frequency)。
    - is_bopomofo=True: 讀音欄位已是注音
    - is_bopomofo=False: 讀音欄位是拼音，需要轉換
    """
    entries = []
    skipped = 0
    past_header = False

    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if line.strip() == '...':
                past_header = True
                continue
            if not past_header:
                continue
            if not line or line.startswith('#'):
                continue

            parts = line.split('\t')
            if len(parts) < 2:
                continue

            word = parts[0].strip()
            reading = parts[1].strip()
            freq = 0
            if len(parts) >= 3:
                try:
                    # RIME frequency can be integer or percentage like "10%"
                    freq_str = parts[2].strip().rstrip('%')
                    freq = int(freq_str)
                except ValueError:
                    try:
                        freq = int(float(freq_str))
                    except ValueError:
                        freq = 0

            if not word or not reading:
                continue

            if is_bopomofo:
                # reading is already bopomofo, space-separated per syllable
                bopo_full = reading.replace(' ', '')
                if bopo_full:
                    entries.append((word, bopo_full, freq))
                else:
                    skipped += 1
            else:
                # reading is pinyin, convert each syllable
                syllables = reading.split()
                bopo_parts = []
                valid = True
                for syl in syllables:
                    bopo = pinyin_to_bopomofo(syl)
                    if not bopo:
                        valid = False
                        break
                    bopo_parts.append(bopo)
                if valid and bopo_parts:
                    entries.append((word, ''.join(bopo_parts), freq))
                else:
                    skipped += 1

    return entries, skipped


# ---------------------------------------------------------------------------
# 下載
# ---------------------------------------------------------------------------

URLS = {
    'bopomofo_tw': 'https://raw.githubusercontent.com/rime/rime-bopomofo/master/bopomofo_tw.dict.yaml',
    'terra_pinyin': 'https://raw.githubusercontent.com/rime/rime-terra-pinyin/master/terra_pinyin.dict.yaml',
}


def download_file(url, dest):
    """下載檔案到指定路徑"""
    print(f"  Downloading {url}")
    print(f"    -> {dest}")
    try:
        urllib.request.urlretrieve(url, dest)
        size = os.path.getsize(dest)
        print(f"    OK ({size:,} bytes)")
        return True
    except Exception as e:
        print(f"    FAILED: {e}")
        return False


# ---------------------------------------------------------------------------
# SQLite 輸出
# ---------------------------------------------------------------------------

def create_database(db_path, entries):
    """建立 SQLite 資料庫"""
    if os.path.exists(db_path):
        os.remove(db_path)

    conn = sqlite3.connect(db_path)
    c = conn.cursor()

    c.execute('''
        CREATE TABLE dict (
            id INTEGER PRIMARY KEY,
            bopomofo TEXT NOT NULL,
            bopomofo_plain TEXT NOT NULL,
            word TEXT NOT NULL,
            frequency INTEGER DEFAULT 0
        )
    ''')

    c.executemany(
        'INSERT INTO dict (bopomofo, bopomofo_plain, word, frequency) VALUES (?, ?, ?, ?)',
        entries,
    )

    c.execute('CREATE INDEX idx_bopomofo ON dict(bopomofo)')
    c.execute('CREATE INDEX idx_bopomofo_plain ON dict(bopomofo_plain)')
    c.execute('CREATE INDEX idx_word ON dict(word)')
    c.execute('CREATE INDEX idx_prefix ON dict(bopomofo_plain COLLATE NOCASE)')

    conn.commit()
    conn.close()


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description='RIME dict -> SQLite converter for YukiBoard')
    parser.add_argument('--local', action='store_true',
                        help='Use local files in scripts/dicts/ instead of downloading')
    parser.add_argument('--local-dir', type=str, default=None,
                        help='Path to directory containing local YAML files')
    args = parser.parse_args()

    build_syllable_map()

    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dicts_dir = args.local_dir or os.path.join(base_dir, 'scripts', 'dicts')
    db_path = os.path.join(base_dir, 'app', 'src', 'main', 'assets', 'rime_bopomofo.db')

    os.makedirs(dicts_dir, exist_ok=True)
    os.makedirs(os.path.dirname(db_path), exist_ok=True)

    # Determine file paths
    bopomofo_tw_path = os.path.join(dicts_dir, 'bopomofo_tw.dict.yaml')
    terra_pinyin_path = os.path.join(dicts_dir, 'terra_pinyin.dict.yaml')

    # Download if not using local mode, or if local files are missing
    if not args.local:
        print("=== Downloading RIME dictionaries ===")
        if not download_file(URLS['bopomofo_tw'], bopomofo_tw_path):
            if not os.path.exists(bopomofo_tw_path):
                print("WARNING: bopomofo_tw.dict.yaml not available, skipping")
        if not download_file(URLS['terra_pinyin'], terra_pinyin_path):
            if not os.path.exists(terra_pinyin_path):
                print("WARNING: terra_pinyin.dict.yaml not available, skipping")
    else:
        print("=== Using local files ===")

    # Parse dictionaries
    all_entries = []  # list of (word, bopomofo, freq)

    if os.path.exists(bopomofo_tw_path):
        print(f"\n=== Parsing bopomofo_tw.dict.yaml ===")
        entries, skipped = parse_rime_yaml(bopomofo_tw_path, is_bopomofo=True)
        print(f"  Entries: {len(entries):,}, Skipped: {skipped:,}")
        all_entries.extend(entries)
    else:
        print(f"\nWARNING: {bopomofo_tw_path} not found, skipping")

    if os.path.exists(terra_pinyin_path):
        print(f"\n=== Parsing terra_pinyin.dict.yaml (pinyin -> bopomofo) ===")
        entries, skipped = parse_rime_yaml(terra_pinyin_path, is_bopomofo=False)
        print(f"  Entries: {len(entries):,}, Skipped: {skipped:,}")
        all_entries.extend(entries)
    else:
        print(f"\nWARNING: {terra_pinyin_path} not found, skipping")

    if not all_entries:
        print("\nERROR: No entries parsed. Ensure dict files are available.")
        sys.exit(1)

    # Deduplicate: keep entry with highest frequency for each (word, bopomofo) pair
    print(f"\n=== Deduplicating ===")
    print(f"  Before: {len(all_entries):,} entries")

    dedup = {}  # (word, bopomofo) -> freq
    for word, bopomofo, freq in all_entries:
        key = (word, bopomofo)
        if key not in dedup or freq > dedup[key]:
            dedup[key] = freq

    print(f"  After:  {len(dedup):,} unique entries")

    # Build final rows: (bopomofo, bopomofo_plain, word, frequency)
    rows = []
    for (word, bopomofo), freq in dedup.items():
        bopomofo_plain = strip_tones(bopomofo)
        rows.append((bopomofo, bopomofo_plain, word, freq))

    # Sort by frequency descending for nicer ordering
    rows.sort(key=lambda r: (-r[3], r[0]))

    # Create database
    print(f"\n=== Creating SQLite database ===")
    print(f"  Output: {db_path}")
    create_database(db_path, rows)
    db_size = os.path.getsize(db_path)
    print(f"  Database size: {db_size:,} bytes ({db_size / 1024 / 1024:.1f} MB)")

    # Stats
    print(f"\n=== Stats ===")
    single_char = sum(1 for (w, b), f in dedup.items() if len(w) == 1)
    multi_char = sum(1 for (w, b), f in dedup.items() if len(w) > 1)
    with_freq = sum(1 for f in dedup.values() if f > 0)
    print(f"  Total entries:  {len(dedup):,}")
    print(f"  Single-char:    {single_char:,}")
    print(f"  Multi-char:     {multi_char:,}")
    print(f"  With frequency: {with_freq:,}")
    print(f"\nDone!")


if __name__ == '__main__':
    main()
