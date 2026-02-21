#!/bin/bash
# Build all modules and create uber JAR
# This builds in dependency order: alt-common → cass-server → rtserver → scrubber-maven → uber JAR

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "building commons"
cd alt-common
mvn clean
mvn compile
mvn package
cd ..
cp alt-common/target/alt-common-1.0-SNAPSHOT.jar ./rtserver-maven/lib/

echo "-------------------building cass-server.jar"
cd cass-server-maven
./install-local-deps.sh
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


echo "-------------------building scrubber.jar"
cd scrubber-maven
mvn clean
mvn compile
mvn package
cd ..
cp scrubber-maven/target/scrubber.jar ./scrubber/repo/scrubber.jar

echo "-------------------building uber jar"
cd scrubber
./sync.sh
./install.sh
mvn clean
mvn compile
mvn package
