// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.util.List;

/**
 * A set of common commands that are automatically installed by {@link Application} and shared by all applications.
 * @author drobin
 */

public class CommonCommands {

    private CommonCommands() {} // no constructor, static only

    protected static void addCommonCommands() { // global "built-in" commands for all applications
        CommandProcessor.addCommand(new CommonCommands.ListCommand());
        CommandProcessor.addCommand(new CommonCommands.HelpCommand());
        CommandProcessor.addCommand(new CommonCommands.ManCommand());
        CommandProcessor.addCommand(new CommonCommands.QuitCommand());
        CommandProcessor.addCommand(new CommonCommands.LogCommand());
        CommandProcessor.addCommand(new CommonCommands.SysCommand());
        CommandProcessor.addCommand(new CommonCommands.TerminalClosedCommand());
        CommandProcessor.addCommand(new CommonCommands.PyCommand());
    }

    public static final int LIST_SPACING = 12;
    public static final int LIST_WIDTH   = 80;

    public static class ListCommand extends Command {
        public ListCommand() { super("list","list",true,"Lists available commands"); }
        public boolean execute(String[] words) {
            // show the list in reverse order so new stuff is at the top
            int position = 1;
            for (int i = CommandProcessor.commands.size() ; i != 0; i--) {
                Command command = CommandProcessor.commands.get(i-1);
                if (!command.hidden) {
                    Shell.print(command.name);
                    for (int padding=command.name.length(); padding<LIST_SPACING; padding++) Shell.print(" ");
                    position += LIST_SPACING;
                    if (position > LIST_WIDTH) { Shell.println(); position = 1; }
                }
            }
            return true;
        }
    }

    public static class ManCommand extends Command {
        public ManCommand() { super("man","man <command>",true,"Gives description of given command"); }
        public boolean execute(String[] words) {
            if (words.length < 2) {
                Shell.println("Usage: "+synopsis);
                Shell.println("Use 'list' command for list of available commands "+synopsis);
            }
            else {
                String name = words[1];
                for (Command command : CommandProcessor.commands) {
                    if (command.name.equals(name) || name.equals("all")) {
                        Shell.println("NAME");
                        Shell.println("    "+command.name);
                        Shell.println();
                        Shell.println("SYNOPSIS");
                        Shell.println("    "+command.synopsis);
                        Shell.println();
                        if (command.description.length>0) {
                            Shell.println("DESCRIPTION");
                            for (String line : command.description) {
                                if (line.equals("OPTIONS")) { Shell.println(); Shell.println("OPTIONS"); }
                                else Shell.println("    " + line);
                            }
                            Shell.println();
                        }
                        if (name.equals("all")) Shell.println("======================================================="); // separator
                        else return true;
                    }
                }
                if (!name.equals("all")) Shell.println("Command '"+name+"' not found");
            }
            return true;
        }
    }

    public static class HelpCommand extends Command {
        public HelpCommand() { super("help","help [<command>]",true,"Gives help on help system or synopsis of single command"); }
        public boolean execute(String[] words) {
            if (words.length < 2) {
                Shell.println("Use 'list' for a list of available commands");
                Shell.println("Use 'help <command>' for synopsis of usage of a command");
                Shell.println("Use 'man <command>' for details of a command");
                return true;
            }
            String name = words[1];
            for (Command command : CommandProcessor.commands) {
                if (command.name.equals(name)) {
                    Shell.println("Usage: " + command.synopsis);
                    return true;
                }
            }
            Shell.println("Command '"+name+"' not found.");
            return true;
        }
    }

    public static class QuitCommand extends Command {
        public QuitCommand() { super("quit","quit [-r]","Shuts down the application, with optional restart request -r"); }
        public boolean execute(String[] words) {
            if (words.length>1) {
                if (words[1].equals("-r")) Application.shutdown("Manual \"quit -r\"",Application.EXIT_RESTART);
                else Shell.println("Usage: "+synopsis);
            }
            else Application.shutdown("Manual \"quit\"",Application.EXIT_NORMAL);
            return true;
        }
    }

    public static class TerminalClosedCommand extends Command {
        public TerminalClosedCommand() { super("_terminal_closed_","_terminal_closed_",true,"Shuts down the application"); }
        public boolean execute(String[] words) {
            Application.shutdown("Terminal window closed",Application.EXIT_NORMAL);
            return true;
        }
    }

    public static class LogCommand extends Command {
        private static Log log = new Log(LogCommand.class);
        public LogCommand() { super("log", "log [test] off|error|warn|info|debug|trace|all ",
                "Adjusts (or tests) the *global* logging threshold.",
                "If 'test' is specified, the command will emit a test message at the specified level.",
                "Otherwise, the command will set the global logging threshold level to the specified level."
        ); }
        public boolean execute(String[] words) {
            if (words.length<2 ) { Shell.println("Usage: "+synopsis); return true; }
            switch (words[1]) {
                case "off":   case "OFF":   Log.setLevel(Log.OFF);   break;
                case "error": case "ERROR": Log.setLevel(Log.ERROR); break;
                case "warn":  case "WARN": case "warning": case "WARNING": Log.setLevel(Log.WARN);  break;
                case "info":  case "INFO":  Log.setLevel(Log.INFO);  break;
                case "debug": case "DEBUG": Log.setLevel(Log.DEBUG); break;
                case "trace": case "TRACE": Log.setLevel(Log.TRACE); break;
                case "all":   case "ALL":   Log.setLevel(Log.ALL);   break;
                case "test":
                    if (words.length<3 ) { Shell.println("Usage: "+synopsis); return true; }
                    switch (words[2]) {
                        case "error": case "ERROR": log.error("Test message at level ERROR"); break;
                        case "warn":  case "WARN": case "warning": case "WARNING": log.warn("Test message at level WARN");   break;
                        case "info":  case "INFO":  log.info("Test message at level INFO");   break;
                        case "debug": case "DEBUG": log.debug("Test message at level DEBUG"); break;
                        case "trace": case "TRACE": log.trace("Test message at level TRACE"); break;
                        case "all":   case "ALL":   log.error("Test message at level ERROR");
                                                    log.warn("test message at level WARN");
                                                    log.info("test message at level INFO");
                                                    log.debug("test message at level DEBUG");
                                                    log.trace("test message at level TRACE"); break;
                    }
            }
            return true;
        }
    }

    public static class SysCommand extends Command {
        public SysCommand() { super("sys","sys stats|maint","Displays system statistics"); }
        public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            switch (words[1]) {
                case "stats":
                    Shell.println("EventQueueSize     "+EventLoop.statsEventQueueSize);
                    Shell.println("EventQueueSizeMax  "+EventLoop.statsEventQueueSizeMax);
                    Shell.println("MaintenanceSize    "+EventLoop.statsMaintenanceSize);
                    Shell.println("MaintenanceSizeMax "+EventLoop.statsMaintenanceSizeMax);
                    return true;
                case "maint":
                    List<EventListener> listeners = EventLoop.getMaintenanceListeners();
                    for (EventListener listener: listeners) Shell.println(listener.toString());
                    return true;
                default:
                    Shell.println("Usage: "+synopsis);
            }
            return true;
        }
    }

    private static class PyCommand extends Command {
        public PyCommand() { super("py","py reset | load <script-name> | run <script-name> | eval <expression>",
                "Python script operations.",
                "",
                "All operations execute in a common python environemt. Therefore all scripts will set, or overwrite, ",
                "global python defs and variables that will remain in the shared python environment until 'py reset' ",
                "is called. Therefore, the only real difference between 'py load' and 'py run' is whether the shell ",
                "re-prompts immediately or not.",
                "",
                "For scripts that define and install new commands, it is perfectly acceptable to reload the script ",
                "over and over for edit/run cycles. CommandProcessor.addCommand(...) will replace the old command with ",
                "the new one dynamically.",
                "",
                "Note that all 'py' command scripts run in the main thread and therefore must return immediately.",
                "i.e., do not use time.sleep() or any other blocking functions in the scripts.",
                "If you have long running operations, then break it up into small tasks and ",
                "use maintenance timer callbacks or some other event-driven mechanism.",
                "OPTIONS",
                "'py load <script>'     Executes the script and returns to the shell prompt immediately.",
                "                       Typically used to define commands and globals.",
                "'py run <script>'      Executes the script and waits for the script to finish before prompting.",
                "                       Scripts can call Shell.commandDone() to finish, or the user can interrupt.",
                "'py eval <expression>' Evaluates the expression and prints the result.",
                "'py reset'             Resets the python engine, clearing all globals and defs."
        ); }
        static ScriptEngine scriptEngine;
        @Override public boolean execute(String[] words) {
            if (scriptEngine == null) resetEngine();
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            switch (words[1]) {
                case "reset":
                    resetEngine();
                    return true;
                case "load":
                    if (words.length < 3) { Shell.println("Usage: "+synopsis); return true; }
                    String scriptName = words[2];
                    try (Reader reader = new FileReader("scripts/"+scriptName+".py")) { scriptEngine.eval(reader); }
                    catch (ScriptException e) { Shell.println("Script exception: "+e.getMessage()); }
                    catch (FileNotFoundException e) { Shell.println("Script file not found"); }
                    catch (IOException e) { Shell.println("IO error:" + e); }
                    return true;
                case "run":
                    if (words.length < 3) { Shell.println("Usage: "+synopsis); return true; }
                    scriptName = words[2];
                    try (Reader reader = new FileReader("scripts/"+scriptName+".py")) { scriptEngine.eval(reader); }
                    catch (ScriptException e) { Shell.println("Script exception: "+e.getMessage()); return true;}
                    catch (FileNotFoundException e) { Shell.println("Script file not found"); return true;}
                    catch (IOException e) { Shell.println("IO error:" + e); return true;}
                    return false;
                case "eval":
                    if (words.length < 3) { Shell.println("Usage: "+synopsis); return true; }
                    StringBuilder expression = new StringBuilder();
                    for (int i=2; i<words.length ; i++) expression.append(words[i]+" "); // yucky: put the split command arguments back together!
                    try {
                        StringReader reader = new StringReader(expression.toString());
                        Object result = scriptEngine.eval(reader);
                        if (result != null) Shell.println(result.toString());
                    }
                    catch (ScriptException e) { Shell.println("Script exception: "+e.getMessage()); }
                    return true;
                default:
                    Shell.println("Usage: "+synopsis);
                    return true;
            }
        }

        private void resetEngine()  {
            scriptEngine = new ScriptEngineManager().getEngineByName("python");
        }
    }

}
