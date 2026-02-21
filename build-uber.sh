#!/bin/bash
# Rebuild uber JAR from current repo/ JARs
# This purges maven cache and reinstalls all project JARs before building

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "-------------------building uber jar"
cd scrubber
./sync.sh
./install.sh
mvn clean
mvn compile
mvn package
