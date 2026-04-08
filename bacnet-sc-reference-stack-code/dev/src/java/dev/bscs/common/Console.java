// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import java.util.Scanner;
import java.util.NoSuchElementException;

/**
 * A {@link Shell} that attempts to interleave logging and interactive commands in one stdio pair.
 * The console is normally an output for logging. So to get its attention for manual commands, the user hits enter to
 * stop logging and get a command prompt. At the prompt, the user enters a command line consisting of one or more
 * commands in a semicolon-separated list. If the command line *ends* with a semicolon, the user is prompted for another
 * line, otherwise logging resumes after the command(s) are processed.  During the pause, logging is just stopped; log
 * messages are NOT accumulated and barfed out after the resume.
 * TODO add a "cancel" capability like Terminal has for cancelling things like the wi and wh cammands.
 * @author drobin
 */
public class Console extends Shell {

    public Console(String title, String prompt) {
        super(title,prompt);

        new Thread(() -> {
            Scanner in = new Scanner(System.in);
            while(true) { // thread ends when application ends
                String commandLine;
                try {
                    commandLine = in.nextLine(); // blocking till user just hits enter to stop logging and get prompt.
                } catch (NoSuchElementException ignored) {
                    // Non-interactive execution (e.g., no stdin with --noterminals).
                    return;
                }
                // The java console handling is hosed. If any stdout happens while the user it typing to stdin the input line
                // buffer is cleared. So there is no consistent/reliable way for the user to type a command *while* there
                // is a possibility that a logging output will kill the input line. So all we can do is stop the logging
                // and prompt for input while the output is quiet.
                Log.pause();
                System.out.println("logging paused...");
                do {
                    System.out.print("$ ");
                    try {
                        commandLine = in.nextLine();  // blocking for command line
                    } catch (NoSuchElementException ignored) {
                        return;
                    }
                    internalIn(commandLine,true); // true = blocking
                } while (commandLine.endsWith(";")); // go back to logging unless the user ends line with ";"
                System.out.println("logging resumed...");
                Log.resume();
            }
        }).start();
    }

    @Override public void internalOut(String s)    { System.out.print(s); }
    @Override protected void internalCommandDone() {  } // we don't care because we uses a blocking internalIn()

}
