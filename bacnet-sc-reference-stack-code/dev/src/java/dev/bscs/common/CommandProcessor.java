// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import dev.bscs.events.EventListener;
import dev.bscs.events.EventType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Listens for manual commands from the shell (running in another thread) and executes them in the main thread and
 * notifies the shell when they are done.  If the shell gives up waiting for a command to be "done", it will issue a
 * "cancel" event before issuing any other commands.
 * @author drobin
 */

public class CommandProcessor {

    static Log log = new Log(CommandProcessor.class);

    protected static List<Command>   commands = new ArrayList<>();
    protected static CommandListener listener = new CommandListener();
    protected static Command         outstandingCommand; // last command, if it doesn't indicate "done"

    public interface CommandSet { void addCommands(); } // used by Application to add commands specified by config files

    /**
     * Asynchronous command events coming from UserShell thread(s) get queued to the main EventLoop and are
     * processed here in the main thread.
     *
     * A SHELL_COMMAND command is further broken down into space-separated "words" before being handed to the command processor.
     *
     * When the command is finished, this method will notify the shell that the command has been processed so
     * it can issue another prompt to the user.  The shell can decide how long it wants to wait for this signal and
     * might timeout on its own to remain responsive to the user.
     *
     * If a command wants to wait for an external event, like a network response, it just returns from handleEvent()
     * but will not signal commandDone() to the shell so the shell waits, not prompting the user yet.
     *
     * If the shell times out or if the user hits initates cancel somehow, the shell will send a SHELL_COMMAND_CANCEL and
     * the active Command will get a cancel() call.
     *
     */
    private static class  CommandListener implements EventListener{
        @Override public void handleEvent(Object source, EventType eventType, Object... args) {
            try {
                if (eventType == Shell.SHELL_COMMAND) {
                    String command = (String) args[0];
                    String[] words;
                    // word splitting only supports "\ " at the moment, no quotes or other escapes
                    if (command.contains("\\")) {
                        words = command.replace("\\ ","\\s").split(" ");
                        for (int i = 0; i<words.length; i++) words[i] = words[i].replace("\\s"," ");
                    }
                    else words = command.split(" ");
                    boolean done = doCommand(words); // command will return false if it needs to wait for an external event
                    if (done) Shell.commandDone();   // if not done, don't release the shell (it is blocking on us)
                }
                else if (eventType == Shell.SHELL_COMMAND_CANCEL) {
                    if (outstandingCommand != null) {
                        outstandingCommand.cancel();
                        outstandingCommand = null;
                    }
                }
            }
            catch (Throwable t) {
                Shell.println("*** command misbehaved ***\n"+t);
                Shell.commandDone();
            }
        }
    }

    private static boolean doCommand(String[] words) {
        if (words.length == 0 || words[0].isEmpty()) return true;
        for (Command command : commands) {
            if (command.name.equals(words[0])) {
                boolean done = command.execute(words);
                outstandingCommand = done? null : command;
                return done;
            }
        }
        Shell.println("Command \""+words[0]+"\" not found");
        return true;
    }

    // this method returns an existing command with same name so applications can replace things like "quit"
    // ...use this power carefully!
    public static Command addCommand(Command command) {
        Command existing = removeCommand(command.name);
        commands.add(command);
        return existing;
    }

    // removes and returns command (or null if not found)
    public static Command removeCommand(String name) {
        Iterator<Command> iter = commands.iterator();
        while(iter.hasNext()) {
            Command cmd = iter.next();
            if (cmd.name.equals(name)) {
                iter.remove();
                return cmd;
            }
        }
        return null;
    }

}
