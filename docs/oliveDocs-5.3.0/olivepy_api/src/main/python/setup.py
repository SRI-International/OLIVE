# Based on https://python-packaging.readthedocs.io/en/latest/minimal.html

from setuptools import setup

import olivepy

setup(name='olivepy',
      version=olivepy.__version__,
      description='Olive API for Python',

      author='SRI International (STAR Lab)',
      author_email='star-lab@sri.com',
      include_package_data=True,
      install_requires=[
          'protobuf',
          'soundfile',
          'numpy',
          'zmq',
      ],
      license='SRI',
      packages=['olivepy'],
      scripts=['bin/olivepymetrics', 'bin/olivepyanalyze.bat', 'bin/olivepyenroll.bat', 'bin/olivepylearn.bat', 'bin/olivepyworkflow.bat', 'bin/olivepyworkflowenroll.bat', 'bin/olivepyutils.bat', 'bin/olivepystatus.bat',
               'bin/olivepyanalyze','bin/olivepyenroll','bin/olivepylearn','bin/olivepyworkflow', 'bin/olivepyworkflowenroll', 'bin/olivepyutils', 'bin/olivepystatus'],
      url='http://www.speech.sri.com/',
      zip_safe=False)
