#!/bin/bash
# Build alt-common module and propagate to uber JAR
# Usage: ./build-common.sh [--no-uber]
#
# `set -e` aborts on any failure. Without it, an mvn compile error would let
# the script continue and shade a STALE jar into the uber JAR while still
# printing BUILD SUCCESS. See feedback_buildrt_silent_failure memory.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "-------------------building alt-common"
cd alt-common
mvn clean
mvn compile
mvn package
cd ..
cp alt-common/target/alt-common-1.0-SNAPSHOT.jar ./rtserver-maven/lib/

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
