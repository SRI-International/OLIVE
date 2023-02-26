
import argparse, json, sys

import olivepy.api.workflow as ow


def main():
    """

    :return:
    """
    parser = argparse.ArgumentParser(prog='olivepyutils')
    
    parser.add_argument('workflow', action='store',
                        help='The workflow definition to use.')

    parser.add_argument('--save_as_text', action='store',
                        help='Save the workflow to a JSON formatted text file having this name.')

    parser.add_argument('--save_as_binary', action='store',
                        help='Save the workflow to a binary formatted workflow file.')

    parser.add_argument('--print_workflow', action='store_true',
                        help='Print the workflow definition file info (before it is actualized/sent to server)')

    args_bad = False
    args = parser.parse_args()
    
    if args.workflow is None :
        print('No workflow definition is specified.')
        args_bad = True

    if not (args.save_as_text or args.print_workflow or args.save_as_binary):
            args_bad = True
            print('The command requires one or more tasks.')

    if args_bad:
        print('Run the command with --help or -h to see all the command line options.')
        quit(1)

    try:
        # first, create the workflow definition from the workflow file:
        owd = ow.OliveWorkflowDefinition(args.workflow)

        if args.save_as_text:
            print("Saving Workflow Definition '{}' as '{}'".format(args.workflow, args.save_as_text))
            owd._save_as_json(args.save_as_text)

        if args.save_as_binary:
            print("Saving Workflow Definition '{}' as '{}'".format(args.workflow, args.save_as_binary))
            owd._save_as_binary(args.save_as_binary)

        if args.print_workflow:
            wdef_json = owd.to_json(indent=1)
            print("Workflow Definition Task Info: {}".format(wdef_json))
            print("")

    except Exception as e:
        print("Workflow failed with error: {}".format(e))


if __name__ == '__main__':
    sys.exit(main())
