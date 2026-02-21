#!/bin/bash
# Build rtserver module and propagate to uber JAR
# Usage: ./build-rt.sh [--no-uber]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "building commons"
cd alt-common
mvn clean
mvn compile
mvn package
cd ..

echo "-------------------building cass-server.jar"
cd cass-server-maven
mvn clean
mvn compile
mvn package
cd ..
cp cass-server-maven/target/cass-server-1.0.0.jar ./rtserver-maven/lib/cass-server.jar
cp cass-server-maven/target/cass-server-1.0.0.jar ./scrubber/repo/cass-server.jar

echo "-------------------building rtserver.jar"
cd rtserver-maven
./sync.sh
mvn clean
mvn compile
mvn package
cd ..
cp rtserver-maven/target/rtserver-1.0-SNAPSHOT.jar ./scrubber/repo/rtserver.jar

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
