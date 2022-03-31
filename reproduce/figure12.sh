#!/usr/bin/env bash

function usage() {
    echo "Usage: $0 #reps #duration_min kafka_host"
    exit 1
}

[[ -z $1 ]] && usage
[[ -z $2 ]] && usage
[[ -z $3 ]] && usage

REPS="$1"
DURATION="$2"
KAFKA_HOST="$3"
DATE_CODE=$(date +%j_%H%M)
COMMIT_CODE=$(git rev-parse --short HEAD)
EXPERIMENT_FOLDER="${COMMIT_CODE}_$(hostname)_${DATE_CODE}"

echo "Starting experiment. Results will be stored in data/output/$EXPERIMENT_FOLDER"
sleep 2

./scripts/run.py ./experiments/predicate/Synthetic.yaml -d "$DURATION" -r "$REPS" --kafkaHost "$KAFKA_HOST" -c "$DATE_CODE"

./reproduce/plot.py --path "data/output/$EXPERIMENT_FOLDER" --plot synthetic