#!/bin/bash

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
data_folder="$script_dir/../data/remote"
[[ -e $data_folder ]] || { echo "ERROR: Directory does not exist: $data_folder"; exit 1; }

function usage() {
  echo "Usage: $0 node commit remote_dir"
  exit 1
}

[[ -z $1 ]] && usage
[[ -z $2 ]] && usage
[[ -z $3 ]] && usage

[[ -e $data_folder/$2 ]] && { echo "Experiment $2 already exists!"; exit 3; }

ssh "$1" "cd $3 && zip -r $2.zip $2"
scp -r "$1:$3/$2.zip" "$data_folder"
cd "$data_folder" || exit 1
unzip "$2.zip"
