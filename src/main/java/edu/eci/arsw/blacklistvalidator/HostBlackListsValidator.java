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

    public List<Integer> checkHost(String ipaddress, int N) throws InterruptedException {

        HostBlacklistsDataSourceFacade skds = HostBlacklistsDataSourceFacade.getInstance();
        int total = skds.getRegisteredServersCount();
        int threadCount = Math.min(N, total);
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

        LOG.log(Level.INFO, "Checked Black Lists:{0} of {1}", new Object[]{checkedLists, total});

        return blackListOcurrences;
    }


    private static final Logger LOG = Logger.getLogger(HostBlackListsValidator.class.getName());



}
