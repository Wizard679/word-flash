#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
用开放许可数据重建词库：
  词条 / 音标 / 中文释义  ← ECDICT (MIT, https://github.com/skywind3000/ECDICT)
  例句 / 中文翻译        ← Tatoeba (CC BY 2.0 FR, https://tatoeba.org)
  专升本词表              ← 公开大纲词表（仅保留单词，释义重新取自 ECDICT）

输出与 App 现有资源格式一致：[word, phonetic, translation, ex1, cn1, ex2, cn2]
"""
import argparse, bz2, csv, json, os, re, sys, collections

try:
    from opencc import OpenCC
    _T2S = OpenCC("t2s")
except Exception:
    _T2S = None

ALLOWED = set("abcdefghijklmnopqrstuvwxyzˈˌːəɪʊæʌŋθðʃʒɜɒɔɑɐʉɨɯɛœøɶɹɾʔɚɝ()-.")
VOWELS = set("aeiouəɪʊæʌɜɒɔɑɛɐʉɨɯœøɶɚɝ")

# ARPAbet → IPA（美音），用于 CMUdict
ARP = {
    "AA": "ɑ", "AE": "æ", "AH": "ʌ", "AO": "ɔ", "AW": "aʊ", "AY": "aɪ",
    "EH": "ɛ", "ER": "ɝ", "EY": "eɪ", "IH": "ɪ", "IY": "i", "OW": "oʊ",
    "OY": "ɔɪ", "UH": "ʊ", "UW": "u",
    "B": "b", "CH": "tʃ", "D": "d", "DH": "ð", "F": "f", "G": "ɡ",
    "HH": "h", "JH": "dʒ", "K": "k", "L": "l", "M": "m", "N": "n",
    "NG": "ŋ", "P": "p", "R": "r", "S": "s", "SH": "ʃ", "T": "t",
    "TH": "θ", "V": "v", "W": "w", "Y": "j", "Z": "z", "ZH": "ʒ",
}
ARP_VOWELS = {"AA", "AE", "AH", "AO", "AW", "AY", "EH", "ER", "EY", "IH", "IY", "OW", "OY", "UH", "UW"}


VOWEL_SYMBOLS = {"ɑ", "æ", "ʌ", "ɔ", "aʊ", "aɪ", "ɛ", "ɝ", "ɚ", "eɪ", "ɪ", "i", "oʊ", "ɔɪ", "ʊ", "u", "ə"}

# 英语音节首允许的辅音组合（用于把重音符号放在正确的音节前）
ONSETS = {
    "bl", "br", "cl", "cr", "dr", "fl", "fr", "gl", "gr", "pl", "pr", "kl", "kr",
    "sp", "st", "sk", "sm", "sn", "sl", "sw", "tw", "dw", "kw", "gw", "θw",
    "spl", "spr", "str", "scr", "skr", "shr", "thr", "chr", "phr",
    "pj", "bj", "fj", "mj", "vj", "tj", "dj", "nj", "lj", "kj", "gj", "sj", "zj", "hj", "rj",
}


def _is_vowel(sym):
    return sym in VOWEL_SYMBOLS


def arpabet_to_ipa(phones):
    """CMUdict 的 ARPAbet → 美式 IPA，并在重读音节前加 ˈ"""
    units = []
    for ph in phones:
        base = ph.rstrip("012")
        stress = ph[len(base):]
        sym = ARP.get(base)
        if sym is None:
            return ""
        if base == "AH" and stress == "0":
            sym = "ə"
        if base == "ER" and stress == "0":
            sym = "ɚ"
        units.append((sym, stress))

    out = [u[0] for u in units]
    primary = next((i for i, (sym, st) in enumerate(units) if st == "1" and _is_vowel(sym)), None)
    if primary is not None:
        prev = next((i for i in range(primary - 1, -1, -1) if _is_vowel(units[i][0])), -1)
        consonants = [u[0] for u in units[prev + 1:primary]]
        onset = len(consonants)
        if consonants:
            onset = len(consonants) - 1
            for k in (3, 2):
                if k <= len(consonants) and "".join(consonants[-k:]) in ONSETS:
                    onset = len(consonants) - k
                    break
            onset = max(0, onset)
        out.insert(prev + 1 + onset, "ˈ")
    s = "".join(out).replace("ɡ", "g").replace("ˈˈ", "ˈ")
    return s.strip("ˈ") if s.startswith("ˈˈ") else s


def load_cmudict(path):
    d = {}
    if not path or not os.path.exists(path):
        return d
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith(";;;"):
                continue
            parts = line.split()
            if len(parts) < 2:
                continue
            w = parts[0].lower()
            if w.endswith(")") and "(" in w:
                continue  # 备选读音
            d[w] = parts[1:]
    return d
POS_RE = re.compile(r"^(a|ad|adj|adv|n|v|vt|vi|prep|conj|pron|num|art|int)\.\s*")


def to_simplified(text: str):
    if not text:
        return text
    if _T2S is None:
        return text
    try:
        return _T2S.convert(text)
    except Exception:
        return text


def norm_phon(raw: str):
    p = (raw or "").strip().strip("/[]")
    if not p:
        return "", "empty"
    p = re.sub(r"\([^)]*\)", "", p)
    p = (p.replace("ә", "ə").replace("є", "e").replace("^", "g")
         .replace("ɡ", "g").replace("\\", "ɜ").replace(":", "ː")
         .replace("'", "ˈ").replace("’", "ˈ").replace("`", "ˈ").replace(",", "ˌ"))
    p = p.strip()
    p = p.split(";")[0]
    p = re.split(r"\s*\.\s+", p)[0]
    p = p.split(" ")[0].strip(" ,;.-")
    bad = [c for c in p if c.lower() not in ALLOWED]
    if bad:
        return "", "bad:" + "".join(sorted(set(bad)))
    if len(p) > 32:
        return "", "long"
    if not any(c in VOWELS for c in p):
        return "", "novowel"
    return p, ""


def norm_translation(t: str):
    lines = [l.strip() for l in (t or "").split("\n") if l.strip()]
    keep = []
    seen_items = set()
    for l in lines:
        # 去掉 [医]/[计] 等领域标签，保留释义本身
        l = re.sub(r"\[[^\]]*\]", "", l).strip()
        if not l:
            continue
        m = POS_RE.match(l)
        pos = ""
        if m:
            pos = "adj. " if m.group(1) == "a" else ("adv. " if m.group(1) == "ad" else m.group(1) + ". ")
            l = l[m.end():]
        items = [x.strip() for x in re.split(r"[，,；;]", l) if x.strip()]
        fresh = [x for x in items if x not in seen_items]
        if not fresh:
            continue  # 与已有义项重复（如 [医] 失神）
        for x in fresh:
            seen_items.add(x)
        keep.append(pos + "，".join(fresh))
        if len(keep) >= 2 or len(seen_items) >= 6:
            break
    s = "；".join(keep)
    if len(s) > 72:
        s = s[:70].rstrip("；，") + "…"
    return s


def clean_word(w: str):
    w = (w or "").strip()
    if not w or " " in w:
        return None
    if not re.fullmatch(r"[A-Za-z][A-Za-z'\-]*", w):
        return None
    if len(w) < 2:
        return None
    return w


def load_ecdict(path):
    rows = {}
    with open(path, encoding="utf-8", errors="replace") as f:
        for r in csv.DictReader(f):
            w = clean_word(r.get("word") or "")
            if not w:
                continue
            if r.get("translation"):
                r["translation"] = r["translation"].replace("\\n", "\n")
            rows[w.lower()] = r
    return rows


def load_sentences(path):
    out = {}
    with bz2.open(path, "rt", encoding="utf-8", errors="replace") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                out[parts[0]] = parts[2]
    return out


def load_links(path, eng_ids, cmn_ids):
    pairs = []
    with bz2.open(path, "rt", encoding="utf-8", errors="replace") as f:
        for line in f:
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            a, b = parts[0], parts[1].strip()
            if a in eng_ids and b in cmn_ids:
                pairs.append((a, b))
            elif b in eng_ids and a in cmn_ids:
                pairs.append((b, a))
    return pairs


BLOCK_WORDS = {
    "sex", "sexual", "porn", "nude", "naked", "rape", "fuck", "shit", "whore",
    "prostitute", "condom", "penis", "vagina", "orgasm", "slut", "bitch",
    "kill", "murder", "suicide", "corpse", "drunk",
}


def ok_sentence(en: str):
    if not (20 <= len(en) <= 130):
        return False
    if any(ch.isdigit() for ch in en):
        return False
    if "http" in en.lower() or "|" in en:
        return False
    words = re.findall(r"[A-Za-z']+", en)
    if not (5 <= len(words) <= 20):
        return False
    if any(w.lower() in BLOCK_WORDS for w in words):
        return False
    if "hit on" in en.lower():
        return False
    return True


def build_examples(eng, cmn, pairs, targets, eng_path=None):
    """为每个目标词挑最多两条例句：先双语，再纯英文兜底"""
    cand = collections.defaultdict(list)
    for eid, cid in pairs:
        en = eng.get(eid, "")
        cn = cmn.get(cid, "")
        if not en or not cn or not ok_sentence(en):
            continue
        toks = {t.lower() for t in re.findall(r"[A-Za-z']+", en)}
        hit = toks & targets
        for w in hit:
            cand[w].append((en, cn))

    def score(item, word):
        en = item[0]
        pos = en.lower().find(word)
        return abs(len(en) - 62) + (0 if pos < 40 else 8)

    examples = {}
    for w, items in cand.items():
        picked = []
        used_en = set()
        for it in sorted(set(items), key=lambda it: score(it, w)):
            if it[0] in used_en:
                continue
            used_en.add(it[0])
            picked.append(it)
            if len(picked) >= 2:
                break
        examples[w] = picked

    # 没有中文对照的词：再从纯英文语料里找一条例句（只保留英文，不编造翻译）
    missing = {w for w in targets if w not in examples}
    if missing and eng_path:
        extra = collections.defaultdict(list)
        with bz2.open(eng_path, "rt", encoding="utf-8", errors="replace") as f:
            for line in f:
                parts = line.split("\t")
                if len(parts) < 3:
                    continue
                en = parts[2].rstrip("\n")
                if not ok_sentence(en):
                    continue
                toks = {t.lower() for t in re.findall(r"[A-Za-z']+", en)} & missing
                for w in toks:
                    if len(extra[w]) < 8:
                        extra[w].append((en, ""))
        for w, items in extra.items():
            cur = examples.get(w, [])
            used_en = {it[0] for it in cur}
            for it in sorted(set(items), key=lambda it: abs(len(it[0]) - 62)):
                if it[0] in used_en or len(cur) >= 2:
                    continue
                used_en.add(it[0])
                cur.append(it)
            if cur:
                examples[w] = cur
    return examples


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ecdict", required=True)
    ap.add_argument("--tatoeba", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--sxb-list", required=True)
    ap.add_argument("--cmudict", default="")
    args = ap.parse_args()

    rows = load_ecdict(args.ecdict)
    cmu = load_cmudict(args.cmudict)
    print(f"ECDICT 词条: {len(rows)}，CMUdict 词条: {len(cmu)}")

    # 单个标签会漏掉基础词（如 computer 只有 gk 标签），按考试范围做并集
    by_tag = collections.defaultdict(set)
    for w, r in rows.items():
        for t in (r.get("tag") or "").split():
            by_tag[t].add(w)
    cet4 = sorted(by_tag["cet4"] | by_tag["gk"] | by_tag["zk"])
    cet6 = sorted(by_tag["cet6"] | by_tag["cet4"] | by_tag["gk"] | by_tag["zk"])

    sxb_words = []
    with open(args.sxb_list, encoding="utf-8") as f:
        old = json.load(f)
        sxb_words = [clean_word(x[0]) for x in old["words"]]
    sxb_words = [w for w in sxb_words if w]
    sxb_words = sorted(set(w.lower() for w in sxb_words) | by_tag["gk"])

    targets = set()
    for lst in (cet4, cet6, sxb_words):
        targets |= {w.lower() for w in lst}
    print(f"四级 {len(cet4)} / 六级 {len(cet6)} / 专升本 {len(set(w.lower() for w in sxb_words))}，去重目标 {len(targets)}")

    print("读取 Tatoeba 语料…")
    cmn = load_sentences(os.path.join(args.tatoeba, "cmn.tsv.bz2"))
    eng_sample_ids = set()
    pair_raw = []
    with bz2.open(os.path.join(args.tatoeba, "links.tsv.bz2"), "rt", encoding="utf-8", errors="replace") as f:
        for line in f:
            parts = line.split("\t")
            if len(parts) >= 2:
                pair_raw.append((parts[0], parts[1].strip()))
    need_eng = {a for a, _ in pair_raw} | {b for _, b in pair_raw if b not in cmn}
    print(f"中文句 {len(cmn)}，英中链接 {len(pair_raw)}，需读取英文句 {len(need_eng)}")

    eng = {}
    with bz2.open(os.path.join(args.tatoeba, "eng.tsv.bz2"), "rt", encoding="utf-8", errors="replace") as f:
        for line in f:
            parts = line.split("\t")
            if len(parts) >= 3 and parts[0] in need_eng:
                eng[parts[0]] = parts[2]
    print(f"英文句 {len(eng)}")

    pairs = load_links(os.path.join(args.tatoeba, "links.tsv.bz2"), set(eng.keys()), set(cmn.keys()))
    print(f"有效英中平行句对 {len(pairs)}")

    examples = build_examples(eng, cmn, pairs, targets, os.path.join(args.tatoeba, "eng.tsv.bz2"))
    bi = sum(1 for v in examples.values() if v and v[0][1])
    print(f"有例句的词 {len(examples)}，其中带中文翻译 {bi}")

    # 组装词库
    def make_bank(bank_id, name, desc, word_list):
        seen = set()
        items = []
        audit = collections.Counter()
        dropped = []
        for raw in word_list:
            w = raw.lower()
            if w in seen:
                continue
            seen.add(w)
            r = rows.get(w)
            if r is None:
                dropped.append((raw, "no-ecdict"))
                continue
            trans = norm_translation(r.get("translation") or "")
            if not trans:
                dropped.append((raw, "no-translation"))
                continue
            # 音标优先用 CMUdict 转写的美音 IPA（与 TTS 一致），缺词时回退 ECDICT
            phon = arpabet_to_ipa(cmu[w]) if w in cmu else ""
            if phon and not re.fullmatch(r"[a-zɪʊəæʌɜɒɔɑɛɐʉɨɯœøɶɹɾʔɚɝŋθðʃʒˈˌː()-]{1,30}", phon):
                phon = ""
            if phon:
                audit["cmu"] += 1
            else:
                phon, why = norm_phon(r.get("phonetic") or "")
                audit["ecdict" if phon else (why or "empty")] += 1
            ex = examples.get(w, [])
            e1 = (ex[0][0], to_simplified(ex[0][1])) if len(ex) > 0 else ("", "")
            e2 = (ex[1][0], to_simplified(ex[1][1])) if len(ex) > 1 else ("", "")
            items.append([r.get("word") or raw, phon, trans, e1[0], e1[1], e2[0], e2[1]])
        obj = {"id": bank_id, "name": name, "desc": desc, "words": items}
        with open(os.path.join(args.out, f"words_{bank_id}.json"), "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
        ex_cov = sum(1 for it in items if it[3])
        cn_cov = sum(1 for it in items if it[4])
        print(f"[{bank_id}] {len(items)} 词，音标 {audit}，例句 {ex_cov} ({ex_cov/len(items):.1%})，例句中文 {cn_cov}，丢弃 {len(dropped)}")
        if dropped[:5]:
            print("  丢弃示例", dropped[:5])
        return items, audit

    b4, a4 = make_bank("cet4", "四级", "CET-4 大纲词汇 · 开放词库", cet4)
    b6, a6 = make_bank("cet6", "六级", "CET-6 大纲词汇 · 开放词库", cet6)
    b7, a7 = make_bank("sxb", "专升本", "专升本英语大纲词汇 · 开放词库", sxb_words)

    meta = {
        "version": 2,
        "banks": [
            {"id": "cet4", "name": "四级", "desc": "CET-4 大纲词汇 · 开放词库", "count": len(b4)},
            {"id": "cet6", "name": "六级", "desc": "CET-6 大纲词汇 · 开放词库", "count": len(b6)},
            {"id": "sxb", "name": "专升本", "desc": "专升本英语大纲词汇 · 开放词库", "count": len(b7)},
        ],
    }
    with open(os.path.join(args.out, "banks.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, separators=(",", ":"))
    attribution = """# 词库来源与许可

本 App 的词条、音标、释义与例句均来自开放许可的语料，不含任何商业词典的释义、例句或录音。

- 词条 / 中文释义：**ECDICT**（MIT License）
  https://github.com/skywind3000/ECDICT
  版权归 ECDICT 作者所有，按 MIT 许可使用；本仓库保留了原始词条的释义内容，未做商业再分发。

- 音标转写：**CMU Pronouncing Dictionary**（BSD 风格许可，Carnegie Mellon University）
  http://www.speech.cs.cmu.edu/cgi-bin/cmudict
  由 ARPAbet 音素按规则转写为美式 IPA，用于界面展示。

- 例句 / 中文翻译：**Tatoeba**（CC BY 2.0 FR）
  https://tatoeba.org
  例句由 Tatoeba 社区贡献者创作，按 CC BY 2.0 许可使用；按协议要求在此署名，未做商业性使用。

- 专升本词表：公开考试大纲词表（仅保留单词本身，释义重新取自 ECDICT）。

- 发音：调用 Android 系统离线 TTS，未内置任何第三方录音。

如需再分发本 App，请保留本文件与上述署名，并遵守各语料的许可条款。
"""
    with open(os.path.join(args.out, "ATTRIBUTION.md"), "w", encoding="utf-8") as f:
        f.write(attribution)
    print("banks.json", meta)
    print("音标统计 四级", a4, "六级", a6, "专升本", a7)


if __name__ == "__main__":
    main()
