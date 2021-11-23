#!/bin/bash

set -e

# LOAD VERSION AND OTHER (?) BUILD INFO
source build_env.sh

# Ask the user for conditional build options
rc_ver=$OLIVEPY_RC_NUMBER

read -p "Include release candidate ('rc${OLIVEPY_RC_NUMBER}') in version number -  (y)es or (n)o? " yn
case $yn in
        [Yy]* ) build_with_rc_version=1; echo "Appending release candidate number to OLIVEPY version...";;
        [Nn]* ) echo "Not including release candidate number in version";;
        * ) echo "Please answer (y)es or (n)o"; exit 1;;
esac

if (( $build_with_rc_version == 1)); then
  read -p "Increment release candidate version (current version: $OLIVEPY_RC_NUMBER) -  (y)es or (n)o? " yn
  case $yn in
          [Yy]* ) build_with_rc_version=1; rc_ver=$(($rc_ver + 1)); echo "New release candidate version: $rc_ver"; \
            sed -i -e "s/OLIVEPY_RC_NUMBER.*/OLIVEPY_RC_NUMBER=${rc_ver}/g" build_env.sh;;
          [Nn]* ) echo "Not incrementing RC version";;
          * ) echo "Please answer (y)es or (n)o"; exit 1;;
  esac

  # Set the rc number
  sed  -i -e "s/__version__ .*/__version__  = '${OLIVEPY_VERSION_NUMBER}rc${rc_ver}'/g" olivepy/__init__.py
  #sed -i -e
#  sed  "s/__version__ .*/__version__  = '${OLIVEPY_VERSION_NUMBER}rc${rc_ver}'/g" olivepy/__init__.py
#  sed  "s/OLIVEPY_RC_NUMBER.*/OLIVEPY_RC_NUMBER=${rc_ver}/g" build_env.sh

  version_str=${OLIVEPY_VERSION_NUMBER}rc${rc_ver}
else
  sed -i -e "s/__version__ .*/__version__  = '${OLIVEPY_VERSION_NUMBER}'/g" olivepy/__init__.py
  version_str=${OLIVEPY_VERSION_NUMBER}
fi

echo ""
echo ""
echo "Packaging OLIVEPY version: ${version_str}"
echo ""
echo ""

rm -rf dist
python3 setup.py sdist bdist_wheel
#rm dist/olivepy-5.1.0rc1.tar.gz
#rsync bin/* dist/

# package it up
#__version__ = '5.2.0rc2'
tar -zcf olivepy-${version_str}_dist.tar.gz dist

echo "Successfully created OLIVEPY distribution: ${version_str}"


