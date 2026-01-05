package com.sandroalmeida.youtubesummarizer.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Utility class for JSON file operations
 * Provides methods to read, create, write, and manage JSON files
 */
public class JsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        // Configure ObjectMapper for pretty printing
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    /**
     * Converts a profile URL to a valid JSON filename
     * Extracts the profile identifier from LinkedIn URLs and creates a safe filename
     * 
     * @param profileUrl the LinkedIn profile URL
     * @return a safe filename for the JSON file
     * @throws RuntimeException if the URL format is invalid
     */
    public static String convertUrlToFileName(String profileUrl) {
        try {
            if (profileUrl == null || profileUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("Profile URL cannot be null or empty");
            }
            
            // Parse the URL to extract the path
            URL url = new URL(profileUrl.trim());
            String path = url.getPath();
            
            // Extract profile identifier from LinkedIn URLs
            // Expected formats:
            // - /in/profile-name/
            // - /talent/profile/profile-id
            // - /pub/profile-name/...
            
            String fileName;
            
            if (path.startsWith("/in/")) {
                // Standard LinkedIn profile: /in/profile-name/
                String profileName = path.substring(4); // Remove "/in/"
                if (profileName.endsWith("/")) {
                    profileName = profileName.substring(0, profileName.length() - 1);
                }
                fileName = "profile_" + profileName;
                
            } else if (path.startsWith("/talent/profile/")) {
                // Recruiter profile: /talent/profile/profile-id
                String profileId = path.substring(16); // Remove "/talent/profile/"
                if (profileId.contains("/")) {
                    profileId = profileId.substring(0, profileId.indexOf("/"));
                }
                fileName = "talent_" + profileId;
                
            } else if (path.startsWith("/pub/")) {
                // Public profile: /pub/profile-name/...
                String profilePath = path.substring(5); // Remove "/pub/"
                String profileName = profilePath.split("/")[0];
                fileName = "pub_" + profileName;
                
            } else {
                // Fallback: use hash of the full URL
                fileName = "profile_" + Math.abs(profileUrl.hashCode());
            }
            
            // Clean the filename to ensure it's filesystem-safe
            fileName = fileName.toLowerCase()
                .replaceAll("[^a-z0-9\\-_]", "_") // Replace non-alphanumeric chars with underscore
                .replaceAll("_{2,}", "_") // Replace multiple underscores with single
                .replaceAll("^_+|_+$", ""); // Remove leading/trailing underscores
            
            // Ensure filename is not empty
            if (fileName.isEmpty()) {
                fileName = "profile_" + Math.abs(profileUrl.hashCode());
            }
            
            return fileName + ".json";
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert URL to filename: " + profileUrl, e);
        }
    }
    
    /**
     * Writes a Java object to a JSON file
     * Creates the parent directories if they don't exist
     * 
     * @param fileName the name of the file to write to
     * @param object the object to serialize to JSON
     * @throws RuntimeException if the file cannot be written
     */
    public static void writeObjectToJson(String fileName, Object object) {
        try {
            Path filePath = Paths.get(fileName);
            
            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // Convert object to JSON and write to file
            objectMapper.writeValue(filePath.toFile(), object);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to write object to JSON file: " + fileName, e);
        }
    }
    
    /**
     * Reads a JSON file and converts it to a Map
     * 
     * @param fileName the name of the file to read
     * @return the JSON content as a Map
     * @throws RuntimeException if the file cannot be read or parsed
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readJsonToMap(String fileName) {
        try {
            Path filePath = Paths.get(fileName);
            
            if (!Files.exists(filePath)) {
                throw new RuntimeException("JSON file not found: " + fileName);
            }
            
            return objectMapper.readValue(filePath.toFile(), Map.class);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON file: " + fileName, e);
        }
    }
    
    /**
     * Reads a JSON file and converts it to the specified class type
     * 
     * @param fileName the name of the file to read
     * @param valueType the class type to convert the JSON to
     * @param <T> the type parameter
     * @return the JSON content as the specified type
     * @throws RuntimeException if the file cannot be read or parsed
     */
    public static <T> T readJsonToObject(String fileName, Class<T> valueType) {
        try {
            Path filePath = Paths.get(fileName);
            
            if (!Files.exists(filePath)) {
                throw new RuntimeException("JSON file not found: " + fileName);
            }
            
            return objectMapper.readValue(filePath.toFile(), valueType);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON file: " + fileName, e);
        }
    }
    
    /**
     * Checks if a JSON file exists
     * 
     * @param fileName the name of the file to check
     * @return true if the file exists, false otherwise
     */
    public static boolean exists(String fileName) {
        return Files.exists(Paths.get(fileName));
    }
    
    /**
     * Deletes a JSON file if it exists
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
            throw new RuntimeException("Failed to delete JSON file: " + fileName, e);
        }
    }
    
    /**
     * Gets the size of a JSON file in bytes
     * 
     * @param fileName the name of the file
     * @return the size of the file in bytes
     * @throws RuntimeException if the file cannot be read or does not exist
     */
    public static long getSize(String fileName) {
        try {
            Path filePath = Paths.get(fileName);
            
            if (!Files.exists(filePath)) {
                throw new RuntimeException("JSON file not found: " + fileName);
            }
            
            return Files.size(filePath);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to get JSON file size: " + fileName, e);
        }
    }
    
    /**
     * Converts an object to a JSON string
     * 
     * @param object the object to convert
     * @return the JSON string representation
     * @throws RuntimeException if the object cannot be serialized
     */
    public static String toJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON string", e);
        }
    }
    
    /**
     * Parses a JSON string to a Map
     * 
     * @param jsonString the JSON string to parse
     * @return the parsed Map
     * @throws RuntimeException if the string cannot be parsed
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJsonString(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON string", e);
        }
    }
}