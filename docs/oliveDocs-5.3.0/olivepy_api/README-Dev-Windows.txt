
This file includes instructions for getting the API project to compile 
and run on Windows.  The philosophy is that you can build software with
Cygwin, but you run software with native Windows tools (cmd or powershell).

This API has both a Java and a Python version.  If you want to use the Java
version, you must have Java and Maven installed.  If you want to use the 
Python version, you must have Python instealled.

Open Issues:
* In future we will shift to OpenSDK 11.

INSTALLING AND CHECKING JAVA AND MAVEN

Java should already be installed

I used Oracle Java 11.0.7, but I encourage you to use the most up 
to date Java available.
You will need to set JAVA_HOME and PATH.

Check with these two commands (Cygwin):
echo $JAVA_HOME
javac -version

Or these two commands (cmd):
set JAVA_HOME
javac -version


Installing Maven, if not already installed.

I used Maven 3.6.3, but I encourage you to use the most up to date 
Maven available.

Download Maven from here: https://maven.apache.org/download.cgi
Follow the installation instructions here: https://maven.apache.org/install.html

I added these three commands to Cygwin's ~/.bash_profile, but your 
installation directory will be different:
export MAVEN_HOME="/cygdrive/c/Users/e33173/Program Files/apache-maven-3.6.3"
export M2_HOME="/cygdrive/c/Users/e33173/Program Files/apache-maven-3.6.3"
PATH="${MAVEN_HOME}/bin:$PATH"

And run these commands once to update all future cmd windows on Windows:
set MAVEN_HOME=C:\Users\e33173\Program Files\apache-maven-3.6.3
set M2_HOME=C:\Users\e33173\Program Files\apache-maven-3.6.3
set PATH=%MAVEN_HOME%\bin;%PATH%
(You must create new cmd windows after running these commands to see the
new environment variables.)

Check with this command:
mvn --version

INSTALLING PYTHON

This software has been tested wit Anaconda Python 3.8.1, but should work with
any Python 3.7 and later.

Before running these tests, make sure the TEST_DATA_ROOT environment variable
is set with something like this: 
set TEST_DATA_ROOT=server38\TestData

In the software\api directory:  (Modify based on where JDK 11 is installed.)

dev_env
set JAVA_HOME=C:\Program Files\Java\jdk-11.0.7m
set PATH=%JAVA_HOME%\bin;%PATH%

Check with this command

BUILDING SOFTWARE

mvn install
You can see the resulting jar file in target, and the resulting 
executables in target/assemble/bin

CREATING A PYTHON PACKAGE

The python command below creates four different distribution files.
However, I think *.tar.gz is the best, so please distribute that one, and you
don't really need to create the rest if you don't want to.

cd src\main\python
python setup.py sdist bdist_wheel bdist_egg bdist
dir dist\olivepy-*
and use this command (from Cygwin) to see what is in it:
tar -ztf  api/src/main/python/dist/olivepy-1.0rc1.tar.gz

CREATE A NEW CONDA ENVIRONMENT FOR TESTING

In the perfect world you would test this on a machine different than you developed it.
However, if you can not do that, you can at least test it in a different python
environment, as follows:

cd src\main\python
conda create --name api-test --clone base
conda activate api-test
pip install dist\olivepy-1.0rc1.tar.gz

You can later remove it with this:
conda env remove --name api-test

RUNNING TESTS

To run the Java test that hit the server:
cd src\main\java\test
python -m unittest -v 
or
python -m unittest -v test_voi.py

