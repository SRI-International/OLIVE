

import logging
import time
from unittest import TestCase

import olive.oliveclient as oc

class TestNew(TestCase):

    def setUp(self):
        #logging.basicConfig(level=logging.DEBUG)
        client_id = "TestAnalyze"
        self.client = oc.OliveClient(client_id, "localhost", 5588, 10)
        self.client.connect()

    def atearDown(self):    
        self.client.disconnect()    
   
    def test_(self):
        annotation_file_name = "learn1.lst"
        annotations = self.client.parse_annotation_file(annotation_file_name)
        self.assertGreater(len(annotations, 0)) # TODO more specific checks, more checks.

        new_domain = 'test_'
        result = self.client.adapt_supervised('plugin', 'domain', annotations, new_domain)
        self.assertEqual(new_domain, result)
        print(new_domain)        


