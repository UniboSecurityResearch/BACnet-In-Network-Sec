// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetsc.SCDatalink;
import dev.bscs.bacnet.bacnetsc.SCProperties;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.common.Application;

/**
 * An application that creates one BACnet/SC device without a hub function. It can be configured to support direct connect or not.
 * It is structurally identical to {@link TestHub} because the SC features are really turned and off by the configuration file.
 * The configuration of this application is controlled by the TestNode.properties file. See the top level README for details.
 * Also see {@link Application} class for details of the structure of the meaning of these overridden methods.
 * @author drobin
 */
public class TestNode extends Application {

    private static SCDatalink scDatalink;

    @Override protected void  start() {
        Device device = new Device(configuration);
        scDatalink = new SCDatalink("SC-1", new SCProperties(configuration),device,0);
        scDatalink.start();
    }

    @Override protected void addCommands() {
        BACnetCommands.addCommands();
        SCCommands.addCommands();
    }

    @Override protected void stop() {
        scDatalink.stop();
    }

    @Override protected void close() {
        scDatalink.close();
    }

}
