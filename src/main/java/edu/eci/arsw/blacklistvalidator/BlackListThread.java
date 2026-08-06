package edu.eci.arsw.blacklistvalidator;

import edu.eci.arsw.spamkeywordsdatasource.HostBlacklistsDataSourceFacade;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class BlackListThread extends Thread {

    private final int start;
    private final int end;
    private final String ipAddress;

    private final List<Integer> blackListOccurrences = new LinkedList<>();
    private int checkedLists = 0;

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

    // Retorna cuantas listas negras encontro en su segmento
    public int getOccurrencesCount() {
        return blackListOccurrences.size();
    }

    // Retorna los numeros de las listas donde aparecio la IP
    public List<Integer> getOccurrences() {
        return Collections.unmodifiableList(blackListOccurrences);
    }

    // Retorna cuantas listas reviso este hilo
    public int getCheckedCount() {
        return checkedLists;
    }
}
