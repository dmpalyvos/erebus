#!/usr/bin/env python3
import yaml
import sys
from argparse import ArgumentParser
import subprocess
import datetime
import os
import shlex
import logging
import signal
from threading import Thread, Lock
import time
import shutil
import random
import socket


CONFIGURATION_FILE = 'scripts/config.yaml'
EXPERIMENT_CONFIG_NAME = 'experiment.yaml'
EXECUTION_LOG_NAME = 'output.log'
EXECUTION_CONFIG_NAME = 'exec_config.sh'
COMPILE_SCRIPT = 'scripts/compile_template.py'
MAX_RETRIES = 2
WAIT_EXTRA_SECONDS = 120
WAIT_BEFORE_SIGKILL_SECONDS = None # None means wait forever. Use that only if experiment script is sure to terminate
FAILED_EXPERIMENTS = []

ACTIVE_PROCESSES = set()
OPEN_DIRECTORIES = set()


def show_progress_bar(progress, start_time):
    barLength = 30  # Modify this to change the length of the progress bar
    status = ""
    if isinstance(progress, int):
        progress = float(progress)
    if not isinstance(progress, float):
        progress = 0
        status = 'error: progress var must be float'
    if progress < 0:
        progress = 0
        status = 'Halt...'
    if progress >= 1:
        progress = 1
        status = 'Done...'
    block = int(round(barLength * progress))
    text = 'Progress: [{0}] {1:.2f}% {2}'.format(
        '#' * block + '-' * (barLength - block),
        progress * 100, status)
    logging.info(text)
    # Estimate remaining time
    duration = time.time() - start_time
    remainingTime = (duration / progress) - duration
    remainingTime = remainingTime / 60  # convert to minutes
    logging.info(
        'Estimated Remaining Time: {:.2f} minutes'.format(remainingTime))


def execute_quick(command, timeout=10):
    return subprocess.run(shlex.split(command), stdout=subprocess.PIPE, timeout=timeout).stdout \
        .decode('utf-8').strip()


def execute(command):
    process = subprocess.Popen(shlex.split(
        command), stderr=subprocess.STDOUT, stdout=subprocess.PIPE)
    ACTIVE_PROCESSES.add(process)
    return process


def monitor(process):
    def continuous_output(process):
        for line in process.stdout:
            logging.info(line.decode('utf-8').strip())

    t = Thread(target=continuous_output, args=(process,))
    t.start()


def rm_open_directories():
    for directory in OPEN_DIRECTORIES:
        try:
            logging.info(f'Removing directory: {directory}')
            shutil.rmtree(directory, ignore_errors=True)
        except Exception as e:
            logging.warning(e)
    OPEN_DIRECTORIES.clear()


def stop_active_processes(sig):
    for process in ACTIVE_PROCESSES:
        try:
            process.send_signal(sig)
        except Exception as e:
            logging.error(
                f'Exception when terminating process {process.args[0]} ({process.pid}): {e}')
    ACTIVE_PROCESSES.clear()


def exit_with_cleanup(signum=None, frame=None):
    logging.info("Cleaning up and exiting...")
    stop_active_processes(signal.SIGTERM)
    sys.exit(0)


def load_config(path):
    with open(path, 'r') as file:
        return yaml.load('\n'.join(file.readlines()), Loader=yaml.CLoader)


def verify_experiment_config(config):
    if has_duplicates([variant['name'] for variant in config['variants']]):
        raise AssertionError(f'Duplicate variant name')


def has_duplicates(list):
    return len(list) != len(set(list))


def get_parser():
    parser = ArgumentParser()
    parser.add_argument(
        'experiment', help='Experiment configuration', type=str)
    parser.add_argument('--duration', '-d', help='Experiment duration, in minutes',
                        type=float,
                        default=1)
    parser.add_argument('--kafkaHost', help='Kafka bootstap server, in format host:port',
                        type=str, required=True)
    parser.add_argument('--variants', '-v', help='Variants to run', type=str,
                        nargs='*', default=None)
    parser.add_argument('--logLevel', dest='logLevel',
                        help='Set experiment log level', type=str)
    parser.add_argument('--resume', type=str, help='Resume experiment with given code',
                        default=None)
    parser.add_argument('--code', '-c', type=str, help='Use custom code instead of day_time',
                        default=None)
    parser.add_argument(
        '--reps', '-r', help='Number of repetitions', type=int, default=1)
    parser.add_argument(
        '--dry', help='Dry run, just print commands and create dirs', dest='dry', action='store_true')
    parser.add_argument(
        '--keep-outputs', help='Keep sink output files for validation (can be very large)', dest='keep_outputs', action='store_true')
    parser.set_defaults(dry=False)
    parser.set_defaults(keep_outputs=False)
    return parser


def init_logging(output_folder):
    logging.basicConfig(level=logging.INFO,
                        format='%(message)s',
                        handlers=[logging.StreamHandler(),
                                  logging.FileHandler(
                            f'{output_folder}/{EXECUTION_LOG_NAME}')
                        ])


def get_variants(experiment_config, selected_variants):
    # Filter variants
    if selected_variants:
        filtered_variants = []
        for variant in experiment_config['variants']:
            for variant_pattern in selected_variants:
                if variant_pattern in variant['name']:
                    filtered_variants.append(variant)
        return filtered_variants
    else:
        return experiment_config['variants']


''' Format the command using all simple parameters defined in variant, experiment_config and kwargs '''


def get_command(template, variant, experiment_config, **kwargs):

    def append_simple_parameters(source, destination):
        for (k, v) in source.items():
            if k in destination:
                print(source[k])
                print('-----')
                print(destination[k])
                raise ValueError(f'Duplicate configuration parameter: {k}')
            if isinstance(v, (str, int, float)):
                destination[k] = v

    template_args = dict(kwargs)
    append_simple_parameters(experiment_config, template_args)
    append_simple_parameters(variant, template_args)
    template = str(template) if isinstance(template, int) else template # Edge edge for kafkaPartitions
    command = template.format(**template_args)
    # YAML adds spaces that confuse the bash executor, remove them
    return command.replace('\n', ' ')


def get_optional_command(key, variant, experiment_config, **kwargs):
    if key in variant:
        return get_command(variant[key], variant, experiment_config, **kwargs)
    else:
        return ''


def create_statistics_folder(statistics_folder):
    if os.path.exists(statistics_folder):
        return False
    os.makedirs(statistics_folder)
    OPEN_DIRECTORIES.add(statistics_folder)
    return True


def create_execution_config(folder, **kwargs):
    execution_config_path = f'{folder}/{EXECUTION_CONFIG_NAME}'
    with open(execution_config_path, 'w') as execution_config:
        for (key, value) in kwargs.items():
            execution_config.write(f'{key.upper()}="{value}"\n')
    return execution_config_path


def clear_outputs(folder):
    logging.info(f'Clearing *.out files from {folder}')
    execute_quick(f"find {folder} -name '*.out' -delete", timeout=30)


if __name__ == '__main__':

    signal.signal(signal.SIGINT, exit_with_cleanup)
    signal.signal(signal.SIGTERM, exit_with_cleanup)

    config = load_config(CONFIGURATION_FILE)
    args, unknown_args = get_parser().parse_known_args()

    duration_seconds = int(args.duration * 60)

    base_dir = os.getcwd()
    commit_hash = execute_quick('git rev-parse --short HEAD')
    time_code = datetime.datetime.now().strftime(
        '%j_%H%M') if not args.code else args.code
    hostname = socket.gethostname().split('\.')[0]
    experiment_code = f'{commit_hash}_{hostname}_{time_code}' if not args.resume else args.resume

    output_folder = os.path.normpath(config['output_folder'] + os.sep + experiment_code)
    if args.resume:
        print('[WARNING] Resuming experiment might give innacurate results!')

    try:
        os.mkdir(output_folder)
    except FileExistsError:
        if not args.resume:
            print(
                f'Warning: Directory {output_folder} already exists! Proceeding anyway in 3 seconds...')
            time.sleep(3)

    init_logging(output_folder)

    # Extra execution arguments
    jvm_args = ''
    extra_args = ' '.join(unknown_args)
    if args.logLevel:
        extra_args += f' --logLevel {args.logLevel}'
        # For affinity library
        jvm_args += f'-Dorg.slf4j.simpleLogger.defaultLogLevel={args.logLevel.lower()} '

    logging.info('Compiling experiment template')
    execute_quick(
        f'python3 {COMPILE_SCRIPT} {args.experiment} {output_folder}/{EXPERIMENT_CONFIG_NAME}')
    experiment_config = load_config(
        f'{output_folder}/{EXPERIMENT_CONFIG_NAME}')
    verify_experiment_config(experiment_config)
    variants = get_variants(experiment_config, args.variants)

    # Save final configuration in output folder
    experiment_config['variants'] = variants
    experiment_config['duration'] = args.duration
    experiment_config['code'] = experiment_code

    with open(f'{output_folder}/{EXPERIMENT_CONFIG_NAME}', 'w') as f:
        f.write(yaml.dump(experiment_config, indent=4))

    total_runs = args.reps * len(variants)
    variant_names = [v['name'] for v in variants]
    total_duration = total_runs * args.duration
    logging.info('-'*100)
    logging.info(f'> Repetitions: {args.reps}')
    logging.info(f'> Query: {experiment_config["query"]}')
    logging.info('> Variants: %s', ','.join(variant_names))
    logging.info(
        f'> Total Duration = {total_duration} minutes ({total_duration/60:.1f} hours)')
    logging.info('-'*100)
    time.sleep(5)
    start_time = time.time()
    run_count = 0

    utilization_command_template = experiment_config['utilization_command']
    executor_script = experiment_config['executor_script']
    job_name = experiment_config['job_name'] if 'job_name' in experiment_config else 'UNDEFINED'
    for rep in range(1, args.reps+1):
        query = experiment_config['query']
        random.shuffle(variants)
        for variant in variants:
            run_name = f'{query}_{variant["name"]}'
            statistics_folder = f'{base_dir}/{output_folder}/{run_name}/{rep}'
            logging.info('-'*100)
            logging.info(f'Executing {run_name} / rep {rep}')

            # Pre-process kafkaPartitions parameter so that it can be used as a template argument inside
            # the spe_command
            kafka_partitions = get_optional_command('kafkaPartitions', variant, experiment_config)
            kafka_partitions = int(kafka_partitions) if kafka_partitions else 1
            variant['kafkaPartitions'] = kafka_partitions

            # Init commands
            spe_command = get_command(variant['spe_command'], variant, experiment_config, jvm_args=jvm_args,
                                      statistics_folder=statistics_folder, duration_seconds=duration_seconds,
                                      rep=rep, extra_args=extra_args, kafkaHost=args.kafkaHost)
            utilization_command = get_command(utilization_command_template, variant, experiment_config,
                                              statistics_folder=statistics_folder, kafkaHost=args.kafkaHost)
            datasource_command = get_optional_command(
                'datasource_command', variant, experiment_config, statistics_folder=statistics_folder,
                kafkaHost=args.kafkaHost)

            if args.dry:
                print(spe_command)
                print(datasource_command)
                print(utilization_command)
                print(f'Kafka partitions = {kafka_partitions}')
                continue

            success = False
            for retry in range(MAX_RETRIES):
                OPEN_DIRECTORIES.clear()
                if not create_statistics_folder(statistics_folder) and args.resume:
                    # If resuming partial previous execution
                    # and this run has finished before, continue
                    logging.info(
                        f'{run_name} / {rep} already executed, skipping...')
                    run_count += 1
                    break
                logging.info(f'Attempt: {retry}')
                logging.info(f'[{time.asctime()}] {spe_command}')
                logging.info(statistics_folder)
                logging.info('-'*100)
                # Parameters passed to the execution script
                execution_config_path = create_execution_config(
                    statistics_folder, spe_command=spe_command, utilization_command=utilization_command, datasource_command=datasource_command,
                    duration_seconds=duration_seconds, statistics_folder=statistics_folder, experiment_yaml=f'{output_folder}/{EXPERIMENT_CONFIG_NAME}', job_name=job_name,
                    kafka_partitions=kafka_partitions, kafka_host=args.kafkaHost)
                experiment_process = execute(
                    f'{executor_script} {execution_config_path}')
                monitor(experiment_process)

                try:
                    exit_code = experiment_process.wait(
                        duration_seconds+WAIT_EXTRA_SECONDS)
                except subprocess.TimeoutExpired:
                    logging.warning('Experiment timed out. Forcing exit')
                    stop_active_processes(signal.SIGTERM)
                    try:
                        exit_code = experiment_process.wait(WAIT_BEFORE_SIGKILL_SECONDS)
                    except subprocess.TimeoutExpired:
                        logging.error(
                            'Failed to cleanup nicely. Sending SIGKILL to active processes. Experiment might be incomplete...')
                        stop_active_processes(signal.SIGKILL)
                        exit_code = 1

                logging.info(f'Experiment exited with code {exit_code}')

                # Execution finished successfully or was terminated by this script
                if exit_code == 0 or exit_code == -signal.SIGTERM.value:
                    success = True
                    break
                # Execution terminated by outside signal
                elif exit_code < 0:
                    exit_with_cleanup()
                else:
                    # Try once again...
                    rm_open_directories()
                    continue

            run_count += 1
            if not success:
                logging.error('Experiment FAILED')
                FAILED_EXPERIMENTS.append(f'{run_name}/{rep}')
            if not args.keep_outputs:
                clear_outputs(statistics_folder)
            time.sleep(config['sleep_after'] * 60)
            show_progress_bar(run_count / total_runs, start_time)

    execute_quick(f'./scripts/preprocess.sh {output_folder}', timeout=300)
    if FAILED_EXPERIMENTS:
        logging.error(
            f'Some experiments FAILED: {", ".join(FAILED_EXPERIMENTS)}')
    logging.info(f'[{time.asctime()}] Finished experiment {experiment_code}')
    exit_with_cleanup()
