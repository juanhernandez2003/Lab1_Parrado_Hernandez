package edu.eci.arsw.blacklistvalidator;

import edu.eci.arsw.spamkeywordsdatasource.HostBlacklistsDataSourceFacade;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parte III - Evaluacion de desempeno.
 *
 * Mide el tiempo que toma validar una direccion IP repartiendo la busqueda
 * entre distintas cantidades de hilos, y busca el punto en el que agregar mas
 * hilos deja de ayudar.
 *
 * Paso 1: cronometro para una sola medicion.
 * Paso 2: secuencia de los cinco experimentos que pide el enunciado.
 * Paso 3: barrido extendido para encontrar el punto de quiebre.
 */
public class PerformanceTest {

    /**
     * IP con ocurrencias dispersas (listas 29, 10034, 20200, 31000 y 70500).
     * Se usa esta y no 200.24.34.55 porque obliga a recorrer casi todo el
     * espacio de busqueda, que es donde el paralelismo se nota.
     */
    public static final String DISPERSED_IP = "202.24.34.55";

    /** Hilos usados en la vuelta de calentamiento (alto, para que sea rapida). */
    private static final int WARMUP_THREADS = 100;

    /** Repeticiones por configuracion, para promediar el ruido del sistema. */
    private static final int REPETITIONS = 3;

    /**
     * Una corrida que dure mas que esto no se repite: el ruido del sistema es
     * despreciable frente a su duracion y repetirla solo gasta tiempo.
     */
    private static final long REPEAT_THRESHOLD_MS = 5_000;

    private static final String CSV_FILE = "resultados_desempeno.csv";

    /** Segundos de espera antes de iniciar la carga, para conectar el perfilador. */
    private static final long ATTACH_GRACE_SECONDS = 20;

    /** Duracion por defecto de la carga sostenida en modo monitor, en segundos. */
    private static final long MONITOR_DEFAULT_SECONDS = 180;

    /** Resultado de una configuracion del experimento. */
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

    /**
     * Numero de nucleos de procesamiento disponibles para la JVM.
     */
    public static int availableCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Ejecuta una validacion y mide cuanto tarda.
     *
     * Se usa System.nanoTime() y no currentTimeMillis() porque nanoTime es
     * monotono: esta pensado para medir intervalos y no se ve afectado si el
     * reloj del sistema se ajusta durante la medicion.
     *
     * @param ipaddress direccion IP a validar.
     * @param threads numero de hilos entre los que se reparte la busqueda.
     * @return duracion de la validacion en milisegundos.
     */
    public static long measure(String ipaddress, int threads) throws InterruptedException {
        HostBlackListsValidator validator = new HostBlackListsValidator();

        long startTime = System.nanoTime();
        List<Integer> occurrences = validator.checkHost(ipaddress, threads);
        long elapsedNanos = System.nanoTime() - startTime;

        if (occurrences.size() != 5) {
            throw new IllegalStateException(
                    "Se esperaban 5 ocurrencias para " + ipaddress + " y se hallaron " + occurrences.size()
                    + ". La particion entre hilos esta perdiendo listas.");
        }

        return elapsedNanos / 1_000_000;
    }

    /**
     * Repite la medicion y devuelve la mediana, que es mas robusta que el
     * promedio ante una corrida atipica (por ejemplo si el sistema operativo
     * decide hacer otra cosa justo en la mitad).
     *
     * Las corridas largas no se repiten: con 1 hilo la validacion tarda
     * minutos y el ruido es despreciable frente a esa duracion.
     */
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

    /**
     * Micro-benchmark de la sobrecarga pura de hilos: crea, arranca y espera N
     * hilos que no hacen absolutamente nada.
     *
     * Esta medicion es la que permite atribuir la degradacion. Si el tiempo
     * total de la busqueda se acerca a este valor, quiere decir que la maquina
     * esta gastando su tiempo administrando hilos en lugar de buscando.
     *
     * @return milisegundos en crear, arrancar y hacer join de N hilos vacios.
     */
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

    /**
     * Configuraciones que pide explicitamente el enunciado:
     * 1 hilo, tantos como nucleos, el doble de nucleos, 50 y 100.
     */
    public static List<Integer> requiredThreadCounts() {
        int cores = availableCores();
        return dedup(new int[]{1, cores, cores * 2, 50, 100});
    }

    /**
     * Barrido extendido: agrega configuraciones grandes para ubicar el punto
     * en el que agregar hilos deja de mejorar el tiempo.
     */
    public static List<Integer> extendedThreadCounts() {
        int cores = availableCores();
        return dedup(new int[]{1, cores, cores * 2, 50, 100, 200, 500, 1000, 2000, 5000});
    }

    /**
     * Elimina repetidos conservando el orden. Hace falta porque en una maquina
     * de 25 nucleos el doble coincide con 50, y no tiene sentido medir dos
     * veces la misma configuracion.
     */
    private static List<Integer> dedup(int[] values) {
        List<Integer> counts = new ArrayList<>();
        for (int n : values) {
            if (n > 0 && !counts.contains(n)) {
                counts.add(n);
            }
        }
        return counts;
    }

    /**
     * Corre la secuencia de experimentos sobre la IP dispersa.
     *
     * Antes de medir se hace una vuelta de calentamiento que se descarta: la
     * JVM interpreta el bytecode al principio y solo despues de varios miles de
     * ejecuciones el compilador JIT lo traduce a codigo nativo. Sin ese
     * descarte, el primer experimento (el de un hilo) cargaria con el costo de
     * la compilacion y saldria artificialmente lento, exagerando la mejora
     * aparente del resto.
     */
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

        // Vuelta de calentamiento descartada: evita que el primer experimento
        // cargue con el costo de la compilacion JIT.
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
                // Cada hilo reserva su propia pila. Pasado cierto punto la JVM
                // no puede pedirle mas hilos nativos al sistema operativo.
                results.add(new Result(threads, -1, -1, true));
                System.out.printf(Locale.ROOT, "%-6d hilos  %8s   %s%n",
                        threads, "FALLO", e.getClass().getSimpleName());
            }
        }

        printSummary(results, cores);
        writeCsv(results);
        return results;
    }

    /**
     * Imprime la tabla de resultados.
     *
     * speedup    S(n) = T(1) / T(n), la magnitud que modela la ley de Amdahl.
     * eficiencia S(n) / n. Por encima de 100% el escalamiento es superlineal,
     *            senal de que el cuello de botella no es la CPU.
     * ms/consulta costo real de UNA consulta vista por UN hilo, es decir
     *            (tiempo * hilos) / 80.000. Mientras el paralelismo sea sano
     *            esta columna se mantiene plana; cuando empieza a crecer
     *            significa que los hilos se estan estorbando entre si.
     */
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
            // Costo por consulta visto por un hilo. Si el reparto fuera
            // perfecto este numero seria constante sin importar cuantos hilos
            // haya; que crezca es la senal de contencion.
            double perQuery = (double) r.millis * r.threads / totalLists;

            String note = "";
            if (r.threads == cores) {
                note = "= nucleos";
            } else if (r.threads == cores * 2) {
                note = "= 2x nucleos";
            }
            if (r.creationOverheadMillis > r.millis / 2) {
                note = note.isEmpty() ? "creacion domina" : note + ", creacion domina";
            }

            System.out.printf(Locale.ROOT,
                    "  %-6d  %10d   %6.2fx   %8.1f%%   %9.3f   %12d   %s%n",
                    r.threads, r.millis, speedup, efficiency, perQuery, r.creationOverheadMillis, note);
        }
    }

    /**
     * Repite una misma configuracion de forma continua durante el tiempo
     * indicado, de modo que la carga sea observable con un perfilador externo.
     *
     * Una validacion aislada con muchos hilos termina en menos de un segundo,
     * intervalo demasiado corto para tomar mediciones de CPU, memoria o estado
     * de los hilos. La espera inicial permite conectar el perfilador antes de
     * que comience la carga.
     *
     * @param threads hilos entre los que se reparte cada validacion.
     * @param durationSeconds duracion de la carga sostenida.
     */
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

    /** Numero de listas negras registradas, para calcular el costo por consulta. */
    private static int totalBlackLists() {
        return HostBlacklistsDataSourceFacade.getInstance().getRegisteredServersCount();
    }

    /**
     * Exporta los resultados en CSV para graficar tiempo vs numero de hilos.
     * Se usa Locale.ROOT para que los decimales salgan con punto y no con coma,
     * que en un CSV separado por comas romperia las columnas.
     */
    private static void writeCsv(List<Result> results) {
        long baseline = results.get(0).millis;
        try (PrintWriter out = new PrintWriter(CSV_FILE, "UTF-8")) {
            out.println("hilos,tiempo_ms,speedup,eficiencia_pct,creacion_ms");
            for (Result r : results) {
                if (r.failed) {
                    continue;
                }
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

    /**
     * Sin argumentos corre el barrido extendido.
     * Con una lista de numeros corre esas configuraciones.
     * Con la palabra 'monitor' sostiene la carga para observarla con un perfilador.
     *
     * Ejemplos:
     *   mvn exec:java "-Dexec.mainClass=edu.eci.arsw.blacklistvalidator.PerformanceTest"
     *   mvn exec:java "-Dexec.mainClass=edu.eci.arsw.blacklistvalidator.PerformanceTest" "-Dexec.args=100,500,1000,5000"
     *   mvn exec:java "-Dexec.mainClass=edu.eci.arsw.blacklistvalidator.PerformanceTest" "-Dexec.args=500 monitor"
     *   mvn exec:java "-Dexec.mainClass=edu.eci.arsw.blacklistvalidator.PerformanceTest" "-Dexec.args=500 monitor 300"
     *
     * En modo monitor el primer numero es la cantidad de hilos y el segundo
     * (opcional) cuantos segundos debe sostenerse la carga.
     */
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
