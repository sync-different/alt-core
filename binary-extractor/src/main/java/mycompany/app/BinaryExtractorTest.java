package com.mycompany.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Test class to demonstrate usage of BinaryExtractorUtil.
 */
public class BinaryExtractorTest {
    
    public static void main(String[] args) {
        // Create a sample multipart file for testing
        createSampleMultipartFile();
        
        // Test the binary extraction
        testBinaryExtraction();
        
        // Test the in-memory extraction
        testInMemoryExtraction();
    }
    
    /**
     * Creates a sample multipart file for testing purposes.
     */
    private static void createSampleMultipartFile() {
        String sampleContent = 
            "-----WebKitFormBoundary7MA4YWxkTrZu0gW\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"test.bin\"\r\n" +
            "Content-Type: octet-stream\r\n" +
            "\r\n" +
            "This is sample binary data that should be extracted.\r\n" +
            "It can contain any bytes including null bytes and special characters.\r\n" +
            "Binary data: \u0000\u0001\u0002\u0003\u00FF\u00FE\u00FD\r\n" +
            "-----WebKitFormBoundary7MA4YWxkTrZu0gW--\r\n";
        
        try {
            Files.write(Paths.get("test-multipart.dat"), sampleContent.getBytes());
            System.out.println("Created sample multipart file: test-multipart.dat");
        } catch (IOException e) {
            System.err.println("Failed to create sample file: " + e.getMessage());
        }
    }
    
    /**
     * Tests the file-to-file binary extraction method.
     */
    private static void testBinaryExtraction() {
        System.out.println("\n=== Testing File-to-File Extraction ===");
        
        try {
            //boolean success = BinaryExtractorUtil.extractBinarySection(
            //    "test-multipart.dat",
            //    "extracted-binary.bin"
            //);

            boolean success = BinaryExtractorUtil.extractBinarySection(
                    "4k_thetestdata.mp4..txt",
                    "extracted-binary2.bin"
            );

            if (success) {
                System.out.println("✓ Binary extraction successful!");
                
                // Read and display the extracted content
                byte[] extractedData = Files.readAllBytes(Paths.get("extracted-binary.bin"));
                System.out.println("Extracted " + extractedData.length + " bytes");
                System.out.println("Content preview: " + new String(extractedData).substring(0, 
                    Math.min(100, extractedData.length)) + "...");
            } else {
                System.out.println("✗ Binary extraction failed");
            }
            
        } catch (IOException e) {
            System.err.println("✗ I/O Error during extraction: " + e.getMessage());
        }
    }
    
    /**
     * Tests the in-memory binary extraction method.
     */
    private static void testInMemoryExtraction() {
        System.out.println("\n=== Testing In-Memory Extraction ===");
        
        try {
            byte[] binaryData = BinaryExtractorUtil.extractBinaryData("test-multipart.dat");
            
            if (binaryData != null) {
                System.out.println("✓ In-memory extraction successful!");
                System.out.println("Extracted " + binaryData.length + " bytes to memory");
                System.out.println("Content preview: " + new String(binaryData).substring(0, 
                    Math.min(100, binaryData.length)) + "...");
            } else {
                System.out.println("✗ In-memory extraction failed");
            }
            
        } catch (IOException e) {
            System.err.println("✗ I/O Error during in-memory extraction: " + e.getMessage());
        }
    }
}
