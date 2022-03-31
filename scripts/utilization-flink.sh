#!/usr/bin/env bash

[[ -z $1 ]] && { echo "Please provide valid statistics folder"; exit 1; }
[[ ! -e $1 ]] && { echo "Please provide valid statistics folder"; exit 1; }

CPU_FILE="$1/cpu.csv"
MEMORY_FILE="$1/memory.csv"
HOSTNAME=$(hostname)

NUMBER_CPUS=$(nproc)
TOTAL_MEMORY=$(awk '/MemTotal/ {print $2 / 1024}' /proc/meminfo)

recordUtilization() {
    CPUMEM=$(top -b -n 1 -p"$(pgrep -d, -f "$1")" 2> /dev/null)
    CPUMEM=$(echo "$CPUMEM" | awk -v ncpu="$NUMBER_CPUS" -v nmem="$TOTAL_MEMORY" 'BEGIN{ cpu = 0; memory = 0; } NR > 7 { cpu+=$9; memory += $10 } END { print cpu/ncpu, memory*nmem/100; }')
    CPU=$(echo "$CPUMEM" | cut -d' ' -f1)
    MEM=$(echo "$CPUMEM" | cut -d' ' -f2)
    TIME_SECONDS=$(date +%s)
    echo "$HOSTNAME,$TIME_SECONDS,$CPU" >> "$CPU_FILE"
    echo "$HOSTNAME,$TIME_SECONDS,$MEM" >> "$MEMORY_FILE"
}

while sleep 1; do
    recordUtilization "org.apache.flink.runtime.taskexecutor.TaskManagerRunner" 
done