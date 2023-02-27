import os
import shutil
import unittest
import uuid

import olivepy.api.oliveclient as oc
from test_utils import TestUtils

class TestLearn(unittest.TestCase):
    '''
    Test "learning" functionality
    '''
    
    @classmethod
    def setUpClass(cls):
        cls.client = oc.OliveClient(cls.__class__.__name__, "localhost", 5588, 10)
        cls.client.connect()

    @classmethod
    def tearDownClass(cls):    
        cls.client.disconnect()

    def test_adapt_sad(self):
        TestUtils.check_plugin('sad-dnn-v6b-py3')
        # Any file that contains path names to test data must be fixed.
        # TEST_DATA_ROOT must be updated to the value of the environment variable.
        test_dir = os.path.join(TestUtils.test_data_root, 'testSuite', 'sad', 'adapt')
        adapt_file = os.path.join(test_dir, 'adaptation-svr.lst')
        fixed_adapt_file = os.path.join(test_dir, 'fixed-adaptation-svr.lst')
        TestUtils.fix_locations(adapt_file, fixed_adapt_file)
        new_name = uuid.uuid4().hex
        
        new_domain_fullpath = self.client.adapt_supervised('sad-dnn-v6b-py3', 'multi-v1', 
                                                           fixed_adapt_file, new_name)
        
        self.assertEqual(new_name, os.path.basename(new_domain_fullpath))
        # TODO: look up new domain and make sure it is there.
        if TestUtils.verbose:
            print("Created new domain: {}".format(new_domain_fullpath))
               
        shutil.rmtree(new_domain_fullpath)