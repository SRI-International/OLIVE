import unittest

import olivepy.messaging.msgutil
import olivepy.api.oliveclient as oc
from test_utils import TestUtils

class TestClient(unittest.TestCase):
    '''Tests get_status, get_active, and get_update_status
       But: test_active does not work now.
       Read README.txt for general information on running tests.'''
    
    @classmethod
    def setUpClass(cls):
        cls.client = oc.OliveClient(cls.__class__.__name__, "localhost", 5588, 10)
        cls.client.connect()

    @classmethod
    def tearDownClass(cls):    
        cls.client.disconnect()
      
    def test_unloadload(self):
        if TestUtils.verbose:
            print('These both return False because of a bug in the server side.')
        result = self.client.unload_plugin_domain('sid-embed-v5', 'multicond-v1')
        self.assertFalse(result)
        if TestUtils.verbose:
            print('Result from unload_plugin_domain was ' + str(result)) 
        if self.client.info is not None:
            print(self.client.info)        
        
        result = self.client.load_plugin_domain('sid-embed-v5 ', 'multicond-v1')
        self.assertFalse(result)
        if TestUtils.verbose:
            print('Result from load_plugin_domain was ' + str(result))
        if self.client.info is not None:
            print(self.client.info)
            
    def test_status(self):
        counts = self.client.get_status()
        self.assertEqual(counts[0], 0)
        self.assertEqual(counts[1], 0)
        self.assertEqual(counts[2], 0)
        if TestUtils.verbose:
            print(counts)        

    def test_active(self):
        actives = self.client.get_active()
        self.assertEqual(actives, [])
        if TestUtils.verbose:
            print(actives)
            
    def test_get_update_status(self):

        plugid = 'lid-embedplda-v1b'
        domainid = 'multi-v1'
        try:

            update_ready, result = self.client.get_update_status(plugid, domainid)
            self.assertTrue(update_ready)
            if TestUtils.verbose:
                print('For {}-{}: update_ready is {}, last_update is {} and has {} parameters.'
                      .format(plugid, domainid, result.update_ready, str(result.last_update),
                              str(len(result.params))))

            plugid = 'lid-embedplda-v1b-py3'
            domainid = 'multi-v1'
            update_ready, result = self.client.get_update_status(plugid, domainid)
            self.assertFalse(update_ready)
            if TestUtils.verbose:
                print('For {}-{}: update_ready is {}, last_update is {} and has {} parameters.'
                      .format(plugid, domainid, result.update_ready, str(result.last_update),
                              str(len(result.params))))

        except olivepy.messaging.msgutil.ExceptionFromServer as efs:
            if TestUtils.verbose:
                print('For {}-{}: plugin-domain does not support updating the status.'
                      .format(plugid, domainid))
