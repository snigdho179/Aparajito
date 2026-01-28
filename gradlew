#!/usr/bin/env bash

# Use the maximum available memory for the build
export GRADLE_OPTS="-Xmx2048m -Xms512m"

# Find the path to the wrapper properties
WRAPPER_PROPERTIES="gradle/wrapper/gradle-wrapper.properties"

# Check if properties file exists
if [ ! -f "$WRAPPER_PROPERTIES" ]; then
    echo "Error: $WRAPPER_PROPERTIES not found. Please create it first."
    exit 1
fi

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Check if java is installed
if ! command -v "$JAVACMD" &> /dev/null; then
    echo "Error: Java not found. Please ensure JDK 17 is installed."
    exit 1
fi

# Download Gradle and start the build
# GitHub Actions usually provides the Gradle binary, so this executes the build
exec ./gradlew "$@"
