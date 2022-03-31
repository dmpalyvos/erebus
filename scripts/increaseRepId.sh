#!/usr/bin/env bash

shopt -s nullglob # Prevent null globs

ROOT_FOLDER=$1
SHIFT=$2
if [[ -z "${SHIFT}" ]]; then
  echo "Please provide rep shift!"
  exit 1
fi

if [[ -z "$ROOT_FOLDER" ]]; then
  echo "Please provide root folder as argument"
  exit 1
fi

if [[ ! -e $ROOT_FOLDER ]]; then
  echo "ERROR: Directory $ROOT_FOLDER does not exist!"
  exit 1
fi

echo "[WARNING] Make sure that the min(repId) + shift is higher than the larger rep id!"
read -p "Press [ENTER] to continue"
cp -r "${ROOT_FOLDER}" "${ROOT_FOLDER}_backup"

for folder in "$ROOT_FOLDER"/**/; do
  echo ">>> Increasing rep IDs by $SHIFT in $folder"
  for execution in "$folder"/**/; do
    rep=$(basename "$execution")
    cmd="mv $folder/$rep $folder/$((rep + SHIFT))"
    # echo "$cmd"
    eval "$cmd"
  done
done
