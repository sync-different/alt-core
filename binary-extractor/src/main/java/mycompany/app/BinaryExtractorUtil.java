package com.mycompany.app;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Utility class for extracting binary data from multipart form data files.
 * 
 * This class handles files with the structure:
 * -----WebKitFormBoundary
 * Content-Disposition: form-data
 * Content-Type: octet-stream
 * 
 * [BINARY DATA]
 * 
 * -----WebKitFormBoundary [suffix]
 */
public class BinaryExtractorUtil {
    
    private static final Logger logger = Logger.getLogger(BinaryExtractorUtil.class.getName());
    
    // Common boundary patterns to search for
    private static final String[] BOUNDARY_PATTERNS = {
        "-----WebKitFormBoundary",
        "-----WebkitFormBoundary", 
        "-----webkitformboundary"
    };
    
    // Header end markers (double newline variations)
    private static final byte[][] HEADER_END_MARKERS = {
        "\r\n\r\n".getBytes(),
        "\n\n".getBytes()
    };
    
    /**
     * Extracts the binary section from a multipart form data file.
     * 
     * @param inputFilePath Path to the input multipart file
     * @param outputFilePath Path where the extracted binary data will be saved
     * @return true if extraction was successful, false otherwise
     * @throws IOException if file I/O operations fail
     */
    public static boolean extractBinarySection(String inputFilePath, String outputFilePath) throws IOException {
        logger.info("Starting binary extraction from: " + inputFilePath);
        
        // Validate input parameters
        if (!validateInputs(inputFilePath, outputFilePath)) {
            return false;
        }
        
        try {
            // Read the entire file into memory
            byte[] fileData = Files.readAllBytes(Paths.get(inputFilePath));
            logger.info("Read " + fileData.length + " bytes from input file");
            
            // Find the end of the first header
            int binaryStart = findHeaderEnd(fileData);
            if (binaryStart == -1) {
                logger.warning("Could not find end of first header (Content-Type section)");
                return false;
            }
            
            // Find the start of the closing boundary
            int binaryEnd = findClosingBoundary(fileData, binaryStart);
            if (binaryEnd == -1) {
                logger.warning("Could not find closing boundary");
                return false;
            }
            
            // Extract the binary data
            int binaryLength = binaryEnd - binaryStart;
            if (binaryLength <= 0) {
                logger.warning("No binary data found between headers");
                return false;
            }
            
            byte[] binaryData = new byte[binaryLength];
            System.arraycopy(fileData, binaryStart, binaryData, 0, binaryLength);
            
            // Write the binary data to output file
            Files.write(Paths.get(outputFilePath), binaryData);
            
            logger.info("Successfully extracted " + binaryLength + " bytes of binary data to: " + outputFilePath);
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "I/O error during binary extraction", e);
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during binary extraction", e);
            return false;
        }
    }
    
    /**
     * Alternative method that returns the extracted binary data as a byte array.
     * 
     * @param inputFilePath Path to the input multipart file
     * @return byte array containing the extracted binary data, or null if extraction failed
     * @throws IOException if file I/O operations fail
     */
    public static byte[] extractBinaryData(String inputFilePath) throws IOException {
        logger.info("Extracting binary data to memory from: " + inputFilePath);
        
        if (!Files.exists(Paths.get(inputFilePath))) {
            logger.warning("Input file does not exist: " + inputFilePath);
            return null;
        }
        
        try {
            byte[] fileData = Files.readAllBytes(Paths.get(inputFilePath));
            
            int binaryStart = findHeaderEnd(fileData);
            if (binaryStart == -1) {
                return null;
            }
            
            int binaryEnd = findClosingBoundary(fileData, binaryStart);
            if (binaryEnd == -1) {
                return null;
            }
            
            int binaryLength = binaryEnd - binaryStart;
            if (binaryLength <= 0) {
                return null;
            }
            
            byte[] binaryData = new byte[binaryLength];
            System.arraycopy(fileData, binaryStart, binaryData, 0, binaryLength);
            
            logger.info("Successfully extracted " + binaryLength + " bytes of binary data to memory");
            return binaryData;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "I/O error during binary data extraction", e);
            throw e;
        }
    }
    
    /**
     * Finds the end of the first header by looking for double newline after Content-Type.
     */
    private static int findHeaderEnd(byte[] data) {
        // First, find "Content-Type:" (case insensitive)
        String contentTypePattern = "content-type:";
        int contentTypePos = indexOfIgnoreCase(data, contentTypePattern.getBytes(), 0);
        
        if (contentTypePos == -1) {
            logger.warning("Content-Type header not found");
            return -1;
        }
        
        // Look for double newline after Content-Type
        for (byte[] marker : HEADER_END_MARKERS) {
            int headerEndPos = indexOf(data, marker, contentTypePos);
            if (headerEndPos != -1) {
                logger.info("Found header end at position: " + (headerEndPos + marker.length));
                return headerEndPos + marker.length;
            }
        }
        
        logger.warning("Could not find header end marker after Content-Type");
        return -1;
    }
    
    /**
     * Finds the start of the closing boundary.
     */
    private static int findClosingBoundary(byte[] data, int startPos) {
        // Look for any of the boundary patterns
        for (String pattern : BOUNDARY_PATTERNS) {
            int boundaryPos = indexOf(data, pattern.getBytes(), startPos);
            if (boundaryPos != -1) {
                logger.info("Found closing boundary at position: " + boundaryPos);
                return boundaryPos;
            }
        }
        
        logger.warning("Could not find closing boundary");
        return -1;
    }
    
    /**
     * Finds the first occurrence of a byte pattern in a byte array.
     */
    private static int indexOf(byte[] source, byte[] pattern, int startPos) {
        if (pattern.length == 0 || startPos >= source.length) {
            return -1;
        }
        
        for (int i = startPos; i <= source.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (source[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Case-insensitive version of indexOf for byte arrays.
     */
    private static int indexOfIgnoreCase(byte[] source, byte[] pattern, int startPos) {
        if (pattern.length == 0 || startPos >= source.length) {
            return -1;
        }
        
        for (int i = startPos; i <= source.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                byte sourceByte = source[i + j];
                byte patternByte = pattern[j];
                
                // Convert to lowercase for comparison
                if (sourceByte >= 'A' && sourceByte <= 'Z') {
                    sourceByte = (byte) (sourceByte + 32);
                }
                if (patternByte >= 'A' && patternByte <= 'Z') {
                    patternByte = (byte) (patternByte + 32);
                }
                
                if (sourceByte != patternByte) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Validates input parameters.
     */
    private static boolean validateInputs(String inputFilePath, String outputFilePath) {
        if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
            logger.warning("Input file path is null or empty");
            return false;
        }
        
        if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
            logger.warning("Output file path is null or empty");
            return false;
        }
        
        if (!Files.exists(Paths.get(inputFilePath))) {
            logger.warning("Input file does not exist: " + inputFilePath);
            return false;
        }
        
        // Create parent directories for output file if they don't exist
        try {
            java.nio.file.Path outputPath = Paths.get(outputFilePath);
            java.nio.file.Path parentDir = outputPath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not create output directory", e);
            return false;
        }
        
        return true;
    }
    
    /**
     * Main method for testing the utility.
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java BinaryExtractorUtil <input-file> <output-file>");
            System.out.println("Example: java BinaryExtractorUtil multipart-data.txt extracted-binary.bin");
            return;
        }
        
        String inputFile = args[0];
        String outputFile = args[1];
        
        try {
            boolean success = extractBinarySection(inputFile, outputFile);
            if (success) {
                System.out.println("Binary extraction completed successfully!");
                System.out.println("Output saved to: " + outputFile);
            } else {
                System.out.println("Binary extraction failed. Check the log for details.");
            }
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
