package com.alterante.utils.extractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Final comprehensive test class to verify BinaryExtractorUtil handles all patterns including \r\n-.
 */
public class BinaryExtractorTestFinal {
    
    public static void main(String[] args) {
        System.out.println("Testing BinaryExtractorUtil with all trailing patterns...\n");
        
        // Test 1: Binary data without any trailing patterns
        testWithoutTrailingPatterns();
        
        // Test 2: Binary data with trailing \n
        testWithTrailingLF();
        
        // Test 3: Binary data with trailing \r
        testWithTrailingCR();
        
        // Test 4: Binary data with trailing \r\n
        testWithTrailingCRLF();
        
        // Test 5: Binary data with trailing \r\n- pattern
        testWithTrailingCRLFDash();
        
        System.out.println("\nAll tests completed!");
    }
    
    /**
     * Test 1: Binary data without any trailing patterns.
     */
    private static void testWithoutTrailingPatterns() {
        System.out.println("=== Test 1: Binary data WITHOUT trailing patterns ===");
        
        byte[] binaryContent = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64  // "World"
        };
        
        createTestFile("test-no-patterns.dat", binaryContent);
        testExtraction("test-no-patterns.dat", "extracted-no-patterns.bin", binaryContent, "No trailing patterns");
        cleanupFiles("test-no-patterns.dat", "extracted-no-patterns.bin");
    }
    
    /**
     * Test 2: Binary data with trailing \n (LF).
     */
    private static void testWithTrailingLF() {
        System.out.println("\n=== Test 2: Binary data WITH trailing \\n (LF) ===");
        
        byte[] binaryContentWithLF = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64, // "World"
            0x0A                          // Trailing LF
        };
        
        byte[] expectedContent = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64  // "World" (no LF)
        };
        
        createTestFile("test-with-lf.dat", binaryContentWithLF);
        testExtraction("test-with-lf.dat", "extracted-with-lf.bin", expectedContent, "Trailing \\n should be removed");
        cleanupFiles("test-with-lf.dat", "extracted-with-lf.bin");
    }
    
    /**
     * Test 3: Binary data with trailing \r (CR).
     */
    private static void testWithTrailingCR() {
        System.out.println("\n=== Test 3: Binary data WITH trailing \\r (CR) ===");
        
        byte[] binaryContentWithCR = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64, // "World"
            0x0D                          // Trailing CR
        };
        
        byte[] expectedContent = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64  // "World" (no CR)
        };
        
        createTestFile("test-with-cr.dat", binaryContentWithCR);
        testExtraction("test-with-cr.dat", "extracted-with-cr.bin", expectedContent, "Trailing \\r should be removed");
        cleanupFiles("test-with-cr.dat", "extracted-with-cr.bin");
    }
    
    /**
     * Test 4: Binary data with trailing \r\n (CRLF).
     */
    private static void testWithTrailingCRLF() {
        System.out.println("\n=== Test 4: Binary data WITH trailing \\r\\n (CRLF) ===");
        
        byte[] binaryContentWithCRLF = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64, // "World"
            0x0D, 0x0A                    // Trailing CRLF
        };
        
        byte[] expectedContent = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64  // "World" (no CRLF)
        };
        
        createTestFile("test-with-crlf.dat", binaryContentWithCRLF);
        testExtraction("test-with-crlf.dat", "extracted-with-crlf.bin", expectedContent, "Trailing \\r\\n should be removed");
        cleanupFiles("test-with-crlf.dat", "extracted-with-crlf.bin");
    }
    
    /**
     * Test 5: Binary data with trailing \r\n- pattern.
     */
    private static void testWithTrailingCRLFDash() {
        System.out.println("\n=== Test 5: Binary data WITH trailing \\r\\n- pattern ===");
        
        byte[] binaryContentWithCRLFDash = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64, // "World"
            0x0D, 0x0A, 0x2D              // Trailing CRLF + dash (boundary start)
        };
        
        byte[] expectedContent = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64  // "World" (no CRLF-)
        };
        
        createTestFile("test-with-crlf-dash.dat", binaryContentWithCRLFDash);
        testExtraction("test-with-crlf-dash.dat", "extracted-with-crlf-dash.bin", expectedContent, "Trailing \\r\\n- pattern should be removed");
        cleanupFiles("test-with-crlf-dash.dat", "extracted-with-crlf-dash.bin");
    }
    
    /**
     * Creates a test multipart file with the given binary content.
     */
    private static void createTestFile(String filename, byte[] binaryContent) {
        String header = "-----WebKitFormBoundary7MA4YWxkTrZu0gW\r\n" +
                       "Content-Disposition: form-data; name=\"file\"; filename=\"test.bin\"\r\n" +
                       "Content-Type: octet-stream\r\n" +
                       "\r\n";
        
        String footer = "\r\n-----WebKitFormBoundary7MA4YWxkTrZu0gW--\r\n";
        
        try {
            byte[] headerBytes = header.getBytes();
            byte[] footerBytes = footer.getBytes();
            
            byte[] fullContent = new byte[headerBytes.length + binaryContent.length + footerBytes.length];
            System.arraycopy(headerBytes, 0, fullContent, 0, headerBytes.length);
            System.arraycopy(binaryContent, 0, fullContent, headerBytes.length, binaryContent.length);
            System.arraycopy(footerBytes, 0, fullContent, headerBytes.length + binaryContent.length, footerBytes.length);
            
            Files.write(Paths.get(filename), fullContent);
            System.out.println("✓ Created test file: " + filename + " with " + binaryContent.length + " bytes of binary data");
            
        } catch (IOException e) {
            System.err.println("✗ Failed to create test file: " + e.getMessage());
        }
    }
    
    /**
     * Tests binary extraction and verifies the result.
     */
    private static void testExtraction(String inputFile, String outputFile, byte[] expectedContent, String testDescription) {
        try {
            boolean success = BinaryExtractorUtil.extractBinarySection(inputFile, outputFile);
            
            if (success) {
                byte[] extractedData = Files.readAllBytes(Paths.get(outputFile));
                
                System.out.println("✓ Extraction successful");
                System.out.println("  Extracted " + extractedData.length + " bytes");
                System.out.println("  Expected " + expectedContent.length + " bytes");
                System.out.println("  Test: " + testDescription);
                
                if (extractedData.length == expectedContent.length) {
                    boolean contentMatches = true;
                    for (int i = 0; i < expectedContent.length; i++) {
                        if (extractedData[i] != expectedContent[i]) {
                            contentMatches = false;
                            System.out.println("✗ Content mismatch at byte " + i + 
                                             ": expected 0x" + String.format("%02X", expectedContent[i]) + 
                                             ", got 0x" + String.format("%02X", extractedData[i]));
                            break;
                        }
                    }
                    
                    if (contentMatches) {
                        System.out.println("✓ Content matches exactly!");
                    }
                } else {
                    System.out.println("✗ Length mismatch!");
                    if (extractedData.length > expectedContent.length) {
                        System.out.println("  Extra bytes detected:");
                        for (int i = expectedContent.length; i < extractedData.length; i++) {
                            System.out.println("    Byte " + i + ": 0x" + String.format("%02X", extractedData[i]));
                        }
                    }
                }
                
                // Print hex dump
                System.out.println("  Hex dump of extracted content:");
                printHexDump(extractedData);
                
            } else {
                System.out.println("✗ Extraction failed");
            }
            
        } catch (IOException e) {
            System.err.println("✗ I/O Error during extraction: " + e.getMessage());
        }
    }
    
    /**
     * Prints a hex dump of the given byte array.
     */
    private static void printHexDump(byte[] data) {
        for (int i = 0; i < data.length; i += 16) {
            System.out.printf("    %08X  ", i);
            
            // Print hex values
            for (int j = 0; j < 16; j++) {
                if (i + j < data.length) {
                    System.out.printf("%02X ", data[i + j]);
                } else {
                    System.out.print("   ");
                }
                if (j == 7) System.out.print(" ");
            }
            
            System.out.print(" |");
            
            // Print ASCII representation
            for (int j = 0; j < 16 && i + j < data.length; j++) {
                byte b = data[i + j];
                if (b >= 32 && b <= 126) {
                    System.out.print((char) b);
                } else {
                    System.out.print(".");
                }
            }
            
            System.out.println("|");
        }
    }
    
    /**
     * Cleans up test files.
     */
    private static void cleanupFiles(String... filenames) {
        for (String filename : filenames) {
            try {
                Files.deleteIfExists(Paths.get(filename));
            } catch (IOException e) {
                System.err.println("Warning: Could not delete " + filename + ": " + e.getMessage());
            }
        }
        System.out.println("✓ Cleaned up test files");
    }
}
