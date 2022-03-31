#!/usr/bin/env python3

import pandas as pd
import matplotlib
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import os
import glob 
from math import floor, ceil, sqrt
import yaml
from tqdm import tqdm
from collections import defaultdict
from adjustText import adjust_text
import argparse

''' Compability with jupyter code'''
def display(obj):
  print(obj)


# Plot visual config
plt.style.use('ggplot')
matplotlib.rcParams['pdf.fonttype'] = 42
matplotlib.rcParams['ps.fonttype'] = 42
matplotlib.rcParams['hatch.linewidth'] = 0.2
matplotlib.rcParams['xtick.labelsize'] = 10
sns.set_palette(sns.color_palette('Set2', n_colors=14, desat=0.9))
sns.set_style("ticks")


###############
### Config ###
###############
# Used to sort variants
BASIC_VARIANT_ORDER = ['NP', 'MA-1', 'MA-P']
# Figures 
EXPORT_FOLDER='./figures'

AXIS_TITLE_SIZE = 14
AXIS_LABEL_SIZE = 12
LEGEND_TITLE_SIZE = 12
LEGEND_LABEL_SIZE = 12

######################
# Auto-set globals
######################
REPORT_FOLDER=''
COMMIT = None
DATA = None
VARIANT_ORDER = None
VARIANT_PARAMETERS=set()

OPERATOR_NAMES = {'SmartGridAnomaly': {'ANOMALY-LIMIT-FILTER': 'F4', 'INTERVAL-ENDS-FILTER': 'F3', 'JOIN': 'J1', 'SINK': 'K'},
                  'LinearRoadAccident': {'FILTER-ACCIDENT': 'F4', 'FILTER-STOPPED': 'F3', 'FILTER-ZERO-SPEED': 'F2', 'SINK': 'K'},
                  'Movies': {'RATING-VALUE-FILTER': 'F3', 'RATINGS-COUNT-FILTER': 'F1', 'USER-RATING-JOIN': 'J1', 'YEAR-FILTER': 'F2', 'SINK': 'K'},
                  'CarLocalMerged': {'FILTER-BICYCLES': 'F1', 'FILTER-CYCLES-INFRONT': 'F2', 'FILTER-PEDESTRIANS': 'F3', 'JOIN': 'J1', 'SINK': 'K'}
                 }
PARAMETER_LABELS = {'rate': 'Throughput (t/s)', 'latency': 'Latency (s)', 'past-rate': 'Past Explanations (t/s)', 
                    'predicate-out': 'Explanations (t/s)', 'cpu': 'CPU (%)', 'memory': 'Memory (GB)', 
                    'predicate-in': 'Intercepted (t/s)'}


# Discard warmup and cooldown
WARMUP_PERCENTAGE = 15
COOLDOWN_PERCENTAGE = 15
VARIANT_NP = 'NI'
VARIANT_EREBUS = 'EB'
VARIANT_EREBUS_WP = 'EB+WP'
VARIANT_NAME_TRANSFORMS = {'NP': VARIANT_NP, 'MA-1': VARIANT_EREBUS, 'MA-P': VARIANT_EREBUS_WP}







#######################



# Fetch from remote
remoteNode=''
remoteDir="streaming-why-not/data/output"
experimentToFetch=None

def selectExperimentToFetch(experimentCode):
    global experimentToFetch
    experimentToFetch = experimentCode
    

def select_remote_node(node):
    global remoteNode
    remoteNode = node
    sshOutput = subprocess.check_output(['ssh', remoteNode, f'cd {remoteDir} && ls -td */'], stderr=subprocess.PIPE, encoding='UTF-8')
    interact(selectExperimentToFetch, experimentCode=sshOutput.replace('/','').split('\n'))

def fetchExperiment():
    if not experimentToFetch:
        print('No experiment selected')
        return
    print(f'Fetching experiment: {experimentToFetch}')
    try:
        subprocess.check_output(['./scripts/fetch.sh', remoteNode, experimentToFetch, remoteDir],
                                                stderr=subprocess.PIPE,
                                                encoding='UTF-8')
    except subprocess.SubprocessError as e:
        print('Failed to fetch:', e.output)
    print('Done!')



def get95CI(data):
    return (1.96*np.std(data))/np.sqrt(len(data))

def save_fig(fig, name, experiment, export=False):
    
    def do_save_fig(fig, figpath):
        print(f'Saving {figpath}')
        fig.savefig(figpath, pad_inches=.1, bbox_inches='tight',)
        
    filename = f'{experiment}_{name}.pdf'
    do_save_fig(fig, f'{REPORT_FOLDER}/{filename}')
    if export:
        do_save_fig(fig, f'{EXPORT_FOLDER}/{filename}')


def percentageDiff(value, reference):
    if reference == 0 or np.isnan(reference):
        return np.nan
    return 100*(value - reference) / reference


def sum_dropna(a, **kwargs):
    if np.isnan(a).all():
        return np.nan
    else:
        return np.sum(a, **kwargs)
    

def aggregate_node_rep(data, parameter, dimensions, t_agg=np.mean, node_agg=None, new_name=None):
    result = data[data.parameter == parameter].groupby(['parameter', 'variant', 'rep', 'node'] + dimensions, )\
                                    .aggregate({'value': t_agg})\
                                    .reset_index()
    if new_name:
        result['parameter'] = new_name
    return result    
    
def aggregate_rep(data, parameter, dimensions,t_agg=np.mean, node_agg=np.mean, new_name=None):
    result = aggregate_node_rep(data, parameter, dimensions, t_agg, new_name)\
                                    .groupby(['parameter', 'variant', 'rep'] + dimensions)\
                                    .aggregate({'value': node_agg})\
                                    .reset_index()
    if new_name:
        result['parameter'] = new_name
    return result

    
def roundPivot(row):
    nr = row.copy().astype(str)
    for i in range(len(row)):
        nr[i] = f'{row[i]:0.3f}'
    return nr.astype(float)  


def computePercentageDiffs(row, bl_index):
    nr = row.copy().astype(str)
    for i in range(len(row)):
        if i == bl_index:
            continue
        pdiff = percentageDiff(row[i], row[bl_index])
        if np.isnan(pdiff):
            nr[i] = 'missing'
            continue
        nr[i] = f'{pdiff:+0.0f}%' if abs(pdiff) < 100 else f'{row[i]/float(row[bl_index]):+0.1f}x'
    nr[bl_index] = '-'
    return nr

def pivot_table(data, index, parameter, dimensions):

    pv = pd.pivot_table(data[data.parameter==parameter], index=index, columns=dimensions, values=['value'])
    try:
        pv.columns = pv.columns.droplevel()
    except:
        print('No levels to remove')
    rounded = pv.apply(roundPivot)
    print(parameter)
    display(rounded)
        
    file = f'{REPORT_FOLDER}/{parameter}.xlsx'
    with pd.ExcelWriter(file) as writer:
        rounded.to_excel(writer, sheet_name='Absolute Values')
        for i, variant in enumerate(pv.index):
            if isinstance(variant, tuple):
                variant = '-'.join(str(v) for v in variant)
            relative = pv.apply(computePercentageDiffs, args=(i, ), axis=0)
            relative.to_excel(writer, sheet_name=f'Relative Diffs {variant}')
            display(relative)
        print(f'Saved {file}')

def create_tables(data, index=['variant'], parameters=['throughput', 'latency'], dimensions=['provenanceActivator']):
    for parameter in parameters:
        try:
            pivot_table(data, index, parameter, dimensions)
        except:
            print(f'Failed to create table {parameter}')

    
def time_series(data, parameters, extra_group=None):
    def linePerVariant(*args, **kwargs):
        data = kwargs.pop('data')
        ax = plt.gca()
        data.dropna().groupby('t').aggregate({'value': np.mean}).rolling(30, min_periods=1).mean().reset_index().plot(x='t', y='value', ax=ax, **kwargs)
    plot_data = data.copy()
    plot_data = plot_data[plot_data.parameter.isin(parameters)]
    if extra_group:
        plot_data[extra_group] = plot_data[extra_group].astype(str)
        plot_data['hue'] = plot_data[['variant', extra_group]].agg('-'.join, axis=1)
    else:
        plot_data['hue'] = plot_data['variant']
    g = sns.FacetGrid(plot_data, col='parameter', col_order=parameters, hue='hue', sharey=False, height=2, aspect=2)
    g.map_dataframe(linePerVariant)
    g.set_titles('{col_name}')
    g.add_legend()
    for i, parameter in enumerate(parameters):
        if 'latency' in parameter:
            for ax in g.axes[:, i]:
                ax.set_yscale('log')
        
    save_fig(g.fig, 'time-series', experimentId(), export=False)


def set_axis_info(axes, idx, title, xlabel, ylabel='', yscale='linear', title_size=14, label_size=12):
    axes.flat[idx].set_title(title, fontsize=title_size, pad=8)
    axes.flat[idx].set_xlabel(xlabel, fontsize=label_size)
    axes.flat[idx].set_ylabel(ylabel, fontsize=label_size)
    axes.flat[idx].set_yscale(yscale)

    
def display_percentage_diffs(axes, ref_idx=None, end_idx=None):
    for i, ax in enumerate(axes.flat):
        print(f'Percent difference from REF => [{ax.get_title()}]:', end=' ')
        axisHeight = ax.get_ylim()[1]
        ref_idx = 0 if not ref_idx else ref_idx
        end_idx = len(ax.patches) if not end_idx else end_idx
        referenceHeight = ax.patches[ref_idx].get_height()
        if referenceHeight == 0 or not np.isfinite(referenceHeight):
            continue
        # ax.patches[4].set_hatch('///')
        # ax.patches[5].set_hatch('///')
        # ax.patches[6].set_hatch('///')
        for p in ax.patches[ref_idx+1:end_idx]:
            height = p.get_height()
            if not np.isfinite(height):
                continue
            diff = percentageDiff(height, referenceHeight)
            diffText = f'{diff:+2.1f}%' if diff <= 100 else f'{float(height) / float(referenceHeight):+0.1f}x'
            print(diffText, end=' ')
            textHeight = .2*height if height / axisHeight > 0.6 else height + (axisHeight * 0.1)
            ax.text(p.get_x()+p.get_width()/2,
                    textHeight,
                    diffText,
                    ha='center', rotation=90, size=10, family='sans', weight='normal', color='#3f3f3f')
        print()
        
def query_sink(data):
    return data[data['node-name'].str.contains('SINK')]

def not_query_sink(data):
    return data[~data['node-name'].str.contains('SINK')]


def variantOrderKey(variant):
    for idx, variantPart in enumerate(BASIC_VARIANT_ORDER):
        if variantPart in variant:
            return idx
    print(f'[WARNING] Unknown variant: {variant}')
    return 1000

def predicateOrderKey(predicate):
    if predicate == 'none':
        return -5
    if predicate == 'passive':
        return -4
    if predicate == 'true':
        return -3
    return sum(map(ord, predicate))


def predicate_selectivity(data):
    base_columns = list(set(data.columns) - set(['parameter', 'value']))
    pout = data[data.parameter == 'predicate-out']
    pin = data[data.parameter == 'predicate-in']
    joined = pd.merge(pout, pin, on=base_columns, suffixes=('_out', '_in'))
    joined['value'] = 100*(joined['value_out'] / joined['value_in'])
    joined.loc[joined.value > 100, 'value'] = 100 # Fix slight measurement errors that might break layout
    joined = joined.drop(columns=['value_in', 'value_out', 'parameter_in', 'parameter_out'])
    joined['parameter'] = 'predicate-selectivity'
    return joined


def check_failed_reps(data, pct_limit):
    durations = data.groupby(['experiment', 'variant', 'rep'] + list(VARIANT_PARAMETERS)).t.max().dropna()
    avg_duration = durations.mean()
    short = durations[durations < pct_limit*avg_duration]
    long = durations[durations > (avg_duration + pct_limit*avg_duration)]
    print(f'Average duration = {int(avg_duration)}s')
    if len(short) > 0:
        print(f'[WARN] {len(short)} short (potentially crashed) executions:')
        for s in short.index:
            print('\t- ', '.'.join([str(i) for i in s]), f'(duration={int(short[s])}s)')
        print()
    if len(long) > 0:
        print(f'[WARN] {len(long)} long (potentially delayed) executions:')
        for s in long.index:
            print('\t- ', '.'.join([str(i) for i in s]), f'(duration={int(long[s])}s)')
        print()


def sorted_ls_by_experiment(path):
    
    def sortkey(folder):
        try:
            parts = folder.split('_')
            commit = parts[0]
            node = parts[1]
            day = int(parts[2])
            time = int(parts[3])
            return (day, time, node, commit)
        except:
            return (0,0,'','')
        
    dirs = [directory for directory in os.listdir(path) if os.path.isdir(os.path.join(path, directory))]
    return sorted(dirs, key=sortkey, reverse=True)

def is_ignored_file(path):
    return False


def loadData(folder):
    global DATA
    global VARIANT_ORDER
    
    def removeWarmupCooldown(df):
        tmax = df.t.max()
        warmup = floor(tmax * (WARMUP_PERCENTAGE/100))
        cooldown = ceil(tmax - (tmax * COOLDOWN_PERCENTAGE/100))
#         print(f'Removing [0, {warmup}) and ({cooldown}, {tmax}]')
        df.loc[(df.t < warmup) | (df.t > cooldown), 'value'] = np.nan
        return df
    
    def subtractMin(df, key):
        df[key] -= df[key].min()
        return df

    def readCsv(file):
        if not file:
            return pd.DataFrame()
        df = pd.read_csv(f'{file}', names=('rep', 'node', 't', 'value'))
        df['rep'] = df['rep'].astype(int)
        df['value'] = df['value'].astype(float)
        df['t'] = df['t'].astype(int)
        if len(df) == 0:
            return df
        df = df.groupby(['rep']).apply(subtractMin, key='t')
#         print(file)
#         display((df.groupby(['t', 'rep'])['t'].size()))
        df = df.groupby(['rep']).apply(removeWarmupCooldown)
        return df

    VARIANT_PARAMETERS.clear()
    dataFrames = []
    with open(f'{folder}/experiment.yaml') as infoYaml:
        experimentInfo = yaml.load('\n'.join(infoYaml.readlines()), Loader=yaml.CLoader)
        variantSchema = experimentInfo['dimensions']['schema'].split('.')
        assert variantSchema[0] == 'variant'
        VARIANT_PARAMETERS.update(variantSchema[1:])
    for experimentFolder in tqdm(os.listdir(folder)):
#         print(experimentFolder)
        if not os.path.isdir(folder + '/' + experimentFolder):
            continue
        parts = experimentFolder.split('_')
        experimentName, experimentVariant = parts[0], '_'.join(parts[1:]) # First _ separates experiment, rest are kept as is
#         print(experimentName, experimentVariant)
        for dataFile in glob.glob(folder + '/' + experimentFolder + '/' + '*.csv'):
            if is_ignored_file(dataFile):
                continue
            parameter = dataFile.split('/')[-1].split('.')[0]
#             print(f'Loading {dataFile}')
            try:
                df = readCsv(dataFile)
                if len(df) == 0:
#                     print(f'Skipping {dataFile}')
                    continue
            except Exception as e:
                print(f'Failed to read {dataFile}')
                raise e
            df['parameter'] = parameter
            df['experiment'] = experimentName
            df['variant'] = experimentVariant
            df[variantSchema] = df.variant.str.split('\.', expand=True)
#             df[variantSchema[1:]] = df[variantSchema[1:]].apply(pd.to_numeric)
            dataFrames.append(df)
    DATA = pd.concat(dataFrames, sort=False)
        
        
    # Preprocess 
    DATA.loc[DATA['parameter'].isin(['latency', 'memory']), 'value'] /= 1e3 # Convert latency to seconds, memory to GB
    DATA.loc[(DATA['parameter'].isin(['latency', 'provsize']) & (DATA['value'] < 0)), 'value'] = np.nan # Negative average values mean missing
    DATA.loc[(DATA['parameter'].isin(['provsize']) & (DATA['variant'] != 'MA-P')), 'value'] = np.nan # Provenance size interesting only for provenance variant
    DATA.loc[(DATA.parameter == 'sink-rate') & (DATA.node.str.contains('DROPPED')), 'parameter'] = 'answer-rate'

    node_parts = DATA['node'].str.split('_', expand=True)
    DATA['node-name'] = node_parts[0]
    DATA['node-index'] = node_parts[1]
    
    # Data type fix
    for dimension in ['parallelism', 'predicateDelay', 'bufferDelay', 'syntheticFilterDiscardRate', 'syntheticPredicateSelectivity']:
        if dimension in DATA.columns:
            print(f'Converting {dimension} values to int')
            DATA[dimension] = DATA[dimension].astype(int)
        
    VARIANT_ORDER = sorted(list(DATA.variant.unique()), key=variantOrderKey)
    print(f'Variant order = {VARIANT_ORDER}')
    DATA.variant = DATA.variant.astype("category")
    DATA.variant = DATA.variant.cat.set_categories(VARIANT_ORDER) 
    
    if 'predicate' not in DATA.columns:
        DATA['predicate'] = 'undefined'
    PREDICATE_ORDER = sorted(list(DATA.predicate.unique()), key=predicateOrderKey)
    # print(f'Predicate order = {PREDICATE_ORDER}')
    DATA.predicate = DATA.predicate.astype("category")
    DATA.predicate = DATA.predicate.cat.set_categories(PREDICATE_ORDER) 
    
    DATA = DATA.append(predicate_selectivity(DATA), ignore_index=True, sort=False)
    
    check_failed_reps(DATA, 0.3)
    
    print()
    print(COMMIT)
    print()
    print(f'Warmup = {WARMUP_PERCENTAGE}% / Cooldown = {COOLDOWN_PERCENTAGE}%')
    print('-'*100)
    header = f'{"Experiment": <20}{"Variant": <20}'
    parameters_list = list(VARIANT_PARAMETERS)
    for p in parameters_list:
        header += str(p).ljust(20)
    header += f'{"Reps": <7}{"Duration"}'
    print(header)
    print('-'*100)
    for label, group in DATA.groupby(['experiment', 'variant'] + parameters_list):
        reps = group.rep.nunique()
        duration = group.t.max() / 60 
        row = ''
        for p in label:
            row += str(p).ljust(20)
        row += f'{reps: <7}{duration:3.1f} min'
        print(row)
    
def get(**kwargs):
    if len(kwargs) == 0:
        raise ValueError('Need at least one argument!')
    queryParts = []
    for key, value in kwargs.items():
        queryParts.append(f'({key} == "{value}")')
    queryStr = ' & '.join(queryParts)
    return DATA.query(queryStr)


def experimentId():
    values = DATA.experiment.unique()
    assert len(values) == 1
    return values[0]


def percent_diffs(row, baseline='NP/none'):
    bl = row[baseline] if baseline in row else 0
    out_row = row.copy().astype(str)
    for i in range(len(row)):
        pdiff = percentageDiff(float(row[i]), float(bl))
        if np.isnan(pdiff):
            out_row[i] = 'missing'
            continue
        out_row[i] = f'{pdiff:+0.0f}%' if abs(pdiff) < 100 else f'{row[i]/float(bl):+0.1f}x'
    out_row[baseline] = 'missing'
    return out_row
    
    
def aggregated_per_rep(data, parameters, dimensions=['predicate'], selectivity_dimensions=['predicate', 'variant'], selectivity_column=True):
    if 'predicate-selectivity' in parameters:
        if not 'predicate-in' in parameters or not 'predicate-out' in parameters:
            raise ValueError('Selectivity requires to include predicate-in and predicate-out in parameters!')
    aggregated_params = []
    for parameter in parameters:
        if parameter == 'predicate-selectivity':
            continue # We will compute it manually 
        aggregated_params.append(aggregate_rep(data, parameter, dimensions, node_agg=NODE_AGGREGATIONS[parameter]))
    aggregated = pd.concat(aggregated_params)
    pselectivity = predicate_selectivity(aggregated)
    if selectivity_column:
        pselectivity = pselectivity.drop(columns='parameter').groupby(selectivity_dimensions).aggregate({'value': np.mean}).reset_index()
        pselectivity.rename(columns={'value': 'predicate-selectivity'}, inplace=True)
        aggregated = pd.merge(aggregated, pselectivity)
        aggregated.loc[aggregated.variant == VARIANT_NP, 'predicate-selectivity'] = np.nan
    else:
        aggregated = aggregated.append(pselectivity, sort=False, ignore_index=True)
    return aggregated 

def comparison_table(data, parameters, dimensions, baseline=f'NP/none'):
    aggregated = aggregated_per_rep(data, parameters, dimensions, selectivity_column=False)
    variant_parts = ['variant'] + dimensions
    aggregated['variant'] = aggregated[variant_parts].astype(str).apply('/'.join, axis=1)
    pv = pd.pivot_table(aggregated, values='value', index='parameter', columns='variant').round(2)
    pv = pv.reindex(index=parameters)
    pct = pv.apply(percent_diffs, axis=1, baseline=baseline)
    print('Absolute Performance')
    display(pv)
    print(f'Performance Relative to {baseline}')
    display(pct)
    file = f'{REPORT_FOLDER}/comparison.xlsx'
    with pd.ExcelWriter(file) as writer:
        pv.to_excel(writer, sheet_name='Absolute Performance')
        pct.to_excel(writer, sheet_name='Relative to Baseline')
        print(f'Saved {file}')
    return pv, pct
    
def answer_percentages(data, dimensions=['predicate'], rename_operators=None, round_digits=1):
    aggregated = aggregate_node_rep(data, 'predicate-out', dimensions=dimensions + ['node-name'], t_agg=sum_dropna)
    variant_parts = ['variant'] + dimensions
    aggregated['variant'] = aggregated[variant_parts].astype(str).apply('/'.join, axis=1)
    # display(aggregated.dropna())
    pv = pd.pivot_table(aggregated, values='value', index='node-name', columns='variant')
    pv[pv == 0] = np.nan # Differentiate between very few and zero outputs after dividing
    for col in pv.columns:
        pv[col] = 100*pv[col] / pv[col].sum()
    pv = pv.round(round_digits)
    if rename_operators:
        pv = pv.rename(index=rename_operators)
    print('Answers per operator (%)')
    display(pv)
    file = f'{REPORT_FOLDER}/answers-per-operator.xlsx'
    with pd.ExcelWriter(file) as writer:
        pv.to_excel(writer, sheet_name='Absolute Values')
        print(f'Saved {file}')
    return pv
        

def auto_set_axis_info(g, xlabel, title_key='col_name', title_fontsize=AXIS_TITLE_SIZE, label_fontsize=AXIS_LABEL_SIZE, title_labels=PARAMETER_LABELS):
    g.set_titles('{' + title_key + '}')
    for ax in g.axes.flat:
        ax.set_title(title_labels[ax.get_title()], fontsize=title_fontsize, pad=8)
        ax.set_xlabel(xlabel, fontsize=label_fontsize)
        ax.set_ylabel('', fontsize=label_fontsize)
        
def total_selectivity(data, variant, predicate):
    data = data[(data.variant == variant) & (data.predicate == predicate)]
    pin = sum_dropna(data[data.parameter == 'predicate-in']['value'])
    pout = sum_dropna(data[data.parameter == 'predicate-out']['value'])
    print(100*pout/pin)
    
    
def predicate_selectivity_plot(data, variants, dimensions=[], no_annotations=[VARIANT_NP], export=False, bbox=(0.75, 0), bottom=0.05, ncol=4, adjusttext_args={}, custom_annotations={}):
    
    parameters = ['rate', 'latency', 'predicate-in', 'memory', 'cpu', 'predicate-out']
    plot_data = aggregated_per_rep(data, parameters, dimensions, selectivity_column=True)
    plot_data['variant'] = plot_data['variant'].replace(VARIANT_NAME_TRANSFORMS, regex=False)
    plot_data = plot_data[plot_data['variant'].isin(variants)]
    # plot_data = plot_data.dropna(how='all')
    # plot_data['variant'] = plot_data['variant'].cat.remove_unused_categories()
    g = sns.relplot(data=plot_data, x='predicate-selectivity', y='value', col='parameter', 
                    hue='variant', style='variant',
                    hue_order=variants, style_order=variants,
                    kind='line', markers=True, markersize=9, 
                    col_order=parameters,
                    height=1.5, aspect=1.77, col_wrap=3, facet_kws={'sharey': False, 'legend_out': False})
        
    def plot_np(data, **kws):
        ax = plt.gca()
        if VARIANT_NP not in data.variant.unique():
            print('No NP variant, skipping annotations')
            return
        agg = data[data.variant==VARIANT_NP].value.aggregate([np.mean, get95CI]).reset_index()
        if len(agg.dropna()) == 0:
            return
        mean = agg.at[0, 'value']
        ci  = agg.at[1, 'value']
        ax.plot([0, 100], [mean, mean], '-', color='C0')
        ax.fill_between((0,100), mean - ci, mean + ci, alpha=0.2, color='C0')
        # recompute the ax.dataLim
        ax.relim()
        # update ax.viewLim using the new dataLim
        ax.autoscale_view()

    def annotate_predicate(data, **kwargs):
        arrowprops=dict(arrowstyle='simple', color='slategray', alpha=0.65, linewidth=0.05)
        texts = []
        ax = plt.gca()
        if VARIANT_NP not in data.variant.unique():
            print('No NP variant, skipping annotations')
            return
        parameter = data.parameter.unique()[0]
        data = data.groupby(['variant', 'predicate']).aggregate({'value': np.mean, 'predicate-selectivity': np.mean}).reset_index()
        referenceValue = data.loc[data.variant == VARIANT_NP, 'value'].values[0]
        for index, row in data.iterrows():
            if row['variant'] in no_annotations or row['predicate'] in ['none', 'true']:
                continue
            y = row['value'] 
            x = row['predicate-selectivity']
            if not np.isfinite(x) or not np.isfinite(y):
                continue
            pdiff = percentageDiff(row['value'], referenceValue)
            if not np.isnan(referenceValue):
                diffStr = f'{pdiff:+0.0f}%' if abs(pdiff) < 100 else f'{row["value"]/float(referenceValue):+0.1f}x'
                diffStr = '\n(' + diffStr + ')'
            else:
                diffStr = ''
            annotation = row['predicate'] + f'{diffStr}'
            key = (parameter, row['variant'], row['predicate'])
            if key in custom_annotations:
                ax.annotate(annotation, xy=(x,y), xytext=custom_annotations[key], arrowprops=arrowprops, ha='left', size=10, color='darkslategray')
            else:
                texts.append(ax.text(x, y, annotation, ha='left', size=10, color='darkslategray'))
        adjust_text(texts, lim=500, autoalign=True, precision=1e-5, ax=ax, arrowprops=arrowprops, **adjusttext_args)
    
    g.map_dataframe(plot_np)

    for ax in g.axes.flat:
        ax.set_ylim(bottom=0)
        ax.ticklabel_format(axis='y', style='sci', scilimits=(-2, 3), useMathText=False)


    sns.despine()
    g.fig.legend(title='', ncol=ncol, bbox_to_anchor=bbox, frameon=True, fontsize=LEGEND_LABEL_SIZE, title_fontsize=LEGEND_TITLE_SIZE) 
    g.fig.subplots_adjust(bottom=bottom)
    
    g.map_dataframe(annotate_predicate)
    xlabel = 'Explanation Ratio (%)'
    auto_set_axis_info(g, xlabel)

    save_fig(g.fig, 'selectivity_performance', experimentId(), export)
    comparison_table(data, parameters + ['provsize'], dimensions)
    
TEXT_ADJUST_CONFIGS = {'SmartGridAnomaly': dict(expand_text=(1.5, 1.5), expand_points=(2., 2)),
                       'LinearRoadAccident': dict(expand_text=(1.5, 1.5), expand_points=(2, 2)),
                       'Movies': dict(expand_text=(1.75, 1.5), expand_points=(2, 1.75)),
                       'CarLocalMerged': dict(expand_text=(1.25, 1.25), expand_points=(2, 2))}
CUSTOM_ANNOTATIONS = defaultdict(lambda: {})
NO_ANNOTATIONS = defaultdict(lambda: [VARIANT_NP])
NO_ANNOTATIONS['LinearRoadAccident'] = [VARIANT_NP, VARIANT_EREBUS_WP]








def predicate_parallelism_plot(data, variants, export=False, bbox=(0.75, 0), bottom=0.35, ncol=4):
        
    parameters = ['rate', 'latency', 'predicate-in']
    dimensions = ['parallelism']
    plot_data = aggregated_per_rep(data, parameters, dimensions, selectivity_column=False)
    plot_data['variant'] = plot_data['variant'].replace(VARIANT_NAME_TRANSFORMS, regex=False)
    plot_data = plot_data[plot_data['variant'].isin(variants)]

    
    g = sns.relplot(data=plot_data, x='parallelism', y='value', col='parameter', 
                    hue='variant', style='variant',
                    hue_order=variants, style_order=variants,
                    kind='line', markers=True, markersize=7, 
                    col_order=parameters,
                    height=1.5, aspect=1.77, col_wrap=3, facet_kws={'sharey': False, 'legend_out': False})
    
    xlabel = 'Parallelism'
    auto_set_axis_info(g, xlabel)

    for ax in g.axes.flat:
        ax.ticklabel_format(axis='y', style='sci', scilimits=(-2, 3), useMathText=False)
        ax.set_xticks(sorted(plot_data.parallelism.unique()))
    sns.despine()
    
    h,l = g.axes[0].get_legend_handles_labels()
    g.axes[0].legend_.remove()
    g.fig.legend(title='', ncol=ncol, bbox_to_anchor=bbox, frameon=True, fontsize=LEGEND_LABEL_SIZE, title_fontsize=LEGEND_TITLE_SIZE) 
    g.fig.subplots_adjust(bottom=bottom)
    save_fig(g.fig, 'parallelism', experimentId(), export)
    comparison_table(data, parameters, dimensions, baseline='NP/1')





def predicate_time_series_compact(data, predicateDelay, bufferDelay, predicate, trange=(-np.inf, np.inf), bbox=(0.97,0), bottom=0.1, export=False):
    
    parameters = ['rate', 'latency', 'predicate-in', 'past-rate', 'predicate-out', 'cpu']
    rolling_period = 15
    min_periods = 3
    marker = {VARIANT_NP: '', VARIANT_EREBUS: 'x', VARIANT_EREBUS_WP: '+' }
    color = {VARIANT_NP: 'C0', VARIANT_EREBUS: 'C1', VARIANT_EREBUS_WP: 'C2'}
    def linePerVariant(*args, **kwargs):
        data = kwargs.pop('data')
        ax = kwargs.pop('ax')
        parameter = data.parameter.unique()[0]
        rep_func = np.mean
        node_func = NODE_AGGREGATIONS[parameter]
        # Aggregate nodes
        data = data.groupby(['t', 'rep']).aggregate({'value': node_func}).reset_index()
        # Rolling mean and 95% confidence interval
        mvalue = data.groupby('rep', as_index=False).rolling(rolling_period, min_periods=min_periods, on='t').aggregate({'value': np.mean}).reset_index().groupby('t').aggregate({'value': np.mean}).reset_index()
        err = data.groupby('rep', as_index=False).rolling(rolling_period, min_periods=min_periods, on='t').aggregate({'value': np.mean}).reset_index().groupby('t').aggregate({'value': get95CI}).reset_index()
        mvalue = mvalue[(mvalue.t >= trange[0]) & (mvalue.t <= trange[1])]
        err = err[(err.t >= trange[0]) & (err.t <= trange[1])]
        mvalue['t'] -= trange[0]
        err['t'] -= trange[0]
        mvalue.plot(x='t', y='value', ax=ax, markevery=len(mvalue)//10, **kwargs)
        ax.fill_between(mvalue['t'], mvalue['value'] - err['value'],mvalue['value'] + err['value'], alpha=0.3, color=kwargs['color'])
        

    data = data[(data['parameter'].isin(parameters)) & (data.predicate == predicate) & (data.predicateDelay == predicateDelay) & (data.bufferDelay == bufferDelay)].copy()
    if len(data) == 0:
        print('No data')
        return
    
    data['variant'] = data['variant'].replace(VARIANT_NAME_TRANSFORMS, regex=False)

    fig, axes = plt.subplots(nrows=4, figsize=(8, 5), sharey=False, sharex=True)
    plot_ax = {'rate': 0, 'latency': 1,  'predicate-in': 3, 'past-rate': 3, 'predicate-out': 3, 'cpu': 2}
    predicate_lines = ['-', '-.', '--']
    special_erebus_params = {'predicate-in': ('Intercepted', 'C3', '<'), 'past-rate': ('Past Explanations', 'C4', '^'), 'predicate-out': ('Present Explanations', 'C5', '>')}
    for (variant, parameter), group in data.groupby(['variant', 'parameter']):
        ax = axes[plot_ax[parameter]]
        color = None
        label = variant
        m = marker[variant]
        ax.set_title(PARAMETER_LABELS[parameter])
        if variant == VARIANT_NP:
            color = 'C0'
        elif variant == VARIANT_EREBUS:
            if parameter in special_erebus_params:
                label, color, m = special_erebus_params[parameter]
                ax.set_yscale('log')
                ax.set_title('Erebus Intercepted/Explanations (t/s)')
            else:
                color = 'C1'
                ax.ticklabel_format(axis='y', style='sci', scilimits=(-1, 2), useMathText=False)
        linePerVariant(data=group, ax=ax, label=label, color=color, marker=m, legend=False)

    for ax in axes.flat:
        ax.tick_params(axis='both', which='major', labelsize=11)
        ax.set_xlabel('Wall-clock time (s)')
        ax.axvline(data.predicateDelay.astype(int).unique()[0]-trange[0], color='dimgray', linestyle='--')
    # g.fig.subplots_adjust(bottom=bottom)
    handles_labels = [dict(zip(*ax.get_legend_handles_labels())) for ax in fig.axes]
    handles_labels = dict((pair[1], pair[0]) for d in handles_labels for pair in d.items())
    ordered_labels = ['NI', 'EB'] + [v[0] for v in special_erebus_params.values()]
    ordered_handles = [handles_labels[l] for l in ordered_labels]
    fig.legend(handles=ordered_handles, labels=ordered_labels, ncol=5, bbox_to_anchor=bbox, fontsize=LEGEND_LABEL_SIZE, frameon=True, columnspacing=1)
    sns.despine()
    fig.tight_layout()
    save_fig(fig, 'delay-time-series', experimentId(), export)



def synthetic_plot(data, export=False, bbox=(1, 0), bottom=0.35):
    parameters = ['rate','latency','predicate-out'] 
    dimensions=['syntheticFilterDiscardRate', 'syntheticPredicateSelectivity', 'syntheticUseEncapsulation']
    
    plot_data = aggregated_per_rep(data, parameters, dimensions, selectivity_column=False)
    plot_data['syntheticUseEncapsulation'] = plot_data['syntheticUseEncapsulation'].replace({'False': 'Custom', 'True': 'Encapsulated'})
    
    g = sns.relplot(data=plot_data, x='syntheticPredicateSelectivity', y='value',
                    # row='syntheticFilterDiscardRate',
                    col='parameter', 
                    hue='syntheticUseEncapsulation', style='syntheticFilterDiscardRate', hue_order=['Encapsulated', 'Custom'],
                    kind='line', markers=True, markersize=7, 
                    col_order=parameters,
                    height=1.5, aspect=1.77,  facet_kws={'sharey': 'col', 'legend_out': False})

    xlabel = 'Explanation Ratio (%)'
    auto_set_axis_info(g, xlabel)

    # for  (interceptionRatio, parameter), ax in g.axes_dict.items():
    #     if parameter == 'rate': # First column
    #         # ax.set_ylabel(r'$i_{O}$ = ' + str(interceptionRatio) + '\%', usetex=True)
    #         ax.set_ylabel(f'{interceptionRatio}% Intercepted')
    
    for ax in g.axes[:, 0]:
        ax.set_ylim(top=1e5)
        ax.ticklabel_format(axis='y', style='sci', scilimits=(-2, 3), useMathText=False)
    # for ax in g.axes[1:, :].flat:
    #     ax.set_title('')
    sns.despine()
    
    h,l = g.axes.flat[0].get_legend_handles_labels()
    g.axes.flat[0].legend_.remove()
    l[0] = 'Tuple Type:'
    l[3] = 'Interception Ratio (%):'
    g.fig.legend(h,l, title='', ncol=6, fontsize=LEGEND_LABEL_SIZE, title_fontsize=LEGEND_TITLE_SIZE, 
                 bbox_to_anchor=bbox, frameon=False, columnspacing=1, handlelength=1) 
    g.fig.subplots_adjust(bottom=bottom)
    comparison_table(data, parameters=parameters, dimensions=dimensions, baseline='MA-1/75/1/False')
    save_fig(g.fig, 'analysis', experimentId(), export)




def read_jmh_csv(path):
    print(f'Loading {path}')
    csv = pd.read_csv(path)
    csv.columns = [col.replace('Param: ','') for col in csv.columns]
    csv.drop(csv[csv.nVariables > csv.nConditions].index, inplace=True)
    csv['Score'] /= 1e3 # From ns to us
    csv['Score Error (99.9%)'] /= 1e3 # From ns to us
    csv['Unit'] = 'us/op'
    node = os.path.splitext(os.path.basename(path))[0].split('-')[0]
    node = 'Odroid' if 'odroid' in node else 'Server'
    csv['node'] = node
    return csv


def jmh_plot(jmh_df, bbox_x=0.0, bbox_y=0.0, bottom=0.1, export=False):
    

    def manual_jmh_lineplot(data, **kws):
        import itertools
        marker = itertools.cycle(('.', '+', 'x', '*'))
        ax = plt.gca()
        err_col = 'Score Error (99.9%)'
        hues = data[kws['hue']].unique()
        cmap = matplotlib.cm.magma_r(hues / hues.max())
        # cmap = sns.color_palette("rocket", as_cmap=True, n_colors=len(hues), ).colors
        for i, (label, group) in enumerate(data.groupby(kws['hue'])):
            ax.plot(kws['x'], 'Score', marker=next(marker), data=group, label=label, alpha=0.7, color=cmap[i])
            ax.fill_between(group[kws['x']], group['Score'] - group[err_col], group['Score']+group[err_col], alpha=0.2)
            # ax.errorbar(group['nConditions'], group['Score'], group[err_col], fmt='-', label=label)
        node = data.node.unique()[0]
        earlyStop = '- Early Termination' if data.earlyStop.unique()[0] else ''
        ax.set_title(f'{node}{earlyStop}', size=14)   

 
    x = 'nVariables'
    hue = 'nConditions'
    jmh_df = jmh_df.copy()
    jmh_df[hue] = jmh_df[hue].astype(int)
    g = sns.FacetGrid(jmh_df, 
                      col='earlyStop', 
                      # col_order = ['False', 'True'], 
                      height=1.75, aspect=2.3, sharey='row')
    g.map_dataframe(manual_jmh_lineplot, x=x, hue=hue)
    g.set_xlabels('# Unique Variables', size=14)
    g.set_ylabels('us', size=14)
    # g.set_titles('{row_name} Early Termination = {col_name}', size=14)
    for ax in g.axes.flat:
        ax.set_ylim(bottom=0)
        ax.set_xticks(jmh_df[x].unique())
    for  (earlyStop),ax in g.axes_dict.items():
        ax.yaxis.set_major_locator(matplotlib.ticker.MultipleLocator(1))
        ax.yaxis.set_major_formatter(matplotlib.ticker.ScalarFormatter())
        if earlyStop == True:   
            ax.set_title(f' Early Termination')
        else:
            ax.set_title(f'All Conditions Evaluated')
    g.add_legend(title='# Conditions', ncol=4, bbox_to_anchor=(bbox_x, bbox_y), fontsize=LEGEND_LABEL_SIZE)
    plt.setp(g._legend.get_title(),fontsize=LEGEND_TITLE_SIZE)
    sns.despine()
    g.fig.subplots_adjust(bottom=bottom)
    save_fig(g.fig, 'predicate_benchmark_variables', 'jmh', export)



NODE_AGGREGATIONS = {'rate': sum_dropna, 'latency': np.mean, 'past-rate': sum_dropna, 'predicate-out': sum_dropna, 
                        'predicate-in': sum_dropna,'answer-rate': sum_dropna, 'past-buffer-size': sum_dropna, 
                                             'memory': sum_dropna, 'cpu': sum_dropna, 'provsize': np.mean}


PLOT_FUNCTIONS = {
    'time-series': lambda: predicate_time_series_compact(DATA, 240, 3600, 'Q1', trange=(60,590), export=True),
    'averages': lambda: predicate_selectivity_plot(DATA, variants=[VARIANT_NP, VARIANT_EREBUS, VARIANT_EREBUS_WP], dimensions=['predicate'], 
                           export=True, no_annotations=NO_ANNOTATIONS[experimentId()], adjusttext_args=TEXT_ADJUST_CONFIGS[experimentId()], 
                           custom_annotations=CUSTOM_ANNOTATIONS[experimentId()]),
    'synthetic': lambda: synthetic_plot(DATA, export=True),
    'scalability': lambda: predicate_parallelism_plot(DATA, variants=[VARIANT_NP, VARIANT_EREBUS, VARIANT_EREBUS_WP], export=True),
    'microbench': lambda: jmh_plot(DATA, bbox_x=0.525, bbox_y=0, bottom=0.4, export=True)
}




if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Plot selected graph')
    parser.add_argument('--path', type=str, required=True, help='Path to the experiment result')
    parser.add_argument('--plot', type=str, required=True, help=f'Plots to produce from ({",".join(PLOT_FUNCTIONS.keys())})')
    parser.add_argument('--export', type=str, required=False, help=f'Path to export the results, in addition to experiment folder', default='./figures')
    args = parser.parse_args()

    REPORT_FOLDER = args.path
    EXPORT_FOLDER = args.export
    os.makedirs(EXPORT_FOLDER, exist_ok=True)

    if not args.plot in PLOT_FUNCTIONS:
        print('ERROR: Unknown plot requested!')
        exit(1)
    
    if args.plot != 'microbench':
        if args.plot == 'time-series':
            WARMUP_PERCENTAGE = 0
            COOLDOWN_PERCENTAGE = 0
        loadData(REPORT_FOLDER)
    else:
        DATA = pd.concat([read_jmh_csv(path) for path in glob.glob(f'{REPORT_FOLDER}/*.csv')], ignore_index=True, sort=False)


    PLOT_FUNCTIONS[args.plot]()













