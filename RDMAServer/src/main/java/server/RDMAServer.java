package main.java.server;

import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import com.ibm.disni.rdma.RdmaServerEndpoint;
import main.java.endpoint.CustomEndpoint;

import java.net.URI;
import java.nio.file.Paths;

public class RDMAServer {
    private RdmaActiveEndpointGroup<ServerEndpoint> endpointGroup;
    private RdmaServerEndpoint<ServerEndpoint> serverEndpoint;
    private String filePath;

    private RDMAServer(String address, String fileDirectory) throws Exception {
        System.out.println("Creating server, listening on " + address);
        System.out.println("Serving files from " + fileDirectory);

        filePath = fileDirectory;
        endpointGroup = new RdmaActiveEndpointGroup<>(1000, false, 128, 4, 128);
        ServerEndpointFactory factory = new ServerEndpointFactory(endpointGroup);
        endpointGroup.init(factory);

        serverEndpoint = endpointGroup.createServerEndpoint();
        serverEndpoint.bind(URI.create(address));
    }

    /**
     * An instance of this handler listens for messages from a single client
     * on a single endpoint and replies accordingly.
     */
    private class ClientHandler implements Runnable {
        private ServerEndpoint endpoint;

        ClientHandler(ServerEndpoint endpoint) {
            super();
            this.endpoint = endpoint;
        }

        @Override
        public void run() {
            System.out.println("[Thread] Client connected, amazing");
            try {
                while (true) {
                    System.out.println("[Thread] Waiting for request from client");
                    String resource = endpoint.receiveResourceName();

                    System.out.println("[Thread] Client requested resource " + resource);
                    // if the resource is valid, load it into the data buffer
                    switch (resource) {
                        case "":
                        case "/":
                        case "/index.html":
                            endpoint.sendFile(Paths.get(filePath, "index.html"));
                            break;
                        case "/network.png":
                            endpoint.sendFile(Paths.get(filePath, "network.png"));
                            break;
                        default:
                            endpoint.sendNotFound();
                            break;
                    }
                    System.out.println("[Thread] Response sent");
                }
            } catch (Exception e) {
                System.err.println("[Thread] Something terrible happened!");
                e.printStackTrace();
            } finally {
                try {
                    endpoint.close();
                } catch (Exception e) {
                    System.err.println("[Thread] Couldn't even close the endpoint, what a disaster");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * This runs on the main thread, it listens for new client connections
     * and forks the handling to a separate thread when a connection is established.
     */
    private void run() throws Exception {
        try {
            while (true) {
                System.out.println("Waiting for client connections");
                Thread t = new Thread(new ClientHandler(serverEndpoint.accept()));
                t.start();
            }
        } finally {
            System.out.println("Closing server endpoint and endpoint group");
            serverEndpoint.close();
            endpointGroup.close();
        }
    }

    public static void main(String... args) throws Exception {
        if (args.length < 2) {
            System.err.println("Missing arguments! Usage: rdmaserver ip:port resource_directory");
            System.exit(1);
        }

        RDMAServer server = new RDMAServer(args[0], args[1]);
        server.run();
    }
}