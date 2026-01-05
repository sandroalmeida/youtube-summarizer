#!/bin/bash

echo "=== LinkedIn Profile Automation Setup ==="
echo

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 11 or later."
    exit 1
fi

# Check Java version
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1-2)
echo "✓ Java version detected: $java_version"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed. Please install Maven."
    exit 1
fi

maven_version=$(mvn -version | head -n 1)
echo "✓ Maven detected: $maven_version"

echo
echo "Creating project directory structure..."

# Create Maven directory structure
mkdir -p src/main/java/com/linkedin/automation
mkdir -p src/main/resources
mkdir -p src/test/java

echo "✓ Directory structure created"

echo
echo "Installing Playwright browsers..."

# Install dependencies first
mvn clean compile

# Install Playwright browsers
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"

echo "✓ Playwright browsers installed"

echo
echo "=== Setup Complete! ==="
echo
echo "To run the application:"
echo "  mvn clean compile exec:java"
echo
echo "Or compile and run manually:"
echo "  mvn clean compile"
echo "  java -cp target/classes:$(mvn dependency:build-classpath -q -D includeScope=runtime -D mdep.outputFile=/dev/stdout) com.linkedin.automation.Main"
echo
echo "Project structure:"
echo "├── pom.xml"
echo "├── src/"
echo "│   └── main/"
echo "│       └── java/"
echo "│           └── com/"
echo "│               └── linkedin/"
echo "│                   └── automation/"
echo "│                       └── Main.java"
echo "└── target/"
echo
echo "Ready to start LinkedIn automation!"