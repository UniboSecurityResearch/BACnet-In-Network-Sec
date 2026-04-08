// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import java.util.EventListener;
import java.util.UUID;

/**
 * Provides a means for {@link }SCConnection} instances to know who "owns" them, both for updating and querying the owner.
 * An SCConnection obviously need to know where to "send up" the received messages, but it also needs to know whether to reject
 * connections based on duplicated VMACs or to drop old connections from duplicated UUIDs.  So this information is provided
 * by the connection's "context", either a {@link SCHubFunction} or a {@link SCNodeSwitch}. Each of these maintain independent
 * sets of connections, so it's not possible to centralize the concept of "duplicate" in the {@link SCNode}.  Other owners like
 * {@link SCHubConnector} and {@link SCDirectConnector} maintain a "context of one" so the duplicate questions are moot but are
 * still answered as part of being an "owner".
 * @author drobin
 */
public interface SCConnectionOwner extends EventListener {
    void         incoming(SCConnection connection, SCMessage message);
    SCConnection findConnectionFor(UUID uuid);
    SCConnection findConnectionFor(SCVMAC vmac);
    void         connectionEstablished(SCConnection connection);
    void         connectionClosed(SCConnection connection);
}
