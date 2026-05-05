#!/bin/bash
# Build scrubber-maven module and propagate to uber JAR
# Usage: ./build-sc.sh [--no-uber]
#
# `set -e` aborts on any failure. Without it, an mvn compile error would let
# the script continue and shade a STALE jar into the uber JAR while still
# printing BUILD SUCCESS. See feedback_buildrt_silent_failure memory.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "-------------------building scrubber.jar"
cd scrubber-maven
mvn clean
mvn compile
mvn package
cd ..
cp scrubber-maven/target/scrubber.jar ./scrubber/repo/scrubber.jar

if [ "$1" != "--no-uber" ]; then
    echo "-------------------rebuilding uber jar"
    cd scrubber
    ./sync.sh
    ./install.sh
    mvn clean
    mvn compile
    mvn package
    cd ..
    echo "-------------------uber JAR rebuilt successfully"
else
    echo "-------------------skipping uber JAR rebuild (--no-uber)"
fi
