@echo off

: This script assumes that MAVEN_HOME is already set for your machine.
: but JAVA_HOME is set here.  (And I'm not sure that is a good idea.)

echo Running dev_env.bat

:
: Set up Maven
:

set PATH=%PATH%;%MAVEN_HOME%\bin

:
: Set up protobuf building
set PATH=C:\Users\e33173\OliveOnWindows\server38\Windows-10\Anaconda3\envs\py38\Library\bin;%PATH%

:
: Set up Java
:

set JAVA_HOME=C:\Program Files\Java\jdk-11.0.7
set PATH=%JAVA_HOME%\bin;%PATH%

:
: Set up Python
:

: Use conda to set up the python environment, especially for py38.
: Do not set PATH in this bat file, or any other bat file! This command should do that work.
: Docs for this:
: https://conda.io/projects/conda/en/latest/user-guide/tasks/manage-environments.html#activating-an-environment
if not "%CONDA_DEFAULT_ENV%" == "py38" (
    call conda activate py38
)
set PYTHONPATH=C:\Users\e33173\OliveOnWindows\server38\software\api\src\main\python;%PYTHONPATH%

call mvn --version
call java -version
call python --version