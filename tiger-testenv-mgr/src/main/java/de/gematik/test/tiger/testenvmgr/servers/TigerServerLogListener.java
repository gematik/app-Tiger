package de.gematik.test.tiger.testenvmgr.servers;

public interface TigerServerLogListener {

    void receiveServerLogUpdate(TigerServerLogUpdate update);
}