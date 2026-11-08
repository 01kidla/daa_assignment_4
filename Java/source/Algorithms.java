import java.util.*;

public final class Algorithms {

    // ---------- Part 1: LCS ----------
    static Map<String,Object> lcs(Map<String,Object> input) {
        String a = (String) input.get("a"), b = (String) input.get("b");
        int n = a.length(), m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++)
            for (int j = 1; j <= m; j++)
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? dp[i - 1][j - 1] + 1
                        : Math.max(dp[i - 1][j], dp[i][j - 1]);
        StringBuilder sb = new StringBuilder();
        int i = n, j = m;
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) { sb.append(a.charAt(i - 1)); i--; j--; }
            else if (dp[i - 1][j] >= dp[i][j - 1]) i--;
            else j--;
        }
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("length", (long) dp[n][m]);
        r.put("sequence", sb.reverse().toString());
        return r;
    }

    // ---------- Part 2: merge (interval DP) ----------
    static Map<String,Object> merge(Map<String,Object> input) {
        List<?> sz = (List<?>) input.get("sizes");
        int n = sz.size();
        Map<String,Object> r = new LinkedHashMap<>();
        if (n <= 1) { r.put("cost", 0L); r.put("plan", new ArrayList<Object>()); return r; }
        long[] pre = new long[n + 1];
        for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + ((Number) sz.get(i)).longValue();
        long[][] dp = new long[n][n];
        int[][] split = new int[n][n];
        for (int len = 2; len <= n; len++) {
            for (int l = 0; l + len - 1 < n; l++) {
                int rr = l + len - 1;
                long best = Long.MAX_VALUE; int bk = l;
                long sum = pre[rr + 1] - pre[l];
                for (int k = l; k < rr; k++) {
                    long c = dp[l][k] + dp[k + 1][rr] + sum;
                    if (c < best) { best = c; bk = k; }
                }
                dp[l][rr] = best; split[l][rr] = bk;
            }
        }
        List<Object> plan = new ArrayList<>();
        buildPlan(0, n - 1, split, plan);
        r.put("cost", dp[0][n - 1]);
        r.put("plan", plan);
        return r;
    }

    private static void buildPlan(int l, int r, int[][] split, List<Object> plan) {
        if (l >= r) return;
        int k = split[l][r];
        buildPlan(l, k, split, plan);
        buildPlan(k + 1, r, split, plan);
        plan.add(new int[]{l, k, r});
    }

    // ---------- Part 3: tree independent set ----------
    static Map<String,Object> tree(Map<String,Object> input) {
        int n = ((Number) input.get("n")).intValue();
        Map<String,Object> res = new LinkedHashMap<>();
        if (n == 0) { res.put("weight", 0L); res.put("selected", new ArrayList<Object>()); return res; }
        List<?> ws = (List<?>) input.get("weights");
        List<?> es = (List<?>) input.get("edges");
        long[] w = new long[n];
        for (int i = 0; i < n; i++) w[i] = ((Number) ws.get(i)).longValue();
        int[] head = new int[n], nxt = new int[2 * es.size()], to = new int[2 * es.size()];
        Arrays.fill(head, -1);
        int cnt = 0;
        for (Object eo : es) {
            List<?> e = (List<?>) eo;
            int u = ((Number) e.get(0)).intValue(), v = ((Number) e.get(1)).intValue();
            to[cnt] = v; nxt[cnt] = head[u]; head[u] = cnt++;
            to[cnt] = u; nxt[cnt] = head[v]; head[v] = cnt++;
        }
        int[] parent = new int[n], order = new int[n];
        Arrays.fill(parent, -1);
        boolean[] seen = new boolean[n];
        int qh = 0, qt = 0;
        order[qt++] = 0; seen[0] = true;
        while (qh < qt) {
            int v = order[qh++];
            for (int e = head[v]; e != -1; e = nxt[e]) {
                int u = to[e];
                if (!seen[u]) { seen[u] = true; parent[u] = v; order[qt++] = u; }
            }
        }
        long[] take = new long[n], skip = new long[n];
        for (int i = 0; i < n; i++) take[i] = w[i];
        for (int idx = qt - 1; idx >= 0; idx--) {
            int v = order[idx];
            int p = parent[v];
            if (p != -1) {
                take[p] += skip[v];
                skip[p] += Math.max(take[v], skip[v]);
            }
        }
        boolean[] taken = new boolean[n];
        List<Object> selected = new ArrayList<>();
        for (int idx = 0; idx < qt; idx++) {
            int v = order[idx];
            int p = parent[v];
            if (p == -1) taken[v] = take[v] > skip[v];
            else taken[v] = !taken[p] && take[v] > skip[v];
            if (taken[v]) selected.add((long) v);
        }
        res.put("weight", Math.max(take[0], skip[0]));
        res.put("selected", selected);
        return res;
    }

    // ---------- Part 4: naive / KMP / Rabin-Karp ----------
    private static Map<String,Object> pos(List<Object> p) {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("positions", p);
        return r;
    }

    private static List<Object> allBoundaries(int n) {
        List<Object> p = new ArrayList<>();
        for (int i = 0; i <= n; i++) p.add((long) i);
        return p;
    }

    static Map<String,Object> naive(Map<String,Object> input) {
        String t = (String) input.get("text"), p = (String) input.get("pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return pos(allBoundaries(n));
        List<Object> out = new ArrayList<>();
        for (int i = 0; i + m <= n; i++) {
            int j = 0;
            while (j < m && t.charAt(i + j) == p.charAt(j)) j++;
            if (j == m) out.add((long) i);
        }
        return pos(out);
    }

    static Map<String,Object> kmp(Map<String,Object> input) {
        String t = (String) input.get("text"), p = (String) input.get("pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return pos(allBoundaries(n));
        List<Object> out = new ArrayList<>();
        if (m > n) return pos(out);
        int[] pi = new int[m];
        for (int i = 1, k = 0; i < m; i++) {
            while (k > 0 && p.charAt(i) != p.charAt(k)) k = pi[k - 1];
            if (p.charAt(i) == p.charAt(k)) k++;
            pi[i] = k;
        }
        for (int i = 0, k = 0; i < n; i++) {
            while (k > 0 && (k == m || t.charAt(i) != p.charAt(k))) k = pi[k - 1];
            if (t.charAt(i) == p.charAt(k)) k++;
            if (k == m) out.add((long) (i - m + 1));
        }
        return pos(out);
    }

    static Map<String,Object> rk(Map<String,Object> input) {
        String t = (String) input.get("text"), p = (String) input.get("pattern");
        int n = t.length(), m = p.length();
        final int B = 31, MOD = 101;
        if (m == 0) return pos(allBoundaries(n));
        List<Object> out = new ArrayList<>();
        if (m > n) return pos(out);
        long high = 1;
        for (int i = 0; i < m - 1; i++) high = (high * B) % MOD;
        long ph = 0, wh = 0;
        for (int i = 0; i < m; i++) {
            ph = (ph * B + p.charAt(i)) % MOD;
            wh = (wh * B + t.charAt(i)) % MOD;
        }
        for (int i = 0; i + m <= n; i++) {
            if (wh == ph) {
                int j = 0;
                while (j < m && t.charAt(i + j) == p.charAt(j)) j++;
                if (j == m) out.add((long) i);
            }
            if (i + m < n) {
                wh = (wh - t.charAt(i) * high % MOD + MOD) % MOD;
                wh = (wh * B + t.charAt(i + m)) % MOD;
            }
        }
        return pos(out);
    }

    // ---------- Part 5: suffix array + Kasai ----------
    static Map<String,Object> suffix(Map<String,Object> input) {
        String t = (String) input.get("text");
        int n = t.length();
        Map<String,Object> res = new LinkedHashMap<>();
        if (n == 0) {
            res.put("sa", new int[0]); res.put("lcp", new int[0]); res.put("repeated", "");
            return res;
        }
        Integer[] sa = new Integer[n];
        int[] rank = new int[n], tmp = new int[n];
        for (int i = 0; i < n; i++) { sa[i] = i; rank[i] = t.charAt(i); }
        for (int k = 1; ; k <<= 1) {
            final int kk = k;
            final int[] rk = rank;
            Comparator<Integer> cmp = (x, y) -> {
                if (rk[x] != rk[y]) return Integer.compare(rk[x], rk[y]);
                int rx = x + kk < n ? rk[x + kk] : -1;
                int ry = y + kk < n ? rk[y + kk] : -1;
                return Integer.compare(rx, ry);
            };
            Arrays.sort(sa, cmp);
            tmp[sa[0]] = 0;
            for (int i = 1; i < n; i++)
                tmp[sa[i]] = tmp[sa[i - 1]] + (cmp.compare(sa[i - 1], sa[i]) < 0 ? 1 : 0);
            System.arraycopy(tmp, 0, rank, 0, n);
            if (rank[sa[n - 1]] == n - 1) break;
        }
        int[] saArr = new int[n], inv = new int[n], lcp = new int[n];
        for (int i = 0; i < n; i++) { saArr[i] = sa[i]; inv[saArr[i]] = i; }
        int h = 0;
        for (int i = 0; i < n; i++) {
            if (inv[i] > 0) {
                int j = saArr[inv[i] - 1];
                while (i + h < n && j + h < n && t.charAt(i + h) == t.charAt(j + h)) h++;
                lcp[inv[i]] = h;
                if (h > 0) h--;
            } else {
                h = 0;
            }
        }
        int best = 0, bi = 0;
        for (int i = 0; i < n; i++) if (lcp[i] > best) { best = lcp[i]; bi = i; }
        res.put("sa", saArr);
        res.put("lcp", lcp);
        res.put("repeated", best == 0 ? "" : t.substring(saArr[bi], saArr[bi] + best));
        return res;
    }

    // ---------- Part 6: Aho-Corasick ----------
    static Map<String,Object> aho(Map<String,Object> input) {
        List<?> pats = (List<?>) input.get("patterns");
        List<?> texts = (List<?>) input.get("texts");
        int p = pats.size();
        int total = 0;
        for (Object o : pats) total += ((String) o).length();
        int maxS = total + 1;
        final int A = 95;
        int[] go = new int[maxS * A];
        Arrays.fill(go, -1);
        int[] fail = new int[maxS];
        int[] term = new int[p];
        int states = 1;
        for (int i = 0; i < p; i++) {
            String s = (String) pats.get(i);
            int v = 0;
            for (int j = 0; j < s.length(); j++) {
                int c = s.charAt(j) - 32;
                if (go[v * A + c] == -1) go[v * A + c] = states++;
                v = go[v * A + c];
            }
            term[i] = v;
        }
        int[] order = new int[states];
        int qh = 0, qt = 0;
        for (int c = 0; c < A; c++) {
            int u = go[c];
            if (u == -1) go[c] = 0;
            else { fail[u] = 0; order[qt++] = u; }
        }
        while (qh < qt) {
            int v = order[qh++];
            for (int c = 0; c < A; c++) {
                int u = go[v * A + c];
                if (u == -1) go[v * A + c] = go[fail[v] * A + c];
                else { fail[u] = go[fail[v] * A + c]; order[qt++] = u; }
            }
        }
        List<Object> rows = new ArrayList<>();
        for (Object to : texts) {
            String t = (String) to;
            long[] vis = new long[states];
            int v = 0;
            for (int i = 0; i < t.length(); i++) {
                v = go[v * A + (t.charAt(i) - 32)];
                vis[v]++;
            }
            for (int idx = qt - 1; idx >= 0; idx--) {
                int u = order[idx];
                vis[fail[u]] += vis[u];
            }
            long[] row = new long[p];
            for (int i = 0; i < p; i++) row[i] = vis[term[i]];
            rows.add(row);
        }
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("counts", rows);
        return r;
    }
}
