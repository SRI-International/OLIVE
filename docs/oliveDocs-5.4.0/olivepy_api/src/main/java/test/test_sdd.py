import glob
import shutil
import subprocess
import os
import unittest

from test_utils import TestUtils

class TestSdd(unittest.TestCase):
    '''Tests the SDD plugin.'''
    
    @classmethod
    def setUpClass(cls):
        TestUtils.check_plugin('sdd-sbcEmbed')
        TestUtils.clean_logs()    
    
    def test_joshua1_js(self): 
        result = TestUtils.cmd(r'%s\OliveEnroll --plugin sdd-sbcEmbed --enroll joshua --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'enroll', 'joshua1.wav')))
        
        self.assertEqual(result.returncode, 0)
        self.assertIn("Successfully enrolled: 'joshua'", result.stdout)
        self.assertEqual(result.stderr, '') 
        
        result = TestUtils.cmd(r'%s\OliveAnalyze --plugin sdd-sbcEmbed --timeout 120 --region --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stderr, '')
        # Checking should be expanded, but for now, just check that the file is the right size
        self.assertEqual(TestUtils.lines_in_file('region.output.txt'), 9)

     
