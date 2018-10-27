#!/usr/bin/env bash

git clone https://github.com/JetBrains/kotlin.git ${GRADLE_PROJECT_DIR}
cd ${GRADLE_PROJECT_DIR}
./gradlew dist ideaPlugin

git clone https://github.com/JetBrains/kotlin.git ${JPS_PROJECT_DIR}
cd ${JPS_PROJECT_DIR}
#TODO: create project files by importing it from gradle
#TODO: build using JPS

./gradlew run ${GRADLE_PROJECT_DIR} ${JPS_PROJECT_DIR} ${UTIL_HOME}/js/dist/report