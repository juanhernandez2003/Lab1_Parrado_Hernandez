/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.eci.arsw.blacklistvalidator;

import edu.eci.arsw.spamkeywordsdatasource.HostBlacklistsDataSourceFacade;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author hcadavid
 */
public class HostBlackListsValidator {

    private static final int BLACK_LIST_ALARM_COUNT=5;

    /**
     * Check the given host's IP address in all the available black lists,
     * and report it as NOT Trustworthy when such IP was reported in at least
     * BLACK_LIST_ALARM_COUNT lists, or as Trustworthy in any other case.
     *
     * La busqueda se reparte entre N hilos: el espacio de listas negras se
     * divide en N segmentos disjuntos y cada hilo revisa el suyo. El metodo
     * espera con join() a que los N hilos terminen, acumula las ocurrencias de
     * todos y solo entonces decide si el host es confiable o no.
     *
     * @param ipaddress suspicious host's IP address.
     * @param N numero de hilos entre los que se reparte la busqueda.
     * @return  Blacklists numbers where the given host's IP address was found.
     * @throws InterruptedException si el hilo principal es interrumpido mientras espera.
     */
    public List<Integer> checkHost(String ipaddress, int N) throws InterruptedException {

        if (N <= 0) {
            throw new IllegalArgumentException("El numero de hilos debe ser mayor que cero, se recibio: " + N);
        }

        HostBlacklistsDataSourceFacade skds = HostBlacklistsDataSourceFacade.getInstance();
        int total = skds.getRegisteredServersCount();

        // No tiene sentido crear mas hilos que listas negras por revisar.
        int threadCount = Math.min(N, total);

        // Reparto balanceado: si 'total' no es divisible por 'threadCount', el
        // residuo se distribuye de a una lista entre los primeros hilos. Asi la
        // particion cubre exactamente [0, total) sin huecos ni solapamientos,
        // sin importar si N es par o impar.
        int segmentSize = total / threadCount;
        int remainder = total % threadCount;

        BlackListThread[] threads = new BlackListThread[threadCount];
        int start = 0;
        for (int i = 0; i < threadCount; i++) {
            int end = start + segmentSize + (i < remainder ? 1 : 0);
            threads[i] = new BlackListThread(start, end, ipaddress);
            threads[i].start();
            start = end;
        }

        // Espera a que los N hilos terminen su sub-problema y acumula resultados.
        LinkedList<Integer> blackListOcurrences = new LinkedList<>();
        int occurrencesCount = 0;
        int checkedLists = 0;
        for (BlackListThread t : threads) {
            t.join();
            blackListOcurrences.addAll(t.getOccurrences());
            occurrencesCount += t.getOccurrencesCount();
            checkedLists += t.getCheckedCount();
        }

        if (occurrencesCount >= BLACK_LIST_ALARM_COUNT) {
            skds.reportAsNotTrustworthy(ipaddress);
        } else {
            skds.reportAsTrustworthy(ipaddress);
        }

        // El conteo proviene de los hilos, no del total teorico, por lo que la
        // informacion reportada es veridica bajo el esquema en paralelo.
        LOG.log(Level.INFO, "Checked Black Lists:{0} of {1}", new Object[]{checkedLists, total});

        return blackListOcurrences;
    }


    private static final Logger LOG = Logger.getLogger(HostBlackListsValidator.class.getName());



}
