import glob
import shutil
import subprocess
import os
import unittest

class TestUtils(unittest.TestCase):
    '''Utility functions used to test CLI / Java programs.'''
    
    # Edit this list to include the plugins you want to test.
    plugins = ['voi-speakingstyle']
    
    # List of all plugins, to make editing list above easier:
    # ldd-sbcEmbed    lid-embedplda   sad-dnn     sid-dplda   sid-embed   
    # sdd-sbcEmbed    tpd-embed   qbe-tdnn
      
    verbose = 'TEST_VERBOSE' in os.environ
    test_data_root = os.environ['TEST_DATA_ROOT']
    bin = os.path.join('..', '..', '..', '..', 'target', 'java_cli', 'bin')
    
    @classmethod
    def check_plugin(cls, plugin_name):
        '''Checks that this plugin should be tested; is listed in test_utils.py file.'''
        if plugin_name not in cls.plugins:
            raise unittest.SkipTest('Not testing '+plugin_name+'. That plugin not listed in test_utils.py.')
            
    @classmethod
    def update_files(cls):
        '''If template files have changed, update lst files.'''
        # Converts *.ltemplate -> *.lst if not already done or out of date.
        # Converts MYPLUGIN -> full path of the plugin.
        for template_file in glob.glob('*.template'):
            root = os.path.splitext(template_file)[0]
            if (not os.path.exists(root + '.lst')
                or os.path.getmtime(root + '.lst') < os.path.getmtime(root + '.template')):
                with open(root + '.template', 'r') as in_file, open(root + '.lst', 'w') as out_file:
                    contents = in_file.read()
                    new_contents = contents.replace('MYPLUGIN',
                                          os.path.abspath(os.path.join(os.getcwd(), os.pardir)))
                    out_file.write(new_contents)        
    @classmethod
    def clean_logs(cls):
        '''Always delete working and log files left over from previous runs.''' 
        # Delete working and log files left over from before.  
        shutil.rmtree('logs', ignore_errors=True)
        shutil.rmtree('WORK', ignore_errors=True)
        shutil.rmtree('OUTPUT', ignore_errors=True)
        fileList = glob.glob('*.log.*')
        fileList2 = glob.glob('*.log')
        for filePath in fileList + fileList2:
            os.remove(filePath)
        file_names = ['output.txt', 'global.output.txt', 'region.output.txt']
        for file_name in file_names:
            if os.path.exists(file_name): 
                os.remove(file_name)
    
    @classmethod
    def cmd(cls, full_cmd, std=True):
        '''Runs a command on the local machine with three changes:
           1. replacing MYPLUGIN with the path to the current plugin.
              (Only use if you are testing from a plugin's directory!)
           2. Integrates with TEST_VERBOSE.
           3. If std is set to True, the default, will print out stdout
              and stderr if return value is not zero.'''
        processed_cmd = full_cmd.replace('MYPLUGIN',
                                         os.path.abspath(os.path.join(os.getcwd(), os.pardir)))
        if cls.verbose:
            print(processed_cmd)
        result = subprocess.run(processed_cmd, shell=True, text=True, capture_output=True)
        if std:
            if result.returncode != 0:
                print('result code: {}'.format(result.returncode))
                print('standard out: ' + result.stdout)
                print('standard err: ' + result.stderr)
        return result  
    
    @classmethod
    def lines_in_file(cls, filename):
        '''Returns the number of lines in a file.  Only used for quick and dirty testing,
           because it does check that the contents are correct.  When every you use this
           function, you could be more specific (and better) in your checking!'''        
        with open(filename, 'r') as file:
            count = len(file.readlines())
        return count
    

