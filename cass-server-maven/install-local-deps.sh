#!/bin/bash

# Script to install system dependencies to local Maven repository
# This resolves the Maven system dependency warnings

echo "Installing system dependencies to local Maven repository..."

# Install JAudioTagger
echo "Installing JAudioTagger..."
mvn install:install-file \
  -Dfile=lib/jaudiotagger-2.2.0-20130321.162819-3.jar \
  -DgroupId=org.jaudiotagger \
  -DartifactId=jaudiotagger \
  -Dversion=2.2.0-SNAPSHOT \
  -Dpackaging=jar

# Install PDFRenderer
echo "Installing PDFRenderer..."
mvn install:install-file \
  -Dfile=lib/PDFRenderer-0.9.1.jar \
  -DgroupId=com.sun.pdfview \
  -DartifactId=PDFRenderer \
  -Dversion=0.9.1 \
  -Dpackaging=jar

echo "System dependencies installed to local Maven repository."
echo "You can now use pom-compatible.xml which references these dependencies properly."
