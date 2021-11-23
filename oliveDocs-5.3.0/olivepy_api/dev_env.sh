
# This is the script needed to build API for Cygwin

if [ "$MAVEN_HOME" = "" ] ; then
    echo "MAVEN_HOME must be set, or this script will not work."
    exit 1
fi


RUNTIME="/cygdrive/c/Users/e33173/OliveOnWindows/server38/Windows-10"

export MAVEN_HOME="/cygdrive/c/Users/e33173/Program Files/apache-maven-3.6.3"
export M2_HOME="/cygdrive/c/Users/e33173/Program Files/apache-maven-3.6.3"
PATH="${MAVEN_HOME}/bin:$PATH"

export JAVA_HOME="/cygdrive/c/Program Files/Java/jdk-11.0.7"
PATH="${JAVA_HOME}\bin:$PATH"

PATH="${RUNTIME}/Anaconda3/envs/py38/Library\bin:${PATH}"

export PATH
