// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

/**
 * Interface for a command line "command" that executes from the interactive user {@link Shell}.
 * Commands are managed by the {@link CommandProcessor} which maintains a list of commands for the application.
 * Applications add their app-specific commands with {@link CommandProcessor#addCommand}.
 *
 * A command is executed by giving it array of "words", which are arguments that were split on space boundaries
 * from a command line entered in a Shell.  The command can finish immediately or can signal to the shell that it has
 * progressive output that will continue over time. A command that has indicated progressive output can be
 * cancelled by the user or by the shell's own timeout.
 *
 * Commands interface with the "help system" (such as it is) by providing a {@link #synopsis} and a {@link #description}
 * for the "list" and "help" commands to use.  Commands can chose to be "unlisted" from the help system by indicating
 * that they are {@link #hidden}
 *
 * Note that while commands are instance-based (you make an instance of a Command to submit to the CommandProcessor) the
 * command processing system itself is static and there is only one interactive shell and one list of commands for the
 * application.
 *
 * @author drobin
 */
public abstract class Command {
    public String   name;        // the name of the command (first word on command line)
    public String   synopsis;    // single line for the 'list' command, and "Usage: ..." scoldings
    public String[] description; // multi-line for the 'help' command
    public boolean  hidden;      // don't show in 'list'

    public Command(String name, String synopsis, String... description){
        this.name = name;
        this.synopsis = synopsis;
        this.description = description;
    }
    public Command(String name, String synopsis, boolean hidden, String... description){
        this(name,synopsis,description);
        this.hidden = hidden;
    }

    /**
     * Executes the command and indicates if its shell output is "done". This is non-blocking and should always return
     * as soon as possible, but if it has any long lasting operations, it can return false to indicate that it's
     * "not done".  A command that returns false will keep the shell at bay (for as long as the shell wants to wait)
     * waiting for the command to asynchronously call {@link Shell#commandDone}.  If the shell gives up waiting or the
     * user cancels somehow (shell dependent), then the shell will invoke (@link #cancel}.
     *
     * @returns true if its output is "done" or false if it wants to continue updating for a while
     */
    public abstract boolean execute(String[] words);

    /**
     * Cancels an outstanding command whose {@link #execute} method indicated that it was "not done". This method should
     * be implemented to prevent further shell output because the user has already been prompted again and the shell has
     * "moved on".
     *
     **/
    public          void    cancel() {}


}
