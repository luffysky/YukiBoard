#!/usr/bin/env python3
"""
合併 RIME luna-pinyin 詞庫到 YukiBoard。

將拼音轉換為注音後合併到 bopomofo_dict.txt 和 bopomofo_phrases.txt。
過濾重複詞條。
"""

import os
import sys
from collections import defaultdict

# 拼音→注音對照表（含聲調）
PINYIN_TO_BOPOMOFO = {
    # 聲母
    'b': 'ㄅ', 'p': 'ㄆ', 'm': 'ㄇ', 'f': 'ㄈ',
    'd': 'ㄉ', 't': 'ㄊ', 'n': 'ㄋ', 'l': 'ㄌ',
    'g': 'ㄍ', 'k': 'ㄎ', 'h': 'ㄏ',
    'j': 'ㄐ', 'q': 'ㄑ', 'x': 'ㄒ',
    'zh': 'ㄓ', 'ch': 'ㄔ', 'sh': 'ㄕ', 'r': 'ㄖ',
    'z': 'ㄗ', 'c': 'ㄘ', 's': 'ㄙ',
    # 韻母
    'a': 'ㄚ', 'o': 'ㄛ', 'e': 'ㄜ', 'i': 'ㄧ', 'u': 'ㄨ', 'v': 'ㄩ',
    'ai': 'ㄞ', 'ei': 'ㄟ', 'ao': 'ㄠ', 'ou': 'ㄡ',
    'an': 'ㄢ', 'en': 'ㄣ', 'ang': 'ㄤ', 'eng': 'ㄥ',
    'er': 'ㄦ',
    'ia': 'ㄧㄚ', 'ie': 'ㄧㄝ', 'iao': 'ㄧㄠ', 'iu': 'ㄧㄡ', 'iou': 'ㄧㄡ',
    'ian': 'ㄧㄢ', 'in': 'ㄧㄣ', 'iang': 'ㄧㄤ', 'ing': 'ㄧㄥ',
    'ua': 'ㄨㄚ', 'uo': 'ㄨㄛ', 'uai': 'ㄨㄞ', 'ui': 'ㄨㄟ', 'uei': 'ㄨㄟ',
    'uan': 'ㄨㄢ', 'un': 'ㄨㄣ', 'uen': 'ㄨㄣ', 'uang': 'ㄨㄤ', 'ong': 'ㄨㄥ',
    'ue': 'ㄩㄝ', 've': 'ㄩㄝ', 'van': 'ㄩㄢ', 'vn': 'ㄩㄣ',
    'iong': 'ㄩㄥ',
}

# 完整音節映射表（處理特殊情況）
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
            # j/q/x + u → ü
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
    """將單個拼音音節轉換為注音"""
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

    return ''  # 無法轉換


def load_existing_dict(filepath):
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


INITIALS_SET = set('ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ')


def get_initial(bopomofo: str) -> str:
    if not bopomofo:
        return ''
    return bopomofo[0] if bopomofo[0] in INITIALS_SET else bopomofo[0]


def main():
    build_syllable_map()

    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dict_path = os.path.join(base_dir, 'app', 'src', 'main', 'res', 'raw', 'bopomofo_dict.txt')
    phrases_path = os.path.join(base_dir, 'app', 'src', 'main', 'res', 'raw', 'bopomofo_phrases.txt')

    tmp_dir = os.environ.get('TEMP', os.environ.get('TMP', '/tmp'))
    rime_path = os.path.join(tmp_dir, 'rime_luna.yaml')

    if not os.path.exists(rime_path):
        print(f"ERROR: {rime_path} not found")
        sys.exit(1)

    print("=== Loading existing dicts ===")
    existing_dict = load_existing_dict(dict_path)
    existing_phrases = load_existing_phrases(phrases_path)
    print(f"  Dict: {len(existing_dict)} keys, {sum(len(v) for v in existing_dict.values())} words")
    print(f"  Phrases: {len(existing_phrases)} keys, {sum(len(v) for v in existing_phrases.values())} phrases")

    print("\n=== Parsing RIME luna-pinyin ===")
    entries = []
    skipped = 0
    converted = 0
    past_header = False
    with open(rime_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if line == '...':
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
            pinyin = parts[1].strip()
            if not word or not pinyin:
                continue

            # Convert each pinyin syllable to bopomofo
            syllables = pinyin.split()
            bopo_syllables = []
            valid = True
            for syl in syllables:
                bopo = pinyin_to_bopomofo(syl)
                if not bopo:
                    valid = False
                    break
                bopo_syllables.append(bopo)

            if not valid:
                skipped += 1
                continue

            converted += 1
            bopo_key = ''.join(bopo_syllables)
            entries.append((word, bopo_key, bopo_syllables))

    print(f"  Converted: {converted}, Skipped: {skipped}")

    # Merge into dict
    dict_added = 0
    phrase_added = 0

    for word, bopo_key, bopo_syllables in entries:
        # Add to dict
        if bopo_key not in existing_dict:
            existing_dict[bopo_key] = []
        if word not in existing_dict[bopo_key]:
            existing_dict[bopo_key].append(word)
            dict_added += 1

        # Add to phrases (multi-char words only)
        if len(bopo_syllables) >= 2:
            abbrev = ''.join(get_initial(s) for s in bopo_syllables)
            if len(abbrev) >= 2:
                if abbrev not in existing_phrases:
                    existing_phrases[abbrev] = []
                if word not in existing_phrases[abbrev]:
                    existing_phrases[abbrev].append(word)
                    phrase_added += 1

    print(f"\n=== Merge results ===")
    print(f"  New dict entries: {dict_added}")
    print(f"  New phrase entries: {phrase_added}")
    print(f"  Total dict: {len(existing_dict)} keys, {sum(len(v) for v in existing_dict.values())} words")
    print(f"  Total phrases: {len(existing_phrases)} keys, {sum(len(v) for v in existing_phrases.values())} phrases")

    # Write dict
    with open(dict_path, 'w', encoding='utf-8', newline='\n') as f:
        for key in sorted(existing_dict.keys()):
            words = existing_dict[key]
            if words:
                f.write(f"{key}\t{' '.join(words)}\n")

    # Write phrases
    with open(phrases_path, 'w', encoding='utf-8', newline='\n') as f:
        for key in sorted(existing_phrases.keys()):
            words = existing_phrases[key]
            if words:
                f.write(f"{key}\t{' '.join(words)}\n")

    print("\nDone!")


if __name__ == '__main__':
    main()
