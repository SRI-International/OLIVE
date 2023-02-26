import threading

import olive.oliveclient as oc

threads = []
global_plugins = [ ['sid-dplda-v1-py3', 'multi-v1'],
                   ['lid-embedplda-v1b-py3', 'multi-v1'], ]

def do_global_analyze(plugin, domain, wav):
    thread_name = threading.current_thread().getName()
    client = oc.OliveClient(thread_name+'cient')
    client.connect()
    print('On {} starting {}-{} on {}'.format(thread_name, plugin, domain, wav))
    results = client.analyze_global(plugin, domain, wav)
    print('On {} results from {}-{} on {}: {}'.format(thread_name, plugin, domain, wav, results))
    client.disconnect()

for plugin in global_plugins:
    t = threading.Thread(target=do_global_analyze, args=(plugin[0], plugin[1], 'doc\TwoSpeakers.wav'))
    threads.append(t)
    t.start()

for num in range(len(global_plugins)):
    threads[num].join()

