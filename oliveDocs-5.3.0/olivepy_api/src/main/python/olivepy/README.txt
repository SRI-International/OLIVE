
This is the README file for developers of the Python Olive API.

The user README file is one level up.
A specific testing README file is in the test directory.


Testing

To test this library on another machine, do this:
git clone ...olive...
cd ...test...
read the README.txt file and either do what it says, or just run this script:
all_tests.bat

Packaging

To package this library for distribution, run this:
cd python
python setup.py sdist
You will see the new olive*.tar.gz file in the dist directory.

You can install this library with a command like:
pip install dist/olive*.tar.gz
