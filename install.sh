#!/usr/bin/env bash

set -x
set -e


# this script home dir
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd ${DIR}

./gradlew installDist
rm -rf /Users/jetbrains/idea-gradle-jps-build-app/utils/dist-compare
cp -r build/install/dist-compare /Users/jetbrains/idea-gradle-jps-build-app/utils/dist-compare/