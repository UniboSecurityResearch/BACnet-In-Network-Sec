// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.ReceiverBase;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * An extension of the SLF4J {@link Logger} that provides some additions like "implementation" and "configuration" to the
 * basic "error" and "warn" methods.
 *
 * DEPENDENCY: Note that this currently has some hard-coded dependency on the "logback" logging backend because it takes
 * advantage of several operations that are not generalized through the SLF4j facade.  If another backend is needed,
 * these methods will have to be emulated. For simplicity/sanity, this is a open source editing operation, and doesn't
 * try to be "pluggable".
 *
 * @author drobin
 */
public class Log  {

    private static Log log = new Log(Log.class); // an instance of ourselves for our *own* log messages

    public static final int ALL   = 0;
    public static final int TRACE = 1;
    public static final int DEBUG = 2;
    public static final int INFO  = 3;
    public static final int WARN  = 4;
    public static final int ERROR = 5;
    public static final int OFF   = 6;

    public Log(Class clazz) { logger = LoggerFactory.getLogger(clazz); }

    private Logger logger;  // SFL4J is a back-end-independent facade

    private static boolean pause;
    public static void pause()  { pause = true; }
    public static void resume() { pause = false; }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // DEPENDENCY: ch.qos.logback
    // The remainder of this file contains dependencies on the logback framework.  It is just too powerful to resist.
    // So if another logging framework is needed, then the following methods will need workarounds/replacements.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   // this sets the location of the configuration file before any loggers are created
    static {
        if (System.getProperty("logback.configurationFile") == null) {
            if (new File("logback.xml").exists()) System.setProperty("logback.configurationFile", "logback.xml");
            else if (new File("config/logback.xml").exists()) System.setProperty("logback.configurationFile", "config/logback.xml");
            //else System.err.println("LOGGING ERROR: Can't find logging configuration file logback.xml in current working directory or in \"config\" subdirectory");
        }
    }

    public static void setLevel(int level) {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        switch (level) {
            case OFF:   rootLogger.setLevel(Level.OFF);   break;
            case ERROR: rootLogger.setLevel(Level.ERROR); break;
            case WARN:  rootLogger.setLevel(Level.WARN);  break;
            case INFO:  rootLogger.setLevel(Level.INFO);  break;
            case DEBUG: rootLogger.setLevel(Level.DEBUG); break;
            case TRACE: rootLogger.setLevel(Level.TRACE); break;
            case ALL:   rootLogger.setLevel(Level.ALL);   break;
        }
    }

    public  void   trace(String s)                                        { if (!pause) logger.trace(s); }
    public  void   trace(String name, String message)                     { if (!pause) logger.trace(name+": " + message); }
    public  void   debug(String s)                                        { if (!pause) logger.debug(s); }
    public  void   debug(String name, String message)                     { if (!pause) logger.debug(name+": " + message); }
    public  void   info(String s)                                         { if (!pause) logger.info(s); }
    public  void   info(String name, String message)                      { if (!pause) logger.info(name+": " + message); }
    public  void   warn(String s)                                         { if (!pause) logger.warn(s); }
    public  void   warn(String name, String message)                      { if (!pause) logger.warn(name+": " + message); }
    public  void   error(String s)                                        { if (!pause) logger.error(s); }
    public  void   error(String name, String message)                     { if (!pause) logger.error(name+": " + message); }
    public  void   error(String s, Throwable e)                           { if (!pause) logger.error(s+": "+e+": "+e.getMessage()); }
    public  void   configuration(String s)                                { if (!pause) logger.error("CONFIGURATION: "+s); }
    public  void   configuration(String name, String message)             { if (!pause) logger.error("CONFIGURATION: "+name+": " + message); }
    public  void   protocol(String s)                                     { if (!pause) logger.error("PROTOCOL: "+s); }
    public  void   protocol(String name, String message)                  { if (!pause) logger.error("PROTOCOL: "+name+": " + message); }
    public  void   implementation(String s)                               { if (!pause) logger.error("IMPLEMENTATION: "+s); }
    public  void   implementation(String name, String message)            { if (!pause) logger.error("IMPLEMENTATION: "+name+": " + message); }

    public static void configure(Configuration configuration) {
        String file = configuration.getString("log.config", "config/logback.xml");
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            ContextInitializer ci = new ContextInitializer(loggerContext);
            URL url = new File(file).toURI().toURL();
            loggerContext.reset();
            ci.configureByResource(url);
            log.debug("Loaded logging configuration from "+file);
        }
        catch (MalformedURLException e) { System.err.println("ERROR: cannot load logger configuration from file '"+file+"'"); }
        catch (JoranException e) { System.err.println("ERROR: cannot configure logger from file '"+file+"': "+e.getLocalizedMessage()); }
    }

    public static void addAppender(Appender<ILoggingEvent> appender) {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        appender.setContext(context);
        rootLogger.addAppender(appender);
    }

    public static void addReceiver(ReceiverBase receiver) {
        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        receiver.setContext(context);
        context.register(receiver);
    }

    public static int  levelFromString(String s) {
        switch (s) {
            case "off":   case "OFF":   return OFF;
            case "error": case "ERROR": return ERROR;
            case "warn":  case "WARN": case "warning": case "WARNING": return WARN;
            case "info":  case "INFO":  return INFO;
            case "debug": case "DEBUG": return DEBUG;
            case "trace": case "TRACE": return TRACE;
            case "all":   case "ALL":   return ALL;
            default:                    return ALL; // really no good answer here - depends on caller's usage
        }
    }
}
