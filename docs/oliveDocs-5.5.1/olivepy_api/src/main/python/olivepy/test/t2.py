import threading

import olive.oliveclient as oc

threads = []
work_tasks = [ ['sid-embed-v5-py3', 'multilang-v1', 'doc\joshua2.wav'],
               ['sid-embed-v5-py3', 'multilang-v1', 'doc\joshua2.wav'], 
               ['sdd-sbcEmbed-v1b-py3', 'telClosetalk-v1', 'doc\joshua2.wav'],
               ['tpd-embed-v1-py3', 'eng-cts-v1', '..\testdata\TaadA_1min.wav'],
               ['tpd-embed-v1-py3', 'rus-cts-v1', '..\testdata\TaadA_1min.wav'],
               ['qbe-tdnn-v7-py3', 'digPtt-v1', 'doc\short1.wav'],
               ['qbe-tdnn-v7-py3', 'multi-v1', 'doc\short1.wav'], ]

def do_global_analyze(plugin, domain, enroll, wav):
    thread_name = threading.current_thread().getName()
    client = oc.OliveClient(thread_name+'cient')
    client.connect()
    print('On {} enrolling {} into {}#{} with {}'.format(thread_name, enroll, plugin, domain, wav))
    results = client.enroll(plugin, domain, enroll, wav)
    print('On {} result from {}-{} on {}: {}'.format(thread_name, plugin, domain, wav, results))
    client.disconnect()
    
oc.OliveClient.setup_multithreading()    

for work_task in work_tasks:
    t = threading.Thread(target=do_global_analyze, args=(work_task[0], work_task[1], 'joshua', work_task[2]))
    threads.append(t)
    t.start()

for num in range(len(work_tasks)):
    threads[num].join()
