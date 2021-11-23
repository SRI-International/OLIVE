@echo off

: Note:
:   * The non-standard formatting makes it obvious that the echo and the real command are 
:     character for character identical.

echo Running tests on olivelearn (equivelent to python olive_learn.py) that need to be inspected manually to ensure they worked.
echo:

if not defined TEST_DATA_ROOT (
    echo TEST_DATA_ROOT must be set for testing to work.
    exit /b 1
)

echo ..\..\bin\olivelearn --plugin sad-dnn-v6b-py3 --domain multi-v1 --adapt new-v1 --input %TEST_DATA_ROOT%\testSuite\sad\adapt\fixed-adaptation-svr.lst
     ..\..\bin\olivelearn --plugin sad-dnn-v6b-py3 --domain multi-v1 --adapt new-v1 --input %TEST_DATA_ROOT%\testSuite\sad\adapt\fixed-adaptation-svr.lst
echo:
