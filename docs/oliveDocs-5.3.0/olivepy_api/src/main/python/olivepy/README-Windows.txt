
This file includes instructions for getting the API project to run on Windows.  

Before starting, the following software must be installed:
* Python (3.x)
* The python library pyzmq and 
* You must be able to run Olive server (which is installed seperately).

This software is tested with Python 3.8.1, but should run for any 3.7.x or 3.8.x
version of Python.

Check Java with these two commands:
python --version

Python API for Olive

This package can be installed with a command like this:
- pip install olivepy-0.1.tar.gz

Documentation on how to use this library is in this Jupyter notebook:
- OliveApiForPythonTutorial.ipynb

Installing pyzmq

conda install pyzmq

Check the zmq library, by starting python and running this command without error:
import zmq

Installing Protobuf

conda install protobuf

Check the protobuf library, by starting python and running this command without error:
import google.protobuf

If you have installed Olive On Windows, then you can run the OliveCmd terminal, and
run the Olive Python API command from there.  You can start an OliveCmd terminal in 
three different ways:
  a) Click on the OliveCmd icon on the Windows Desktop.
  b) Click on the OliveCmd menu item in the Windows Start Menu (under Olive).
  c) Start a Windows cmd terminal and run the OliveCmd.bat file in your
     Olive On Windows distribution in the software\bin directory.


Release Notes:
* In future we will shift to OpenSDK 11.


