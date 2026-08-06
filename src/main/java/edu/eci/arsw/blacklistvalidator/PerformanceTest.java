package edu.eci.arsw.blacklistvalidator;

import edu.eci.arsw.spamkeywordsdatasource.HostBlacklistsDataSourceFacade;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// Parte III - Evaluacion de desempeno
public class PerformanceTest {

    public static final String DISPERSED_IP = "202.24.34.55";

    private static final int WARMUP_THREADS = 100;
    private static final int REPETITIONS = 3;
    private static final long REPEAT_THRESHOLD_MS = 5_000;
    private static final String CSV_FILE = "resultados_desempeno.csv";
    private static final long ATTACH_GRACE_SECONDS = 20;
    private static final long MONITOR_DEFAULT_SECONDS = 180;

    public static class Result {
        public final int threads;
        public final long millis;
        public final long creationOverheadMillis;
        public final boolean failed;

        Result(int threads, long millis, long creationOverheadMillis, boolean failed) {
            this.threads = threads;
            this.millis = millis;
            this.creationOverheadMillis = creationOverheadMillis;
            this.failed = failed;
        }
    }

    public static int availableCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    // Mide cuanto tarda una validacion con N hilos
    public static long measure(String ipaddress, int threads) throws InterruptedException {
        HostBlackListsValidator validator = new HostBlackListsValidator();
        long startTime = System.nanoTime();
        List<Integer> occurrences = validator.checkHost(ipaddress, threads);
        long elapsedNanos = System.nanoTime() - startTime;

        if (occurrences.size() != 5) {
            throw new IllegalStateException(
                    "Se esperaban 5 ocurrencias para " + ipaddress + " y se hallaron " + occurrences.size());
        }

        return elapsedNanos / 1_000_000;
    }

    // Repite la medicion y devuelve la mediana para reducir el ruido
    public static long measureRepeatedly(String ipaddress, int threads) throws InterruptedException {
        long first = measure(ipaddress, threads);
        if (first > REPEAT_THRESHOLD_MS) {
            return first;
        }

        List<Long> samples = new ArrayList<>();
        samples.add(first);
        for (int i = 1; i < REPETITIONS; i++) {
            samples.add(measure(ipaddress, threads));
        }
        Collections.sort(samples);
        return samples.get(samples.size() / 2);
    }

    // Mide cuanto tarda solo crear y arrancar N hilos vacios
    public static long measureThreadCreationOverhead(int threads) throws InterruptedException {
        Thread[] noOps = new Thread[threads];
        long startTime = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            noOps[i] = new Thread(() -> { });
            noOps[i].start();
        }
        for (Thread t : noOps) {
            t.join();
        }
        return (System.nanoTime() - startTime) / 1_000_000;
    }

    // Las 5 configuraciones que pide el enunciado
    public static List<Integer> requiredThreadCounts() {
        int cores = availableCores();
        return dedup(new int[]{1, cores, cores * 2, 50, 100});
    }

    // Barrido extendido para ver donde deja de mejorar
    public static List<Integer> extendedThreadCounts() {
        int cores = availableCores();
        return dedup(new int[]{1, cores, cores * 2, 50, 100, 200, 500, 1000, 2000, 5000});
    }

    private static List<Integer> dedup(int[] values) {
        List<Integer> counts = new ArrayList<>();
        for (int n : values) {
            if (n > 0 && !counts.contains(n)) {
                counts.add(n);
            }
        }
        return counts;
    }

    public static List<Result> runSuite(List<Integer> threadCounts) throws InterruptedException {
        int cores = availableCores();

        System.out.println("=== Evaluacion de desempeno ===");
        System.out.println("Maquina        : " + System.getProperty("os.name")
                + " / " + System.getProperty("os.arch"));
        System.out.println("JVM            : " + System.getProperty("java.version"));
        System.out.println("Nucleos        : " + cores);
        System.out.println("Memoria max JVM: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB");
        System.out.println("IP bajo prueba : " + DISPERSED_IP);
        System.out.println("Configuraciones: " + threadCounts);
        System.out.println();

        // Vuelta de calentamiento para que el JIT compile antes de medir
        measure(DISPERSED_IP, WARMUP_THREADS);

        List<Result> results = new ArrayList<>();
        for (int threads : threadCounts) {
            try {
                long overhead = measureThreadCreationOverhead(threads);
                long millis = measureRepeatedly(DISPERSED_IP, threads);
                results.add(new Result(threads, millis, overhead, false));
                System.out.printf(Locale.ROOT, "%-6d hilos  %8d ms   creacion %5d ms%n",
                        threads, millis, overhead);
            } catch (OutOfMemoryError e) {
                results.add(new Result(threads, -1, -1, true));
                System.out.printf(Locale.ROOT, "%-6d hilos  %8s   %s%n",
                        threads, "FALLO", e.getClass().getSimpleName());
            }
        }

        printSummary(results, cores);
        writeCsv(results);
        return results;
    }

    private static void printSummary(List<Result> results, int cores) {
        Result base = results.get(0);
        long baseline = base.millis;
        int totalLists = totalBlackLists();

        System.out.println();
        System.out.println("Resumen (base: " + base.threads + " hilo(s) = " + baseline + " ms):");
        System.out.println("  hilos   tiempo(ms)   speedup   eficiencia   ms/consulta   creacion(ms)   nota");
        for (Result r : results) {
            if (r.failed) {
                System.out.printf(Locale.ROOT, "  %-6d  %10s%n", r.threads, "FALLO");
                continue;
            }
            double speedup = (r.millis == 0) ? 0 : (double) baseline / r.millis;
            double efficiency = speedup / r.threads * 100.0;
            double perQuery = (double) r.millis * r.threads / totalLists;

            String note = "";
            if (r.threads == cores) note = "= nucleos";
            else if (r.threads == cores * 2) note = "= 2x nucleos";
            if (r.creationOverheadMillis > r.millis / 2)
                note = note.isEmpty() ? "creacion domina" : note + ", creacion domina";

            System.out.printf(Locale.ROOT,
                    "  %-6d  %10d   %6.2fx   %8.1f%%   %9.3f   %12d   %s%n",
                    r.threads, r.millis, speedup, efficiency, perQuery, r.creationOverheadMillis, note);
        }
    }

    public static void monitorMode(int threads, long durationSeconds) throws InterruptedException {
        String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        String pid = jvmName.contains("@") ? jvmName.substring(0, jvmName.indexOf('@')) : jvmName;

        System.out.println("=== Carga sostenida ===");
        System.out.println("PID            : " + pid);
        System.out.println("Nucleos        : " + availableCores());
        System.out.println("Hilos          : " + threads);
        System.out.println("Duracion       : " + durationSeconds + " s");
        System.out.println("Espera inicial : " + ATTACH_GRACE_SECONDS + " s");
        System.out.println();

        Thread.sleep(ATTACH_GRACE_SECONDS * 1000);

        long deadline = System.currentTimeMillis() + durationSeconds * 1000;
        int round = 0;
        long accumulated = 0;
        while (System.currentTimeMillis() < deadline) {
            long elapsed = measure(DISPERSED_IP, threads);
            round++;
            accumulated += elapsed;
            System.out.printf(Locale.ROOT, "vuelta %-4d %6d ms%n", round, elapsed);
        }

        System.out.println();
        System.out.printf(Locale.ROOT, "Vueltas: %d   Hilos creados: %d   Tiempo medio: %d ms%n",
                round, (long) round * threads, round == 0 ? 0 : accumulated / round);
    }

    private static int totalBlackLists() {
        return HostBlacklistsDataSourceFacade.getInstance().getRegisteredServersCount();
    }

    private static void writeCsv(List<Result> results) {
        long baseline = results.get(0).millis;
        try (PrintWriter out = new PrintWriter(CSV_FILE, "UTF-8")) {
            out.println("hilos,tiempo_ms,speedup,eficiencia_pct,creacion_ms");
            for (Result r : results) {
                if (r.failed) continue;
                double speedup = (r.millis == 0) ? 0 : (double) baseline / r.millis;
                out.printf(Locale.ROOT, "%d,%d,%.4f,%.2f,%d%n",
                        r.threads, r.millis, speedup, speedup / r.threads * 100.0,
                        r.creationOverheadMillis);
            }
            System.out.println();
            System.out.println("Resultados en " + CSV_FILE);
        } catch (IOException e) {
            System.err.println("No se pudo escribir " + CSV_FILE + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        boolean monitor = false;
        List<Integer> counts = new ArrayList<>();

        for (String arg : args) {
            if ("monitor".equalsIgnoreCase(arg.trim())) {
                monitor = true;
            } else {
                for (String token : arg.split(",")) {
                    if (!token.trim().isEmpty()) {
                        counts.add(Integer.parseInt(token.trim()));
                    }
                }
            }
        }

        if (monitor) {
            int threads = counts.isEmpty() ? availableCores() : counts.get(0);
            long seconds = (counts.size() > 1) ? counts.get(1) : MONITOR_DEFAULT_SECONDS;
            monitorMode(threads, seconds);
            return;
        }

        runSuite(counts.isEmpty() ? extendedThreadCounts() : counts);
    }
}
