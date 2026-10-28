"""修复被单字符替换破坏的 Java 源文件。

背景：一次 PowerShell 失误把 ItemOutlinePipeline.java 里的
    's' -> 'e'   和   'i' -> 'n'
（`$r[0]` / `$r[1]` 取到的是字符串的首/次字符，于是每个 's' 被换成 'e'、每个 'i' 被换成 'n'）。

损害是**可逆的**：任何标识符都只能是"把若干 e 还原成 s、若干 n 还原成 i"得到的。
于是用项目里**其它完好的 .java 文件**收集词典，再对每个损坏的标识符枚举
所有还原组合（e→s/e、n→i/n），在词典里找命中项即可精确恢复。

字符串字面量与英文注释无法用词典恢复（它们不在词典里），脚本会报告出来，人工处理。
"""
import re
import sys
import pathlib
from collections import Counter

ROOT = pathlib.Path(r"C:\Users\chy20\Desktop\desktop\Project\RottenRuinsSplendiding")
TARGET = ROOT / "src/main/java/org/bytechen/hall/client/rend/glint/ItemOutlinePipeline.java"

KEYWORDS = """abstract assert boolean break byte case catch char class const continue default do double
else enum extends final finally float for goto if implements import instanceof int interface long native
new package private protected public return short static strictfp super switch synchronized this throw
throws transient try void volatile while true false null var record yield""".split()

IDENT = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")

def harvest():
    words = Counter()
    for p in (ROOT / "src/main/java").rglob("*.java"):
        if p.resolve() == TARGET.resolve():
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for m in IDENT.finditer(text):
            words[m.group(0)] += 1
    for k in KEYWORDS:
        words[k] += 1000
    return words

def variants(tok):
    """枚举 tok 的所有还原候选（e->s 或 e，n->i 或 n）。"""
    out = [""]
    for ch in tok:
        if ch == "e":
            out = [o + "e" for o in out] + [o + "s" for o in out]
        elif ch == "n":
            out = [o + "n" for o in out] + [o + "i" for o in out]
        else:
            out = [o + ch for o in out]
    return out

def repair_word(tok, words):
    if tok in words:
        return tok, 0
    best, best_score = None, -1
    for cand in variants(tok):
        if cand != tok and cand in words:
            score = words[cand]
            if score > best_score:
                best, best_score = cand, score
    if best is not None:
        return best, 1
    return tok, -1  # 无法恢复（多半在字符串字面量/英文注释里）

def main():
    src = TARGET.read_text(encoding="utf-8")
    words = harvest()
    stats = Counter()
    bad = []

    def repl(m):
        tok = m.group(0)
        fixed, flag = repair_word(tok, words)
        if flag == 1:
            stats["fixed"] += 1
        elif flag == -1:
            stats["unrecovered"] += 1
            if len(tok) > 2:
                bad.append(tok)
        else:
            stats["ok"] += 1
        return fixed

    out = IDENT.sub(repl, src)
    backup = TARGET.with_suffix(".java.corrupt")
    if not backup.exists():
        backup.write_text(src, encoding="utf-8")
    TARGET.write_text(out, encoding="utf-8")
    print("fixed=%d ok=%d unrecovered=%d" % (stats["fixed"], stats["ok"], stats["unrecovered"]))
    if bad:
        print("--- 无法恢复的词（多半来自字符串字面量/英文注释，需人工核对）---")
        for w, c in Counter(bad).most_common(60):
            print("  %-28s x%d" % (w, c))

if __name__ == "__main__":
    sys.exit(main())
