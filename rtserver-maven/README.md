# RTServer Maven Project

This is a Maven-based build of the Alterante RT Server - a personal cloud storage solution.

## Project Structure

```
rtserver-maven/
├── pom.xml                  # Maven configuration
├── lib/                     # Local JAR dependencies
│   ├── cass-server.jar      # Contains utils package
│   └── mailer.jar           # Email functionality
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── javaapplication1/   # Main application classes
│   │   │   └── netty/              # Netty-based server classes
│   │   └── resources/
│   │       └── www-server.properties
│   └── test/
│       └── java/            # Test classes (empty)
```

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher

## Building the Project

1. Navigate to the project directory:
   ```bash
   cd rtserver-maven
   ```

2. Clean and compile:
   ```bash
   mvn clean compile
   ```

3. Package into JAR:
   ```bash
   mvn package
   ```

This will create two JAR files in the `target` directory:
- `rtserver-1.0-SNAPSHOT.jar` - Regular JAR without dependencies
- `rtserver-1.0-SNAPSHOT-jar-with-dependencies.jar` - Executable JAR with all dependencies

## Running the Application

### Option 1: Using the executable JAR
```bash
java -jar target/rtserver-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### Option 2: Using Maven
```bash
mvn exec:java -Dexec.mainClass="javaapplication1.Main"
```

## Dependencies

The project includes the following main dependencies:
- **Netty 4.1.68** - High-performance networking
- **Apache Cassandra Thrift** - Database connectivity
- **MapDB 1.0.9** - Embedded database
- **JSON Smart** - JSON processing
- **Commons libraries** - Various utilities
- **JavaMail** - Email functionality

## Configuration

The main configuration file is located at:
`src/main/resources/www-server.properties`

## Notes

- The project uses Java 11 as the target version
- Local JARs (cass-server.jar and mailer.jar) are included as system dependencies
- The main class is `javaapplication1.Main`
