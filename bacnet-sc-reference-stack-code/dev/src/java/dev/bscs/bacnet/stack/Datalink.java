// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

/**
 * The common interface provided by all datalinks to the single NetworkLayer.
 */
public interface Datalink {

    // this is the method used by the Network Layer to send NPDUs
    void          dlUnitdataRequest(byte[] da, byte[] data, int priority, boolean der, AuthData auth);

    void          setNetwork(int network);
    int           getNetwork();

    NetworkLayer  getNetworkLayer();

    String        getName();

    byte[]        getMac();
    String        getMacAsString();
    String        macToString(byte[] mac);

    boolean       start();
    void          stop();  // request to stop - normal async state machine goes to idle
    void          close(); // rude immediate halt - synchronous shutdown
    void          dump(String prefix);  // dump status to Shell



}
