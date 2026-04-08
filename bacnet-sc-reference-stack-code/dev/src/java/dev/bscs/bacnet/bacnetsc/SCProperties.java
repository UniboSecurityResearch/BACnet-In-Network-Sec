// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.common.Configuration;

/**
 * The holder of all properties that govern the behavior af an SC datalink. This is a generic data object with all
 * public members, so populate it any way you want. For file-configured applications, there is convenience constructor
 * that reads its sc.xxx values from a Configuration object, which likely got its values from a *.properties file.
 * For embedded applications, the properties will come from somewhere else, like a Network Port object.
 * Almost every important class in the SC datalink just takes this properties bundle rather than taking a bunch of
 * individual values in its constructor. These properties are effectively read-only configuration properties that don't
 * change (therefore don't need to be checked for changes) while the datalink is running. For example, when {@link SCNode}
 * changes the {@link #vmac}, the datalink is restarted.
 * @author drobin
 */
public class SCProperties implements Cloneable  {

    public static final int DEFAULT_MAX_NPDU               = 1497;
    public static final int DEFAULT_MAX_BVLC               = 1600;

    // Proposed Network Port properties:
    public boolean    hubFunctionEnable              = false;
    public String     hubFunctionAcceptURIs          = "";  // space separated list of URIs
    public String     directConnectAcceptURIs        = "";  // space separated list of URIs
    public String     primaryHubURI                  = "";
    public String     failoverHubURI                 = "";
    public String     privateKey                     = "";
    public String     operationalCertificate         = "";
    public String     caCertificates                 = "";
    public int        maxNPDULengthAccepted          = DEFAULT_MAX_NPDU;
    public int        maxBVLCLengthAccepted          = DEFAULT_MAX_BVLC;
    public int        initiatingHeartbeatTimeout     = 300000;
    public int        acceptingHeartbeatTimeout      = 610000;
    public int        minimumReconnectTime           =   5000;
    public int        maximumReconnectTime           =  60000;
    public int        connectionWaitTimeout           =  4000;
    public int        disconnectWaitTimeout           =  2000;
    // "should be" in Network Port ?
    public String     hubFunctionBindURI              = "";  // host:port to bind to (path not supported)
    public String     directConnectBindURI            = "";  // host:port to bind to (path not supported)
    public boolean    directConnectEnable             =   true;
    public int        addressResolutionFreshnessLimit = 600000;   // time to re-evaluate address resolutions
    public int        addressResolutionTimeout        =   5000;   // maybe should just be a generic "bvlcResponseTimeout"
    public int        addressResolutionDelay          =  30000;   // this is akin to "minimum/maximumReconnectTime"
    // testing only (makes implementation non-standard)
    public boolean    nodeEnable                     = true;
    public String     tlsVersion                     = "TLSv1.3";
    public boolean    noValidation                   = false;
    public boolean    allowPlain                     = false;
    public boolean    allowYY422destination          = false; // YY.4.2.2 (as initially published) implied DC destination is optional
    // implementation specific
    public SCVMAC     vmac                           = SCVMAC.makeRandom(); // changeable at runtime
    public int        serverBindTimeout              =   2000; // time to wait for server to bind() to their assigned listening port
    public int        serverStartupTimeout           =  60000; // time to wait for hub function and node switch to bind and start
    public int        serverStartupDelay             =   2000; // delay after server starts before starting client threads
    public int        startupFailureRetryDelay       =  30000;
    public boolean    nakUnknownProprietaryFunctions = true;
    public boolean    logUnknownProprietaryFunctions = true;
    public String     policyClass                    = "";

    //////////// STATUS PROPERTIES /////////////

    public boolean    tlsError;
    public int        tlsErrorClass;
    public int        tlsErrorCode;
    public String     tlsErrorReason;

    // For file-based configuration, this constructor will get its values from a Configuration object, likely read from a file.
    public SCProperties(Configuration properties) {
        // Proposed Network Port properties
        hubFunctionEnable                 = properties.getBoolean("sc.hubFunctionEnable", hubFunctionEnable);
        hubFunctionAcceptURIs             = properties.getString( "sc.hubFunctionAcceptURIs", hubFunctionAcceptURIs);
        directConnectAcceptURIs           = properties.getString( "sc.directConnectAcceptURIs", directConnectAcceptURIs);
        primaryHubURI                     = properties.getString( "sc.primaryHubURI", primaryHubURI);
        failoverHubURI                    = properties.getString( "sc.failoverHubURI", failoverHubURI);
        privateKey                        = properties.getString( "sc.privateKey", privateKey);
        operationalCertificate            = properties.getString( "sc.operationalCertificate", operationalCertificate);
        caCertificates                    = properties.getString( "sc.caCertificates", caCertificates);
        maxNPDULengthAccepted             = properties.getInteger("sc.maxNPDULengthAccepted", maxNPDULengthAccepted);
        maxBVLCLengthAccepted             = properties.getInteger("sc.maxBVLCLengthAccepted", maxBVLCLengthAccepted);
        initiatingHeartbeatTimeout        = properties.getInteger("sc.initiatingHeartbeatTimeout", initiatingHeartbeatTimeout);
        acceptingHeartbeatTimeout         = properties.getInteger("sc.acceptingHeartbeatTimeout", acceptingHeartbeatTimeout);
        minimumReconnectTime              = properties.getInteger("sc.minimumReconnectTime",minimumReconnectTime);
        maximumReconnectTime              = properties.getInteger("sc.maximumReconnectTime",maximumReconnectTime);
        connectionWaitTimeout             = properties.getInteger("sc.connectionWaitTimeout",connectionWaitTimeout);
        disconnectWaitTimeout             = properties.getInteger("sc.disconnectWaitTimeout",disconnectWaitTimeout);
        // "should be" in Network Port ?
        hubFunctionBindURI                = properties.getString( "sc.hubFunctionBindURI", hubFunctionBindURI);
        directConnectBindURI              = properties.getString( "sc.directConnectBindURI", directConnectBindURI);
        directConnectEnable               = properties.getBoolean("sc.directConnectEnable", directConnectEnable);
        addressResolutionFreshnessLimit   = properties.getInteger("sc.addressResolutionFreshnessLimit",addressResolutionFreshnessLimit);
        addressResolutionTimeout          = properties.getInteger("sc.addressResolutionTimeout",addressResolutionTimeout);
        addressResolutionDelay            = properties.getInteger("sc.addressResolutionDelay",addressResolutionDelay);
        // testing only (makes implementation non-standard)
        nodeEnable                        = properties.getBoolean("sc.nodeEnable", nodeEnable); // for testing hubs w/o local node
        tlsVersion                        = properties.getString( "sc.tlsVersion",tlsVersion);
        noValidation                      = properties.getBoolean("sc.noValidation",noValidation);
        allowPlain                        = properties.getBoolean("sc.allowPlain",allowPlain);
        allowYY422destination             = properties.getBoolean("sc.allowYY422destination",allowYY422destination);
        // implementation specific
        vmac                              = new SCVMAC(properties.getMAC("sc.vmac",6,vmac.toBytes()));
        serverBindTimeout                 = properties.getInteger("sc.serverBindTimeout",serverBindTimeout);
        serverStartupTimeout              = properties.getInteger("sc.serverStartupTimeout",serverStartupTimeout);
        serverStartupDelay                = properties.getInteger("sc.serverStartupDelay",serverStartupDelay);
        startupFailureRetryDelay          = properties.getInteger("sc.startupFailureRetryDelay",startupFailureRetryDelay);
        nakUnknownProprietaryFunctions    = properties.getBoolean("sc.nakUnknownProprietaryFunctions",nakUnknownProprietaryFunctions);
        logUnknownProprietaryFunctions    = properties.getBoolean("sc.logUnknownProprietaryFunctions",logUnknownProprietaryFunctions);
        // plugable trust policies
        policyClass                       = properties.getString("sc.policyClass",policyClass);
    }


}
