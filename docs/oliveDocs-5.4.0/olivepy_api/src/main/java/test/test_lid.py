import glob
import shutil
import subprocess
import os
import unittest

from test_utils import TestUtils

class TestLid(unittest.TestCase):
    '''Tests the LID plugin.'''
    
    @classmethod
    def setUpClass(cls):
        TestUtils.check_plugin('lid-embedplda')
        TestUtils.clean_logs()
        
    def test_local(self):        
        result = TestUtils.cmd('OliveAnalyze --plugin lid-embedplda --timeout 120 --lid --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stderr, '')
        # Checking should be expanded, but for now, just check that the file is the right size
        self.assertEqual(TestUtils.lines_in_file('global.output.txt'), 19)
