from dev.bscs.common import Shell
from dev.bscs.common import Timer
from dev.bscs.common import Command
from dev.bscs.common import CommandProcessor
from dev.bscs.common import Formatting
from dev.bscs.bacnet.applications import BACnetCommands
from dev.bscs.bacnet.stack.data import BACnetPropertyIdentifier
from dev.bscs.bacnet.stack.data.base import BACnetObjectIdentifier
from dev.bscs.bacnet.stack.services import ReadPropertyClient
from dev.bscs.bacnet.stack.services import WhoIsClient
from dev.bscs.events import EventListener
from dev.bscs.events import EventLoop
from java.lang import String
from java.util.concurrent import ConcurrentLinkedQueue
from jarray import array

# This is just a demonstration of how to add commands from Python.
# If you run
#      $ py load DempCommands.py
# the static code here will add two commands to the terminal.
# You can see the available commands with
#      $ list
# You can see the dynamic nature of this scripy be changing this file and just running the "load" command again.
# The changed commands become immediately available without needing to restart.

class PwixCommand(Command):
    
    class MyListener(EventListener):
        def __init__(self,parent):
            self.parent = parent
        def handleEvent(self, source, eventType, *args):
            if self.parent.timer.remaining() == 0:
                binding = self.parent.queue.poll()
                if binding != None:
                    self.parent.timer.start(2000)
                    Shell.print(Formatting.toNetMac(binding.dnet,binding.dadr))
                    self.parent.readPropertyClient.request(BACnetCommands.getSelectedDevice(), binding.dnet, binding.dadr,BACnetObjectIdentifier.combine(8,0x3FFFFF),BACnetPropertyIdentifier.OBJECT_NAME, -1)
    
    class MyWhoIsClient(WhoIsClient):
        def __init__(self,parent):
            self.parent = parent
        def success(self, device, binding, auth):
            self.parent.queue.offer(binding)
            EventLoop.emit(self, self.parent.listener, EventLoop.EVENT_MAINTENANCE)

            
    class MyReadPropertyClient(ReadPropertyClient):
        def __init__(self,parent):
            self.parent = parent
        def success(self, device, value, auth):
            Shell.println(" name=" + value.toString() + " auth=" + auth.toString())
            self.parent.timer.clear()
            EventLoop.emit(self, self.parent.listener, EventLoop.EVENT_MAINTENANCE)
        def failure(self, device, failure, auth):
            Shell.println(" failure="+failure)
            self.parent.timer.clear()

    def __init__(self):
        Command.__init__(self,"pwix","pwix",array(["WhoIsExtended - a WhoIs followed by a ReadProperty"],String))
        self.queue = ConcurrentLinkedQueue()
        self.timer = Timer()
        self.readPropertyClient = PwixCommand.MyReadPropertyClient(self)
        self.listener = PwixCommand.MyListener(self)
        self.whoIsClient = PwixCommand.MyWhoIsClient(self)

    def execute(self, words):
        self.whoIsClient.request(BACnetCommands.getSelectedDevice(),65535,None)
        EventLoop.addMaintenance(self.listener)
        return False # keeps prompt open for long running operation

    def cancel(self):
        self.whoIsClient.cancel(BACnetCommands.getSelectedDevice())
        EventLoop.removeMaintenance(self.listener)
        
        
class HiCommand(Command):
    def __init__(self):
        Command.__init__(self,"hi","hi",array(["hi - say Hi!"],String))

    def execute(self, words):
        Shell.println("Hi!")
        return True # command is finished, re-prompt

# if you reload this script, the commands gets properly replaced with new versions.
CommandProcessor.addCommand(PwixCommand()) 
CommandProcessor.addCommand(HiCommand()) 

  
    
           



