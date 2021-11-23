
This is the testing directory for the python client to Olive Enterprise API for Python.
It contains four sections:
    RELEASE NOTES ON TESTING
    INSTALLING FOR TESTING
    RUNNING TESTS
    CODING STYLE GUIDE FOR TESTS

RELEASE NOTES ON TESTING

* Enhansement is not ready for testing.

* The manual_* tests don't work and need to be replaced, anyway.  

* The test_client test has one error in it.  Ignore for now.

* I can not find a way to uninstall olivepy.

INSTALLING FOR TESTING

These instructions assume you already have a Python 3.7+ environment, which includes
both python and pip.  An Anaconda installation is fine.

Run this command to install olivepy as a user would:
pip install olivepy-0.1.tar.gz
(Name of the *.tar.gz file might very depending on the release.)

Check that things are basically OK:
python
make sure the version is 3.7+ and enter the following imports and make sure you don't get any errors:
import olivepy.oliveclient
import zmq
import numpy
import soundfile
print(olivepy.__version__)




It is recommended to delete the development source, so that you can not run them by mistake:
cd api\src\main\python\olivepy
del oliveclient.py olive_*.py

Obviously, be very careful about pushing changes back to the git repo, since you must not push back this deletion!

Get the test suite from git:

mkdir TestData
cd TestData
git clone USER@vserv2.speech.sri.com:/olive/api.git
scp -r USER@vserv2.speech.sri.com:/home/software/kdd/repos/testdata .

The TEST_DATA_ROOT is used to find the test data, so set it now.
set TEST_DATA_ROOT=%CD%
(Note that TEST_DATA_ROOT is not set to testSuite, but to TestData, its parent.)

In TestData, there should be three directories: testSuite, testdata, and bindata.
In the parent directory, there should be two directories: TestData and api.

cd ..

Now you are ready to run some tests!

RUNNING TESTS

Before running tests, you must start the scenicserver in one cmd window.
Follow the Olive instructions to do that, which should be similar to this:
cd ... olive directory ...
idento_env
python olive\server\scenicserver.py

Now start a different cmd window to run your tests.
All the tests are in this directory:
cd api\src\main\python\olivepy\test

You can run all the standard automated tests like this:
all_tests

You should not use unttest's discovery features by running tests like this:
python -m unittest -v or python -m unittest discover ." because these tests
have some order dependances.  (Which should be removed, but have not been.)
In particular, basic should be run frist, enroll needs to be run before analyze,
and unenroll after.   All_tests does this.

You can run some longer test suites like this:
python -m unittest test_long.py

You can run spedific tests like this:
python -m unittest test_analyze.py
    Minimum output: just prints a dot for each test.
python -m unittest -v test_analyze.py
    More output: one line per test with the name and success of the test.
set TEST_VERBOSE=1
python -m unittest -v test_analyze.py
    Running these two commands prints the data returned from each call.
    
You can also run single tests as follows:  (All the same -v/TEST_VERBOSE options work for them.)
python -m unittest test_analyze.TestAnalyze.test_sad2

You can unset TEST_VERTOSE like this:
set TEST_VERBOSE=
when setting it, it does not matter what you set it too.

You can 

You can test the command line interface with commands like these:

oliveenroll --help
oliveanalyze --help
olivelearn --help

For example, like this:
oliveenroll --plugin sid-embed-v5-py3  --domain multilang-v1   --enroll joshua  --wav %TEST_DATA_ROOT%\sid\enroll\joshua2.wav




CODING STYLE GUIDE FOR TESTS

* Use the existing tests as templates.

* PEP-8 

* Single blank lines between code to run and checking that it ran properly.

* Each call to the server should be followed by automatic checks (ie. self.assert* calls) and also print statements which will only run if self.verbose is set.

* TestUtils class in test_utils.py is the library of shared test code.
Make sure you understand and reuse what is already there.
Put new shared code and data in there.

* To create unique names, use this code:
import uuid
uuid.uuid4().hex

* If you want more logging output, add this to your tests:
import logging
logging.basicConfig(level=logging.DEBUG)

* If you want to slow things down, add this to your tests:
import time
time.sleep(10)