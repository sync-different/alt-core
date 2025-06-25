#!/bin/bash

# RTServer Maven Build Script

echo "==============================================="
echo "RTServer Maven Build Script"
echo "==============================================="

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed. Please install Maven 3.6 or higher."
    exit 1
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed. Please install Java 11 or higher."
    exit 1
fi

# Display versions
echo "Maven version:"
mvn -version | grep "Apache Maven"
echo ""
echo "Java version:"
java -version 2>&1 | grep "version"
echo ""

# Build options
case "$1" in
    clean)
        echo "Cleaning project..."
        mvn clean
        ;;
    compile)
        echo "Compiling project..."
        mvn clean compile
        ;;
    package)
        echo "Building JAR package..."
        mvn clean package
        echo ""
        echo "Build complete! JARs created in target/ directory:"
        echo "  - rtserver-1.0-SNAPSHOT.jar"
        echo "  - rtserver-1.0-SNAPSHOT-jar-with-dependencies.jar"
        ;;
    run)
        echo "Running RTServer..."
        mvn exec:java -Dexec.mainClass="javaapplication1.Main"
        ;;
    test)
        echo "Running tests..."
        mvn test
        ;;
    *)
        echo "Usage: ./build.sh [clean|compile|package|run|test]"
        echo ""
        echo "Options:"
        echo "  clean    - Clean build artifacts"
        echo "  compile  - Compile source code"
        echo "  package  - Create JAR files"
        echo "  run      - Run the application"
        echo "  test     - Run unit tests"
        echo ""
        echo "Example: ./build.sh package"
        ;;
esac
