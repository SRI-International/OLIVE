import unittest

import olivepy.api.oliveclient as oc
from test_utils import TestUtils

class TestUnenroll(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.client = oc.OliveClient(cls.__class__.__name__, "localhost", 5588, 10)
        cls.client.connect()

    @classmethod
    def tearDownClass(cls):    
        cls.client.disconnect()   

    def test_unenroll_sid_multi(self):
        TestUtils.check_plugin('sid-embed-v5-py3')
        success = self.client.unenroll('sid-embed-v5-py3', 'multilang-v1', 'joshua')
        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully unenrolled {} in {} {}'.format('joshua', 'sid-embed-v5-py3', 'multilang-v1'))
   
    def test_unenroll_sdd_tel(self):
        TestUtils.check_plugin('sdd-sbcEmbed-v1b-py3')
        success = self.client.unenroll('sdd-sbcEmbed-v1b-py3', 'telClosetalk-v1', 'joshua')
        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully unenrolled {} in {} {}'.format('joshua', 'sid-embed-v5-py3', 'multilang-v1'))
   
    def test_unenroll_tpd_eng(self):
        TestUtils.check_plugin('tpd-embed-v1-py3')
        success = self.client.unenroll('tpd-embed-v1-py3', 'eng-cts-v1', 'joshua')
        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully unenrolled {} in {} {}'.format('joshua', 'tpd-embed-v1-py3', 'eng-cts-v1'))

    def test_unenroll_tpd_rus(self):
        success = self.client.unenroll('tpd-embed-v1-py3', 'rus-cts-v1', 'joshua')
        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully unenrolled {} in {} {}'.format('joshua', 'tpd-embed-v1-py3', 'rus-cts-v1'))
            
    def test_unenroll_qbe_dig(self):
        TestUtils.check_plugin('qbe-tdnn-v7-py3')
        success = self.client.unenroll('qbe-tdnn-v7-py3', 'digPtt-v1', 'joshua')
        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully unenrolled {} in {} {}'.format('joshua', 'qbe-tdnn-v7-py3', 'digPtt-v1')) 
            
    def test_unenroll_qbe_multi(self):
        TestUtils.check_plugin('qbe-tdnn-v7-py3')
        success = self.client.unenroll('qbe-tdnn-v7-py3', 'multi-v1', 'joshua')
        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully unenrolled {} in {} {}'.format('joshua', 'qbe-tdnn-v7-py3', 'multi-v1'))             