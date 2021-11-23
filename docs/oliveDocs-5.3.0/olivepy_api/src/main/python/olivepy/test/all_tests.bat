@echo off

if not defined TEST_DATA_ROOT (
    echo TEST_DATA_ROOT must be set for testing to work.
    exit /b 1
)

echo: 
echo Running test_basic.py
python -m unittest test_basic.py

echo: 
echo Running test_client.py
python -m unittest test_client.py

echo:
echo Running test_enroll.py
python -m unittest test_enroll.py

echo:
echo Running test_analyze.py
python -m unittest test_analyze.py

echo:
echo Running test_unenroll.py
python -m unittest test_unenroll.py

echo:
echo Running test_modification.py
python -m unittest test_modification.py

echo:
echo Running test_learn.py
python -m unittest test_learn.py

: These tests take a long time to run, so are not run by default:
: echo:
: echo Running test_long.py
: python -m unittest long.py