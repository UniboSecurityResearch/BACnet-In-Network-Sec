from dev.bscs.common import Shell
from dev.bscs.common import Timer
from dev.bscs.common import Command
from dev.bscs.common import Application
from dev.bscs.common import CommandProcessor
from dev.bscs.common import Formatting
from dev.bscs.bacnet.applications import BACnetCommands
from dev.bscs.bacnet.applications import SCCommands
from dev.bscs.bacnet.stack import AuthData
from dev.bscs.bacnet.bacnetsc import SCProperties
from dev.bscs.bacnet.bacnetsc import SCDatalink
from dev.bscs.bacnet.bacnetsc import SCVMAC
from dev.bscs.bacnet.stack import Binding
from dev.bscs.bacnet.stack import Device
from dev.bscs.bacnet.stack import Failure
from dev.bscs.bacnet.stack.data import BACnetPropertyIdentifier
from dev.bscs.bacnet.stack.data.base import BACnetData
from dev.bscs.bacnet.stack.data.base import BACnetObjectIdentifier
from dev.bscs.bacnet.stack.services import ReadPropertyClient
from dev.bscs.bacnet.stack.services import WhoIsClient
from dev.bscs.events import EventListener
from dev.bscs.events import EventLoop
from dev.bscs.events import EventType
from java.io import FileNotFoundException
from java.io import FileReader
from java.io import IOException
from java.io import Reader
from java.lang import String
from java.util import Queue
from java.util.concurrent import ConcurrentLinkedQueue
from jarray import array
	
# In addition to running scripts from the terminal or console, the entire 
# application can be written as a python class that extends the java Application 
# class.  This allows the python code to install event handlers and callbacks to 
# execute arbitrary scripted actions without user interaction after the start() 
# method returns. A python application can be created by specifying 
# "common.mainScript" in addition to the "common.mainClass" (which in that case 
# indicates the name of the python class). e.g., 
#    common.mainClass   = TestSCSC
#    common.mainScript  = scripts/TestSCSC.py
 
class TestSCSC(Application):

    def start(self):
        configuration = Application.configuration
        device = Device(configuration)
        scNetworkNumber1 = configuration.getInteger("app.scNetworkNumber1", 55501)
        scNetworkNumber2 = configuration.getInteger("app.scNetworkNumber2", 55502)

        scProperties1 = SCProperties(configuration)

        scProperties2 = SCProperties(configuration)

        scProperties2.vmac = SCVMAC(configuration.getMAC("app.2.sc.vmac", 6, array([0,0,0,0,0,0],'b')))
        scProperties2.primaryHubURI = configuration.getString("app.2.sc.primaryHubURI", "")
        scProperties2.hubFunctionEnable = configuration.getBoolean("app.2.sc.hubFunctionEnable", True)
        scProperties2.hubFunctionBindURI = configuration.getString("app.2.sc.hubFunctionBindURI", "")

        self.scDatalink1 = SCDatalink("SC-1", scProperties1, device, scNetworkNumber1)
        self.scDatalink2 = SCDatalink("SC-2", scProperties2, device, scNetworkNumber2)

        self.scDatalink1.start()
        self.scDatalink2.start()
    
    def addCommands(self):
        BACnetCommands.addCommands()
        SCCommands.addCommands()
    
    def stop(self):
        self.scDatalink1.stop()
        self.scDatalink2.stop()
    
    def close(self):
        self.scDatalink1.close()
        self.scDatalink2.close()
        

