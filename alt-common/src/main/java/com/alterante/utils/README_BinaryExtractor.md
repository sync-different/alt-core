# BinaryExtractorUtil - Usage Guide

## Overview

The `BinaryExtractorUtil` class is a standalone utility for extracting binary data from multipart form data files. It handles files with the following structure:

```
-----WebKitFormBoundary[random-string]
Content-Disposition: form-data; name="file"; filename="example.bin"
Content-Type: octet-stream

[BINARY DATA SECTION]

-----WebKitFormBoundary[random-string]--
```

## Features

- **Robust boundary detection**: Handles various WebKit boundary naming conventions
- **Binary-safe extraction**: Preserves all bytes including null bytes and special characters
- **Flexible line ending support**: Works with both `\r\n` and `\n` line endings
- **Two extraction modes**: File-to-file and in-memory extraction
- **Comprehensive error handling**: Detailed logging and validation
- **Case-insensitive header matching**: Finds Content-Type headers regardless of case

## Usage Examples

### 1. File-to-File Extraction

```java
import com.mycompany.app.BinaryExtractorUtil;

try {
    boolean success = BinaryExtractorUtil.extractBinarySection(
        "input/multipart-upload.dat", 
        "output/extracted-file.bin"
    );
    
    if (success) {
        System.out.println("Binary extraction completed successfully!");
    } else {
        System.out.println("Extraction failed - check file format");
    }
} catch (IOException e) {
    System.err.println("I/O Error: " + e.getMessage());
}
```

### 2. In-Memory Extraction

```java
import com.mycompany.app.BinaryExtractorUtil;

try {
    byte[] binaryData = BinaryExtractorUtil.extractBinaryData("multipart-file.dat");
    
    if (binaryData != null) {
        System.out.println("Extracted " + binaryData.length + " bytes");
        // Process the binary data as needed
        processData(binaryData);
    } else {
        System.out.println("Extraction failed");
    }
} catch (IOException e) {
    System.err.println("I/O Error: " + e.getMessage());
}
```

### 3. Command Line Usage

You can also run the utility directly from the command line:

```bash
# Compile the class
javac com/mycompany/app/BinaryExtractorUtil.java

# Run the extraction
java com.mycompany.app.BinaryExtractorUtil input-file.dat output-file.bin
```

## Method Reference

### `extractBinarySection(String inputFilePath, String outputFilePath)`

Extracts binary data from a multipart file and saves it to a new file.

**Parameters:**
- `inputFilePath`: Path to the input multipart file
- `outputFilePath`: Path where the extracted binary data will be saved

**Returns:** `boolean` - true if extraction succeeds, false otherwise

**Throws:** `IOException` - if file I/O operations fail

### `extractBinaryData(String inputFilePath)`

Extracts binary data from a multipart file and returns it as a byte array.

**Parameters:**
- `inputFilePath`: Path to the input multipart file

**Returns:** `byte[]` - extracted binary data, or null if extraction fails

**Throws:** `IOException` - if file I/O operations fail

## Supported File Formats

The utility recognizes these boundary patterns:
- `-----WebKitFormBoundary`
- `-----WebkitFormBoundary` (lowercase 'k')
- `-----webkitformboundary` (all lowercase)

And these header end markers:
- `\r\n\r\n` (Windows/HTTP standard)
- `\n\n` (Unix style)

## Error Handling

The utility provides comprehensive error handling:

- **File validation**: Checks if input files exist
- **Directory creation**: Automatically creates output directories
- **Format validation**: Verifies multipart structure
- **Detailed logging**: Uses Java logging for debugging

## Testing

Run the included test class to verify functionality:

```bash
javac com/mycompany/app/BinaryExtractorTest.java
java com.mycompany.app.BinaryExtractorTest
```

This will create a sample multipart file and test both extraction methods.

## Integration with Existing Projects

To use this utility in your existing Java project:

1. Copy `BinaryExtractorUtil.java` to your source directory
2. Update the package declaration if needed
3. Import and use the static methods as shown in the examples above

## Performance Considerations

- The utility loads the entire input file into memory for processing
- For very large files (>1GB), consider implementing a streaming version
- Binary data is copied once during extraction for optimal performance
- Output directories are created automatically if they don't exist

## Troubleshooting

**Common Issues:**

1. **"Content-Type header not found"**: Ensure your file has a proper multipart structure
2. **"Could not find closing boundary"**: Check that the boundary markers match
3. **"No binary data found"**: Verify there's content between the headers
4. **IOException**: Check file permissions and disk space

**Debug Mode:**

Enable Java logging to see detailed extraction information:
```bash
java -Djava.util.logging.level=INFO com.mycompany.app.BinaryExtractorUtil input.dat output.bin
