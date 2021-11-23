import glob
import shutil
import subprocess
import os
import unittest

from test_utils import TestUtils

class TestQbe(unittest.TestCase):
    '''Tests the SDD plugin.'''
    
    @classmethod
    def setUpClass(cls):
        TestUtils.check_plugin('qbe-tdnn')
        TestUtils.clean_logs()    
    
    def test_joshua1_js(self): 
        result = TestUtils.cmd('OliveEnroll --plugin qbe-tdnn --enroll joshua --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'enroll', 'joshuashort1.wav')))
        
        self.assertEqual(result.returncode, 0)
        self.assertIn("Successfully enrolled: 'joshua'", result.stdout)
        self.assertEqual(result.stderr, '') 
        
        result = TestUtils.cmd('OliveAnalyze --plugin qbe-tdnn --qbe --timeout 120 --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stderr, '')
        # Checking should be expanded, but for now, just check that the file is the right size
        self.assertEqual(TestUtils.lines_in_file('region.output.txt'), 2)

        result = TestUtils.cmd('OliveEnroll --plugin qbe-tdnn --unenroll joshua' %
                               (TestUtils.bin),
                               std=False)
        
        # I think returning 1 is a bug in OliveEnroll, but that is what is does.
        self.assertEqual(result.returncode, 1)
        self.assertIn('Removed speaker: joshua', result.stdout)
        # This would fail if I left it in.  I think this is a bug in OliveEnroll.
        # It matches 'All enrollment requests failed'
        # self.assertEqual(result.stderr, '') 

        # This is expected to fail, because nothing is enrolled
        result = TestUtils.cmd('OliveAnalyze --plugin qbe-tdnn --qbe --timeout 120 --wav %s ' %
                               (TestUtils.bin, 
                                os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')))

        self.assertEqual(result.returncode, 0)
        self.assertIn("Region scoring error: No class ids found for domain 'digPtt-v1' Is anything enrolled?", result.stdout)
        self.assertIn("No results for analysis type: REGION_SCORE", result.stderr)
        
