

import argparse
import os, time
import json
import logging
import traceback

import olivepy.api.oliveclient
import olivepy.api.olive_async_client as olive_async
import olivepy.utils as utils


def heartbeat_notification(heatbeat):
    """Callback method, notified by the async client that a heartbeat message has been received from the OLIVE server"""

    if heatbeat:
        if heatbeat.HasField("stats"):
            stats = heatbeat.stats
            print("System CPU Used:    %02.01f%%" % stats.cpu_percent)
            print("System CPU Average: %02.01f%%" % stats.cpu_average)
            print("System MEM Used:    %02.01f%%" % stats.mem_percent)
            print("System MEM Max:     %02.01f%%" % stats.max_mem_percent)
            print("System SWAP Used:   %02.01f%%" % stats.swap_percent)
            print("System SWAP Max:    %02.01f%%" % stats.max_swap_percent)
            print("Number active jobs: " + str(stats.pool_busy))
            print("Number pending jobs: " + str(stats.pool_pending))
            print("Number finished jobs: " + str(stats.pool_finished))
            print("Max number jobs: " + str(stats.max_num_jobs))
            print("Server version: " + str(stats.server_version))
            print("\n")
    else:
        print("No OLIVE heartbeat received.  Olive server or connection down")

# This is the main function of the script.  It does two things:
#   1. Gather up the command line arguments.
#   2. Connect to the server, do the analysis, and disconnect from the server.

if __name__ == '__main__':
    
    parser = argparse.ArgumentParser(prog='olivepystatus')
    
    parser.add_argument('-C', '--client-id', action='store', default='olivepy_status',
                        help='Experimental: the client_id to use')

    parser.add_argument('-d', '--domain', action='store',
                        help='The domain to use')
    parser.add_argument('-p', '--plugin', action='store',
                        help='The plugin to use.')
    parser.add_argument('-P', '--port', type=int, action='store', default=5588,
                        help='The port to use.') 
    parser.add_argument('-s', '--server', action='store', default='localhost',
                        help='The machine the server is running on. Defaults to %(default)s.')
    parser.add_argument('-t', '--timeout', type=int, action='store', default=10,
                        help='The timeout to use')  
    parser.add_argument('--debug', action='store_true',
                        help='Debug mode ')

    # this arguments do not require a plugin/domain and audio input
    parser.add_argument('--print', action='store_true',
                        help='Print all available plugins and domains')
    parser.add_argument('--status', action='store_true',
                        help='Get server status ')
    parser.add_argument('--heartbeat', action='store_true',
                        help='Debug mode ')

    args_bad = False
    args = parser.parse_args()

    # Simple logging config
    if args.debug:
        log_level = logging.DEBUG
    else:
        log_level = logging.INFO
        # log_level = logging.WARN
    logging.basicConfig(level=log_level)


    # if args.plugin is None or args.domain is None:
    #     print('No plugin or domain is specified and one of each is required.')
    #     args_bad = True

    if not args.status and not args.print and not args.heartbeat:
        print(
            'No command has been given.  One of status or print must be given or nothing will be done.')
        args_bad = True


    if args_bad:
        print('Run the command with --help or -h to see all the command line options.')
        quit(1)

    if args.client_id is None or args.server is None or args.port is None or args.timeout is None:
        print('Internal error: a required variable is not set.')
        quit(2)
    

    # we may use both the async and simple clients
    client = None
    async_client = None
    # if args.status or args.print:
    #     client = olivepy.api.oliveclient.OliveClient(args.client_id, args.server, args.port, args.timeout)
    #     # The async client can also be used, but using callbacks is kinda awkward for this command line client:
    #     # client = AsyncOliveClient(args.client_id, args.server, args.port, args.timeout)
    #     client.connect()
    #
    # if args.heartbeat:
    #     async_client = olive_async.AsyncOliveClient(args.client_id, args.server, args.port, args.timeout)
    #     async_client.connect(monitor_status=True)

    client = olive_async.AsyncOliveClient(args.client_id, args.server, args.port, args.timeout)
    async_client = client
    client.connect(monitor_status=True)

    try:
        if args.status:
            status_response = client.get_status()
            if status_response.is_successful():
                # print("OLIVE server status: {}".format(json.dumps(status_response.to_json())))
                print("OLIVE server status: {}".format(status_response.to_json()))
            # if using the simple client:
            # print("Server pending jobs: {}, busy jobs: {}, finished jobs: {}, version: {}".format(status[0], status[1], status[2], status[3]))

        if args.print:
            plugin_response = client.request_plugins()
            if plugin_response.is_successful():
                # print("Olive plugins: {}".format(json.dumps(plugin_response.to_json(), indent=1)))

                if args.plugin:
                    # filter results by plugin
                    json_output = plugin_response.to_json(indent=-1)['plugins']
                    matched = False
                    for pd in json_output:
                        if pd['id'] == args.plugin:
                            matched = True
                            if args.domain:
                                domains = pd['domain']
                                filtered_domains = []
                                for dom in domains:
                                    if dom['id'] == args.domain:
                                        filtered_domains.append(dom)
                                if not filtered_domains:
                                    print("Requested domain '{}' not found for plugin: '{}'".format(args.domain, args.plugin))
                                pd['domain'] = filtered_domains
                            print("Plugin: {}".format(json.dumps(pd, indent=1)))
                    if not matched:
                        print("Requested plugin '{}' not found".format(args.plugin))

                else:
                    print("Olive plugins: {}".format(plugin_response.to_json(indent=1)))




        if args.heartbeat:
            print("")
            print("Press CTRL-C to stop listening for OLIVE server heartbeats")
            print("")
            async_client.add_heartbeat_listener(heartbeat_notification)
            async_client.worker.join()



    except Exception as e:
        print("olivepystatus failed with error: {}".format(e))
        print(traceback.format_exc())
    finally:
        if client:
            client.disconnect()
        if async_client:
            async_client.disconnect()

    print('Exiting...')


def print_plugins(client, sys):
    # Alternate way of printing plugin info
    results = client.request_plugins()
    for plugin in results:
        if len(sys.argv)==1 or sys.argv[1] == plugin.id:
            print('Plugin: {}({}) from {}: {}'
                  .format(plugin.id, plugin.task, plugin.vendor, plugin.desc))
            if len(plugin.trait) != 0:
                traits = [str(tt.type) for tt in plugin.trait]
                print('    Traits: ' + ', '.join(traits))
            for domain in plugin.domain:
                print('    Domain: {} '.format(domain.id))
                if domain.class_id != []:
                    print('        Enrolled: ' + ', '.join(domain.class_id))