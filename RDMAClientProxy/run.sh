#!/bin/bash

proxy_port=8081
rdma_server="rdma://10.0.2.15:8080"
deps="target/rdma-1.0-SNAPSHOT.jar:lib/disni-1.0-jar-with-dependencies.jar:lib/disni-1.0-tests.jar"

export LD_LIBRARY_PATH=/usr/local/lib

java -cp "$deps" main.java.proxy.RDMAClientProxy $proxy_port $rdma_server
