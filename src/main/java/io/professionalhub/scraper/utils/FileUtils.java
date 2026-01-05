package io.professionalhub.scraper.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Utility class for simple text file operations
 * Provides methods to read, create, write, and overwrite text files
 */
public class FileUtils {
    
    /**
     * Reads the content of a text file and returns it as a string
     * 
     * @param fileName the name of the file to read
     * @return the content of the file as a string
     * @throws RuntimeException if the file cannot be read or does not exist
     */
    public static String read(String fileName) {
        try {
            Path filePath = Paths.get(fileName);
            
            if (!Files.exists(filePath)) {
                throw new FileNotFoundException("File not found: " + fileName);
            }
            
            List<String> lines = Files.readAllLines(filePath);
            return String.join(System.lineSeparator(), lines);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + fileName, e);
        }
    }
    
    /**
     * Creates an empty text file with the specified name
     * If the file already exists, this method does nothing
     * 
     * @param fileName the name of the file to create
     * @throws RuntimeException if the file cannot be created
     */
    public static void create(String fileName) {
        try {
            Path filePath = Paths.get(fileName);
            
            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // Create the file only if it doesn't exist
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + fileName, e);
        }
    }
    
    /**
     * Appends a new line with the specified content to the file
     * If the file doesn't exist, it will be created first
     * 
     * @param fileName the name of the file to write to
     * @param content the content to append as a new line
     * @throws RuntimeException if the file cannot be written to
     */
    public static void write(String fileName, String content) {
        try {
            Path filePath = Paths.get(fileName);
            
            // Create the file if it doesn't exist
            if (!Files.exists(filePath)) {
                create(fileName);
            }
            
            // Append the content as a new line
            String lineToWrite = content + System.lineSeparator();
            Files.write(filePath, lineToWrite.getBytes(), 
                       StandardOpenOption.APPEND);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to file: " + fileName, e);
        }
    }
    
    /**
     * Overwrites the entire file content with the specified content
     * Creates the file if it doesn't exist
     * 
     * @param fileName the name of the file to overwrite
     * @param content the new content for the file
     * @throws RuntimeException if the file cannot be overwritten
     */
    public static void overwrite(String fileName, String content) {
        try {
            Path filePath = Paths.get(fileName);
            
            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // Write the content, creating or overwriting the file
            Files.write(filePath, content.getBytes());
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to overwrite file: " + fileName, e);
        }
    }
    
    /**
     * Checks if a file exists
     * 
     * @param fileName the name of the file to check
     * @return true if the file exists, false otherwise
     */
    public static boolean exists(String fileName) {
        return Files.exists(Paths.get(fileName));
    }
    
    /**
     * Deletes a file if it exists
     * 
     * @param fileName the name of the file to delete
     * @return true if the file was deleted, false if it didn't exist
     * @throws RuntimeException if the file cannot be deleted
     */
    public static boolean delete(String fileName) {
        try {
            Path filePath = Paths.get(fileName);
            return Files.deleteIfExists(filePath);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + fileName, e);
        }
    }
    
    /**
     * Gets the size of a file in bytes
     * 
     * @param fileName the name of the file
     * @return the size of the file in bytes
     * @throws RuntimeException if the file cannot be read or does not exist
     */
    public static long getSize(String fileName) {
        try {
            Path filePath = Paths.get(fileName);
            
            if (!Files.exists(filePath)) {
                throw new FileNotFoundException("File not found: " + fileName);
            }
            
            return Files.size(filePath);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to get file size: " + fileName, e);
        }
    }
}