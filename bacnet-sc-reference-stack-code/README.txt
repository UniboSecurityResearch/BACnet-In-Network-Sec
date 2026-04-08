======== 3.1 - August 12, 2021 - BACnet/SC Reference Implementation ========

The project is an implementation of the BACnet/SC (BACnet Secure Connect) 
protocol as defined in Annex AB of ANSI/ASHRAE Standard 135-2020, as well as 
some functions to manage keys and certificates. All aspects of the project are 
fully supported on Windows, Linux and Mac.

In addition to the complete source code, this distribution also contains 
several pre-built applications.  The included applications are: 

- TestHub – a hub that can be used as either primary or failover.

- TestHubs – a single application that consists of two independent hubs. Manual 
commands are provided to separately take the primary or failover hubs down or 
up to simplify testing of an external node’s failover mechanism (the hubs’ 
internal nodes can be turned off to simplify the logs).

- TestNode – a single node device.

- TestNodes – a single application that can create hundreds of nodes for 
scalability testing. Manual commands are provided to show individual or 
summarized connection status of the nodes.

- TestRouter – an IP-to-SC router.

- TestRouters – a single application consisting of a pair of independent 
routers configured for IP-to-SC-to-IP routing.

- TestCombo – combines features of TestRouter, TestHubs, and TestNodes.

- TestSCSC – Router between BACnet/SC and BACnet/SC.

Configuration of all addresses and behavior options is done with text-based 
property files. SC datalinks can be individually configured to accept direct 
connections, to use TLS 1.2, 1.3, or plain WebSockets, and to ignore 
certificate validation. Logging uses the “logback” framework, and the logging 
destination and format can be configured with an xml file.

Keys and certificates can be either external PEM files or represented as hex 
strings in the properties file. Shell scripts are provided to create and 
manage root, intermediate, signing, and operational certificate chains.

Manual interactions are either via a pop-up terminal window, with command 
history and editing, or via the stdin/stdout console. The set of manual 
commands for all devices includes basic operations like Who-Is and 
ReadProperty, as well as SC-specific commands to initiate direct connections or 
show connection status.

================================ Running =====================================

This distribution comes pre-built and ready to run on Windows, Linux, and Mac. 
The main application is written in Java and this distribution package includes 
a freely redistributable (OpenJDK) embedded java runtime, so that download and 
separate installation of java is not required. In the bin folder are three zip 
files containing the runtime support for the three supported platforms. The 
launch script or batch file will automatically unzip the correct one.

On Windows, you can just double click on the 'Application.cmd' file in Windows 
File Explorer to launch it in a new terminal window.  The batch file will 
change directory to itself so it can be run from anywhere.

On Linux and Mac, you should launch the 'Application' bash script from a 
terminal window, either with bash like this:  

    $ bash ./Application

Or make the file executable with: 

    $ chmod +x Application

And run as: 

    $ ./Application

The script will change directory to itself so it can be run from anywhere.

You should always run from a command/terminal window because the logging goes 
to the console by default. This can be changed if desired.  See "Logging" 
below. 

Launching "Application" this way will pop up a dialog box that lets you choose 
an example app to run.  The list of choices is populated from the available 
*.properties in the 'config' subdirectory.

If you want to run an example app directly, bypassing the "choose" dialog, just 
specify the location of the properties file on the command line.  e.g., 

    $ ./Application config/TestHub.properties

    C:\BACnetSC>.\Application.cmd config\TestHub.properties

Using this method, the .properties file can be located anywhere, but keep in 
mind that relative file references within the .properties file are still 
evaluated relative to the project root, not to the .properties file location.

There are also versions of these launchers that install the patch to enable 
WireShark key file export.  They are called ApplicationWS and ApplicationWS.cmd. 
These launchers are for decoding TLS in WireShark on the same machine as the Application.
They use a key extractor library that writes to a file "ssl.log" in the current directory.

============================= Configuring ==================================

By default, these examples are configured to use 127.0.0.1 for SC connections, 
so they work among themselves, so access to external IP addresses, for IP 
routers or external SC devices, will need to be configured.

In the 'config' subdirectory are .properties files corresponding to each of the 
examples apps.  These .properties files support a simple form of variable 
substitution using ${varname}, so common "testing environment" settings like 
IP address are set at the top of the file for convenience and can then be 
referenced in several places below.

For example, the TestHub.properties file looks like this:

   x-sc-host = 127.0.0.1
   ...
   sc.primaryHubURI      = wss://${x-sc-host}:4443
   sc.failoverHubURI     = wss://${x-sc-host}:4444
   sc.hubFunctionEnable  = true
   sc.hubFunctionBindURI = wss://${x-sc-host}:4443
   ...

The TestRouter example needs real IP addresses, and given that most test 
machines have more than one address, the implementation doesn't attempt to 
"guess" which one to use.  Note also that things like routers will need other 
things configured like network numbers.  These are done using "app.xxx" 
properties. 

   x-sc-host = 127.0.0.1
   x-ip-host = 192.168.1.222
   ...
   app.ipNetworkNumber = 5501
   app.scNetworkNumber = 5502
   ...
   ip.bindAddress      = ${x-ip-host}
   ip.bindPort         = 47808

Device object properties, like the all important device instance, are configured 
with device.xxx properties:

   device.instance    = 555001
   device.uuid        = 67258ee1-a98c-44f2-8c4a-111111111111

As you can start to see, the properties are divided into families: app.xxx, 
ip.xxx, sc.xxx, and device.xxx.  

The device.xxx properties are used to set the properties of the Device object 
and are named to match their BACnet property names, e.g., device.modelName. 
These are the same on all apps and are consumed by DeviceObjectProperties.java.

The sc.xxx names, where possible, match the proposed Network Port property 
names. These properties are the same in all apps and are consumed by 
SCProperties.java

The app.xxx properties are all "application-specific" and are defined by the 
top level app to match its functionality, like 'app.numberOfNodes=100' for 
the TestNodes app. Sometimes this is needed when there are more than one of 
something and the app needs to manage the second set. 

For example, TestHubs has two devices in it, so it uses app.xxx level 
properties to define the second set, and copies those to the like named 
properties in the failover hub.

   # These "normal" properties are for the primary hub device
   device.instance                    = 555001
   device.uuid                        = 67258ee1-a98c-44f2-8c4a-111111111111
   sc.vmac                            = 111111111111
   sc.hubFunctionBindURI              = wss://${x-sc-host}:4443

   # And these "app" level properties are copied to the failover hub device
   app.failover.device.instance       = 555002
   app.failover.device.uuid           = 4e7b1094-93ad-4d73-924a-222222222222
   app.failover.sc.vmac               = 222222222222
   app.failover.sc.hubFunctionBindURI = wss://${x-sc-host}:4444

Trying to list all the configuration properties would make this README quickly 
out of date.  So, for a list of the properties supported by the SC datalink, 
the IP datalink, the Device object and applications, see:
 
   For:        See under dev/src/java/dev/bscs/...
   sc.xxx      .../bacnet/bacnetsc/SCProperties.java
   ip.xxx      .../bacnet/bacnetip/IPDatalinkProperties.java
   device.xxx  .../bacnet/stack/objects/DeviceObjectProperties.java
   common.xxx  .../common/Application.java    (common for all apps)
   app.xxx     .../bacnet/applications/*.java (app-specific)

The example apps .properties files list the properties that are most 
appropriate for that app, but they do not list all the others that remain equal 
to their default values.  So if, for example, you want a Node to advertise a 
max NPDU other than the default of 1497, you would set the 
sc.maxBVLCLengthAccepted property. Note that the sc.xxx property names match 
the proposed Network Port property names in Addendum cc.

============================== Certificates ===================================

Certificates and keys are specified by the "sc.privateKey", 
"sc.operationalCertificate", and "sc.caCertificates" configuration properties.  
All will accept either a file name of a PEM formatted file, or a HEX string of 
the DER bytes. The "sc.caCertificates" property accepts a colon separated list. 
e.g., sc.caCertificates = config/SigningCert1.pem:config/SigningCert2.pem

Certificates and keys can be either RSA or EC (P256).  The key file must be in 
the unencrypted PKCS#8 wrapped format (begin with ----BEGIN PRIVATE KEY----), not 
in X9.62 format (----BEGIN EC PARAMETERS----- or ----- BEGIN EC PRIVATE KEY----).
Convert with: openssl pkcs8 -topk8 -nocrypt -in <x962file> -out <pksc8file>
     
An additional part of the project, for creation and management of TLS keys and 
certificates, is written as a bash shell file.  In the "cert" subdirectory is 
a bash script file called "cert".  The top of that file has fairly extensive 
documentation and examples about how to use its features. The script has been 
verified to run on Mac, Linux, and the Windows 10 Linux subsystem. 

If you're on Windows and don't have a Linux shell installed yet, go to 
Control Panel -> Programs and Features -> Turn Windows features on or off -> 
Windows Subsystem for Linux. And reboot (of course). Then head to the microsoft 
store and search "Linux" and pick one. Ubuntu is recommended if you don't care 
otherwise. Once installed, you turn a regular command prompt into a Linux 
prompt by typing "wsl". Unix rules apply, so you execute the script as "./cert"

============================ Manual Commands =================================

By default, all example apps pop up a GUI "terminal window" for entering manual 
commands and seeing the results. 

Use 'list' for a list of available commands.
Use 'help <command>' for synopsis of the usage of a command.
Use 'man <command>' for details of a command, or 'man all' for everything. 

Each command *line* can consist of multiple semicolon-separated commands, e.g., 
  
   $ target 55502:333333333333; rp; target 55502:111111111111; rp 
   $ hub report 1; hub report 2

The common commands are: 

inject      dc          hb          ar          as          conn        node
hub         datalink    bindings    wh          wi          rp          wp
target      device      routes      sys         log         quit

Most commands complete immediately and return to the command prompt. But some 
are intentionally long running (like 'wi' listening for I-Am's) and will only 
return to the promp after timing out (default 10 seconds) or if you hit enter 
to terminate them.

In addition to common commands, several apps provide their own specific 
commands. For example, TestNodes provides "nodestats" and "nodecounts". And, 
most useful for testing, TestHubs provides "pup","pdn", "fup", and "fdn"  for 
primary up/down and failover up/down.

The terminal window supports command history and editing. In addition to the 
expected up and down arrows for history on the last line, when you click the 
cursor up to a previous line and hit enter, that line is copied to the end 
and is ready to just hit enter again to repeat, or you can edit it before 
hitting enter. For convenience, it doesn't matter where the cursor is on the 
edited line when you hit enter, the entire line will be accepted.

As an alternative to the GUI terminal, the manual commands can also be entered 
through the console, interleaved with the logging output.  This can be selected 
with app.shell=console in the configuration. When this is selected, the 
terminal window will not appear and when you hit enter on the console, the 
logging will stop and it will prompt you for a command line. After the command 
results are displayed, logging will resume. You can keep the logging paused and 
get another prompt by ending the command line with a semicolon.  

Applications can be given extra commands with the "common.commandClass" 
configuration property. This specifies a class that implements the CommandSet 
interface that specifies a collection of commands to be given to any 
application via a configuration property rather than changing the application's 
code.

======================== Changing / Building =================================

This initial release provides project files for IntelliJ IDEA 2020.1 Community 
Edition, which is available for Windows, Mac, and Linux at jetbrains.com/idea. 
The project files are located in the 'dev' subdirectory.

Launch IDEA and open the dev/BACnetSC.ipr file. "Run/Debug Configurations" are 
set up for all the example apps.  All paths in the IDEA project should be 
relative so it should run from anywhere as long as this entire distribution 
structure is kept intact. Note that the file arrangement is a little unusual in 
that the out and lib directories are not at the same level as the src, but IDEA 
has no problem with that.

You can run or debug multiple apps at a time in one instance of IDEA. Each will 
show up as a tab at the bottom and you can switch between their console log 
outputs.  This feature is particularly important when you want to set 
breakpoints in both the hub and the node, for example.

======================== Extending / Scripting ===============================

Python support is included in two ways. First, the "py" command runs scripts 
out of the "./scripts" directory and is designed for running python scripts from 
the command line in a persistent and resettable python environment. The python 
bridge is implemented with jython and gives complete access to all java classes. 
Thus, a loaded python script can install new first-class commands implemented 
in python. This capability is demonstrated by the DemoCommands.py script. 

In addition to running scripts from the terminal or console, the entire 
application can be written as a python class that extends the java Application 
class.  This allows the python code to install event handlers and callbacks to 
execute arbitrary scripted actions without user interaction after the start() 
method returns. A python application can be created by specifying 
"common.mainScript" in addition to the "common.mainClass" (which in that case 
indicates the name of the python class).  An example is provided by the 
TestSCSC.py script and the TestSCSCpy.properties configuration file.

============================= The Code =======================================

The programming model is a single threaded "event loop" style like most GUIs, 
JavaScript, etc. This allows the vast majority of the code to run in a single 
thread so that it is all mutually thread-safe and does not require 
synchronization, while still allowing asynchronous events from other threads to 
be processed without latency or polling.  

For example, all the datalink, network, and application layers run in the same 
thread but "network events" like incoming packets from multiple low level 
listening threads are fired as events on a thread-safe queue and de-queued 
immediately in the main thread to be processed up and down the stack.  Since 
nothing in the main stack blocks, almost everything is written as state 
machines that respond to the externally queued events or "maintenance" timers.

Additionally, the code style is deliberately simple, not clever. By limiting 
the "java-isms" (it's written in a single module at Java level 8) and not 
relying on a particular threading or synchronization model, the code is 
intended to be as portable/understandable/translatable as it can be.

It also has *minimal* dependencies on any external libraries. Only Logging and 
WebSockets libraries are required.  Both dependencies have narrow abstraction 
layers and are written specifically to allow substitution of another WebSockets 
or logging libraries if desired. All the TLS stuff is done with the built-in 
functions.

To understand the example applications, you can start with their class comments, 
and also refer to the Application.java file in the src/dev/bscs/common directory 
which has a good write-up of the overall application mechanism. The example apps 
are also fairly simple. For example, TestHub in its entirety is shown below and 
TestRouter with its two datalinks is almost as simple.

public class TestHub extends Application {
 private SCDatalink scDatalink;
 @Override void  start() {
   Device device = new Device(configuration);
   scDatalink = new SCDatalink("SC-1",new SCProperties(configuration),device,0);
   scDatalink.start();
 }
 @Override protected void addCommands() {
   BACnetCommands.addCommands();
   SCCommands.addCommands();
 }
 @Override void stop()  { scDatalink.stop();  }
 @Override void close() { scDatalink.close(); }
}

TestNodes and TestRouters are the most complicated since they need to tweak 
the properties for multiple devices. But in all, the suite of examples provides
the pieces for building custom combo apps fairly quickly.

======================== Implemented Features ===============================

Hey, it's supposed to be a "reference implementation", right?  That means that 
it has to implement *everything*. But it doesn't have to be a real "product". 
So some of those features and capabilities are implemented in a form that aids 
testing but not necessarily "real world".  For example, establishing or tearing 
down direct connections in a real product would be driven by that product's 
needs which can be quite varied. In this reference implementation, such 
establishment is driven by human needs through manual commands or by scripts.

======================== Non-standard Features ==============================

This implementation is capable of using plain WebSockets, TLS 1.2, or TLS 1.3.  
Additionally, you can turn off certificate validation and turn off the 
"internal node" to keep clutter out of the log files for hubs.  These 
extensions controlled by configuration properties: 

   sc.nodeEnable   = true|false        (default is true)
   sc.tlsVersion   = TLSv1.3|TLSv1.3   (default is TLSv1.3)
   sc.noValidation = true|false        (default is false)
 
The choice of plain web sockets is made with the URIs by choosing the 
corresponding scheme.  For example, to use plain WebSockets, simply change 
"wss" to "ws", on both sides: 

   sc.primaryHubURI      = ws://${x-sc-host}:8080
   sc.hubFunctionBindURI = ws://${x-sc-host}:8080

These non-standard "features" are designed to keep your progress moving on 
other aspects of the protocol rather than just a "can't connect" stonewall.

Additionally, the spec says (incorrectly) in YY.4.2.2 that destination VMAC can 
be optionally present. But YY.2.1 says specifically that it must be omitted. 
Until an interpretation request can be published, this implementation will 
accept or reject based on a configuration setting "sc.allowYY422destination" 
(default is false).

============================= Error Injection ================================

One of the most powerful features is the ability to inject errors into an otherwise-
compliant implementation for the testing of *other* implementations.  The "inject"
command allows you to inject errors into incoming or outgoing messages. The impact of 
this feature in the core code is minimized by having a few strategic places where
calls are made to hooks that modify incoming or outgoing things. Generally, most(all?) 
of the errors are "one time" events and there is only one error lying in wait at a time. 
Some injections have "match" criteria that wait till the match occurs. Others just 
operate on the *next* call to the hook. They clear themselves after triggering, so none 
are intended to be persistent. Run "man inject" and "inject list" to see the long list 
of possibilities.

This feature is not limited to injecting "errors", it can also be used to add custom 
data options or replace payloads.

============================== Logging ======================================

As a reference implementation and testing tool, it is intended that the logging 
be fairly extensive and verbose (and helpful). If there is anything missing or 
confusing, the logs can be improved with community input during the beta phase.

There is a detailed example of how to read the logs in the config subdirectory 
called "Interpreted Log Example.txt". This is a walk-through of a request and 
response as they travel through a pair of routers from IP to SC to IP and back.
It is also a good example of why it is sometimes desirable to make a single app 
with multiple devices since the flow of messages through the two routers is all 
properly interleaved in time in one log.

The logging format and location is controlled by the "logback" framework. The 
logback configuration file "logback.xml" is located in the 'config' 
subdirectory.  Description of this file or its capabilities will not be covered 
here, so just refer to the logback website for details. 

If you want an application specific alternative to the default config/logback.xml, 
you can specify the logback configuration in the application's .properties file. 
e.g., 

    log.config = config/MyAlternateLogback.xml

In addition to controlling the format and location, the "level" of logging can 
be controlled with the manual command "log".  For example, setting this to 
"trace" will cause the WebSocket library to barf up a bunch of details if 
you're having problems with WebSocket negotiations (yes, the external WebSocket 
library and this new code use the same logging framework). 

Additionally, you can turn on TLS debugging with the config property 
app.tlsDebug.  Setting this to "all" will turn on a ton of details. The 
app.tlsDebug property is copied internally to javax.net.debug so you can google 
that for options other than "all". Unfortunately, this seems to be only 
changeable at startup so there is no manual command for this.  Examples:

   app.tlsDebug = all

   app.tlsDebug = ssl:handshake:verbose:keymanager

The TLS debugging does not use the common logging framework, so if you 
redefine logback configuration, note that the TLS debug messages will still 
go to the console.

========================= Known Issues / Bugs ===============================

There is a WebSocket library error where close() is not actually closing an 
initiated connection (See "ZOMBIE socket" comment in SCConnection.java)  When 
the *accepting* side closes it, this zombie is finally killed. This is harmless 
to operation but might look strange in the logs.

===============================================================================









