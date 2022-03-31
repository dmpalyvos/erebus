import os
import yaml
import argparse
from copy import copy


PARAMETER_CONFIG_KEY = 'dimensions'
# Parent of script directory 
REPO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)).rstrip(os.sep))

parser = argparse.ArgumentParser()
parser.add_argument('INPUT', help='The path to the input template YAML file')
parser.add_argument('OUTPUT', help='The path to the output YAML file')
args = parser.parse_args()

with open(args.INPUT, 'r') as file:
    raw_file = '\n'.join(file.readlines())
    raw_file = raw_file.replace('{repo_dir}', REPO_DIR)
    conf = yaml.load(raw_file, Loader=yaml.CLoader)
    schema = conf[PARAMETER_CONFIG_KEY]['schema']
    for i, parameter in enumerate(schema.split('.')):
        if i == 0:
            # Variants are already defined by user
            assert parameter == 'variant', 'First schema parameter should be "variant"'
            continue
        newVariants = []
        for variant in conf['variants']:
            filter_parameters = variant['filter'] if 'filter' in variant else {}
            filter_parameters_values = filter_parameters[parameter] if parameter in filter_parameters else ()
            for value in conf[PARAMETER_CONFIG_KEY][parameter]:
                if filter_parameters and value not in filter_parameters_values:
                  # if this variant only wants a subset of the filter_parameters
                  # and this one is not in it
                    continue
                configuredVariant = copy(variant)
                configuredVariant['args'] += f' --{parameter} {value} '
                configuredVariant['name'] += f'.{value}'
                if parameter in configuredVariant:
                  raise ValueError(f'{parameter} already in variant {configuredVariant["name"]}')
                configuredVariant[parameter] = value
                newVariants.append(configuredVariant)
        conf['variants'] = newVariants
    print(f'# {len(conf["variants"])} total runs')
    with open(args.OUTPUT, 'w+') as output_file:
        yaml.safe_dump(conf, output_file)
