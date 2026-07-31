#!/bin/sh

# Licensed under the Apache License, Version 2.0.

APP_HOME=$( cd -P "${0%/*}" >/dev/null 2>&1 && pwd )
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in PATH." >&2
    exit 1
fi

exec "$JAVACMD" "-Xmx64m" "-Xms64m" \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"

