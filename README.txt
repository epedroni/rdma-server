# Running instructions:

* cd to the desired directory, do mvn clean package.
* once the jar is built, use the run script to run it.
* run the server first and then one or more proxies (but if running multiple proxies, change the port in the run script).
* the server needs to bind to the IP of the interface, it is hardcoded in run.sh as the IP of the VM.
* it also needs the directory where the resources and jars are located, this is hardcoded relative to the location of run.sh, so run it must be run from there.

To test it:
* with curl: do `curl -x 127.0.0.1:8081 www.rdmawebpage.com`, replacing the IP to test it from outside the VM
* with Chromium: `chromium --proxy-server=127.0.0.1:8081 --user-data-dir=/tmp`
* with Firefox: change the proxy settings in the profile
* with Safari: get a real laptop, install linux, see chromium or firefox above


# Documentation:

The RDMAServer creates a server endpoint and binds to the IP of the VM on port 8080. The main thread accepts connections on this endpoint. When a connection is accepted, a new thread is spawned to handle communication with that client. 

The client handler thread begins by blocking until a string is received from the client in the RECV buffer. When that happens, the server fetches the requested resource into the data buffer and replies to the client with the location of the buffer and length of the file loaded, as well as the buffer key. The client uses the information to issue an RDMA READ request, which copies the contents of the server's data buffer into the client's data buffer.

The client proxy works in a similar way, by accepting TCP connections from clients and forking the handling to a separate thread. However, the client proxy maintains a single RDMA endpoint to the server; RDMA communication is done from a single thread using the single endpoint. Incoming HTTP requests coming in from the client are queued in a thread-safe queue until they are handled by the RDMA handling thread.

