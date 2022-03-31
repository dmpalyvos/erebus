#!/usr/bin/env bash
set -e

echo "Downloading datasets..."
gdown -O data/input/datasets.zip https://drive.google.com/uc?id=1464hH2-b7eKvtGh-tK_ku4aouFiagISw
echo
echo "Extracting files..."
echo
unzip -q data/input/datasets.zip -d data/input/
rm data/input/datasets.zip
echo
echo "Download successful!"
echo
