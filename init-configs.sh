#!/usr/bin/env bash

set -e

NODE="$1"
if [[ $NODE != "server" && $NODE != "odroid" ]]; then
  echo "Please provide node: server / odroid"
  exit 1
fi

set -x
cp "configs/flink/${NODE}/flink-conf.yaml"  flink-1.14.0/conf/
cp "configs/kafka/${NODE}/server.properties"  kafka_2.13-3.1.0/config/