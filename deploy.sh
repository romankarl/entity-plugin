#!/bin/bash

mvn package
service neo4j-service stop
cp target/entities-plugin-0.0.1-SNAPSHOT.jar /usr/share/neo4j/plugins/
service neo4j-service start

