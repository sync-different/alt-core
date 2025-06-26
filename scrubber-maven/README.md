# Scrubber Maven Project

This is a Maven conversion of the original Scrubber project, which was previously built using Ant and NetBeans.

## Project Structure

```
scrubber-maven/
├── pom.xml                 # Maven configuration
├── src/
│   └── main/
│       └── java/
│           ├── main/       # Main application entry point
│           ├── services/   # Service layer classes
│           ├── processor/  # File processing utilities
│           ├── utils/      # Utility classes
│           └── amazon/     # Amazon Drive integration
├── config/                 # Configuration files
└── target/                 # Maven build output (generated)
```

## Dependencies

The project has been updated to use modern Maven dependencies:

### Core Dependencies
- **Java 11** - Target platform
- **Apache Cassandra Thrift 3.11.15** - Database connectivity
- **MapDB 3.0.9** - Embedded database
- **Apache POI 5.2.4** - Office document processing
- **Apache Thrift 0.17.0** - RPC framework
- **SLF4J 2.0.7** - Logging framework

### HTTP & Networking
- **Apache HttpClient 5.2.1** - HTTP client library
- **Commons HttpClient 3.1** - Legacy HTTP support

### Media & File Processing
- **JAudioTagger 2.0.3** - Audio metadata processing
- **Thumbnailator 0.4.19** - Image thumbnail generation
- **JAI ImageIO Core 1.4.0** - Image processing

### Communication
- **JavaMail 1.6.2** - Email functionality
- **RestFB 2023.12.0** - Facebook API integration

### Utilities
- **JSON Smart 2.4.10** - JSON processing
- **DOM4J 2.1.4** - XML processing
- **Commons Codec 1.15** - Encoding utilities

## Build Instructions

### Prerequisites
- Java 11 or higher
- Maven 3.6 or higher

### Building the Project

```bash
# Clean and compile
mvn clean compile

# Run tests (if any)
mvn test

# Package the application
mvn package

# Create executable JAR with dependencies
mvn clean package
```

The build will create two JAR files in the `target/` directory:
- `scrubber.jar` - Standard JAR
- `scrubber-shaded.jar` - Fat JAR with all dependencies included

### Running the Application

```bash
# Using the fat JAR (recommended)
java -jar target/scrubber-shaded.jar

# Using the standard JAR (requires classpath)
java -cp target/scrubber.jar:target/lib/* main.Main
```

## Known Issues and Limitations

### Compilation Issues
The project currently has compilation errors due to:

1. **MapDB API Changes**: The code uses MapDB 1.0.x API, but Maven uses 3.0.x which has breaking changes
2. **Missing WinRun4J**: Windows service functionality is not available in Maven Central
3. **PDF Renderer**: Legacy PDF processing library not available in Maven Central

### Recommended Solutions

1. **MapDB Migration**: Update code to use MapDB 3.x API
2. **WinRun4J Alternative**: Consider using Apache Commons Daemon or Java Service Wrapper
3. **PDF Processing**: Migrate to Apache PDFBox or iText

### Temporary Workarounds

For immediate compilation, you can:

1. **Install missing JARs to local repository**:
   ```bash
   mvn install:install-file -Dfile=../lib/WinRun4J.jar -DgroupId=org.boris -DartifactId=winrun4j -Dversion=0.4.5 -Dpackaging=jar
   mvn install:install-file -Dfile=../lib/PDFRenderer-0.9.1.jar -DgroupId=com.sun.pdfview -DartifactId=pdf-renderer -Dversion=0.9.1 -Dpackaging=jar
   ```

2. **Uncomment system dependencies** in pom.xml and ensure JAR files exist in `../lib/`

## Migration Notes

### From Ant to Maven
- Source code moved from `src/` to `src/main/java/`
- Configuration files copied to `config/`
- Dependencies converted from JAR files to Maven coordinates
- Build configuration moved from `build.xml` to `pom.xml`

### Java Version Upgrade
- Upgraded from Java 7/8 to Java 11
- Some deprecated API warnings need to be addressed
- Modern dependency versions for better Java 11 compatibility

### Package Structure
- All packages maintained: `main`, `services`, `processor`, `utils`, `amazon`
- No changes to class names or package declarations

## Development

### IDE Setup
Import as a Maven project in your IDE:
- **IntelliJ IDEA**: File → Open → Select `pom.xml`
- **Eclipse**: File → Import → Existing Maven Projects
- **VS Code**: Open folder with Maven extension

### Adding Dependencies
Add new dependencies to `pom.xml`:
```xml
<dependency>
    <groupId>group.id</groupId>
    <artifactId>artifact-id</artifactId>
    <version>version</version>
</dependency>
```

### Configuration
- Application configuration files are in `config/`
- Maven configuration in `pom.xml`
- Logging configuration can be added to `src/main/resources/`

## Original Project
This Maven project is converted from the original Ant-based Scrubber project. The original build files (`build.xml`, `nbproject/`) are preserved in the parent directory.
