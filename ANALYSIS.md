# Assignment 4 — analysis

## 1. Environment and commands

- Language / runtime: Java — `openjdk version "25.0.4.1" 2026-08-18 LTS`java -version`>`
- OS / CPU / RAM: `<Windows 11, AMD Ryzen 5 8645HS w/ Radeon 760M Graphics, 16 GB DDR5>`
- Final commit URL submitted in Moodle: Submitted in Moodle (Assignment 4, Online text) as the full commit URL with the 40-character SHA.

Commands, from the repository root (`out/` is a local build directory and is not committed):

| Purpose | Command |
|---|---|
| Startup check | `python tools/run.py --setup` |
| Public checks (44 cases and a repeated mixed batch) | `python tools/run.py --check` |
| Build | `javac -d out Java/source/Main.java Java/source/Json.java Java/source/Algorithms.java` |
| Own cases (one process, mixed tasks) | `java -cp out Main < tests/student/cases.jsonl` |
| Randomized oracle test and scale tests | `python tests/student/stress_oracle.py out` |
| Search benchmark | `javac -d out Java/source/Algorithms.java tests/student/Bench.java` then `java -cp out Bench` |

Only measurements that I actually ran are reported in this document.

## 2. Algorithm explanations

Common conventions: indices are zero-based; all numbers that can be large (`cost`, `weight`) are `long`; every call reads only its own request, so no state survives between requests.

### 2.1 `lcs` — prefix dynamic programming

- **State.** `dp[i][j]` is the length of a longest common subsequence of the prefixes `a[0..i)` and `b[0..j)`; `i` is the number of characters used from `a`, `j` from `b`.
- **Base cases.** `dp[0][j] = dp[i][0] = 0` (an empty prefix shares nothing).
- **Transitions.** If `a[i-1] == b[j-1]` then `dp[i][j] = dp[i-1][j-1] + 1`; otherwise `dp[i][j] = max(dp[i-1][j], dp[i][j-1])`.
- **Order.** Row by row, left to right: every cell depends only on the cell above, the cell on the left and the diagonal cell, which are already computed.
- **Correctness.** If the last characters match, some optimal solution uses both (an exchange argument: replacing the last matched pair by this pair never shortens the subsequence), so `1 + dp[i-1][j-1]` is optimal. If they differ, at least one of them is unused, so the optimum is the better of the two smaller subproblems. Induction on `i + j`.
- **Reconstruction and ties.** Walk back from `(n, m)`: equal characters are taken and the walk goes diagonally; otherwise it moves up if `dp[i-1][j] >= dp[i][j-1]`, else left. The collected characters are reversed. Different tie rules give different but equally long answers, which the contract accepts.
- **Complexity.** O((n+1)(m+1)) time and memory (the full table is needed for reconstruction). Output is at most min(n, m) characters.
- **Test and defect it detects.** `a="tea_time", b="team"` → `team`: a solution that returns the longest common *substring* would give length 3 (`tea`). `a=""` detects off-by-one errors in the base row.

### 2.2 `merge` — interval DP

- **State.** `dp[l][r]` is the minimum total cost to merge original blocks `l..r` into one block, using only adjacent merges. `pre` holds prefix sums, so `sum(l..r) = pre[r+1] - pre[l]`.
- **Base cases.** `dp[l][l] = 0`. For `n <= 1` the answer is cost 0 and an empty plan.
- **Transitions.** `dp[l][r] = min over k in [l, r-1] of dp[l][k] + dp[k+1][r] + sum(l..r)`: the *last* merge joins `[l, k]` and `[k+1, r]` and costs their combined size. The best `k` is stored in `split[l][r]`.
- **Order.** By increasing interval length, so both sub-intervals (shorter) are ready.
- **Correctness.** In every valid plan the last merge splits the interval at some `k`; the two sides were merged independently and optimally (optimal substructure), and the last merge always costs `sum(l..r)`.
- **Reconstruction.** `buildPlan(l, r)` recurses into `[l, k]` and `[k+1, r]` first and only then appends `[l, k, r]`, so child merges always precede their parent (the plan is chronological). It has exactly `n-1` entries. Recursion depth is at most `n <= 200`. Ties: the first `k` with strictly smaller cost wins.
- **Complexity.** O(n^3) time, O(n^2) memory (`dp` as `long`, `split` as `int`). Costs reach about 2 * 10^11 for `n = 200` and sizes up to 10^9, so `long` is required.
- **Test and defect it detects.** `[2,3,4]` → 14 (the other order costs 16): a greedy "merge the two smallest" is accepted only if the blocks are adjacent. `[1,1,1,1]` → 8 detects chain-only plans (9).

### 2.3 `tree` — take/skip DP on a tree

- **State.** `take[v]` is the best weight in the subtree of `v` when `v` is selected; `skip[v]` is the best weight when `v` is not selected.
- **Base cases / transitions.** A leaf has `take = w[v]`, `skip = 0`. In general `take[v] = w[v] + sum over children skip[c]` and `skip[v] = sum over children max(take[c], skip[c])`. The answer is `max(take[root], skip[root])`; since `skip >= 0` the empty set is always allowed, so all-negative inputs give 0.
- **Order.** The tree is stored as arrays (`head`, `next`, `to`). A BFS from vertex 0 records `order[]` and `parent[]`. Processing `order` *backwards* handles every child before its parent (postorder equivalent) **without recursion**, so a chain of 100 000 vertices cannot overflow the stack.
- **Correctness.** An independent set in a subtree either contains `v` (then no child) or not (then children are independent). The two cases partition all sets, and subtrees of different children do not interact. The optimum does not depend on the root.
- **Reconstruction.** Go forward through `order`: the root is selected if `take > skip`; a child is selected only if its parent is not selected and `take[c] > skip[c]`. A selected parent therefore forces `skip` for its children. Ties prefer *not* selecting. Each vertex appears at most once.
- **Complexity.** O(n) time and auxiliary memory, including reconstruction.
- **Test and defect it detects.** Star `weights=[10,3,3,3]` → weight 10, selected `[0]`: if the parent decision were ignored in reconstruction the leaves (9) or an invalid mix would appear. `[-3,-5]` → 0 detects code that forces at least one vertex.

### 2.4 `naive` — baseline search

- **Idea.** For every start `i` in `0..n-m`, compare `text[i+j]` with `pattern[j]` for `j = 0..m-1`; report `i` if all `m` characters match.
- **Boundaries.** The last valid start is `n-m` (so the loop condition is `i + m <= n`); overlaps are reported because each start is tried independently; an empty pattern returns all boundaries `0..n` (n+1 positions, including the final one); `m > n` returns `[]`. No built-in search function is used.
- **Correctness.** Direct from the definition of an occurrence.
- **Complexity.** O(n*m) time worst case (for example `text = a^n`, `pattern = a^(m-1)b`), O(1) auxiliary memory plus O(z) output.
- **Test and defect.** `aaaaa / aaa` → `[0,1,2]` detects a loop that jumps `m` after a match (misses overlaps); `ab / ""` → `[0,1,2]` detects a missing final boundary.

### 2.5 `kmp` — prefix function

- **State.** `pi[i]` is the length of the longest proper prefix of `pattern[0..i]` that is also its suffix. It is built in O(m) with the same fallback loop used for matching.
- **Matching.** `k` is the number of pattern characters currently matched. For every text character: while `k > 0` and (`k == m` or the characters differ), set `k = pi[k-1]`; if the characters match, `k++`; if `k == m`, report `i - m + 1`.
- **Fallback after a mismatch** reuses the longest border of the already matched prefix, so the text pointer never moves backwards. **Fallback after a full match** (`k == m`) uses `pi[m-1]`, which keeps overlapping occurrences (starts 2 and 4 in `abababa / aba`).
- **Correctness.** Invariant: after processing `text[0..i]`, `k` is the length of the longest prefix of the pattern that is a suffix of `text[0..i]`; borders are exactly the candidates for the next longer match.
- **Boundaries.** The empty pattern is answered with `0..n` before indexing the pattern; `m > n` returns `[]`.
- **Complexity.** O(n + m + z) time (each step of `k` is paid for by an earlier increment), O(m) auxiliary memory, O(z) output.
- **Test and defect.** `abababa / aba` → `[0,2,4]` detects resetting `k` to 0 after a match; `ab / abc` detects reading beyond the text.

### 2.6 `rk` — exact Rabin–Karp

- **Hash.** `H(s) = (s[0]*31^(m-1) + s[1]*31^(m-2) + ... + s[m-1]) mod 101` over ASCII codes; the leftmost character has the highest power.
- **Rolling update.** `H(next) = ((H(cur) - code(text[i]) * 31^(m-1)) * 31 + code(text[i+m])) mod 101`. `31^(m-1) mod 101` is precomputed; negative remainders are normalised by adding 101 before the final `mod`. 64-bit `long` arithmetic never overflows (values below 101 * 31 * 128).
- **Verification.** Equal hashes are only *candidates*: every candidate window is compared character by character, and only verified windows are reported. The modulus 101 is deliberately tiny, so collisions are frequent and testable: `ab` and `dj` both hash to 75 (97*31 + 98 = 3105 = 30*101 + 75; 100*31 + 106 = 3206 = 31*101 + 75), and `abdj / ab` must still return `[0]`.
- **Correctness.** Every true occurrence has equal hashes, so it is always a candidate and always verified; false candidates are rejected by the comparison. Therefore the output is exact.
- **Boundaries.** The empty pattern and `m > n` are handled before any window is built.
- **Complexity.** With `c` candidate windows: O(n + m + c*m) time. This can reach O(n*m) (for example `a^n / a^m`, where every window is a true match and is verified). It is **not** linear in the worst case. Memory: O(1) auxiliary plus O(z) output (rolling window, no prefix hashes).
- **Effect of the small modulus.** For random text a non-matching window collides with probability about 1/101, so about n/101 extra candidates appear, each usually failing on the first characters. On the repetitive benchmark case (`a^n`, pattern `a^(m-1)b`) the pattern hash differs from every window hash by exactly 1 (mod 101), so there are no candidates at all, while naive does about n*m comparisons. This is a statement about this toy modulus, not about production hashes.
- **Test and defect.** The collision case above detects a solution that trusts hash equality; `aaaa / aa` → `[0,1,2]` detects a rolling update that is wrong after a verified match.

### 2.7 `suffix` — prefix doubling and Kasai

**Suffix array by prefix doubling.**
- **State.** After the round with parameter `k`, `rank[i]` is the rank of suffix `i` among all suffixes compared by their first `2k` characters (equal prefixes share a rank). The initial rank is the character code (comparison by the first character, `k = 1`).
- **Round.** Sort `sa` by the pair `(rank[i], rank[i+k])`, where `rank[i+k]` is `-1` if `i+k >= n` (a shorter suffix is smaller). Then assign new ranks by walking the sorted order and incrementing the rank whenever the pair changes. Double `k`. Stop when all `n` ranks are distinct (`rank[sa[n-1]] == n-1`); at most `ceil(log2 n) + 1` rounds are needed.
- **Correctness.** The first `2k` characters of suffix `i` are its first `k` characters followed by the first `k` characters of suffix `i+k`, so the pair of ranks orders suffixes by `2k` characters. Distinct ranks mean the order is total.
- **Complexity.** Comparison sort per round: O(n log n); total O(n log^2 n). For `n = 50 000` this is fast (see the scale run). Memory O(n) (the `Integer[]` boxing makes the constant larger). Only nonempty suffixes are ordered; no sentinel; suffix strings are never materialised.

**Kasai LCP (separate algorithm).**
- Let `inv[sa[i]] = i`. Process suffixes in text order `i = 0..n-1`; for `inv[i] > 0` compare with the previous suffix in `sa` order, `j = sa[inv[i]-1]`, extending `h` while characters match; set `lcp[inv[i]] = h`, then `h = max(h-1, 0)`. `lcp[0] = 0`.
- **Why reuse is valid.** If suffix `i` has LCP `h > 0` with its predecessor, then suffix `i+1` has LCP at least `h-1` with the predecessor of suffix `i+1` (drop the first common character). So `h` falls by at most 1 per step and rises at most `2n` times in total: O(n).

**Longest repeated substring.** Two suffixes share a prefix `s` exactly when `s` is a common prefix of them; among suffixes in sorted order, the maximum common prefix of any pair equals the maximum over *adjacent* pairs. So `repeated = text.substring(sa[i], sa[i] + max(lcp))` for the position `i` of the maximum (any one is accepted); `""` if `max(lcp) = 0`. Occurrences may overlap (`ana` in `banana`).

- **Test and defect.** `banana` → `sa=[5,3,1,0,4,2]`, `lcp=[0,1,3,0,0,2]`, `repeated=ana`: wrong handling of `rank[i+k]` beyond the end breaks `sa`; `aaaa` detects wrong ordering of a suffix that is a prefix of another; empty text detects crashes on `n = 0`.

### 2.8 `aho` — Aho–Corasick

- **Trie.** All patterns are inserted into one trie stored in a flat array `go[state*95 + (c - 32)]` (alphabet: ASCII 32–126). `term[i]` is the state where pattern `i` ends. Duplicate patterns end in the same state, so they have separate dictionary positions but share one trie state and therefore always get equal counts.
- **Failure links.** `fail[u]` is the state of the longest proper suffix of the string of `u` that is also a trie prefix. BFS from the root fills `fail` and completes `go` for missing edges (`go[v][c] = go[fail[v]][c]`), so matching never needs to follow failure links explicitly.
- **Counting.** For each text (state reset to the root and a fresh counter array for every text): move through the automaton and increment `vis[state]` for each visited state. Then process states in **reverse BFS order** (deepest first) and add `vis[u]` to `vis[fail[u]]`. Afterwards `vis[s]` is the number of text positions where the string of state `s` ends, *including* occurrences reached as suffixes of longer visited states, so patterns that are suffixes of other patterns (`he` inside `she`) are counted. The answer for dictionary position `i` is `vis[term[i]]`.
- **Why this is efficient.** Dense inputs never enumerate individual matches: the total work per text is O(|text| + S + p), not O(number of matches).
- **Boundaries.** An empty dictionary gives one empty row per text; an empty `texts` array gives `[]`; an empty text gives a row of zeros.
- **Complexity.** With `L` the total pattern length, `S <= L + 1` states and `p` patterns: construction O(L + 95*S + p) time and O(95*S + p) memory (about 38 MB for the maximum `L = 100 000`); each text O(|text| + S + p); output O(q*p).
- **Test and defect.** `["he","she","he"]` on `sheshe` → `[2,2,2]` per row detects missing suffix-pattern counts (without propagation `he` would be 0 inside `she`) and duplicate handling; the repeated text and the empty text detect counts that are not reset between texts.

## 3. Own tests

Source: `tests/student/cases.jsonl` (all cases run as one mixed batch in a single process: `java -cp out Main < tests/student/cases.jsonl`). Expected values were derived by hand from the contract; "Actual result" is the program output of that run.

| Command | Input | Expected result | Actual result / log | What it checks |
|---|---|---|---|---|
| lcs | `{"a":"tea_time","b":"team"}` | {"length":4,"sequence":"team"} | `{"length":4,"sequence":"team"}` PASS | Subsequence is not a substring: the final m is separated from 'tea_ti'. |
| lcs | `{"a":"AGGTAB","b":"GXTXAYB"}` | {"length":4,"sequence":"GTAB"} | `{"length":4,"sequence":"GTAB"}` PASS | Mismatch transition (max of up/left) and reconstruction on a classic unique optimum. |
| lcs | `{"a":"","b":"abc"}` | {"length":0,"sequence":""} | `{"length":0,"sequence":""}` PASS | Empty input: base row/column of the table; catches index errors. |
| lcs | `{"a":"abc","b":"def"}` | {"length":0,"sequence":""} | `{"length":0,"sequence":""}` PASS | No common character: the table stays 0 and reconstruction returns the empty string. |
| merge | `{"sizes":[2,3,4]}` | cost 14, plan [[0,0,1],[0,1,2]] | `{"cost":14,"plan":[[0,0,1],[0,1,2]]}` PASS | Adjacent merges only: merging (3,4) first would cost 7+9=16. |
| merge | `{"sizes":[1,1,1,1]}` | cost 8, any valid plan with 3 merges | `{"cost":8,"plan":[[0,0,1],[2,2,3],[0,1,3]]}` PASS | Balanced split beats a left chain (2+2+4=8 vs 2+3+4=9); plan validity (children before parent). |
| merge | `{"sizes":[7]}` | {"cost":0,"plan":[]} | `{"cost":0,"plan":[]}` PASS | n<=1: no merges. |
| tree | `{"n":3,"weights":[4,9,6],"edges":[[0,1],[1,2]]}` | weight 10, selected [0,2] | `{"weight":10,"selected":[0,2]}` PASS | Two leaves (4+6) beat the middle vertex (9). |
| tree | `{"n":4,"weights":[10,3,3,3],"edges":[[0,1],[0,2],[0,3]]}` | weight 10, selected [0] | `{"weight":10,"selected":[0]}` PASS | Star: taking the root forbids its children; the three leaves give only 9. |
| tree | `{"n":2,"weights":[-3,-5],"edges":[[0,1]]}` | {"weight":0,"selected":[]} | `{"weight":0,"selected":[]}` PASS | All-negative weights: the empty set is allowed. |
| tree | `{"n":0,"weights":[],"edges":[]}` | {"weight":0,"selected":[]} | `{"weight":0,"selected":[]}` PASS | Empty tree. |
| naive | `{"text":"aaaaa","pattern":"aaa"}` | {"positions":[0,1,2]} | `{"positions":[0,1,2]}` PASS | Overlapping matches and the last valid start n-m. |
| naive | `{"text":"ab","pattern":""}` | {"positions":[0,1,2]} | `{"positions":[0,1,2]}` PASS | Empty pattern matches every boundary 0..n including the final one. |
| kmp | `{"text":"abababa","pattern":"aba"}` | {"positions":[0,2,4]} | `{"positions":[0,2,4]}` PASS | Fallback via pi[m-1] after a full match keeps overlapping starts 2 and 4. |
| kmp | `{"text":"ab","pattern":"abc"}` | {"positions":[]} | `{"positions":[]}` PASS | Pattern longer than text. |
| rk | `{"text":"abdj","pattern":"ab"}` | {"positions":[0]} | `{"positions":[0]}` PASS | Known collision: 'ab' and 'dj' both hash to 75 (base 31, mod 101); character verification must reject 'dj'. |
| rk | `{"text":"aaaa","pattern":"aa"}` | {"positions":[0,1,2]} | `{"positions":[0,1,2]}` PASS | Rolling update after a verified match must not skip windows. |
| suffix | `{"text":"banana"}` | sa [5,3,1,0,4,2], lcp [0,1,3,0,0,2], repeated "ana" | `{"sa":[5,3,1,0,4,2],"lcp":[0,1,3,0,0,2],"repeated":"ana"}` PASS | Overlapping repeat (ana at 1 and 3) and Kasai reuse. |
| suffix | `{"text":"aaaa"}` | sa [3,2,1,0], lcp [0,1,2,3], repeated "aaa" | `{"sa":[3,2,1,0],"lcp":[0,1,2,3],"repeated":"aaa"}` PASS | Shorter suffix sorts first; many doubling ties. |
| suffix | `{"text":"abc"}` | sa [0,1,2], lcp [0,0,0], repeated "" | `{"sa":[0,1,2],"lcp":[0,0,0],"repeated":""}` PASS | No repeated substring. |
| suffix | `{"text":""}` | sa [], lcp [], repeated "" | `{"sa":[],"lcp":[],"repeated":""}` PASS | Empty text: no sentinel, no empty suffix. |
| aho | `{"patterns":["he","she","he"],"texts":["sheshe","sheshe",""]}` | {"counts":[[2,2,2],[2,2,2],[0,0,0]]} | `{"counts":[[2,2,2],[2,2,2],[0,0,0]]}` PASS | Suffix pattern (he inside she), duplicate patterns, reset between texts, empty text. |
| aho | `{"patterns":["aa"],"texts":["aaaa"]}` | {"counts":[[3]]} | `{"counts":[[3]]}` PASS | Overlapping occurrences through failure links. |
| aho | `{"patterns":[],"texts":["abc"]}` | {"counts":[[]]} | `{"counts":[[]]}` PASS | Empty dictionary still yields one (empty) row per text. |
| aho | `{"patterns":["a"],"texts":[]}` | {"counts":[]} | `{"counts":[]}` PASS | Empty texts array. |

Randomized oracle test (`python tests/student/stress_oracle.py out`): 400 random small instances of each command (all eight commands, including empty inputs and repeated characters) are compared with independently written brute-force oracles (substring scan, `sorted(range(n), key=lambda i: t[i:])` plus common prefixes, exhaustive subsets for the tree, a separate recursive DP for merge, a quadratic LCS table with validity checks of the witness, per-pattern occurrence counting for Aho–Corasick). Witnesses (LCS subsequence, merge plan, selected vertices, repeated substring) are validated logically, not compared literally. Result line:

```
random tests: 3200 failures: 0
```

Scale runs from the same script (maximum published sizes: tree chain of 100 000 vertices, `suffix` on `a^50000` and on random text, `kmp` 200 000 / 100 000, `rk` 10 000 / 2 000, `naive` 3 000 / 1 500, `lcs` 1 000 x 1 000, `merge` n = 200, Aho–Corasick with 10 000 patterns and 100 texts). Wall-clock seconds including JVM start-up on my machine:

```
<scale tree chain n=100000: 0.47s, output starts: '{"weight":18628613795334,"selected":[0,2,5,9,14,16,22,24,27,'
scale suffix a^50000: 0.33s, output starts: '{"sa":[49999,49998,49997,49996,49995,49994,49993,49992,49991'
scale suffix random 50000: 0.36s, output starts: '{"sa":[49999,11133,11134,11135,45419,11136,42324,34800,45420'
scale kmp 200000/100000: 0.29s, output starts: '{"positions":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18'
scale rk 10000/2000: 0.24s, output starts: '{"positions":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18'
scale naive 3000/1500: 0.20s, output starts: '{"positions":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18'
scale lcs 1000x1000: 0.20s, output starts: '{"length":805,"sequence":"babaababaaaaabbabbabbaabbbbbbaabab'
scale merge n=200: 0.20s, output starts: '{"cost":1544000000000,"plan":[[0,0,1],[2,2,3],[0,1,3],[4,4,5'
scale aho 10000 patterns, 100 texts: 0.50s, output starts: '{"counts":[[1991,1991,1991,1991,1991,1991,1991,1991,1991,199'>
```

## 4. Search comparison (naive / KMP / exact RK)

Method: `tests/student/Bench.java` calls the three implementations on identical inputs. Three sizes `(n, m) = (1000, 100), (2000, 200), (3000, 300)` and three cases: **random-iid** (text and pattern iid over {a,b,c,d}), **random-planted** (pattern copied from the text, so at least one match) and **repetitive** (`a^n` and pattern `a^(m-1)b`, the worst case for naive). Generator: `java.util.Random` with seed `12345 + n` (printed in the table). Correctness comes first: before and during timing every output is compared with an independent oracle based on `String.indexOf` (the oracle is not used inside the algorithms), and the last column states `OK` and the number of matches `z`. Compilation time is excluded. Warm-up policy: untimed warm-up calls (JIT) for every (case, size, algorithm) before measuring. Each of the 3 measured runs is `REPS = 20` consecutive calls and the table shows microseconds per call; the median of the three runs is given. Commands: see section 1.

Raw measurements (output of `java -cp out Bench` on my machine):

<<!-- Environment -->          
java.version=25.0.4.1, os=Windows 11 10.0 (amd64), cpus=12, maxHeapMiB=3900
<!-- CPU model and RAM: add them by hand (Task Manager -> Performance) -->

| Case | n | m | Seed/generator | Algorithm | Run 1 (us/call) | Run 2 (us/call) | Run 3 (us/call) | Median (us/call) | Correct outputs checked |
|---|---|---|---|---|---|---|---|---|---|
| random-iid | 1000 | 100 | java.util.Random seed 13345, alphabet {a,b,c,d}, iid text and iid pattern | naive | 69.6 | 74.7 | 78.7 | 74.7 | OK (z=0) |
| random-iid | 1000 | 100 | java.util.Random seed 13345, alphabet {a,b,c,d}, iid text and iid pattern | kmp | 25.3 | 25.2 | 25.6 | 25.3 | OK (z=0) |
| random-iid | 1000 | 100 | java.util.Random seed 13345, alphabet {a,b,c,d}, iid text and iid pattern | rk | 25.2 | 24.9 | 23.0 | 24.9 | OK (z=0) |
| random-iid | 2000 | 200 | java.util.Random seed 14345, alphabet {a,b,c,d}, iid text and iid pattern | naive | 5.2 | 4.8 | 4.7 | 4.8 | OK (z=0) |
| random-iid | 2000 | 200 | java.util.Random seed 14345, alphabet {a,b,c,d}, iid text and iid pattern | kmp | 57.2 | 59.7 | 82.9 | 59.7 | OK (z=0) |
| random-iid | 2000 | 200 | java.util.Random seed 14345, alphabet {a,b,c,d}, iid text and iid pattern | rk | 76.8 | 91.4 | 77.9 | 77.9 | OK (z=0) |
| random-iid | 3000 | 300 | java.util.Random seed 15345, alphabet {a,b,c,d}, iid text and iid pattern | naive | 13.2 | 12.5 | 12.6 | 12.6 | OK (z=0) |
| random-iid | 3000 | 300 | java.util.Random seed 15345, alphabet {a,b,c,d}, iid text and iid pattern | kmp | 9.2 | 10.1 | 9.1 | 9.2 | OK (z=0) |
| random-iid | 3000 | 300 | java.util.Random seed 15345, alphabet {a,b,c,d}, iid text and iid pattern | rk | 47.0 | 46.9 | 46.7 | 46.9 | OK (z=0) |
| random-planted | 1000 | 100 | java.util.Random seed 13345, alphabet {a,b,c,d}, pattern copied from text at offset 662 | naive | 24.8 | 25.2 | 23.6 | 24.8 | OK (z=1) |
| random-planted | 1000 | 100 | java.util.Random seed 13345, alphabet {a,b,c,d}, pattern copied from text at offset 662 | kmp | 25.8 | 26.3 | 25.7 | 25.8 | OK (z=1) |
| random-planted | 1000 | 100 | java.util.Random seed 13345, alphabet {a,b,c,d}, pattern copied from text at offset 662 | rk | 24.8 | 24.2 | 24.1 | 24.2 | OK (z=1) |
| random-planted | 2000 | 200 | java.util.Random seed 14345, alphabet {a,b,c,d}, pattern copied from text at offset 778 | naive | 48.9 | 57.9 | 63.7 | 57.9 | OK (z=1) |
| random-planted | 2000 | 200 | java.util.Random seed 14345, alphabet {a,b,c,d}, pattern copied from text at offset 778 | kmp | 61.5 | 64.8 | 64.9 | 64.8 | OK (z=1) |
| random-planted | 2000 | 200 | java.util.Random seed 14345, alphabet {a,b,c,d}, pattern copied from text at offset 778 | rk | 64.0 | 63.8 | 63.9 | 63.9 | OK (z=1) |
| random-planted | 3000 | 300 | java.util.Random seed 15345, alphabet {a,b,c,d}, pattern copied from text at offset 2055 | naive | 7.4 | 7.1 | 15.8 | 7.4 | OK (z=1) |
| random-planted | 3000 | 300 | java.util.Random seed 15345, alphabet {a,b,c,d}, pattern copied from text at offset 2055 | kmp | 9.6 | 8.9 | 9.4 | 9.4 | OK (z=1) |
| random-planted | 3000 | 300 | java.util.Random seed 15345, alphabet {a,b,c,d}, pattern copied from text at offset 2055 | rk | 48.2 | 47.8 | 48.4 | 48.2 | OK (z=1) |
| repetitive | 1000 | 100 | deterministic: text a^n, pattern a^(m-1)b | naive | 131.8 | 131.4 | 131.1 | 131.4 | OK (z=0) |
| repetitive | 1000 | 100 | deterministic: text a^n, pattern a^(m-1)b | kmp | 5.8 | 5.7 | 6.0 | 5.8 | OK (z=0) |
| repetitive | 1000 | 100 | deterministic: text a^n, pattern a^(m-1)b | rk | 15.5 | 15.4 | 22.3 | 15.5 | OK (z=0) |
| repetitive | 2000 | 200 | deterministic: text a^n, pattern a^(m-1)b | naive | 531.4 | 534.2 | 544.0 | 534.2 | OK (z=0) |
| repetitive | 2000 | 200 | deterministic: text a^n, pattern a^(m-1)b | kmp | 11.3 | 11.6 | 11.3 | 11.3 | OK (z=0) |
| repetitive | 2000 | 200 | deterministic: text a^n, pattern a^(m-1)b | rk | 32.3 | 31.3 | 31.3 | 31.3 | OK (z=0) |
| repetitive | 3000 | 300 | deterministic: text a^n, pattern a^(m-1)b | naive | 1219.9 | 1111.2 | 1142.9 | 1142.9 | OK (z=0) |
| repetitive | 3000 | 300 | deterministic: text a^n, pattern a^(m-1)b | kmp | 43.6 | 18.0 | 15.3 | 18.0 | OK (z=0) |
| repetitive | 3000 | 300 | deterministic: text a^n, pattern a^(m-1)b | rk | 54.8 | 44.4 | 40.7 | 44.4 | OK (z=0) |>

Discussion (to be checked against the table above):
- Naive does about n*m comparisons only when there are long partial matches; on random text over four letters most windows fail after one or two comparisons, so it is competitive on short inputs. On the repetitive case its cost grows roughly with n*m.
- KMP does O(n + m) work in every case, independent of the text, at the price of building `pi` and a slightly heavier inner loop.
- RK with base 31 and modulus 101 does a constant amount of work per window plus verification of candidates. Because the modulus is tiny, about one window in 101 collides on random text and is verified (cheap, since it usually fails at once). In the repetitive case there are no candidates at all (hash difference 1 mod 101), so RK stays linear there; with `pattern = a^m` every window would be a candidate *and* a true match, which gives O(n*m) — this is why linear worst-case time must not be claimed for RK.
- The known collision `ab` / `dj` (both 75) is part of the own test table above.
- With such small inputs absolute times are microseconds and noisy; I therefore compare medians and the growth between sizes rather than single numbers.

## 5. Sources and AI assistance

- Course material and the assignment repository documents (README, `docs/guide/CONTRACTS.md`, `RUBRIC.md`, `TESTING.md`, `SUBMISSION.md`) and the interactive lesson linked from the README.
- AI assistance: Claude (Anthropic) was used as an assistant. It produced the first version of `Java/source/Algorithms.java` (all eight commands), the files in `tests/student` (`cases.jsonl`, `stress_oracle.py`, `Bench.java`) and a draft of this document. `Main.java` and `Json.java` are the unmodified starter files.
- Verification of that work: the code was compiled together with the starter files; the examples from `CONTRACTS.md` were run; the randomized comparison against independent brute-force oracles (section 3) and the maximum-size scale runs passed; I ran `python tools/run.py --check` and checked the Public checks run for the submitted commit.
