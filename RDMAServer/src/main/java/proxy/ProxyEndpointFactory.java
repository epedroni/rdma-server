package main.java.proxy;

import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import com.ibm.disni.rdma.RdmaEndpointFactory;
import com.ibm.disni.rdma.verbs.RdmaCmId;
import main.java.endpoint.CustomEndpoint;

import java.io.IOException;

public class ProxyEndpointFactory implements RdmaEndpointFactory<ProxyEndpoint> {

    private RdmaActiveEndpointGroup<ProxyEndpoint> endpointGroup;

    public ProxyEndpointFactory(RdmaActiveEndpointGroup<ProxyEndpoint> endpointGroup) throws IOException {
        this.endpointGroup = endpointGroup;
    }

    @Override
    public ProxyEndpoint createEndpoint(RdmaCmId idPriv, boolean serverSide) throws IOException {
        return new ProxyEndpoint(endpointGroup, idPriv, serverSide);
    }

}
