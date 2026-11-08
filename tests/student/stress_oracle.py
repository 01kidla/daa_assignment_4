"""Randomized oracle test for Assignment 4 (Java solution).

Usage (from the repository root, after `javac -d out Java/source/Main.java Java/source/Json.java Java/source/Algorithms.java`):
    python tests/student/stress_oracle.py out

Every command is compared with an independently written brute-force oracle on small random inputs
(including empty inputs), all in ONE process with mixed task types (catches stale state between requests).
The second part runs maximum-size inputs and prints wall-clock seconds (includes JVM start-up).
"""
import json, os, random, subprocess, sys, time
from functools import lru_cache

CP = sys.argv[1] if len(sys.argv) > 1 else "out"
random.seed(7)


def occ(t, p):
    return [i for i in range(len(t) - len(p) + 1) if t[i:i + len(p)] == p]


def lcs_len(a, b):
    dp = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]
    for i in range(1, len(a) + 1):
        for j in range(1, len(b) + 1):
            dp[i][j] = dp[i - 1][j - 1] + 1 if a[i - 1] == b[j - 1] else max(dp[i - 1][j], dp[i][j - 1])
    return dp[-1][-1]


def is_subseq(s, t):
    it = iter(t)
    return all(c in it for c in s)


def run_batch(reqs):
    inp = "\n".join(json.dumps(r) for r in reqs) + "\n"
    out = subprocess.run(["java", "-cp", CP, "Main"], input=inp, capture_output=True, text=True).stdout
    lines = [l for l in out.strip().split("\n") if l.startswith("{")]
    assert len(lines) == len(reqs), (len(lines), len(reqs))
    return [json.loads(l) for l in lines]


reqs, exp = [], []
for _ in range(400):
    al = random.choice(["ab", "abc", "a"])
    t = "".join(random.choice(al) for _ in range(random.randint(0, 25)))
    p = "".join(random.choice(al) for _ in range(random.randint(0, 5)))
    for task in ("naive", "kmp", "rk"):
        reqs.append({"task": task, "text": t, "pattern": p})
        exp.append(("pos", occ(t, p) if p else list(range(len(t) + 1))))
    reqs.append({"task": "suffix", "text": t}); exp.append(("suf", t))
    a = "".join(random.choice(al) for _ in range(random.randint(0, 12)))
    reqs.append({"task": "lcs", "a": a, "b": t[:12]}); exp.append(("lcs", a, t[:12]))
    pats = ["".join(random.choice(al) for _ in range(random.randint(1, 4))) for _ in range(random.randint(0, 5))]
    texts = [t, "", p]
    reqs.append({"task": "aho", "patterns": pats, "texts": texts}); exp.append(("aho", pats, texts))
    n = random.randint(0, 8)
    sizes = [random.randint(0, 9) for _ in range(n)]
    reqs.append({"task": "merge", "sizes": sizes}); exp.append(("merge", sizes))
    n = random.randint(0, 9)
    w = [random.randint(-5, 9) for _ in range(n)]
    edges = [[random.randint(0, i - 1), i] for i in range(1, n)]
    reqs.append({"task": "tree", "n": n, "weights": w, "edges": edges}); exp.append(("tree", n, w, edges))

bad = 0
for r, e, o in zip(reqs, exp, run_batch(reqs)):
    ok = True
    k = e[0]
    if k == "pos":
        ok = o["positions"] == e[1]
    elif k == "suf":
        t = e[1]; n = len(t)
        sa = sorted(range(n), key=lambda i: t[i:])
        lcp = [0] + [len(os.path.commonprefix([t[sa[i - 1]:], t[sa[i]:]])) for i in range(1, n)] if n else []
        best = max(lcp) if n else 0
        rep = o["repeated"]
        ok = o["sa"] == sa and o["lcp"] == lcp and len(rep) == best and (best == 0 or len(occ(t, rep)) >= 2)
    elif k == "lcs":
        a, b = e[1], e[2]; s = o["sequence"]
        ok = o["length"] == lcs_len(a, b) == len(s) and is_subseq(s, a) and is_subseq(s, b)
    elif k == "aho":
        _, pats, texts = e
        ok = o["counts"] == [[len(occ(t, p)) for p in pats] for t in texts]
    elif k == "merge":
        sz = e[1]; n = len(sz)

        @lru_cache(None)
        def f(l, r):
            if l == r: return 0
            return min(f(l, m) + f(m + 1, r) for m in range(l, r)) + sum(sz[l:r + 1])

        best = f(0, n - 1) if n > 1 else 0
        blocks = {(i, i): sz[i] for i in range(n)}; cost = 0; valid = True
        for l, m, rr in o["plan"]:
            if (l, m) not in blocks or (m + 1, rr) not in blocks:
                valid = False; break
            s = blocks.pop((l, m)) + blocks.pop((m + 1, rr)); blocks[(l, rr)] = s; cost += s
        ok = valid and o["cost"] == best == cost and (n <= 1 or len(o["plan"]) == n - 1)
    elif k == "tree":
        _, n, w, edges = e
        best = 0
        for mask in range(1 << n):
            if all(not (mask >> u & 1 and mask >> v & 1) for u, v in edges):
                best = max(best, sum(w[i] for i in range(n) if mask >> i & 1))
        sel = o["selected"]
        ok = (o["weight"] == best == sum(w[i] for i in sel) and len(set(sel)) == len(sel)
              and all(not (u in sel and v in sel) for u, v in edges))
    if not ok:
        bad += 1; print("FAIL", r, o)
print("random tests:", len(reqs), "failures:", bad)


def timed(name, req):
    t0 = time.time()
    out = subprocess.run(["java", "-cp", CP, "Main"], input=json.dumps(req) + "\n", capture_output=True, text=True).stdout
    print(f"scale {name}: {time.time() - t0:.2f}s, output starts: {out[:60]!r}")


n = 100000
timed("tree chain n=100000", {"task": "tree", "n": n, "weights": [random.randint(-10**9, 10**9) for _ in range(n)], "edges": [[i, i + 1] for i in range(n - 1)]})
timed("suffix a^50000", {"task": "suffix", "text": "a" * 50000})
timed("suffix random 50000", {"task": "suffix", "text": "".join(random.choice("ab") for _ in range(50000))})
timed("kmp 200000/100000", {"task": "kmp", "text": "a" * 200000, "pattern": "a" * 100000})
timed("rk 10000/2000", {"task": "rk", "text": "a" * 10000, "pattern": "a" * 2000})
timed("naive 3000/1500", {"task": "naive", "text": "a" * 3000, "pattern": "a" * 1500})
timed("lcs 1000x1000", {"task": "lcs", "a": "".join(random.choice("ab") for _ in range(1000)), "b": "".join(random.choice("ab") for _ in range(1000))})
timed("merge n=200", {"task": "merge", "sizes": [10**9] * 200})
timed("aho 10000 patterns, 100 texts", {"task": "aho", "patterns": ["a" * 10 for _ in range(10000)], "texts": ["a" * 2000 for _ in range(100)]})
