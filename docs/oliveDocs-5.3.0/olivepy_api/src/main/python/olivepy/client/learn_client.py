
import argparse
import os

import olivepy.api.oliveclient

# This is the main function of the script.  It does two things:
#   1. Gather up the command line arguments.
#   2. Connect to the server, do the analysis, and disconnect from the server.

if __name__ == '__main__':
    
    parser = argparse.ArgumentParser(prog='olivepylearn')
   
    parser.add_argument('-a', '--adapt', action='store',
                        help='Adapt a plugin/domain, giving the new domain ADAPT')
    parser.add_argument('-c', '--channel', action='store', default=1,
                        help='Learn from this channel of audio (defaults to %(default)s).')
    parser.add_argument('-C', '--client-id', action='store', default='olive_learn',
                        help='Experimental: the client_id to use')
    parser.add_argument('-d', '--domain', action='store',
                        help='The domain to use')
    parser.add_argument('-f', '--fullobj', action='store_true',
                        help='Experimental for troubleshooting: Print the last full protobuf object from the server.')    
    parser.add_argument('-i', '--input', action='store', 
                        help='Input data from INPUT.')    
    parser.add_argument('-p', '--plugin', action='store',
                        help='The plugin to use.')
    parser.add_argument('-P', '--port', type=int, action='store', default=5588,
                        help='The port to use.') 
    parser.add_argument('-s', '--server', action='store', default='localhost',
                        help='The machine the server is running on. Defaults to %(default)s.')
    parser.add_argument('-S', '--serialized', action='store_true',
                        help='Serialize audio file and send it to the server instead of the decoded samples')    
    parser.add_argument('-t', '--timeout', type=int, action='store', default=10,
                        help='The timeout to use')  
    parser.add_argument('-T', '--train', action='store',
                        help='Train a plugin/domain, giving the new domain TRAIN')
    parser.add_argument('-L', '--threshold', type=float, action='store',
                        help='Apply threshold THRESHOLD when scoring')    
    
    args_bad = False
    args = parser.parse_args()
    
    if args.plugin is None:
        print('No plugin is specified and one is required.')
        args_bad = True

    if args.domain is None :
        print('No domain is specified and one is required.')
        args_bad = True
        
    if args.adapt is None and args.train is None:
        print('No adapt or train is specified and one of them is required.')
        args_bad = True
        
    if args.input is None:
        print('The command requires an input file argument.')
        args_bad = True
    else:
        if not os.path.exists(args.input):
            print('Your input file ({}) is not found.'.format(args.input))
            args_bad = True        
        
    if args.serialized:
        print('Setting serialized values is not supported yet.')
        args_bad = True
        
    if args.threshold is not None:
        print('Setting threshold values is not supported yet.')
        args_bad = True
        
    if args_bad:
        print('Run the command with --help or -h to see all the command line options.')
        quit(1)

    if args.client_id is None or args.server is None or args.port is None or args.timeout is None:
        print('Internal error: a required variable is not set.')
        quit(2)
    
    client = olivepy.api.oliveclient.OliveClient(args.client_id, args.server, args.port, args.timeout)
    client.connect()
    
    if args.adapt:
        print('Adapting {}-{} with {} to become {}-{}'.format(
            args.plugin, args.domain, args.input, args.plugin, args.adapt))
        
        new_domain_fullpath = client.adapt_supervised(args.plugin, args.domain, args.input, args.adapt)
        new_domain = os.path.basename(new_domain_fullpath)
        if new_domain is not None:
            print('Adapted new domain: ' + new_domain)
        else:
            print('Adaptation process failed.  No new domain was created.')

    if args.train:
        print('The --Train action is not yet supported.  You can only use --adapt.')
        
    if args.fullobj:
        print(str(client.get_fullobj()))
            
    client.disconnect() 