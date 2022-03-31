#!/usr/bin/env bash
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)

# Config Start
KAFKA_VERSION="2.13-3.1.0"
MAX_ATTEMPTS=10
# Config End
DATA_PARTITIONS="$1"
BOOTSTRAP_SERVER="$2"
(( DATA_PARTITIONS >= 1 )) || { echo "Please provide #partitions >= 1"; exit 1; }
[[ -z $BOOTSTRAP_SERVER ]] && { echo "Please provide kafka bootstrap server"; exit 1; }
KAFKA_DIR="$script_dir/../kafka_${KAFKA_VERSION}"
TOPIC_SCRIPT="$KAFKA_DIR/bin/kafka-topics.sh" 
SCRIPT_CONFIG="$script_dir/topic-init.properties"

if [[ ! -e $TOPIC_SCRIPT ]]; then
    echo "Cannot find $TOPIC_SCRIPT" 1>&2
    exit 1
fi


createTopic() {
  topic="$1"
  nPartitions="$2"
  attempts=0
  echo "[$(date)] (Re)creating topic '$topic' (partitions=$nPartitions) at $BOOTSTRAP_SERVER" 1>&2
  "$TOPIC_SCRIPT" --delete --topic "$topic" --bootstrap-server "$BOOTSTRAP_SERVER" --command-config "$SCRIPT_CONFIG"
  until "$TOPIC_SCRIPT" --create --topic "$topic" --partitions "$nPartitions" --bootstrap-server "$BOOTSTRAP_SERVER" --command-config "$SCRIPT_CONFIG"
  do
    echo "Attempt $attempts"
    "$TOPIC_SCRIPT" --delete --topic "$topic" --bootstrap-server "$BOOTSTRAP_SERVER" --command-config "$SCRIPT_CONFIG"
    (( attempts++ ))
    if (( attempts > MAX_ATTEMPTS )); then
      echo "[$(date)] Failed to create topic '$topic' after $MAX_ATTEMPTS attempts"
      exit 1;
    fi
    sleep 5
  done
  echo "[$(date)] Successfully created topic '$topic'" 1>&2
}

createTopic "answers" "$DATA_PARTITIONS"
createTopic "source" "$DATA_PARTITIONS"
createTopic "predicate" "1"

