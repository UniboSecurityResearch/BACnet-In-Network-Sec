// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import dev.bscs.events.EventLoop;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * A base class for simple data-driven applications with optional user shell interactions.
 *
 * It is driven by a configuration file (xxx.properties) which contains, at the very least, the name of the real
 * application class to load. The configuration file must have an "common.mainClass" property containing the full
 * name of an Application subclass to instantiate.  e.g., app.mainClass = org.foo.myapps.MyApplication
 *
 * If the application is defined by a Python Script, then the configuration item "common.mainScript" specifies the
 * script file and the "common.mainClass" specifies the Python class to instantiate. That class must extend this class.
 *
 * The configuration file can be givan as the first command line argument.  Files paths are evaluated relative the the
 * current working directory.  If no file name is provided, this will search the current directory for *.properties
 * files and pop up a GUI picker. Other command line arguments can be processed later by subclasses.
 *
 * Example subclass:
 *
 * public class MyClass extends Application {
 *   // these methods are called in this order:
 *   void startShell() {}                      // optional
 *   void parseArgs(String[] args) {}          // optional
 *   void start() { put something here! }      // required
 *   void run() { super.run(); }               // not recommended to override
 *   // additionally, these could be called during the course of operation
 *   void stop() {}                            // optional
 *   void close() {}                           // recommended
 *   void command(String[] words)              // optional
 * }
 *
 * Each of these methods is described below. None of these methods are declared to throw exceptions and any runtime
 * exceptions will be considered fatal.  There is no "respawn" wrapper process.
 *
 *  ==== loadConfiguration ====
 *
 *  Reporting configuration errors can be a bit of a chicken-and-egg problem because the method to report the
 *  errors can be based on the command line arguments.  Therefore, this Application base class does this in three phases:
 *
 *      loadConfiguration() // method internal to this class that only looks at arg[0]
 *      startShell()        // subclass method to start the terminal/console/logs/etc based on args or configuration
 *      parseArgs()         // now you can parse the rest of the args and report errors to the shell
 *
 *  The loadConfiguration() here will look at arg[0] as a filename to load a properties file.
 *
 *  ==== startShell ====
 *
 *  A user shell is optional, and a subclass is free to override this method to do nothing. The default behavior is
 *  to look at the configuration property "common.shell". If that property is absent or equal to "terminal", then a
 *  GUI window will be launched for user interaction. If the property is "console" then stdin/stdout is used
 *  and manual commands are interleaved with logging output and logging will pause while commands are being entered.
 *  Command lines can consist of multiple commands separated with semicolons. Command lines ending
 *  with a semicolon will keep the logging paused and prompt for another command. Otherwise the logging will resume
 *  after the command is executed.
 *
 *  ==== parseArgs ====
 *
 *  The default behavior is to do nothing, and it is likely that this will not need to be overridden because it is
 *  anticipated that most things are driven by the configuration files.
 *
 * ==== addCommands ====
 *
 *  If the application provides a user shell and wants to provide processing of manual commands other than "quit",
 *  then it should override this method.
 *
 *  It is important to note that these commands run in the main thread, not in the thread of the shell. The base Shell
 *  and CommandProcessor classes provide a method to queue commands from the shell thread to be easily processed
 *  by the main thread in Command objects added by this method. This has advantage that command processing is absolutely
 *  free to call other main-thread methods without worry of synchronization issues. If you don't add any commands, the
 *  default set includes common commands like "quit" that will call shutdown() which calls close() and exit().
 *
 *  It *is* possible to replace an existing command, so the CommandProcessor.addCommand() will return the existing
 *  Command for possible chaining.
 *
 *  ==== start ====
 *
 *  Obviously the most important method, and *must* be provided. In fact, this base class will die() if this method
 *  is not overridden.  Since applications are assumed to be based on EventLoop, this method must return so that the
 *  main thread can be used to start the event loop processing.
 *
 *  It is expected that subclasses will create the initial business logic objects and fire off an initial Event to
 *  one of them, or register something with EventLoop.onMaintenance(). Don't take too long here because if you start
 *  things that generate events, the events will all just be queued up because the event loop is not running yet.
 *  So you generally just fire some initial event(s) and then start other stuff once the normal event loop is running.
 *
 *  ==== run ====
 *
 *  Don't mess with this unless you want to completely replace EventLoop (really? why are you using this framework?)
 *  This method is not expected to return until the application is shut down. The base method simply calls
 *  EventLoop.run(...) and there is nothing after than except System.exit().
 *
 *  There are two configuration properties that affect how the EventLoop runs:
 *    "common.maintenanceInterval" will determine how often (ms) maintenance events are fired. Default is one second (1000).
 *    Maintenance events are usually used to "groom" lists of dead things or check state timers that are usually on the
 *    order of seconds.
 *    "common.haltOnOverflow" will cause the application to fatal exit if the event queue overflows as opposed to
 *    throwing away events. Possibly useful for testing to gauge stress, but is false by default.
 *
 *  ==== stop ====
 *
 *  If your application wants to gracefully stop operations, e.g., politely disconnecting client connections, etc.,
 *  based on some kind of application-defined signal to do so, then it can override this method. However, there is
 *  nothing in the base framework that calls stop() or knows how to wait for it to complete its graceful cessation.
 *
 *  This method is provided as a central place for such functionality, but the default behavior does not use it.
 *  For example the shutdown() method simply calls close() because it doesn't know how to "wait" for the actions
 *  initiated by stop() to complete. This method should return immediately after queuing up some asynchronous
 *  actions to take place.
 *
 *  ==== close ====
 *
 *  It is recommended that applications implement this to preserve integrity of external resources. This is a
 *  synchronous/immediate method (no Events). The application should close databases, ports, files, etc.
 *  synchronously before returning from close() because the system will likely exit() immediately thereafter.
 *
 *  @author drobin
 */
public class Application {

    private   static Log            log = new Log(Application.class);

    public static final String versionPropertiesFileName = "config/sys/Version.properties";
    public static final String systemPropertiesFileName  = "config/sys/System.properties";
    public static final String commonPropertiesFileName  = "config/Common.properties";

    private   static Application    application;

    public    static Configuration  version       = new Configuration();
    public    static Configuration  system        = new Configuration(version);
    public    static Configuration  common        = new Configuration(system);
    public    static Configuration  configuration = new Configuration(common);

    private   static class          UsageException extends Exception { UsageException(String s){super(s);}}
    private   static boolean        exitOnShutdown = true;
    private   static int            exitCode = 0;
    private   static CommandProcessor.CommandSet configuredCommands;
    private   static ScriptEngine   scriptEngine = null;

    public static final int EXIT_NORMAL  = 0;
    public static final int EXIT_ERROR   = 1;
    public static final int EXIT_RESTART = 2;

    public static void main(String[] args) {
        try {
            // Everything is driven by the static 'configuration' member, including which Application subclass to
            // instantiate. But first we need a file to load it from.
            // So check if it's given on the command line, else choose from *.properties files in ./config
            String configurationFileName = (args.length >= 1)?  args[0] : getConfigurationFileName();

            // the properties files have nested defaults as: <app> --> _Common --> _System --> _Version
            try { version.load(versionPropertiesFileName); }
            catch (IOException e) { throw new UsageException("Could not load required configuration file \"./" + versionPropertiesFileName + "\"\n" + e + ": " + e.getLocalizedMessage()); }
            try { system.load(systemPropertiesFileName); }
            catch (IOException e) { throw new UsageException("Could not load required configuration file \"./" + systemPropertiesFileName + "\"\n" + e + ": " + e.getLocalizedMessage()); }
            try { configuration.load(commonPropertiesFileName); }
            catch (IOException e) { throw new UsageException("Could not load required configuration file \"./" + commonPropertiesFileName + "\"\n" + e + ": " + e.getLocalizedMessage()); }
            try { configuration.load(configurationFileName); }
            catch (IOException e) { throw new UsageException("Could not load required configuration file \"./" + configurationFileName + "\"\n" + e + ": " + e.getLocalizedMessage()); }

            // As soon as possible, set up the preferred logging configuration. Of course, some messages may have already
            // logged to the default configuration with complaints about the Configuration instance itself.
            Log.configure(configuration);

            // So now we have a configuration, find out what main class we're supposed to load
            String mainClass = configuration.getString("common.mainClass", null);
            if (mainClass == null) {
                throw new UsageException(
                        "No main class specified in configuration file.\n" +
                        "You must provide a fully qualified class name in \n" +
                        "the \"common.mainClass\" property. For Example: \n" +
                        "common.mainClass = dev.bscs.applications.MyApp");
            }

            Application instance = null;

            // if mainClass is a file path, then it's a script - only python supported at the moment
            String mainScript = configuration.getString("common.mainScript", null);
            if (mainScript != null) {
                scriptEngine = new ScriptEngineManager().getEngineByName("python");
                if (scriptEngine == null) { throw new UsageException("The python engine is not available. Is the jython jar in the class path?"); }
                try (Reader reader = new FileReader(mainScript)) { scriptEngine.eval(reader); }
                catch (ScriptException e) { throw new UsageException("The script specified by common.mainScript contains an error.\n" + e.getMessage()); }
                catch (FileNotFoundException e) { throw new UsageException("The script specified by common.mainScript was not found.\n"); }
                catch (IOException e) { throw new UsageException("The script file specified by common.mainScript could not be read.\n"+ e.getMessage()); }
                // OK, the script read in OK, now lets make a new instance
                Object result = scriptEngine.eval(mainClass+"()"); // make an instance of the main class using the Python constructor syntax
                if (result == null) throw new UsageException("The Python class specified by common.mainClass could not be created by script specified by common.mainScript.\n");
                if (!(result instanceof Application)) throw new UsageException("The Python class specified by common.mainClass is not a subclass of Application.\n");
                instance = (Application)result;
            }
            else {
                // Now, cross your fingers really hard and see if we can find that class...
                Class<Application> clazz;
                try {
                    clazz = (Class<Application>)Class.forName(mainClass);
                } catch (ClassNotFoundException e) {
                    throw new UsageException(
                            "The main class specified in the configuration was not found.\n" +
                                    "You must provide a fully qualified class name in \n" +
                                    "the \"common.mainClass\" property. For Example: \n" +
                                    "     common.mainClass = dev.bscs.bacnet.applications.MyApp\n" +
                                    "This class must be found in the java classpath)\n");
                }
                // Almost there... make an instance of what is hopefully a subclass of Application
                try {
                    instance = clazz.getDeclaredConstructor().newInstance();
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
                    throw new UsageException(
                            "The main class specified in the configuration file \n" +
                                    "could not be instantiated\n" + e + ":" + e.getLocalizedMessage() + "\n");
                }
            }

            setDebugOptions();
            CommonCommands.addCommonCommands();

            // now, the application configuration has the option of providing a class containing extra commands.
            String commandClass = configuration.getString("common.commandClass", null);
            Class<CommandProcessor.CommandSet> commandSetClass = null;
            if (commandClass != null) {
                try {
                    commandSetClass = (Class<CommandProcessor.CommandSet>)Class.forName(commandClass);
                } catch (ClassNotFoundException e) {
                    throw new UsageException(
                            "The command class specified in the configuration was not found.\n" +
                                    "You must provide a fully qualified class name in \n" +
                                    "the \"common.commandClass\" property. For Example: \n" +
                                    "     common.commandClass = com.example.testapps.MyCommands\n" +
                                    "This class must be found in the java classpath)\n");
                }
                // Almost there... make an instance of what is hopefully a subclass of Application
                try {
                    configuredCommands = commandSetClass.getDeclaredConstructor().newInstance();
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
                    throw new UsageException(
                            "The command class specified in the configuration file \n" +
                            "could not be instantiated\n" + e + ":" + e.getLocalizedMessage() + "\n");
                }
                // we *don't* call commandSetInstance.addCommands() here, because we want to commands to be added *after*
                // the mainClass adds theirs.  This is just to be nice so they show up first in the 'list' command.
            }

            // Now here's the key... this doMain() is called from the *subclass* instance.
            // If the subclass *really* wants to override all of the main() above, it can provide its own static main()
            // and just call doMain on itself! This is why it appears strange here that we are passing our own
            // configuration to ourselves, but that's because the subclass *could* provide its own main() and load its
            // own configuration and then call doMain() with that configuration. By default though, we hand the configuration
            // to the subclass here it it hands it right back, since it normally doesn't override either main() or doMain().
            instance.doMain(configuration,args);
        }
        catch (UsageException u) {
            Shell.println(u.getMessage());
            System.out.println(u.getMessage());
            System.exit(EXIT_ERROR);
        }
        catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String epitaph = "\nFatal error: "+t.getMessage()+ "\n" + sw.toString()+ "\n"+"Shell is closed. No more interactions here.\n";
            Shell.println(epitaph);
            try { Thread.sleep(500); } catch (InterruptedException ignore){} // delay so outputs don't collide
            log.implementation(epitaph);
            try { Thread.sleep(500); } catch (InterruptedException ignore){} // delay so outputs don't collide
            System.err.println(epitaph);
            EventLoop.stop();
            System.exit(EXIT_ERROR);
        }
    }

    /**
     * doMain() runs the application through its phases. See write-up at top.
     * It looks like it's called directly from main() above, but it's actually called via the subclass instance.
     * This is so methods like parseArgs(), start(), etc., are defined by the subclass.
     * The only thing the main() in this base class does is load the configuration and instantiate the subclass.
     * If a subclass wants to do all that itself, it can just provide its own static main() and call doMain() on itself.
     * @param configuration provided by the subclass or passed through the subclass from the main() in this class
     * @param args          same args as in main(args)
     */
    private void doMain(Configuration configuration, String[] args) {
        if (Application.application != null) { log.implementation("Can't start two copies of Application in one VM"); return; }
        Application.application = this;            // static reference to the singleton Application subclass instance
        Application.configuration = configuration; // this *might* be provided by a subclass calling doMain() directly
        // see write-up at top of this file for description of each of these
        startShell();
        addCommands();
        parseArgs(args);
        if (configuredCommands != null) configuredCommands.addCommands();
        start();
        run();
        if (exitOnShutdown) System.exit(exitCode);
    }

    protected static void setDebugOptions() { // subclasses *could* override this if really needed
        String javaxNetDebug = configuration.getString("common.tlsDebug","");
        if (!javaxNetDebug.isEmpty()) System.setProperty("javax.net.debug",javaxNetDebug);
    }

    /**
     * shutdown() is public, so anyone can call it, but it's likely only for fatal configuration things or manual "quit"
     * @param reason String to report as the cause
     * @param code number to report as the process exit code. Generally, 0=normal, 1=fatal-error, 2=restart-request
     */
    public static void  shutdown(String reason, int code) {
        exitCode = code;
        shutdown(reason,true);
    }
    /**
     * shutdown() is public, so anyone can call it, but it's likely only for fatal configuration things or manual "quit"
     * @param reason String to report as the cause
     * @param exit if true, it will call System.exit() closing any GUI windows - be sure that's what you want
     */
    public static void  shutdown(String reason, boolean exit) {
        // public: anyone can call this in case of emergency
        log.info("Application shutting down because: "+reason);
        Shell.println("Application shutting down because: "+reason);
        application.close();
        exitOnShutdown = exit;
        EventLoop.stop();
    }

    ////////////////////////////////////////////////////////////////
    /////////////// Methods overridden by subclasses ///////////////
    ////////////////////////////////////////////////////////////////

    /**
     * This is probably *not* going to be overridden by a subclass. If someone writes a really cool shell (like a
     * built-in ssh server), then it should come here as a standard option for all applications to be able to use!
     */
    protected void startShell() {
        if (configuration.getString("common.shell","terminal").equals("terminal")) {
            String title = configuration.getString("common.title",application.getClass().getSimpleName());
            new Terminal(title, "$ ",10,10,400,900);
        } else {
            new Console(application.getClass().getSimpleName(), "$ ");
        }
    }

    /**
     * Probably not overridden by subclass because most configuration is in the properties file. But if an application
     * wants to *also* support some command line options, then this is the place to do it.
     * @param args the same ones from main()
     */
    protected void parseArgs(String[] args)  { }

    /**
     * Overridden by applications that want to add commands beyond the common quit/log/sys stuff.
     */
    protected void addCommands() { }

    /**
     * Subclasses MUST at least override this one method to have a meaning application. See write-up at top.
     */
    protected void start() { log.implementation("No application-specific start() provided. Nothing to do!"); }

    /**
     * Don't override this unless you really need to.  It starts the event loop, the heartbeat of this whole framework.
     */
    protected void run() { // highly unlikely to override
        int     maintenanceInterval = configuration.getInteger("common.maintenanceInterval",1000);
        boolean haltOnOverflow      = configuration.getBoolean("common.haltOnOverflow",false);
        EventLoop.run(maintenanceInterval,haltOnOverflow);
    }

    /**
     * This is completely application-specific and has no meaning by default.  See write-up at top.
     */
    protected void stop()  { }

    /**
     * This is RECOMMENDED to override if there are any resources that need to be closed properly on shutdown.
     */
    protected void close() { }


    ////////////////////////////////////////////////////////////////
    /////////////////////// Private helpers ////////////////////////
    ////////////////////////////////////////////////////////////////

    private static String getConfigurationFileName() throws UsageException {
        List<String> names = new ArrayList<>();
        // collect a list of all *.properties files in the "./config" subdir
        File configDir = new File("config");
        if (!configDir.isDirectory()) throw new UsageException("No \"config\" subdirectory found. Are you running in the right working directory?");
        for (String name : configDir.list()) if (name.endsWith(".properties")) names.add(name.substring(0, name.indexOf(".properties")));
        // if there are *no* properties files found, then complain
        if (names.size() == 0) throw new UsageException(
                "No configuration file specified on command line and none found in \"./config\".\n" +
                "You must provide a configuration file (xxx.properties) as the first argument\n" +
                "on the command line or have some in a \"config\" subdirectory to pick from.");
        // otherwise, let the user select one
        Chooser chooser = new Chooser("Choose configuration",10,10,200,600,names);
        synchronized (chooser.done) { try { chooser.done.wait(); } catch (InterruptedException ignore) { } }
        return "config/"+chooser.result+".properties";
    }

    private static class Chooser implements ActionListener {
        private JFrame      frame;
        public String       result;
        public final Object done = new Object();
        public Chooser(String title, int xLocation, int yLocation, int height, int width, List<String> strings) {
            frame = new JFrame(title);
            frame.setLayout(new FlowLayout());
            for (String string : strings) {
                Button button = new Button(string);
                button.addActionListener(this);
                frame.add(button);
            }
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    frame.setBounds(xLocation, yLocation, width, height);
                    frame.setVisible(true);
                }
            });
        }
        public void actionPerformed(ActionEvent e) {
            result = e.getActionCommand();
            synchronized (done) { done.notify(); }
            frame.dispose();
        }
    }
}
