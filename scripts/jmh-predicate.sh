#!/usr/bin/env bash

set -e
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)

EXPERIMENT_PACKAGE="io.palyvos.provenance.usecases.microbench.predicate"
experimentClass="$1"
shift

[[ -e experimentClass ]] && { echo "Please provide experiment class name (in package $EXPERIMENT_PACKAGE)"; exit 1; }

COMMIT_CODE=$(git rev-parse --short HEAD)
TIME_CODE=$(date +%j_%H%M)
OUTPUT_NAME="$(hostname)-predicate-bench-${COMMIT_CODE}-${TIME_CODE}"
OUTPUT_PATH="data/output/${OUTPUT_NAME}"
mkdir -p "$OUTPUT_PATH"
OUTPUT_FILE="${OUTPUT_PATH}/${OUTPUT_NAME}.csv"
LOG_FILE="$OUTPUT_PATH"/jmh.out
echo "Saving results to: $OUTPUT_PATH"
sleep 1
java -cp jars/helpers-1.0-SNAPSHOT.jar "${EXPERIMENT_PACKAGE}.${experimentClass}" -rf csv -rff "$OUTPUT_FILE" "$@" 2>&1 | tee "$LOG_FILE"
echo
echo "$OUTPUT_NAME" # To use in other scripts
