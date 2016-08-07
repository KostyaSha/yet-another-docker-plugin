#!/usr/bin/env bash

mkdir ~/docker-logs || :
for i in $(docker  ps -a -q); do
    echo "Dumping $i" 
    docker logs $i > ~/docker-logs/"$i" 
done
