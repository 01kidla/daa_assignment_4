import java.util.*;
import java.util.function.Function;

/**
 * Benchmark of naive / KMP / exact Rabin-Karp on identical inputs.
 * Build:  javac -d out Java/source/Algorithms.java tests/student/Bench.java
 * Run:    java -cp out Bench
 * Compilation time is NOT measured. Warm-up: 300 untimed calls per (case, size, algorithm).
 * Each measured run = REPS consecutive calls; the table shows microseconds per call.
 */
public class Bench {
    static final long SEED = 12345L;
    static final int REPS = 20;
    static final int WARMUP = 300;

    static Map<String, Object> req(String t, String p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task", "search");
        m.put("text", t);
        m.put("pattern", p);
        return m;
    }

    /** Independent oracle (uses String.indexOf, which is allowed here but not inside the algorithms). */
    static List<Object> oracle(String t, String p) {
        List<Object> out = new ArrayList<>();
        int from = 0;
        while (true) {
            int i = t.indexOf(p, from);
            if (i < 0) break;
            out.add((long) i);
            from = i + 1;
        }
        return out;
    }

    static String randomString(Random r, int len, String alphabet) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    static String repeat(char c, int len) {
        char[] a = new char[len];
        Arrays.fill(a, c);
        return new String(a);
    }

    static double median(double a, double b, double c) {
        double[] x = {a, b, c};
        Arrays.sort(x);
        return x[1];
    }

    public static void main(String[] args) {
        System.out.println("<!-- Environment -->");
        System.out.println("java.version=" + System.getProperty("java.version")
                + ", os=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")"
                + ", cpus=" + Runtime.getRuntime().availableProcessors()
                + ", maxHeapMiB=" + Runtime.getRuntime().maxMemory() / (1024 * 1024));
        System.out.println("<!-- CPU model and RAM: add them by hand (Task Manager -> Performance) -->");
        System.out.println();
        System.out.println("| Case | n | m | Seed/generator | Algorithm | Run 1 (us/call) | Run 2 (us/call) | Run 3 (us/call) | Median (us/call) | Correct outputs checked |");
        System.out.println("|---|---|---|---|---|---|---|---|---|---|");

        int[][] sizes = {{1000, 100}, {2000, 200}, {3000, 300}};
        String[] cases = {"random-iid", "random-planted", "repetitive"};
        String[] algNames = {"naive", "kmp", "rk"};
        List<Function<Map<String, Object>, Map<String, Object>>> algs = List.of(
                Algorithms::naive, Algorithms::kmp, Algorithms::rk);

        for (String cs : cases) {
            for (int[] sz : sizes) {
                int n = sz[0], m = sz[1];
                Random rnd = new Random(SEED + n);
                String text, pattern, gen;
                if (cs.equals("random-iid")) {
                    text = randomString(rnd, n, "abcd");
                    pattern = randomString(rnd, m, "abcd");
                    gen = "java.util.Random seed " + (SEED + n) + ", alphabet {a,b,c,d}, iid text and iid pattern";
                } else if (cs.equals("random-planted")) {
                    text = randomString(rnd, n, "abcd");
                    int off = rnd.nextInt(n - m + 1);
                    pattern = text.substring(off, off + m);
                    gen = "java.util.Random seed " + (SEED + n) + ", alphabet {a,b,c,d}, pattern copied from text at offset " + off;
                } else {
                    text = repeat('a', n);
                    pattern = repeat('a', m - 1) + "b";
                    gen = "deterministic: text a^n, pattern a^(m-1)b";
                }
                Map<String, Object> in = req(text, pattern);
                List<Object> expected = oracle(text, pattern);

                for (int a = 0; a < 3; a++) {
                    Function<Map<String, Object>, Map<String, Object>> alg = algs.get(a);
                    boolean ok = true;
                    for (int w = 0; w < WARMUP; w++) {
                        ok &= expected.equals(alg.apply(in).get("positions"));
                    }
                    double[] us = new double[3];
                    for (int run = 0; run < 3; run++) {
                        long t0 = System.nanoTime();
                        Object last = null;
                        for (int r = 0; r < REPS; r++) last = alg.apply(in).get("positions");
                        long t1 = System.nanoTime();
                        ok &= expected.equals(last);
                        us[run] = (t1 - t0) / 1000.0 / REPS;
                    }
                    System.out.printf(Locale.ROOT, "| %s | %d | %d | %s | %s | %.1f | %.1f | %.1f | %.1f | %s (z=%d) |%n",
                            cs, n, m, gen, algNames[a], us[0], us[1], us[2],
                            median(us[0], us[1], us[2]), ok ? "OK" : "MISMATCH", expected.size());
                }
            }
        }
    }
}
