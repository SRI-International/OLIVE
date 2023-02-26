
import logging
import time
import os
import unittest

import olivepy.api.oliveclient as oc
from olivepy.test.test_utils import TestUtils


# This class is expected to be run first, so it does not use the setUp/tearDown
# functions that all the rest use, so that if the basic create/connect/disconnect
# does not work, the tests themselves will fail, and not the fixture code.

# DO NOT USE THIS AS A TEMPLATE FOR OTHER TESTS!

class TestClient(unittest.TestCase):        
    
    def test_monitoring(self):
        if TestUtils.verbose:
            logging.basicConfig(level=logging.DEBUG)
        
        client = oc.OliveClient("testcient", "localhost", 5588, 10)
        self.assertFalse(client.is_connected())
        client.connect(True)
        self.assertTrue(client.is_connected())
        # Make sure we don't get dropped after a few seconds.
        time.sleep(10)
        self.assertTrue(client.is_connected())
        client.disconnect()
        self.assertFalse(client.is_connected())
    
    #@unittest.skip("not ready yet")
    def test_monitoring2(self):    
        TestUtils.check_plugin('sad-dnn-v6b-py3')
        logging.basicConfig(level=logging.DEBUG)

        client_id = "testcient"
        client = oc.OliveClient(client_id, "localhost", 5588, 10)

        self.assertFalse(client.is_connected())
        client.connect(True)
        # or if you want server status:
        # client.connect(True)
        self.assertTrue(client.is_connected())

        # By default SAD returns frame scores
        frames = client.analyze_frames('sad-dnn-v6-v2',
                                       'tel-v1',
                                       os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav'))
        #  TODO: check frames

        # But here is an example of getting star/end regions (in seconds) from a SAD request
        regions = client.analyze_regions('sad-dnn-v6-v2',
                                         'tel-v1',
                                         os.path.join(TestUtils.test_data_root, 'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav'))

        # print regions
        if TestUtils.verbose:
            for start, end in regions:
                print("{} to {}".format(start, end))

        # Pause then exit
        time.sleep(3)
        client.connect()

    def test_plugins(self):
        client = oc.OliveClient("test_basic", "localhost", 5588, 10)
        self.assertFalse(client.is_connected())
        
        client.connect()
        self.assertTrue(client.is_connected())
        
        results = client.request_plugins()
        # results is a list of plugins:
        self.assertGreater(len(results), 0)
        for plugin in results:
            self.assertGreater(len(plugin.domain), 0)
            if TestUtils.verbose:
                print('Plugin: {}({}) from {}: {}'.format(plugin.id, plugin.task, plugin.vendor, plugin.desc))
                if len(plugin.trait) != 0:
                    traits = [str(tt.type) for tt in plugin.trait]
                    print('    Traits: ' + ', '.join(traits)) 
                for domain in plugin.domain:
                    print('    Domain: {} '.format(domain.id))
                    if domain.class_id != []:
                        print('        Enrolled: ' + ', '.join(domain.class_id))
        
        self.assertTrue(client.is_connected())
        client.disconnect()
        self.assertFalse(client.is_connected())