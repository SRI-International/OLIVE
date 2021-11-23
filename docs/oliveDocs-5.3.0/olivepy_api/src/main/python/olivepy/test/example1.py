import os
import threading
import time

import olive.oliveclient as oc

threads = []
test_data_root = os.environ['TEST_DATA_ROOT']
work_tasks = [ os.path.join(test_data_root, 'testSuite', 'sid', 'enroll', 'joshua2.wav'),
               os.path.join(test_data_root, 'testSuite', 'sid', 'enroll', 'joshua2.wav'), 
               os.path.join(test_data_root, 'testSuite', 'sid', 'enroll', 'joshua2.wav'),
               os.path.join(test_data_root, 'testdata', 'TaadA_1min.wav'),
               os.path.join(test_data_root, 'testdata', 'TaadA_1min.wav'),]


def do_stat(iterations=10, delay=10):
    thread_name = threading.current_thread().getName()
    client = oc.OliveClient(thread_name+'cient')
    client.connect()
    for ii in range(iterations):
        print('At %d seconds:' % (ii * delay))
        counts = client.get_status()
        print('  tasks pending=%d, busy=%d, finished=%d' % (counts[0], counts[1], counts[2]))        
        actives = client.get_active()
        print('  IDs: '+ ', '.join(actives))          
        time.sleep(delay)
    client.disconnect()
    
    
def do_global_analyze(wav):
    time.sleep(1)
    thread_name = threading.current_thread().getName()
    client = oc.OliveClient(thread_name+'cient')
    client.connect()
    print('On {} into {}#{} with {}'.format(thread_name, 'lid-embedplda-v1b-py3', 'multi-v1', wav))
    results = client.analyze_global('lid-embedplda-v1b-py3', 'multi-v1', wav)
    print('On {} result from {}-{} on {} got {} scores'.format(thread_name, 'lid-embedplda-v1b-py3', 'multi-v1', wav, len(results)))
    client.disconnect()
    
oc.OliveClient.setup_multithreading()    

stat_thread = threading.Thread(target=do_stat, args=(10,1))
threads.append(stat_thread)
stat_thread.start()

for work_task in work_tasks:
    print(work_task)
    work_thread = threading.Thread(target=do_global_analyze, args=(work_task,))
    threads.append(work_thread)
    work_thread.start()

for num in range(len(work_tasks)):
    threads[num].join()