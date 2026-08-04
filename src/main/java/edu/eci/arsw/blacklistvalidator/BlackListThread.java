package edu.eci.arsw.blacklistvalidator;

import edu.eci.arsw.spamkeywordsdatasource.HostBlacklistsDataSourceFacade;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Hilo que revisa el segmento de listas negras [start, end) buscando una IP.
 *
 * Cada instancia trabaja sobre una porcion disjunta del espacio de busqueda, por
 * lo que no comparte estado mutable con los demas hilos: la unica clase
 * compartida es HostBlacklistsDataSourceFacade, que es thread-safe.
 *
 * No se requiere 'volatile' ni sincronizacion sobre los contadores porque el
 * hilo que crea estas instancias solo los consulta despues de hacer join(), y
 * join() establece una relacion 'happens-before' que garantiza la visibilidad
 * de todo lo escrito por el hilo antes de terminar.
 */
public class BlackListThread extends Thread {

    private final int start;
    private final int end;
    private final String ipAddress;

    private final List<Integer> blackListOccurrences = new LinkedList<>();

    /** Numero de listas negras que este hilo alcanzo a revisar. */
    private int checkedLists = 0;

    /**
     * @param start indice de la primera lista negra a revisar (inclusivo).
     * @param end   indice de la ultima lista negra a revisar (exclusivo).
     * @param ipAddress direccion IP sospechosa.
     */
    public BlackListThread(int start, int end, String ipAddress) {
        this.start = start;
        this.end = end;
        this.ipAddress = ipAddress;
    }

    @Override
    public void run() {
        HostBlacklistsDataSourceFacade skds = HostBlacklistsDataSourceFacade.getInstance();
        for (int i = start; i < end; i++) {
            checkedLists++;
            if (skds.isInBlackListServer(i, ipAddress)) {
                blackListOccurrences.add(i);
            }
        }
    }

    /**
     * Permite 'preguntarle' al hilo cuantas ocurrencias de servidores
     * maliciosos encontro en su segmento.
     *
     * @return numero de listas negras del segmento en las que aparecio la IP.
     */
    public int getOccurrencesCount() {
        return blackListOccurrences.size();
    }

    /**
     * @return numeros de las listas negras del segmento donde aparecio la IP.
     */
    public List<Integer> getOccurrences() {
        return Collections.unmodifiableList(blackListOccurrences);
    }

    /**
     * Numero real de listas negras revisadas por este hilo. Sirve para que el
     * LOG de checkHost reporte informacion veridica, y sigue siendo correcto si
     * mas adelante se agrega una condicion de parada temprana.
     *
     * @return cantidad de listas efectivamente consultadas.
     */
    public int getCheckedCount() {
        return checkedLists;
    }
}
