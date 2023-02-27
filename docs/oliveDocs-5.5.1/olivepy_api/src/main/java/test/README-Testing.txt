
Before running these tests, make sure the TEST_DATA_ROOT environment variable
is set with something like this: 

set TEST_DATA_ROOT=server38\TestData

In the software\api directory:  (Modify based on where JDK 11 is installed.)

dev_env
set JAVA_HOME=C:\Program Files\Java\jdk-11.0.7
set PATH=%JAVA_HOME%\bin;%PATH%

Run all the tests here with a command like this:

python -m unittest -v