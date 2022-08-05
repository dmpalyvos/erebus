# Erebus

In the following, we are providing necessary setup steps as well as instructions on how to run the
experiments from our paper and how to visualize them. All steps have been tested under **Ubuntu
20.04**.

## Setup

### Step 0: Dependencies

The following dependencies are not managed by our automatic setup and need to be installed
beforehand.:

- git
- wget
- maven
- unzip
- OpenJDK 1.8
- python3 (+ pip3)

For Ubuntu 20.04: `sudo apt-get install git wget maven unzip openjdk-8-jdk python3-pip python3-yaml libyaml-dev cython`

Below we assume the repository has been cloned into the directory `repo_dir`. 

### Step 1: Install Python Requirements

- Using a virtual environment is suggested. 
- Initializing the environment and running the experiments requires: `gdown pyyaml tqdm`
- Recreating the figures requires additionally: `pandas matplotlib seaborn numpy adjustText xlsxwriter` 

To install the python requirements automatically, from `repo_dir`, run *one* of the following commands:

```bash
# Full Dependencies (Running Experiments and Plotting)
pip install -r requirements.txt
# Minimal Dependencies (Running Experiments Only)
pip install -r requirements-minimal.txt

```

### Step 2: Automated Setup

This method will automatically download Apache Flink, Apache Kafka and the input datasets,
and compile the framework.

1. From `repo_dir`, run `./auto-setup.sh`
2. Run `./init-configs.sh NODE_TYPE` where NODE_TYPE is `odroid` or `server` to use the Kafka/Flink configurations used in our experiments


#### (Alternative) Manual Setup

In case of problems with the automatic setup, you can prepare the environment manually:

1. Download [Apache Flink 1.14.0](https://archive.apache.org/dist/flink/flink-1.14.0/flink-1.14.0-bin-scala_2.11.tgz) and decompress in `repo_dir`
2. Download [Apache Kafka 2.13-3.1.0](https://archive.apache.org/dist/kafka/3.1.0/kafka_2.13-3.1.0.tgz) and decompress in `repo_dir`
3. Get the input datasets, either using `./get-datasets.sh` or by manually downloading from [here](https://drive.google.com/uc?id=1464hH2-b7eKvtGh-tK_ku4aouFiagISw) and decompressing in `repo_dir/data/input`
4. Compile the two experiment jars, from repo dir: `mvn -f helper_pom.xml clean package && mv target/helper*.jar jars; mvn clean package`
2. Run `./init-configs.sh NODE_TYPE` where NODE_TYPE is `odroid` or `server` to use the Kafka/Flink configurations used in our experiments


## Running Experiments

### Starting Kafka


In your Kafka node (referred to as `kafka_node` below), run from `repo_dir`:
```bash
cd kafka_2.13-3.1.0
bin/zookeeper-server-start.sh config/zookeeper.properties
```
and then, in another terminal session (same directory):
```
bin/kafka-server-start.sh config/server.properties
```

### Automatic Reproduction of the Paper's Experiments and Plots

The `reproduce/` directory contains a script for each evaluation figure of the paper, which will run the experiment automatically, store the results, and create a plot. You need to provide the `#repetitions`, `duration` in minutes, and the hostname/port of the `kafka_node` as arguments.
For example, to reproduce Figure 6, run (from `repo_dir`):

```bash
# Reproduce Figure 6 of the paper for 3 repetitions of 10 minutes
./reproduce/figure6.sh 3 10 kafka_node:9092
```

The only exception is `./reproduce/figure11.sh`, which takes as arguments the number of forks, 
warmup iterations and regular iterations of JMH:
```bash
# Reproduce Figure 11 of the paper for 3 forks 10 warmup iterations and 25 regular iterations
./reproduce/figure6.sh 3 10 25
```

where kafka_node is the host running Kafka (`localhost` if you started Kafka on the same machine).

Results are stored in the folder `data/output`.
In case of problems with python dependencies (especially on Odroids), it is also possible to plot in a separate device, after running the experiment, by copying the result folder and calling reproduce/plot.py with the arguments of the respective reproduce script.

*Some experiment scripts print debugging information that
is usually safe to ignore, as long as the figures are generated successfully.*


#### Keeping explanations

By default, explanations are deleted after the experiment finishes because they can consume a lot of disk space. If you want to keep the explanations, pass the argument `--keep-outputs` to the experiment script.


## Appendix 

### Original Datasets

The original sets from our evaluation are found below:

- MOV: https://www.kaggle.com/rounakbanik/the-movies-dataset
- SGA: https://debs.org/grand-challenges/2014/ -> http://www.doc.ic.ac.uk/~mweidlic/sorted.csv.gz
- CAR: https://www.argoverse.org/

### PyYAML Issues

In case of errors related to YAML CLoader in Odroids, install `python3-yaml` using `apt-get` and do not use a python virtual environment. 
See here for more details: https://github.com/yaml/pyyaml/issues/108#issuecomment-370459912
