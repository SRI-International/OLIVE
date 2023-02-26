
Python API for Olive

This package can be installed with a command like this:
- pip3 install olivepy-5.3.0rc8-py3-none-any.whl

Or to upgrade:
- pip3 install --upgrade olivepy-5.3.0rc8-py3-none-any.whl

To remove:

pip3 uninstall  olivepy-5.0.0rc1-py3-none-any.whl

NOTE This package depends on the 3rd party packages in requirements that are listed in requirements.txt.  Thes packages can be installed using pip:
- sudo pip3 install protobuf
- sudo pip3 install zmq


Or if installing on CentOS 7 we have a distribution that includes the 3rd party dependencies, so you  

- pip3 install -r requirements.txt --use-wheel --no-index --find-links wheelhouse dist/olivepy-5.2.0rc10-py3-none-any.whl



# CentOS Notes

To build on CentOS for a wheel distribution that includes 3rd party dependencies use the setup_centos.py script:

python3 setup_centos.py package bdist_wheel


This should create these distribution artifacts:

olivepy-5.2.0-py3-none-any.whl
olive.wheelhouse.tar.gz
requirements.txt

To install, you need the requirements.txt:

pip3 install -r requirements.txt --use-wheel --no-index --find-links wheelhouse dist/olivepy-5.2.0rc10-py3-none-any.whl