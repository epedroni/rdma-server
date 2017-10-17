package main.java.server;

import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import com.ibm.disni.rdma.verbs.IbvSendWR;
import com.ibm.disni.rdma.verbs.RdmaCmId;
import main.java.endpoint.CustomEndpoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class ServerEndpoint extends CustomEndpoint {
    public ServerEndpoint(RdmaActiveEndpointGroup<? extends CustomEndpoint> endpointGroup, RdmaCmId idPriv, boolean serverSide) throws IOException {
        super(endpointGroup, idPriv, serverSide);
    }

    /**
     * Creates a recv work request and blocks until
     * a string is received on recv buffer.
     */
    public String receiveResourceName() throws Exception {
        receive();

        recvBuf.clear();
        int length = recvBuf.getInt();
        char[] name = new char[length];
        recvBuf.asCharBuffer().get(name);
        return String.valueOf(name);
    }

    /**
     * Writes the specified file into the data buffer, sends
     * the client the information necessary to access it.
     */
    public void sendFile(Path path) throws Exception {
        // load the file into the data buffer
        dataBuf.clear();
        dataBuf.put(Files.readAllBytes(path));

        // tell the client about where to find it
        sendBuf.clear();
        sendBuf.put(RESOURCE_FOUND);
        sendBuf.putLong(dataMr.getAddr());
        sendBuf.putInt(dataBuf.position());
        sendBuf.putInt(dataMr.getLkey());
        sendBuf.clear();

        IbvSendWR sendWR = new IbvSendWR();
        sendWR.setWr_id(wrId);
        sendWR.setSg_list(sgeListSend);
        sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
        sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);

        List<IbvSendWR> wrList = new LinkedList<>();
        wrList.add(sendWR);

        this.postSend(wrList).execute().free();

        // block until confirmation is received
        this.wcEvents.take();
    }

    /**
     * Sends the not found code to the client, in case the
     * requested file did not exist.
     */
    public void sendNotFound() throws Exception {
        sendBuf.clear();
        sendBuf.put(RESOURCE_NOT_FOUND);
        sendBuf.clear();

        IbvSendWR sendWR = new IbvSendWR();
        sendWR.setWr_id(wrId);
        sendWR.setSg_list(sgeListSend);
        sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
        sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);

        List<IbvSendWR> wrList = new LinkedList<>();
        wrList.add(sendWR);

        this.postSend(wrList).execute().free();

        // block until confirmation is received
        this.wcEvents.take();
    }
}
