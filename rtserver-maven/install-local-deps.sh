#!/bin/bash

# Script to install system dependencies to local Maven repository
# This resolves the Maven system dependency warnings

echo "Installing system dependencies to local Maven repository..."

# Install Alt-common
echo "Installing Alt-common..."
mvn install:install-file \
  -Dfile=lib/alt-common-1.0-SNAPSHOT.jar \
  -DgroupId=com.alterante.utils \
  -DartifactId=alt-common \
  -Dversion=1.0 \
  -Dpackaging=jar

# Install Cass-Server
echo "Installing Cass-Server..."
mvn install:install-file \
  -Dfile=lib/cass-server.jar \
  -DgroupId=com.alterante \
  -DartifactId=cass-server \
  -Dversion=1.0 \
  -Dpackaging=jar

echo "System dependencies installed to local Maven repository."
echo "You can now use pom-compatible.xml which references these dependencies properly."
