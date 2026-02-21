#!/bin/bash
# Build alt-common module and propagate to uber JAR
# Usage: ./build-common.sh [--no-uber]

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
