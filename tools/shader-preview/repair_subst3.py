import re, pathlib
from collections import Counter
ROOT = pathlib.Path(r"C:\Users\chy20\Desktop\desktop\Project\RottenRuinsSplendiding")
TARGET = ROOT / "src/main/java/org/bytechen/hall/client/rend/glint/ItemOutlinePipeline.java"
BACKUP = TARGET.with_suffix(".java.corrupt")
KEYWORDS = """abstract assert boolean break byte case catch char class const continue default do double
else enum extends final finally float for goto if implements import instanceof int interface long native
new package private protected public return short static strictfp super switch synchronized this throw
throws transient try void volatile while true false null var record yield""".split()
IDENT = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")

src_dict = Counter()
for p in (ROOT / "src/main/java").rglob("*.java"):
    if p.resolve() == TARGET.resolve(): continue
    for m in IDENT.finditer(p.read_text(encoding="utf-8", errors="ignore")):
        src_dict[m.group(0)] += 1
for k in KEYWORDS: src_dict[k] += 100000

cls_dict = Counter()
for p in (ROOT / "build").rglob("*.class"):
    try: data = p.read_bytes()
    except OSError: continue
    for m in re.finditer(rb"[A-Za-z_$][A-Za-z0-9_$]{3,}", data):   # ★ 只收 >=4 字符，滤掉垃圾短串
        try: cls_dict[m.group(0).decode("ascii")] += 1
        except UnicodeDecodeError: pass

def variants(tok):
    out = [""]
    for ch in tok:
        if ch == "e": out = [o+"e" for o in out] + [o+"s" for o in out]
        elif ch == "n": out = [o+"n" for o in out] + [o+"i" for o in out]
        else: out = [o+ch for o in out]
    return out

def repair(tok):
    if tok in src_dict: return tok, 0          # ★ 只信 .java 词典判定"本来就对"
    best, score = None, -1
    for c in variants(tok):
        if c == tok: continue
        w = src_dict.get(c, 0) * 4 + cls_dict.get(c, 0)   # 源码词典优先
        if w > score and (c in src_dict or c in cls_dict): best, score = c, w
    return (best, 1) if best else (tok, -1)

src = (BACKUP if BACKUP.exists() else TARGET).read_text(encoding="utf-8")
st = Counter(); bad = []
def repl(m):
    t = m.group(0); f, flag = repair(t)
    st["fixed" if flag==1 else "ok" if flag==0 else "unrec"] += 1
    if flag == -1 and len(t) > 3: bad.append(t)
    return f
TARGET.write_text(IDENT.sub(repl, src), encoding="utf-8")
print("fixed=%d ok=%d unrec=%d" % (st["fixed"], st["ok"], st["unrec"]))
print("unrec sample:", [w for w,_ in Counter(bad).most_common(15)])
