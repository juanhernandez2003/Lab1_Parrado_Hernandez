package edu.eci.arsw.blacklistvalidator;

import edu.eci.arsw.spamkeywordsdatasource.HostBlacklistsDataSourceFacade;
import java.util.LinkedList;
import java.util.List;

public class BlackListThread extends Thread {

    private final int start;
    private final int end;
    private final String ipAddress;
    private final List<Integer> blackListOccurrences = new LinkedList<>();

    public BlackListThread(int start, int end, String ipAddress) {
        this.start = start;
        this.end = end;
        this.ipAddress = ipAddress;
    }

    @Override
    public void run() {
        HostBlacklistsDataSourceFacade skds = HostBlacklistsDataSourceFacade.getInstance();
        for (int i = start; i < end; i++) {
            if (skds.isInBlackListServer(i, ipAddress)) {
                blackListOccurrences.add(i);
            }
        }
    }

    public List<Integer> getOccurrences() {
        return blackListOccurrences;
    }
}
