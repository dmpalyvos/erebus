#!/usr/bin/env bash

function usage() {
    echo "Usage: $0 #forks #warmup_iterations #iterations"
    exit 1
}

[[ -z $1 ]] && usage
[[ -z $2 ]] && usage
[[ -z $3 ]] && usage

FORKS="$1"
WARMUP="$2"
REPS="$3"

echo "Starting experiment. Results will be stored in data/output/$EXPERIMENT_FOLDER"
sleep 2

OUT=$(./scripts/jmh-predicate.sh OneVariablePredicateBenchmark -f "$FORKS" -wi "$WARMUP" -i "$REPS" | tee /dev/fd/2)
EXPERIMENT_FOLDER=$(echo "$OUT" | tail -n 1)

./reproduce/plot.py --path "data/output/$EXPERIMENT_FOLDER" --plot microbench