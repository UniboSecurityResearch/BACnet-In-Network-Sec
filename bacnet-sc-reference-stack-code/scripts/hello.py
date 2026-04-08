from dev.bscs.common import Shell

# This is just a test to show what happens if you call Shell.commandDone or not.
# Since this is not an official "command", it does not have an execute() method 
# that gets a chance to return True or False to indicates to the Shell whether 
# to re-prompt or not. 

# If you run this with "py run hello", it alternates between finishing 
# immediately or waiting for you to cancel it with an Enter keystroke.
# If you run it wil "py load hello" it will always re-prompt immediately 
# because "load" does not expect you to do anything long running.

try: 
    global_hi_alternator = not global_hi_alternator
except NameError:
    global_hi_alternator = False

if global_hi_alternator:
	Shell.println("Hello!")
	Shell.commandDone()
	# since you called commandDone(), you're finished and the shell will reprompt
else:
	Shell.println("Hello...")
	# Since you did not call commandDone(), you're not finished and the shell will wait to re-prompt
	# Here you would launch some long running operation, probably with a maintenance listener

