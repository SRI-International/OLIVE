
import os
import unittest

import olivepy.messaging.msgutil
import olivepy.api.oliveclient as oc
from .test_utils import TestUtils


# CLG: Hack refactor to make it easier to change/update the plugin/domains used by these tests
# sad_plugin_name = "sad-dnn-v6-py3"
sad_plugin_name = "sad-dnn-v6-v2"
# sad_domain_name = "multi-v1"
sad_domain_name = "tel-v1"

lid_plugin_name = "lid-embedplda-v1b-py3"
lid_domain_name = "multi-v1"

sdd_plugin_name = "sdd-sbcEmbed-v1b-py3"
sdd_domain_name1 = "micFarfield-v1"
sdd_domain_name2 = "telClosetalk-v1"

sid_plugin_name1 = "sid-dplda-v1-py3"
sid_domain_name1 = "multi-v1"

sid_plugin_name2 = "sid-embed-v5-py3"
sid_domain_name2 = "multicond-v1"

tpd_plugin_name = "tpd-embed-v1-py3"
tpd_domain_name1 = "eng-cts-v1"
tpd_domain_name2 = "rus-cts-v1"

qbe_plugin_name = "qbe-tdnn-v7-py3"
qbe_domain_name1 = "digPtt-v1"
qbe_domain_name2 = "multi-v1"



class TestAnalyze(unittest.TestCase):
    '''Tests analyze_frames, analyze_global, and analyze_regions.
       Read README.txt for general information on running tests.'''
    
    joshuasarah_path = os.path.join(TestUtils.test_data_root, 
                                    'testSuite', 'sid', 'test', 'JoshuaSarahSpeaking.wav')

    def setUp(self):

        if not os.path.exists(self.joshuasarah_path):
            self.skipTest("Test audio file '{}' does not exist".format(self.joshuasarah_path))

    @classmethod
    def setUpClass(cls):
        cls.client = oc.OliveClient(cls.__class__.__name__, "localhost", 5588, 10)
        cls.client.connect()

    @classmethod
    def tearDownClass(cls):    
        cls.client.disconnect()     

    def test_sad2(self):
        TestUtils.check_plugin(sad_plugin_name)
        frames = self.client.analyze_frames(sad_plugin_name, sad_domain_name, self.joshuasarah_path)

        # CLG changing this to use a dummy plugin for testing
        self.assertEqual(len(frames), 3)
        self.assertAlmostEqual(frames[0], 1.0, 2)
        self.assertAlmostEqual(frames[1], 2.0, 2)
        self.assertAlmostEqual(frames[2], -5.1, 2)
        if TestUtils.verbose:
            print('Returned {} frames.  First 3 are {}'
                  .format(len(frames), ', '.join(map(str, frames[0:2]))))

        # self.assertEqual(len(frames), 19004)
        # self.assertAlmostEqual(frames[0], -2.179, 2)
        # self.assertAlmostEqual(frames[1], -2.26, 2)
        # self.assertAlmostEqual(frames[-2], -1.52, 2)
        # self.assertAlmostEqual(frames[-1], -1.448, 2)
        # if TestUtils.verbose:
        #     print('Returned {} frames.  First 10 are {}'
        #           .format(len(frames), ', '.join(map(str, frames[0:9]))))
                
        # But here is an example of getting star/end regions (in seconds) from a SAD request
        regions = self.client.analyze_regions(sad_plugin_name, sad_domain_name, self.joshuasarah_path)

        self.assertEqual(len(regions), 1)
        if TestUtils.verbose:
            for region in regions:
                print('{} {}-{} {}'.format(region.class_id, region.start_t, region.end_t, region.score)) 
            
    def test_lid1(self):
        TestUtils.check_plugin(lid_plugin_name)
        results = self.client.analyze_global(lid_plugin_name, lid_domain_name, self.joshuasarah_path)
        
        self.assertEqual(len(results), 19)
        # For humans, just print out two values
        for result in results:
            if result.class_id == 'eng':
                rus = result.score
                self.assertAlmostEqual(result.score, 2.7266, 3)
            else:
                key = result.class_id
                value = result.score
                self.assertLess(result.score, 0)
        if TestUtils.verbose:        
            print('Got {} results.  Rus is {} and {} is {}.'.format(len(results), rus, key, value)) 
   
    def test_lid1s(self):
        '''Tests analyze global passing in binary audio data (not the path of an audio file).'''
        TestUtils.check_plugin(lid_plugin_name)
        frefile = os.path.join(TestUtils.test_data_root, 'testSuite', 'lid', 'test', '20001011_1130_1230_rfi_64.wav')
        buffer = TestUtils.read_wav(frefile)
        results = self.client.analyze_global(lid_plugin_name, lid_domain_name,
                                             buffer, self.client.AUDIO_SERIALIZED)
        self.assertEqual(len(results), 19)
        # For humans, just print out two values
        for result in results:
            if result.class_id == 'fre':
                fre = result.score
                self.assertAlmostEqual(result.score, 6.6479, 3)
            else:
                key = result.class_id
                value = result.score
                self.assertLess(result.score, 0)
        if TestUtils.verbose:        
            print('Got {} results.  Rus is {} and {} is {}.'.format(len(results), fre, key, value))            

    def test_lid_eng(self):
        TestUtils.check_plugin(lid_plugin_name)
        engfile = os.path.join(TestUtils.test_data_root, 'testSuite', 'lid', 'test', 'z_eng_rugby.wav')
        results = self.client.analyze_global(lid_plugin_name, lid_domain_name, engfile)

        self.assertEqual(len(results), 19)
        # For humans, just print out two values
        for result in results:
            if result.class_id == 'eng':
                eng = result.score
                self.assertAlmostEqual(result.score, 1.913, 3)
            else:
                key = result.class_id
                value = result.score
                self.assertLess(result.score, 0)
        if TestUtils.verbose:
            print('Got {} results.  Eng is {} and {} is {}.'.format(len(results), eng, key, value))        

    def test_lid_rus(self):
        TestUtils.check_plugin('lid-embedplda-v1b-py3')
        rusfile = os.path.join(TestUtils.test_data_root, 'testSuite', 'lid', 'test', 'RUS001_14618_inline_landline_50.wav')
        results = self.client.analyze_global(lid_plugin_name, lid_domain_name, rusfile)

        self.assertEqual(len(results), 19)
        # For humans, just print out two values
        for result in results:
            if result.class_id == 'rus':
                rus = result.score
                self.assertAlmostEqual(result.score, 3.850, 3)
            else:
                key = result.class_id
                value = result.score
                self.assertLess(result.score, 0)
        if TestUtils.verbose:        
            print('Got {} results.  Rus is {} and {} is {}.'.format(len(results), rus, key, value))
    
    def test_sdd1(self):
        TestUtils.check_plugin(sdd_plugin_name)
        regions = self.client.analyze_regions(sdd_plugin_name, sdd_domain_name1, self.joshuasarah_path)
        self.assertEqual(len(regions), 9)
        if TestUtils.verbose:
            print('Results for sdd-sbcEmbed-v1b-py3, micFarfield-v1, JoshuaSarahSpeaking.wav')
            for region in regions:
                print('{} {}-{} {}'.format(region.class_id, region.start_t, region.end_t, region.score))
          
    def test_sdd1s(self):
        TestUtils.check_plugin(sdd_plugin_name)
        buffer = TestUtils.read_wav(self.joshuasarah_path)
        regions = self.client.analyze_regions(sdd_plugin_name, sdd_domain_name1,
                                              buffer, self.client.AUDIO_SERIALIZED)
        self.assertEqual(len(regions), 9)
        if TestUtils.verbose:
            print('Results for sdd-sbcEmbed-v1b-py3, micFarfield-v1, SERIALIZED JoshuaSarahSpeaking.wav')
            for region in regions:
                print('{} {}-{} {}'.format(region.class_id, region.start_t, region.end_t, region.score))            
    
    def test_sdd2(self):
        TestUtils.check_plugin(sdd_plugin_name)
        regions = self.client.analyze_regions(sdd_plugin_name, sdd_domain_name2, self.joshuasarah_path)
        self.assertEqual(len(regions), 9)
        if TestUtils.verbose:
            print('Results for sdd-sbcEmbed-v1b-py3, telClosetalk-v1, JoshuaSarahSpeaking.wav')    
            for region in regions:
                print('{} {}-{} {}'.format(region.class_id, region.start_t, region.end_t, region.score))
    
    def test_sid_dplda1(self):
        TestUtils.check_plugin(sid_plugin_name1)
        windy = 0
        results = self.client.analyze_global(sid_plugin_name1, sid_domain_name1, self.joshuasarah_path)
        
        self.assertGreater(len(results), 0)
        for result in results:
            if result.class_id == 'windy':
                windy = result.score
        self.assertAlmostEqual(windy, -5.5058, 3)
        
        if TestUtils.verbose:
            print('Results for sid-dplda-v1-py3, multi-v1, JoshuaSarahSpeaking.wav')
            for result in results:
                print('{} is {}'.format(result.class_id, result.score))
            
    def test_sid_embed1(self):
        TestUtils.check_plugin(sid_plugin_name2)
        joshua = 0
        results = self.client.analyze_global(sid_plugin_name2, sid_domain_name2, self.joshuasarah_path)
        
        self.assertGreater(len(results), 0)
        for result in results:
            if result.class_id == 'joshua':
                joshua = result.score
        self.assertAlmostEqual(joshua, 6.613, 3)
        
        if TestUtils.verbose:
            print('Results for sid-embed-v5-py3, multicond-v1, JoshuaSarahSpeaking.wav')
            for result in results:
                print('{} is {}'.format(result.class_id, result.score))
            
    def test_sid_embed1s(self):
        TestUtils.check_plugin(sid_plugin_name2)
        buffer = TestUtils.read_wav(self.joshuasarah_path)
        results = self.client.analyze_global(sid_plugin_name2, sid_domain_name2,
                                             buffer, self.client.AUDIO_SERIALIZED)

        self.assertGreater(len(results), 0)
        for result in results:
            if result.class_id == 'joshua':
                joshua = result.score
        self.assertAlmostEqual(joshua, 6.613, 3)
        
        if TestUtils.verbose:
            print('Results for sid-embed-v5-py3, multicond-v1, JoshuaSarahSpeaking.wav')
            for result in results:
                print('{} is {}'.format(result.class_id, result.score))           

    # Maybe choose wav file where something is found?
    def test_tpd_eng1(self): 
        TestUtils.check_plugin(tpd_plugin_name)
        regions = self.client.analyze_regions(tpd_plugin_name, tpd_domain_name1, self.joshuasarah_path)
        self.assertEqual(len(regions), 0)
        if TestUtils.verbose:
            print('Results for tpd-embed-v1-py3, eng-cts-v1, JoshuaSarahSpeaking.wav')
            for region in regions:
                print('{} {}-{} {}'.format(region.class_id, region.start_t, region.end_t, region.score))
      
    #@unittest.skip('Never tested in Java and does not work here.')
    def test_tpd_toosmall(self):
        TestUtils.check_plugin(tpd_plugin_name)
        frefile = os.path.join(TestUtils.test_data_root, 'testSuite', 'lid', 'test',             
                               '20001011_1130_1230_rfi_64.wav')
        with self.assertRaises(Exception) as context:
            regions = self.client.analyze_regions(tpd_plugin_name, tpd_domain_name2, frefile)
            if TestUtils.verbose:
                print('Did not get the ExceptionFromServer that we expected!')

        self.assertIsInstance(context.exception, olivepy.messaging.msgutil.ExceptionFromServer)
        self.assertIn('Insufficient speech found', str(context.exception))
        if TestUtils.verbose:
            print('Got the ExceptionFromServer that we expected!')

    def test_qbe_dig(self):
        # TestUtils.check_plugin(qbe_plugin_name)
        regions = self.client.analyze_regions(qbe_plugin_name, qbe_domain_name1, self.joshuasarah_path)
        self.assertEqual(len(regions), 2)
        if TestUtils.verbose:
            print('Results for qbe-tdnn-v7-py3, digPtt-v1, JoshuaSarahSpeaking.wav')
            for region in regions:
                print('{} {}-{} {}'.format(region.class_id, region.start_t, region.end_t, region.score))

    def test_qbe_multi(self):
        TestUtils.check_plugin(qbe_plugin_name)
        regions = self.client.analyze_regions(qbe_plugin_name, qbe_domain_name2, self.joshuasarah_path)
        self.assertEqual(len(regions), 2)
        if TestUtils.verbose:
            print('Results for qbe-tdnn-v7-py3, multi-v1, JoshuaSarahSpeaking.wav')
            for region in regions:
                 print('{} {}-{} {}'.format(region.class_id, region.start_t, region.end_t, region.score)) 

    def test_qbe_multis(self):
        TestUtils.check_plugin(qbe_plugin_name)
        buffer = TestUtils.read_wav(self.joshuasarah_path)
        regions = self.client.analyze_regions(qbe_plugin_name, qbe_domain_name2,
                                              buffer, self.client.AUDIO_SERIALIZED)
        self.assertEqual(len(regions), 2)
        if TestUtils.verbose:
            print('Results for qbe-tdnn-v7-py3, multi-v1, SERIALIZED JoshuaSarahSpeaking.wav')
            for region in regions:
                print('{} {}-{} {}'.format(region.class_id, region.start_t, region.end_t, region.score)) 