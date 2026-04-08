// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import dev.bscs.events.EventLoop;
import dev.bscs.events.EventType;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The abstract concept of a manual interactive user shell, like a terminal window or a console or ssh session.
 * At the moment, this maintains a single static public shell that command handlers use to
 * present their results.
 *
 * Since commands usually execute in the main {@link EventLoop} thread and not in the thread of the Shell, this base class
 * provides a means to notify the Shell's thread when the main thread has finished processing the command so the user
 * can be prompted again.  See the known implementations {@link Terminal} and {@link Console}.
 *
 * @author drobin
 */
public abstract class Shell {

    static Log log = new Log(Shell.class);

    public static final EventType SHELL_COMMAND = new EventType("ShellCommand");
    public static final EventType SHELL_COMMAND_CANCEL = new EventType("ShellCommandCancel");

    // public methods for command processors to use
    public static void println(String format, Object... args) { println(String.format(format,args)); }
    public static void print(String format, Object... args)   { print(String.format(format,args));   }
    public static void println(String s)                      { print(s); println(); }
    public static void print(String s)                        { if (theShell!=null) theShell.internalOut(s);    else System.err.print(s); }
    public static void println()                              { if (theShell!=null) theShell.internalOut("\n"); else System.err.println();  }
    public static void setStatus(String s)                    { if (theShell!=null) theShell.internalSetStatus(s,Color.BLACK); }
    public static void setStatus(String s, Color c)           { if (theShell!=null) theShell.internalSetStatus(s,c); }
    public static void commandDone()                          { synchronized (commandDoneSem) { commandDoneSem.set(true); commandDoneSem.notify(); } }


    // internal things for shell implementations
    protected static Shell   theShell; // the one and only shell instance
    protected static String  title;
    protected static String  prompt;
    protected static final AtomicBoolean commandDoneSem = new AtomicBoolean(true); // commands are asynchronous, so this wait/notify indicates that command is finished and user can be prompted again

    protected Shell(String title, String prompt) {
        Shell.title  = title;
        Shell.prompt = prompt;
        Shell.theShell = this;
    }


    protected abstract void internalOut(String s);  // implemented by subclass to actually output something somewhere

    protected abstract void internalCommandDone(); // usually used to prompt again

    protected          void internalSetStatus(String s, Color c) { } // not abstract but must be overridden to do anything (i.e. Terminal has a status bar, console does not)

    protected          void internalIn(String commandLine, boolean blocking) { // called by subclasses to do something with user input line
        if (blocking) doCommandLine(commandLine);                     // if the caller wants blocking, then just call the blocking doCommand() directly
        else new Thread(()->{ doCommandLine(commandLine); }).start(); // otherwise launch a separate thread that will call us back with internalCommandDone with finished
    }

    protected          void cancel() {  // called to (politely) cancel an outstanding long-running command by sending it an event
        EventLoop.emit(Shell.class, CommandProcessor.listener, SHELL_COMMAND_CANCEL);
        commandDone();
    }

    //////////////////////////////

    private void doCommandLine(String commandLine) { // this BLOCKS until commandDone is called
        for (String command : commandLine.split(";")) {
            // since commands execute asynchronously on the main thread, we're not sure when the command processor is
            // finished to know when to prompt the user again. So this will wait up to 10 seconds for a command to say
            // it's done with Shell.commandDone()
            //
            // B L O C K I N G ...
            //
            synchronized (commandDoneSem) {
                commandDoneSem.set(false);
                EventLoop.emit(Shell.class, CommandProcessor.listener, SHELL_COMMAND, command);
                try { commandDoneSem.wait(10000L); }
                catch (InterruptedException ignore) { log.implementation("commandDone.wait() interrupted?");}
            }
            // if the command never completed and we timed out waiting for it, then send a cancel to indicate that
            // the shell has moved on and not to update with any "late" results.
            if (!commandDoneSem.get()) {
                cancel();
            }
        }
        internalCommandDone();
    }
}
