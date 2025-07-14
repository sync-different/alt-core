package com.alterante.utils.extractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Test class to verify the BinaryExtractorUtil fix for trailing newline removal.
 */
public class BinaryExtractorTestWithNewline {
    
    public static void main(String[] args) {
        System.out.println("Testing BinaryExtractorUtil with trailing newline removal...\n");
        
        // Test 1: Binary data without trailing newline
        testWithoutTrailingNewline();
        
        // Test 2: Binary data with trailing newline
        testWithTrailingNewline();
        
        System.out.println("\nAll tests completed!");
    }
    
    /**
     * Test extraction of binary data without trailing newline.
     */
    private static void testWithoutTrailingNewline() {
        System.out.println("=== Test 1: Binary data WITHOUT trailing newline ===");
        
        // Create binary content without any trailing newlines
        byte[] binaryContent = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64  // "World"
        };
        
        createTestFile("test-no-newline.dat", binaryContent);
        testExtraction("test-no-newline.dat", "extracted-no-newline.bin", binaryContent);
        cleanupFiles("test-no-newline.dat", "extracted-no-newline.bin");
    }
    
    /**
     * Test extraction of binary data with trailing newline.
     */
    private static void testWithTrailingNewline() {
        System.out.println("\n=== Test 2: Binary data WITH trailing newline ===");
        
        // Create binary content WITH a trailing newline
        byte[] binaryContentWithNewline = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64, // "World"
            0x0A                          // Trailing newline
        };
        
        // Expected content after newline removal
        byte[] expectedContent = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64  // "World" (no newline)
        };
        
        createTestFile("test-with-newline.dat", binaryContentWithNewline);
        testExtraction("test-with-newline.dat", "extracted-with-newline.bin", expectedContent);
        cleanupFiles("test-with-newline.dat", "extracted-with-newline.bin");
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
    private static void testExtraction(String inputFile, String outputFile, byte[] expectedContent) {
        try {
            boolean success = BinaryExtractorUtil.extractBinarySection(inputFile, outputFile);
            
            if (success) {
                byte[] extractedData = Files.readAllBytes(Paths.get(outputFile));
                
                System.out.println("✓ Extraction successful");
                System.out.println("  Extracted " + extractedData.length + " bytes");
                System.out.println("  Expected " + expectedContent.length + " bytes");
                
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
