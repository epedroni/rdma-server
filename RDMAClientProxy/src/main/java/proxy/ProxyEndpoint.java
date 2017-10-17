package main.java.proxy;

import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import com.ibm.disni.rdma.verbs.IbvSendWR;
import com.ibm.disni.rdma.verbs.RdmaCmId;
import com.ibm.disni.rdma.verbs.SVCPostSend;
import main.java.endpoint.CustomEndpoint;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class ProxyEndpoint extends CustomEndpoint {
    ProxyEndpoint(RdmaActiveEndpointGroup<? extends CustomEndpoint> endpointGroup, RdmaCmId idPriv, boolean serverSide) throws IOException {
        super(endpointGroup, idPriv, serverSide);
    }

    /**
     * Sends the specified string, blocking until the request is completed.
     */
    public void sendResourceName(String string) throws Exception {
        sendBuf.putInt(string.length());
        sendBuf.asCharBuffer().put(string);
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
     * Reads from the server's memory using RDMA, returns the array of bytes read.
     */
    private byte[] read(long addr, int length, int lkey) throws Exception {
        // TODO could optimise this so it doesnt not RDMA the entire buffer from the server
        IbvSendWR sendWR = new IbvSendWR();
        sendWR.setWr_id(wrId++);
        sendWR.setSg_list(this.sgeListData);
        sendWR.setOpcode(IbvSendWR.IBV_WR_RDMA_READ);
        sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
        sendWR.getRdma().setRemote_addr(addr);
        sendWR.getRdma().setRkey(lkey);

        List<IbvSendWR> wrList = new LinkedList<>();
        wrList.add(sendWR);
        SVCPostSend send = postSend(wrList);
        send.execute().free();
        this.wcEvents.take();

        dataBuf.clear();
        byte[] content = new byte[length];
        dataBuf.get(content);

        return content;
    }

    /**
     * Waits for the server to reply to a request, and if the resource is found,
     * reads it off the server memory and returns it. Otherwise, returns an empty array.
     */
    public byte[] receiveData() throws Exception {
        receive();

        recvBuf.clear();
        byte status = recvBuf.get();

        if (status == RESOURCE_FOUND) {
            long addr = recvBuf.getLong();
            int length = recvBuf.getInt();
            int lkey = recvBuf.getInt();

            return read(addr, length, lkey);
        } else {
            return new byte[0];
        }
    }
}
