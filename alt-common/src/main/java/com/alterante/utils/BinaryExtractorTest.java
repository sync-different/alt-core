package com.alterante.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Test class to verify the BinaryExtractorUtil fix for the extra 0x0A byte issue.
 */
public class BinaryExtractorTest {
    
    public static void main(String[] args) {
        System.out.println("Testing BinaryExtractorUtil fix for extra 0x0A byte...\n");
        
        // Create a test multipart file with known binary content
        createTestMultipartFile();
        
        // Test the extraction
        testBinaryExtraction();
        
        // Clean up test files
        //cleanupTestFiles();
    }
    
    /**
     * Creates a test multipart file with specific binary content.
     */
    private static void createTestMultipartFile() {
        // Create binary content without any trailing newlines
        byte[] binaryContent = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
            0x00, 0x01, 0x02, 0x03,       // Some binary bytes
            0x57, 0x6F, 0x72, 0x6C, 0x64  // "World"
        };
        
        // Create the multipart structure
        String header = "-----WebKitFormBoundary7MA4YWxkTrZu0gW\r\n" +
                       "Content-Disposition: form-data; name=\"file\"; filename=\"test.bin\"\r\n" +
                       "Content-Type: octet-stream\r\n" +
                       "\r\n";
        
        String footer = "\r\n-----WebKitFormBoundary7MA4YWxkTrZu0gW--\r\n";
        
        try {
            // Combine header + binary content + footer
            byte[] headerBytes = header.getBytes();
            byte[] footerBytes = footer.getBytes();
            
            byte[] fullContent = new byte[headerBytes.length + binaryContent.length + footerBytes.length];
            System.arraycopy(headerBytes, 0, fullContent, 0, headerBytes.length);
            System.arraycopy(binaryContent, 0, fullContent, headerBytes.length, binaryContent.length);
            System.arraycopy(footerBytes, 0, fullContent, headerBytes.length + binaryContent.length, footerBytes.length);
            
            Files.write(Paths.get("test-multipart.dat"), fullContent);
            System.out.println("✓ Created test multipart file with " + binaryContent.length + " bytes of binary data");
            
        } catch (IOException e) {
            System.err.println("✗ Failed to create test file: " + e.getMessage());
        }
    }
    
    /**
     * Tests the binary extraction and verifies no extra bytes are added.
     */
    private static void testBinaryExtraction() {
        try {
            boolean success = BinaryExtractorUtil.extractBinarySection(
                "test-multipart.dat", 
                "extracted-test.bin"
            );
            
            if (success) {
                // Read the extracted file and check its contents
                byte[] extractedData = Files.readAllBytes(Paths.get("extracted-test.bin"));
                
                System.out.println("✓ Extraction successful");
                System.out.println("  Extracted " + extractedData.length + " bytes");
                
                // Expected binary content
                byte[] expectedContent = {
                    0x48, 0x65, 0x6C, 0x6C, 0x6F, // "Hello"
                    0x00, 0x01, 0x02, 0x03,       // Some binary bytes
                    0x57, 0x6F, 0x72, 0x6C, 0x64  // "World"
                };
                
                // Verify the content matches exactly
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
                        System.out.println("✓ Content matches exactly - no extra bytes added!");
                    }
                } else {
                    System.out.println("✗ Length mismatch: expected " + expectedContent.length + 
                                     " bytes, got " + extractedData.length + " bytes");
                    
                    if (extractedData.length == expectedContent.length + 1) {
                        byte lastByte = extractedData[extractedData.length - 1];
                        System.out.println("  Extra byte at end: 0x" + String.format("%02X", lastByte));
                        if (lastByte == 0x0A) {
                            System.out.println("  ✗ ISSUE: Extra 0x0A (newline) byte detected!");
                        }
                    }
                }
                
                // Print hex dump of extracted content for verification
                System.out.println("\nHex dump of extracted content:");
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
            System.out.printf("%08X  ", i);
            
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
    private static void cleanupTestFiles() {
        try {
            Files.deleteIfExists(Paths.get("test-multipart.dat"));
            Files.deleteIfExists(Paths.get("extracted-test.bin"));
            System.out.println("\n✓ Cleaned up test files");
        } catch (IOException e) {
            System.err.println("Warning: Could not clean up test files: " + e.getMessage());
        }
    }
}
