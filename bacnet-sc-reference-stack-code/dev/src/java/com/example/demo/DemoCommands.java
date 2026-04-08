// Copyright, distribution, and usage are defined by the top level LICENSE file.
package com.example.demo;

import dev.bscs.bacnet.applications.BACnetCommands;
import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.data.BACnetPropertyIdentifier;
import dev.bscs.bacnet.stack.data.base.BACnetData;
import dev.bscs.bacnet.stack.data.base.BACnetObjectIdentifier;
import dev.bscs.bacnet.stack.services.ReadPropertyClient;
import dev.bscs.bacnet.stack.services.WhoIsClient;
import dev.bscs.common.*;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.events.EventType;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This is a command that was made just for a demonstration of how to create a command.
 * It is of limited usefulness but is included here because it was referenced in a webinar.
 * It can be included without code changes in any application with a line in the configuration like this:
 *     common.commandClass = com.example.demo.DemoCommands
 * @author drobin
 */

public class DemoCommands implements CommandProcessor.CommandSet {

    public void addCommands() { CommandProcessor.addCommand(new WixCommand()); }

    private static class WixCommand extends Command implements EventListener {
        public WixCommand() { super("wix","wix","WhoIsExtended - a WhoIs followed by a ReadProperty");}
        Queue<Binding> queue = new ConcurrentLinkedQueue<>();
        Timer timer = new Timer();
        @Override public void handleEvent(Object source, EventType eventType, Object... args) {
            Binding binding;
            if (timer.remaining() == 0 && (binding = queue.poll()) != null) {
                timer.start(2000L);
                Shell.print(Formatting.toNetMac(binding.dnet,binding.dadr));
                readPropertyClient.request(
                        BACnetCommands.getSelectedDevice(), binding.dnet, binding.dadr,
                        BACnetObjectIdentifier.combine(8,0x3FFFFF),
                        BACnetPropertyIdentifier.OBJECT_NAME, -1
                );
            }
        }
        ReadPropertyClient readPropertyClient = new ReadPropertyClient() {
            @Override protected void success(Device device, BACnetData value, AuthData auth) {
                Shell.println(" name="+value.toString()+" auth="+auth);
                timer.clear();
                EventLoop.emit(this, WixCommand.this, EventLoop.EVENT_MAINTENANCE);
            }
            @Override protected void failure(Device device, Failure failure, AuthData auth) {
                Shell.println(" failure="+failure);
                timer.clear();
                EventLoop.emit(this, WixCommand.this, EventLoop.EVENT_MAINTENANCE);
            }
        };
        WhoIsClient whoIsClient = new WhoIsClient() {
            @Override protected void success(Device device, Binding binding, AuthData auth) {
                queue.offer(binding);
                EventLoop.emit(this, WixCommand.this, EventLoop.EVENT_MAINTENANCE);
            }
        };
        @Override public boolean execute(String[] words) {
            // We want to execute a global Who-Is,
            // and then for each response, execute a ReadProperty for the Device object's name.
            whoIsClient.request(BACnetCommands.getSelectedDevice(),65535,null);
            EventLoop.addMaintenance(this);
            return false; // returning false indicates that this command is not "done"
        }
        @Override public void cancel() {
            EventLoop.removeMaintenance(this);
            whoIsClient.cancel(BACnetCommands.getSelectedDevice());
        }
    }

}
