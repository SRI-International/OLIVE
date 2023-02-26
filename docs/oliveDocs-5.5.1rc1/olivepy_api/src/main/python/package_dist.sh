#!/bin/bash

set -e

GIT_HASH=`git rev-parse --short HEAD`
OLIVEPY_VERSION_NUMBER=`git branch --show-current`
CURRENT_DATE=`date -u +"%Y%m%d"`

version_str=${OLIVEPY_VERSION_NUMBER}-${GIT_HASH}-${CURRENT_DATE}

sed  -i -e "s/__version__ .*/__version__  = '${version_str}'/g" olivepy/__init__.py

echo ""
echo ""
echo "Packaging OLIVEPY version: ${version_str}"
echo ""
echo ""

rm -rf dist
python3 setup.py sdist bdist_wheel
tar -zcf olivepy-${version_str}_dist.tar.gz dist

echo "Successfully created OLIVEPY distribution: ${version_str}"
