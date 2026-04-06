#!/usr/bin/env python3
"""
合併 libchewing 詞庫到 YukiBoard 字典。

讀取 libchewing 的 tsi.csv + word.csv，轉換格式後合併到：
- bopomofo_dict.txt  (注音→字/詞)
- bopomofo_phrases.txt (聲母簡拼→詞組)

過濾重複，按頻率排序。
"""

import csv
import sys
import os
from collections import defaultdict

# 注音聲母（用於簡拼）
INITIALS = set('ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ')
TONES = set('ˊˇˋ˙')

def get_initial(syllable: str) -> str:
    """取注音音節的聲母（第一個字）"""
    s = syllable.strip()
    if not s:
        return ''
    # 聲調符號本身
    if s[0] in TONES:
        return s[0]
    return s[0]

def strip_tone(syllable: str) -> str:
    """去掉聲調符號"""
    return syllable.rstrip('ˊˇˋ˙ˉ')

def parse_libchewing_csv(filepath):
    """解析 libchewing CSV: phrase,frequency,bopomofo"""
    entries = []
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            parts = line.split(',', 2)
            if len(parts) < 3:
                continue
            word = parts[0].strip()
            try:
                freq = int(parts[1].strip())
            except ValueError:
                freq = 0
            bopomofo = parts[2].strip()
            if not word or not bopomofo:
                continue
            # 過濾純注音符號條目
            if len(word) == 1 and (word in INITIALS or word in TONES or
                                    '\u3105' <= word <= '\u312F'):
                continue
            entries.append((word, freq, bopomofo))
    return entries

def load_existing_dict(filepath):
    """載入現有 bopomofo_dict.txt: 注音\t字1 字2 ..."""
    data = defaultdict(list)
    if not os.path.exists(filepath):
        return data
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if not line or line.startswith('#'):
                continue
            parts = line.split('\t', 1)
            if len(parts) < 2:
                continue
            key = parts[0].strip()
            words = [w.strip() for w in parts[1].split(' ') if w.strip()]
            data[key] = words
    return data

def load_existing_phrases(filepath):
    """載入現有 bopomofo_phrases.txt: 簡拼\t詞1 詞2 ..."""
    data = defaultdict(list)
    if not os.path.exists(filepath):
        return data
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if not line or line.startswith('#'):
                continue
            parts = line.split('\t', 1)
            if len(parts) < 2:
                continue
            key = parts[0].strip()
            words = [w.strip() for w in parts[1].split(' ') if w.strip()]
            data[key] = words
    return data

def merge_dicts(existing_dict, libchewing_entries, freq_map):
    """合併 libchewing 單字/詞條目到 dict"""
    added = 0
    for word, freq, bopomofo in libchewing_entries:
        # 轉換注音格式：libchewing 用空格分音節，dict 用連寫
        syllables = bopomofo.split()
        key = ''.join(syllables)

        if key not in existing_dict:
            existing_dict[key] = []

        if word not in existing_dict[key]:
            existing_dict[key].append(word)
            added += 1

        # 記錄頻率
        freq_map[(key, word)] = max(freq_map.get((key, word), 0), freq)

    return added

def merge_phrases(existing_phrases, libchewing_entries, freq_map):
    """合併多字詞到 phrases（簡拼索引）"""
    added = 0
    for word, freq, bopomofo in libchewing_entries:
        if len(word) < 2:
            continue
        syllables = bopomofo.split()
        if len(syllables) < 2:
            continue

        # 聲母簡拼
        abbrev = ''.join(get_initial(s) for s in syllables)
        if not abbrev or len(abbrev) < 2:
            continue

        if abbrev not in existing_phrases:
            existing_phrases[abbrev] = []

        if word not in existing_phrases[abbrev]:
            existing_phrases[abbrev].append(word)
            added += 1

        freq_map[(abbrev, word)] = max(freq_map.get((abbrev, word), 0), freq)

    return added

def sort_by_freq(words, key_prefix, freq_map):
    """按頻率排序詞列表"""
    return sorted(words, key=lambda w: -freq_map.get((key_prefix, w), 0))

def main():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dict_path = os.path.join(base_dir, 'app', 'src', 'main', 'res', 'raw', 'bopomofo_dict.txt')
    phrases_path = os.path.join(base_dir, 'app', 'src', 'main', 'res', 'raw', 'bopomofo_phrases.txt')

    tmp_dir = os.environ.get('TEMP', os.environ.get('TMP', '/tmp'))
    tsi_path = os.path.join(tmp_dir, 'libchewing_tsi.csv')
    word_path = os.path.join(tmp_dir, 'libchewing_word.csv')

    if not os.path.exists(tsi_path):
        print(f"ERROR: {tsi_path} not found. Download it first.")
        sys.exit(1)

    print("=== 載入現有字典 ===")
    existing_dict = load_existing_dict(dict_path)
    existing_phrases = load_existing_phrases(phrases_path)
    print(f"  現有 dict: {len(existing_dict)} 注音鍵, {sum(len(v) for v in existing_dict.values())} 字詞")
    print(f"  現有 phrases: {len(existing_phrases)} 簡拼鍵, {sum(len(v) for v in existing_phrases.values())} 詞組")

    print("\n=== 解析 libchewing 詞庫 ===")
    tsi_entries = parse_libchewing_csv(tsi_path)
    print(f"  tsi.csv: {len(tsi_entries)} 條目")

    word_entries = []
    if os.path.exists(word_path):
        word_entries = parse_libchewing_csv(word_path)
        print(f"  word.csv: {len(word_entries)} 條目")

    all_entries = word_entries + tsi_entries

    freq_map = {}
    # 載入現有條目的頻率（假設位置就是頻率）
    for key, words in existing_dict.items():
        for i, w in enumerate(words):
            freq_map[(key, w)] = max(1000 - i * 10, 1)  # 前面的字頻率更高

    for key, words in existing_phrases.items():
        for i, w in enumerate(words):
            freq_map[(key, w)] = max(1000 - i * 10, 1)

    print("\n=== 合併字典 ===")
    dict_added = merge_dicts(existing_dict, all_entries, freq_map)
    print(f"  新增 dict 條目: {dict_added}")

    phrases_added = merge_phrases(existing_phrases, all_entries, freq_map)
    print(f"  新增 phrases 條目: {phrases_added}")

    # 排序每個鍵的詞列表（按頻率）
    for key in existing_dict:
        existing_dict[key] = sort_by_freq(existing_dict[key], key, freq_map)
    for key in existing_phrases:
        existing_phrases[key] = sort_by_freq(existing_phrases[key], key, freq_map)

    print(f"\n=== 寫入字典 ===")
    print(f"  合併後 dict: {len(existing_dict)} 注音鍵, {sum(len(v) for v in existing_dict.values())} 字詞")
    print(f"  合併後 phrases: {len(existing_phrases)} 簡拼鍵, {sum(len(v) for v in existing_phrases.values())} 詞組")

    # 寫入 dict
    with open(dict_path, 'w', encoding='utf-8', newline='\n') as f:
        for key in sorted(existing_dict.keys()):
            words = existing_dict[key]
            if words:
                f.write(f"{key}\t{' '.join(words)}\n")

    # 寫入 phrases
    with open(phrases_path, 'w', encoding='utf-8', newline='\n') as f:
        for key in sorted(existing_phrases.keys()):
            words = existing_phrases[key]
            if words:
                f.write(f"{key}\t{' '.join(words)}\n")

    print("\n=== 完成！===")

if __name__ == '__main__':
    main()
