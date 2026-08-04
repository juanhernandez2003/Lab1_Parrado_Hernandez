package edu.eci.arsw.blacklistvalidator;

import java.util.List;

/**
 * Parte III - Evaluacion de desempeno.
 *
 * Mide el tiempo que toma validar una direccion IP repartiendo la busqueda
 * entre distintas cantidades de hilos.
 *
 * Paso 1: cronometro para una sola medicion.
 */
public class PerformanceTest {

    /**
     * IP con ocurrencias dispersas (listas 29, 10034, 20200, 31000 y 70500).
     * Se usa esta y no 200.24.34.55 porque obliga a recorrer casi todo el
     * espacio de busqueda, que es donde el paralelismo se nota.
     */
    public static final String DISPERSED_IP = "202.24.34.55";

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

        long elapsedMillis = elapsedNanos / 1_000_000;

        System.out.printf("hilos=%-4d tiempo=%6d ms   ocurrencias=%d %s%n",
                threads, elapsedMillis, occurrences.size(), occurrences);

        return elapsedMillis;
    }

    /**
     * Ejecuta una unica medicion. Recibe opcionalmente el numero de hilos como
     * argumento; si no se indica, usa un hilo.
     *
     * Ejemplo:
     *   mvn exec:java "-Dexec.mainClass=edu.eci.arsw.blacklistvalidator.PerformanceTest" "-Dexec.args=4"
     */
    public static void main(String[] args) throws InterruptedException {
        int threads = (args.length > 0) ? Integer.parseInt(args[0]) : 1;

        System.out.println("Nucleos disponibles: " + availableCores());
        System.out.println("IP bajo prueba: " + DISPERSED_IP);
        System.out.println();

        measure(DISPERSED_IP, threads);
    }
}
