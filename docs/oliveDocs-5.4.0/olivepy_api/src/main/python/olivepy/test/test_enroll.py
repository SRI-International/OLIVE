import os
import unittest

import olivepy.api.oliveclient as oc
from test_utils import TestUtils

class TestEnroll(unittest.TestCase):
    '''Tests enroll.
       Read README.txt for general information on running tests.'''

    taada_path = os.path.join(TestUtils.test_data_root, 'testdata', 'TaadA_1min.wav')
    joshua_path = os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'enroll', 'joshua2.wav')
    joshua_short_path = os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'enroll', 'joshuashort1.wav')

    def setUp(self):
        if not os.path.exists(self.taada_path):
            self.skipTest("Test audio file '{}' does not exist".format(self.taada_path))
        if not os.path.exists(self.joshua_path):
            self.skipTest("Test audio file '{}' does not exist".format(self.joshua_path))
        if not os.path.exists(self.joshua_short_path):
            self.skipTest("Test audio file '{}' does not exist".format(self.joshua_short_path))

    @classmethod
    def setUpClass(self):
        self.client = oc.OliveClient(self.__class__.__name__, "localhost", 5588, 10)
        self.client.connect()

    @classmethod
    def tearDownClass(self):
        self.client.disconnect()

    def test_enroll_error1(self):
        TestUtils.check_plugin('sid-embed-v5-py3')
        success = self.client.enroll('sid-embed-v5-py3', 'multilang-v1', 'joshua', 'non-existant-file')
        info = self.client.get_info()
        fullobj = self.client.get_fullobj()

        self.assertFalse(success)
        self.assertEqual(info, 'Audio file non-existant-file does not exist')
        self.assertNotEqual(fullobj, None)
        if not success and TestUtils.verbose:
            print('Successfully got missing file message.')

    def test_enroll_sid_mc(self):
        TestUtils.check_plugin('sid-embed-v5-py3')
        success = self.client.enroll('sid-embed-v5-py3', 'multicond-v1', 'joshua',
                                     self.joshua_path)

        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully enrolled {} in {} {}'
                  .format('joshua', 'sid-embed-v5-py3', 'multicond-v1'))

    def test_enroll_sid_ml(self):
        TestUtils.check_plugin('sid-embed-v5-py3')
        success = self.client.enroll('sid-embed-v5-py3', 'multilang-v1', 'joshua', self.joshua_path)

        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully enrolled {} in {} {}'
                  .format('joshua', 'sid-embed-v5-py3', 'multilang-v1'))

    def test_enroll_sdd_tel(self):
        TestUtils.check_plugin('sdd-sbcEmbed-v1b-py3')
        success = self.client.enroll('sdd-sbcEmbed-v1b-py3', 'telClosetalk-v1', 'joshua', self.joshua_path)

        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully enrolled {} in {} {}'
                  .format('joshua', 'sdd-sbcEmbed-v1b-py3', 'multilang-v1'))

    def test_enroll_tpd_eng(self):
        TestUtils.check_plugin('tpd-embed-v1-py3')
        success = self.client.enroll('tpd-embed-v1-py3', 'eng-cts-v1', 'joshua', self.taada_path)

        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully enrolled {} in {} {}'
                  .format('joshua', 'tpd-embed-v1-py3', 'eng-cts-v1'))

    def test_enroll_tpd_rus(self):
        TestUtils.check_plugin('tpd-embed-v1-py3')
        success = self.client.enroll('tpd-embed-v1-py3', 'rus-cts-v1', 'joshua', self.taada_path)

        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully enrolled {} in {} {}'.format('joshua', 'tpd-embed-v1-py3', 'rus-cts-v1'))

    def test_enroll_qbe_dig(self):
        TestUtils.check_plugin('qbe-tdnn-v7-py3')
        success = self.client.enroll('qbe-tdnn-v7-py3', 'digPtt-v1', 'joshua', self.joshua_short_path)

        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully enrolled {} in {} {}'
                  .format('joshua', 'qbe-tdnn-v7-py3', 'digPtt-v1')) 

    def test_enroll_qbe_multi(self):
        TestUtils.check_plugin('qbe-tdnn-v7-py3')
        success = self.client.enroll('qbe-tdnn-v7-py3', 'multi-v1', 'joshua', self.joshua_short_path)

        self.assertTrue(success)
        if success and TestUtils.verbose:
            print('Successfully enrolled {} in {} {}'
                  .format('joshua', 'qbe-tdnn-v7-py3', 'multi-v1'))             