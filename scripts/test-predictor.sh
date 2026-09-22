#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
    export JAVA_HOME
fi
mkdir -p build/predictor-tests
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d build/predictor-tests app/src/main/java/dev/habitdock/Predictor.java app/src/main/java/dev/habitdock/PrivacyPolicy.java tests/PredictorTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp build/predictor-tests PredictorTest
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d build/predictor-tests app/src/main/java/dev/habitdock/AppCatalog.java app/src/main/java/dev/habitdock/WidgetSizing.java tests/PickerLayoutTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp build/predictor-tests PickerLayoutTest
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d build/predictor-tests app/src/main/java/dev/habitdock/IconBackground.java tests/IconBackgroundTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -Djava.awt.headless=true -cp build/predictor-tests IconBackgroundTest
