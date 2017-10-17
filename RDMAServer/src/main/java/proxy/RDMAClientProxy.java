package main.java.proxy;

import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import main.java.server.RDMAServer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RDMAClientProxy {
    private RdmaActiveEndpointGroup<ProxyEndpoint> endpointGroup;
    private ProxyEndpoint endpoint;
    private BlockingQueue<Request> requestQueue;

    private RDMAClientProxy(int port, String rdmaAddress) throws Exception {
        requestQueue = new LinkedBlockingQueue<>();

        endpointGroup = new RdmaActiveEndpointGroup<>(1000, false, 128, 4, 128);
        ProxyEndpointFactory factory = new ProxyEndpointFactory(endpointGroup);
        endpointGroup.init(factory);

        System.out.println("Creating endpoint");
        endpoint = endpointGroup.createEndpoint();
        endpoint.connect(URI.create(rdmaAddress));
        System.out.println("Connected to RDMA server");

        System.out.println("Starting serversocket thread");
        new Thread(new ServerThread(port)).start();
    }

    /**
     * The client proxy uses a single instance of this runnable. Its job
     * is to listen for incoming client connections and fork them to their
     * own handler threads.
     */
    private class ServerThread implements Runnable {
        private ServerSocket serverSocket;

        ServerThread(int port) throws Exception {
            System.out.println("Creating server socket, listening on port " + port);
            this.serverSocket = new ServerSocket(port);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    System.out.println("[ServerThread] Waiting for client...");
                    Socket clientSocket = serverSocket.accept();

                    System.out.println("[ServerThread] New client connected, forking to handler");
                    Thread t = new Thread(new ConnectionHandler(clientSocket));
                    t.start();
                }
            } catch (IOException e) {
                System.err.println("[ServerThread] Error while accepting client connection!");
                e.printStackTrace();
            }
        }
    }

    /**
     * Instances of this runnable are created and executed by threads whenever
     * a client connects. Any input from the client is handled here first and
     * forwarded to the request queue, allowing the RDMA backend to deal with
     * a single request at a time.
     */
    private class ConnectionHandler implements Runnable {
        private Socket clientSocket;
        private BufferedReader br;

        private ConnectionHandler(Socket socket) throws IOException {
            super();
            this.clientSocket = socket;
            this.br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                while (true) {
                    System.out.println("[Thread] Waiting for client input");
                    String rawRequest = br.readLine();

                    if (rawRequest == null) {
                        System.out.println("[Thread] End of stream reached, client has disconnected, bailing");
                        break;
                    } else if (!rawRequest.startsWith("GET")) {
                        continue;
                    }

                    System.out.println("[Thread] Queueing client's request");
                    requestQueue.add(new Request(clientSocket, rawRequest));
                }
            } catch (Exception e) {
                System.out.println("[Thread] Something bad happened!");
                e.printStackTrace();
            } finally {
                try {
                    System.out.println("[Thread] Closing connection with client");
                    clientSocket.getOutputStream().flush();
                    clientSocket.close();
                } catch (Exception e) {
                    System.out.println("[Thread] Exception while closing stuff, what a disaster");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * The context of a request: the raw string received from the client,
     * and the socket that can be used to reply.
     */
    private class Request {
        private final Socket socket;
        private final String rawRequest;

        private Request(Socket socket, String rawRequest) {
            this.socket = socket;
            this.rawRequest = rawRequest;
        }
    }

    /**
     * This runs on the main thread, taking requests from the queue
     * and sending them to the RDMA server.
     */
    private void run() throws Exception {
        try {
            while (true) {
                System.out.println("Taking request from queue...");
                Request r = requestQueue.take();

                System.out.println("Got request!");
                String[] request = r.rawRequest.split(" ");

                if (request.length < 3)
                    continue;

                String method = request[0];
                String url = request[1];
                String protocol = request[2];

                System.out.println("Received url: " + url);
                Matcher m = Pattern.compile("(?:https?://)?www\\.rdmawebpage\\.com(.*)").matcher(url);

                if (m.matches()) {
                    try {
                        System.out.println("Received request for website, forwarding to server");
                        endpoint.sendResourceName(m.group(1));

                        byte[] response = endpoint.receiveData();

                        if (response.length > 0) {
                            System.out.println("Forwarding response to client");
                            found(protocol, response, r.socket.getOutputStream());
                        } else {
                            System.out.println("Resource not found");
                            notFound(protocol, r.socket.getOutputStream());
                        }
                    } catch (Exception e) {
                        System.out.println("Something bad happened, 504");
                        e.printStackTrace();
                        gatewayTimeout(protocol, r.socket.getOutputStream());
                    }
                } else {
                    System.out.println("We don't handle this domain");
                    notFound(protocol, r.socket.getOutputStream());
                }

            }
        } finally {
            System.out.println("Closing endpoint group");
            endpointGroup.close();
        }
    }

    /**
     * Returns a 200 OK to the client, including the specified content.
     */
    private void found(String httpVersion, byte[] content, OutputStream os) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(httpVersion + " 200 OK\n");
        sb.append("Date: " + Instant.now().toString() + "\n");
        sb.append("Content-Length: " + content.length + "\n");
        sb.append('\n');

        os.write(sb.toString().getBytes());
        os.write(content);
        os.flush();
    }

    /**
     * Returns a 404 not found to the client.
     */
    private void notFound(String httpVersion, OutputStream os) throws IOException {
        String response = "Page not found";
        StringBuilder sb = new StringBuilder();
        sb.append(httpVersion + " 404 Not found\n");
        sb.append("Date: " + Instant.now().toString() + "\n");
        sb.append("Content-Length: " + response.getBytes().length + "\n");
        sb.append('\n');

        os.write(sb.toString().getBytes());
        os.write(response.getBytes());
        os.flush();
    }

    /**
     * Returns a 504 gateway timeout to the client.
     */
    private void gatewayTimeout(String httpVersion, OutputStream os) throws IOException {
        String response = "Gateway timeout";
        StringBuilder sb = new StringBuilder();
        sb.append(httpVersion + " 504 Gateway timeout\n");
        sb.append("Date: " + Instant.now().toString() + "\n");
        sb.append("Content-Length: " + response.getBytes().length + "\n");
        sb.append('\n');

        os.write(sb.toString().getBytes());
        os.write(response.getBytes());
        os.flush();
    }

    public static void main(String... args) throws Exception {
        if (args.length < 2) {
            System.out.println("Missing argument! Usage: rmdaclientproxy port rdmaip:rdmaport");
            System.exit(1);
        }

        RDMAClientProxy proxy = new RDMAClientProxy(Integer.parseInt(args[0]), args[1]);
        proxy.run();
        System.out.println("Finished");
    }
}
