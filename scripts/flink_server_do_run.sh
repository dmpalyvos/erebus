#!/usr/bin/env bash

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)

# Flink folder must exist at the top-level repository directory
FLINK_VERSION="1.14.0"
FLINK_PATH="$script_dir/../flink-${FLINK_VERSION}"
START_CLUSTER="$FLINK_PATH/bin/start-cluster.sh"
STOP_CLUSTER="$FLINK_PATH/bin/stop-cluster.sh"
CREATE_KAFKA_TOPICS="$script_dir/kafka-init.sh"

[[ -e $FLINK_PATH ]] || { echo "Error: Flink not found at $FLINK_PATH"; exit 1; }

FORCE_GC_CMD="jcmd | grep org.apache.flink.runtime.taskexecutor.TaskManagerRunner | cut -d ' ' -f 1 | xargs -I {} jcmd {} GC.run"

# CLI ARGS
usage() { echo "Usage: $0 COMMANDS_FILE " 1>&2; exit 1; }

# Experiment script
EXPERIMENT_COMMANDS=$1
if [ -z "$EXPERIMENT_COMMANDS" ]; then
  usage
fi
if [ ! -e "$EXPERIMENT_COMMANDS" ]; then
  echo "Experiment file $1 does not exist!"
  exit 1
fi
shift 


source "$EXPERIMENT_COMMANDS" # See run.py#create_execution_config for loaded parameters

datasource_pid=""
spe_pid=""
spe_output=""
utilization_pid=""
job_stopper_pid=""

checkStatusAndExit() {
  echo "Checking status and exiting"
  wait "$spe_pid"
  spe_exit_code="$?" 
  if [[ $spe_output == *JobCancellationException* ]]; then
    echo "[EXEC] Flink Job Cancelled"
    exit 0
  fi
  if [[ $spe_output == *JobSubmissionException* ]]; then
    echo "$spe_output"
    echo "[EXEC] Failed to submit flink job!"
    exit 1 
  fi
  if (( spe_exit_code > 0 && spe_exit_code < 128)); then
    echo "$spe_output"
    echo "[EXEC] SPE exited with error: $spe_exit_code"
    exit "$spe_exit_code"
  fi
  echo "$spe_output"
  echo "[EXEC] Success (exit code: $spe_exit_code)"
  exit 0
}

stopFlinkCluster() {
  eval "$STOP_CLUSTER"
  sleep 15
  # Make absolutely sure that there is no leftover TaskManager
  pgrep -f "org.apache.flink.runtime.taskexecutor.TaskManagerRunner" | xargs -I {} kill -9 {}
  rm -r /tmp/flink-*  &>/dev/null
  rm -r /tmp/blobStore-*  &>/dev/null
}

startFlinkCluster() {
  eval "$START_CLUSTER"
  sleep 15
}

clearActiveProcs() {
  [[ -n $datasource_pid ]] && kill "$datasource_pid"
  [[ -n $utilization_pid ]] && kill "$utilization_pid"
  [[ -n $job_stopper_pid ]] && kill "$job_stopper_pid"
  stopFlinkCluster
  REAL_DURATION=$SECONDS
  echo "[EXEC] Experiment Duration: $REAL_DURATION"
  checkStatusAndExit
}


trap clearActiveProcs SIGINT SIGTERM

stopFlinkCluster

if ! eval "$CREATE_KAFKA_TOPICS" "$KAFKA_PARTITIONS" "$KAFKA_HOST"; then
  echo "Failed to create kafka topics" 1>&2
  exit 1
fi

startFlinkCluster

eval "$FORCE_GC_CMD" > /dev/null
[[ -n $DATASOURCE_COMMAND ]] && eval "$DATASOURCE_COMMAND"
datasource_pid="$!"

eval "$UTILIZATION_COMMAND &"
utilization_pid="$!"

python3 "scripts/flinkJobStopper.py" "$DURATION_SECONDS" &
job_stopper_pid="$!"

echo "[$(date)] Starting query..." 1>&2
SECONDS=0
spe_output=$($SPE_COMMAND 2>&1) 
spe_pid="$!"

clearActiveProcs
