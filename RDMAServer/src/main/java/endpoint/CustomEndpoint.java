package main.java.endpoint;

import com.ibm.disni.rdma.RdmaActiveEndpoint;
import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import com.ibm.disni.rdma.verbs.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Adapted from https://github.com/zrlio/disni/blob/master/src/test/java/com/ibm/disni/examples/SendRecvServer.java
 */
public abstract class CustomEndpoint extends RdmaActiveEndpoint {

    protected static final int BUFFER_SIZE = 4096;
    protected static final byte RESOURCE_FOUND = 1;
    protected static final byte RESOURCE_NOT_FOUND = 0;

    protected final ByteBuffer sendBuf;
    protected final ByteBuffer recvBuf;
    protected final ByteBuffer dataBuf;

    protected IbvMr sendMr;
    protected IbvMr recvMr;
    protected IbvMr dataMr;

    protected LinkedList<IbvSge> sgeListSend;
    protected LinkedList<IbvSge> sgeListRecv;
    protected LinkedList<IbvSge> sgeListData;

    protected ArrayBlockingQueue<IbvWC> wcEvents;

    protected int wrId = 1000;

    public CustomEndpoint(RdmaActiveEndpointGroup<? extends CustomEndpoint> endpointGroup, RdmaCmId idPriv, boolean serverSide) throws IOException {
        super(endpointGroup, idPriv, serverSide);

        // allocateDirect creates off-heap buffers which are not subject to garbage collection, so they should really be reused!
        this.sendBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.recvBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.dataBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);

        this.sgeListSend = new LinkedList<>();
        this.sgeListRecv = new LinkedList<>();
        this.sgeListData = new LinkedList<>();
        this.wcEvents = new ArrayBlockingQueue<>(10);
    }

    @Override
    public void init() throws IOException{
        super.init();
        this.sendMr = registerMemory(sendBuf).execute().free().getMr();
        this.recvMr = registerMemory(recvBuf).execute().free().getMr();
        this.dataMr = registerMemory(dataBuf).execute().free().getMr();

        this.sgeListSend.add(makeSge(sendMr));
        this.sgeListRecv.add(makeSge(recvMr));
        this.sgeListData.add(makeSge(dataMr));

        // prepare to receive initial communication
        IbvRecvWR recvWR = new IbvRecvWR();
        recvWR.setWr_id(wrId++);
        recvWR.setSg_list(sgeListRecv);

        List<IbvRecvWR> wrList = new LinkedList<>();
        wrList.add(recvWR);

        this.postRecv(wrList).execute().free();
    }

    @Override
    public void dispatchCqEvent(IbvWC wc) throws IOException {
        wcEvents.add(wc);
    }

    @Override
    public void close() throws IOException, InterruptedException {
        super.close();
        deregisterMemory(sendMr);
        deregisterMemory(recvMr);
        deregisterMemory(dataMr);
    }

    /**
     * Makes basic SGEs.
     */
    private IbvSge makeSge(IbvMr mr) {
        IbvSge sge = new IbvSge();
        sge.setAddr(mr.getAddr());
        sge.setLength(mr.getLength());
        sge.setLkey(mr.getLkey());
        return sge;
    }

    /**
     * Block until something is received on the recv buffer.
     */
    protected void receive() throws Exception {
        IbvRecvWR recvWR = new IbvRecvWR();
        recvWR.setWr_id(wrId++);
        recvWR.setSg_list(sgeListRecv);

        List<IbvRecvWR> wrList = new LinkedList<>();
        wrList.add(recvWR);

        this.postRecv(wrList).execute().free();

        // block until confirmation is received
        this.wcEvents.take();
    }
}