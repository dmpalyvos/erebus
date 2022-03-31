#!/usr/bin/env bash

shopt -s nullglob # Prevent null globs

ROOT_FOLDER=$1

PREPROCESS_FILES=("rate" "sink-rate" "latency" "predicate-in" "predicate-out" "provsize" "past-buffer-size" "past-rate" "kafka-rate")
COPY_FILES=("memory" "cpu" "externalmemory" "externalcpu")
EXTENSION=".csv"


if [[ -z ${ROOT_FOLDER} ]]; then
  echo "Please provide root (commit) folder as argument"
  exit 1
fi

if [[ ! -e ${ROOT_FOLDER} ]]; then
  echo "ERROR: Directory $ROOT_FOLDER does not exist!"
  exit 1
fi

for experiment in "$ROOT_FOLDER"/**/; do
  rm "$experiment"/*.csv 2> /dev/null
  echo ">>> Processing $experiment"
  for execution in "$experiment"/**/; do
    executionCode=$(basename "$execution")
    rep=$executionCode
    counter=0
    for metric in "${PREPROCESS_FILES[@]}"; do
    # echo $metric
      for file in  "$execution/${metric}_"*.csv; do
        baseName=$(basename "$file" "$EXTENSION")
        nodeName=${baseName#${metric}_}
        awk -v rep="$rep" -v nodeName="$nodeName" -F "," 'NR > 1 {print rep "," nodeName "," $0}' "$file" >> "${experiment}/${metric}.csv"
        (( counter++ ))
      done
    done
    for metric in "${COPY_FILES[@]}"; do
        if [[ -e "$execution/$metric.csv" ]]; then
          awk -v rep="$rep" -F "," '{print rep "," $0}' "${execution}/$metric.csv" >> "${experiment}/${metric}.csv"
        fi
        (( counter++ ))
    done
  done
done
