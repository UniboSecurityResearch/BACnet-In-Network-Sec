// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

/**
 * A extension of the standard {@link Properties} class that provides simple variable substitution and get-with-default methods.
 * The substitution syntax is ${variable-name} and can be included more than once on a line and will be recursively resolved.
 * For example, in following, baz will get the value "B-A-CAD-B"
 *    foo = A
 *    bar = C${foo}D
 *    baz = B-${foo}-${bar}-B
 * In addition to adding parsing for new data types like UUID, this also supports simple queries for what kind of operating system
 * the application is running on.
 * @author drobin
 */
public class Configuration extends Properties {

    public String fileName;

    private static Log log = new Log(Configuration.class);

    public Configuration() {
        super();
        fileName = "__empty__";
    }

    public Configuration(Configuration defaultProperties) {
        super(defaultProperties);
        fileName = "__empty__";
    }

    public void load(String fileName) throws IOException {
        this.fileName = fileName;
        load(new FileInputStream(fileName));
    }

    @Override public String  getProperty(String name) {
        String raw = super.getProperty(name);
        if (raw != null) raw = raw.trim();
        return raw != null && raw.contains("${")?  substitute(raw) : raw;
    }

    private String substitute(String raw) { return _substitute(raw,raw,0); }
    private String _substitute(String original, String s, int depth) {
        if (depth > 100) {
            log.configuration("Too much recursion in variable substitution of "+original);
            return "<error>";
        }
        try {  // split string into start${variable}end and replace variable with raw property and recurse
            String start = s.substring(0, s.indexOf("${"));
            String variableName = s.substring(s.indexOf("${") + 2, s.indexOf("}"));
            String end = s.substring(s.indexOf("}") + 1);
            String variableValue = super.getProperty(variableName); // don't use *our* getProperty here because it will not detect recursion
            String result = start + (variableValue!=null?variableValue:"") + end;
            return result.contains("${")? _substitute(original,result,depth+1) : result; // keep going as long as there are ${} left
        }
        catch (Exception e ) {
            log.configuration("Badly formed variable substitution in " + original + ": " + e.getLocalizedMessage());
            return "<error>";
        }
    }

    public long    getLong(String name, long defaultValue) {
        String property = getProperty(name);
        if ( property != null ) try { return Long.parseLong(property); }
        catch (NumberFormatException nfe) { log.configuration("Number format problem for \""+name+"\" in config file \""+fileName+"\"");}
        return defaultValue;
    }

    public int     getInteger(String name, int defaultValue) {
        String property = getProperty(name);
        if ( property != null ) try { return Integer.parseInt(property); }
        catch (NumberFormatException nfe) { log.configuration("Number format problem for \""+name+"\" in config file \""+fileName+"\"");}
        return defaultValue;
    }

    public byte[] getMAC(String name, int length, byte[] defaultValue) {
        byte[] mac = new byte[length];
        String property = getProperty(name);
        if ( property != null ) {
            try {
                if (property.contains(".")) {  // dotted decimal
                    String[] parts = property.split("\\.");
                    if (parts.length != length) log.configuration("Format problem for '" + name + "' in config file '" + fileName + "': not a valid dotted decimal MAC: " + property);
                    else for (int i = 0; i < length; i++) mac[i] = (byte) Integer.parseUnsignedInt(parts[i]);
                } else if (property.contains(":")) { // hex with colons
                    String[] parts = property.split(":");
                    if (parts.length != length) log.configuration("Format problem for '" + name + "' in config file '" + fileName + "': not a valid colon-separated hex MAC: " + property);
                    else for (int i = 0; i < length; i++) mac[i] = (byte) Integer.parseUnsignedInt(parts[i], 16);
                } else {  // straight hex
                    if (property.length() != length * 2) log.configuration("Format problem for '" + name + "' in config file '" + fileName + "': not a valid hex MAC: " + property);
                    else for (int i = 0; i < length; i++)
                        mac[i] = (byte) Integer.parseUnsignedInt(property.substring(i * 2, i * 2 + 2), 16);
                }
                return mac;
            } catch (Exception e) {
                log.configuration("Format problem for '" + name + "' in config file '" + fileName + "': "+e.getLocalizedMessage());
            }
        }
        return defaultValue;
    }

    public UUID getUUID(String name, UUID defaultValue) {
        String property = getProperty(name);
        if ( property != null ) try { return UUID.fromString(property); }
        catch (Exception e) { log.configuration("UUID format problem for \""+name+"\" in config file \""+fileName+"\"");}
        return defaultValue;
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        String property = getProperty(name);
        return ( property != null )? Boolean.parseBoolean(property) : defaultValue;
    }

    public String  getString(String name, String defaultValue) {
        return getProperty(name,defaultValue);
    }

    // some convenience methods with nowhere else to go (related to "configuration", so we'll leave them here)
    public static boolean isUnixBased() { return isLinux() || isSolaris() || isMacOS() || isBSD(); }
    public static boolean isWindows()   { return osName().contains("windows"); }
    public static boolean isMacOS()     { return osName().contains("mac"); }
    public static boolean isLinux()     { return osName().contains("linux"); }
    public static boolean isBSD()       { return osName().contains("bsd"); }
    public static boolean isSolaris()   { return osName().contains("solaris") || osName().contains("sunos");  }
    private static String osName()      { return System.getProperty("os.name").toLowerCase(); }

}

