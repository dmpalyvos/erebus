#!/usr/bin/env bash
set -e

echo "Automatic setup about to start"
sleep 3

echo "Downloading Apache Flink..."
wget -O flink-1.14.0-bin-scala_2.11.tgz https://archive.apache.org/dist/flink/flink-1.14.0/flink-1.14.0-bin-scala_2.11.tgz
tar zxvf flink-1.14.0-bin-scala_2.11.tgz
rm flink-1.14.0-bin-scala_2.11.tgz 
echo "Done."

echo "Downloading Apache Kafka..."
wget -O kafka_2.13-3.1.0.tgz https://archive.apache.org/dist/kafka/3.1.0/kafka_2.13-3.1.0.tgz
tar -xzf kafka_2.13-3.1.0.tgz
rm kafka_2.13-3.1.0.tgz
echo "Done."

bash get-datasets.sh

echo
echo "Compiling..."
echo
mvn -f helper_pom.xml clean package && mv target/helper*.jar jars
mvn clean package

echo "Done!"
