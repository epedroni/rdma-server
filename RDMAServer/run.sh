#!/bin/bash

rdma_server="rdma://10.0.2.15:8080"
resources="src/main/resources"
deps="target/rdma-1.0-SNAPSHOT.jar:lib/disni-1.0-jar-with-dependencies.jar:lib/disni-1.0-tests.jar"

export LD_LIBRARY_PATH=/usr/local/lib

java -cp "$deps" main.java.server.RDMAServer $rdma_server $resources
