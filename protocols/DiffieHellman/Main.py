from Sockets import Client
from Sockets import Server
from argparse import ArgumentParser
import time

if __name__ == '__main__':

	parser = ArgumentParser()
	parser.add_argument("-m", "--mode", dest="mode", type=str, required=True,
	                    help="CLIENT to start a client or SERVER to start a server"
	                    )

	parser.add_argument("-d", "--debug", dest="debug", required=False,
	                    help="to print debug messages, enable this option",
	                    action="store_true"
	                    )

	args = parser.parse_args()

	if args.debug:
		print(args)

	if args.mode.lower() == "client":
		server = "localhost" # input("Server IP: ")
		with open("./results_DH.txt", "w") as results_file:
			for i in range(10000):
				time_start = time.time()
				client = Client.ClientSocket(args.debug)
				client.start_client(server)
				time_end = time.time()
				results_file.write("%s\n" % (time_end - time_start))
				print(f"Test DH number {i}")

	elif args.mode.lower() == "server":
	    Server.start_server(args.debug)
