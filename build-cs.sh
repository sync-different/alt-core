#!/bin/bash
# Build cass-server module and propagate to uber JAR
# Usage: ./build-cs.sh [--no-uber]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "-------------------building cass-server.jar"
cd cass-server-maven
./install-local-deps.sh
mvn clean
mvn compile
mvn package
cd ..
cp cass-server-maven/target/cass-server-1.0.0.jar ./rtserver-maven/lib/cass-server.jar
cp cass-server-maven/target/cass-server-1.0.0.jar ./scrubber/repo/cass-server.jar

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
