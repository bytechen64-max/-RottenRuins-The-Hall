"""第二版修复：词典额外加入"上次成功编译的 .class 常量池名字"。

为什么需要：第一版词典只来自其它 .java 文件，收不到 ItemOutlinePipeline 独有的标识符
（ringShader / maskTarget / arcStrength ... 这些只在这个文件里出现）。
而上一次成功编译的 .class 文件里**包含它们的原始拼写**（常量池 + 调试信息），
所以把 .class 里的可打印标识符全部倒出来当词典，就能把剩下的也精确还原。

注意：必须从 `.java.corrupt` 备份重新修，不能对已经修过一遍的文本再修一次
（s→e 已经生效过，二次替换会继续破坏）。
"""
import re
import sys
import pathlib
from collections import Counter

ROOT = pathlib.Path(r"C:\Users\chy20\Desktop\desktop\Project\RottenRuinsSplendiding")
TARGET = ROOT / "src/main/java/org/bytechen/hall/client/rend/glint/ItemOutlinePipeline.java"
BACKUP = TARGET.with_suffix(".java.corrupt")

KEYWORDS = """abstract assert boolean break byte case catch char class const continue default do double
else enum extends final finally float for goto if implements import instanceof int interface long native
new package private protected public return short static strictfp super switch synchronized this throw
throws transient try void volatile while true false null var record yield""".split()

IDENT = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")


def harvest_sources():
    words = Counter()
    for p in (ROOT / "src/main/java").rglob("*.java"):
        if p.resolve() == TARGET.resolve():
            continue
        for m in IDENT.finditer(p.read_text(encoding="utf-8", errors="ignore")):
            words[m.group(0)] += 1
    for k in KEYWORDS:
        words[k] += 100000
    return words


def harvest_classes():
    """从已编译的 class 文件里倒出所有名字（常量池/调试信息都是原始拼写）。"""
    words = Counter()
    for p in (ROOT / "build").rglob("*.class"):
        try:
            data = p.read_bytes()
        except OSError:
            continue
        for m in re.finditer(rb"[A-Za-z_$][A-Za-z0-9_$]{1,}", data):
            try:
                words[m.group(0).decode("ascii")] += 1
            except UnicodeDecodeError:
                pass
    return words


def variants(tok):
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
    return (best, 1) if best is not None else (tok, -1)


def main():
    src_path = BACKUP if BACKUP.exists() else TARGET
    src = src_path.read_text(encoding="utf-8")
    words = harvest_sources()
    cls = harvest_classes()
    print("source dict=%d  class dict=%d" % (len(words), len(cls)))
    words.update(cls)

    stats = Counter()
    bad = []

    def repl(m):
        tok = m.group(0)
        fixed, flag = repair_word(tok, words)
        stats["fixed" if flag == 1 else "ok" if flag == 0 else "unrecovered"] += 1
        if flag == -1 and len(tok) > 2:
            bad.append(tok)
        return fixed

    out = IDENT.sub(repl, src)
    TARGET.write_text(out, encoding="utf-8")
    print("fixed=%d ok=%d unrecovered=%d" % (stats["fixed"], stats["ok"], stats["unrecovered"]))
    if bad:
        print("--- 仍无法恢复（多半在字符串字面量/英文注释里）---")
        for w, c in Counter(bad).most_common(40):
            print("  %-30s x%d" % (w, c))


if __name__ == "__main__":
    sys.exit(main())
