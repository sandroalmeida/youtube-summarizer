package com.sandroalmeida.youtubesummarizer.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

@Configuration
public class YouTubeApiConfig {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeApiConfig.class);
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Value("${youtube.api.client-secret-file:}")
    private String clientSecretFile;

    @Value("${youtube.api.app-name:YouTube Summarizer}")
    private String appName;

    @Value("${youtube.api.tokens-directory:tokens}")
    private String tokensDirectory;

    @Bean
    public NetHttpTransport httpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public YouTube youTube(NetHttpTransport httpTransport) throws IOException {
        Credential credential = authorize(httpTransport);
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(appName)
                .build();
    }

    private Credential authorize(NetHttpTransport httpTransport) throws IOException {
        // Find the client secret JSON file
        File secretFile = findClientSecretFile();
        logger.info("Using client secret file: {}", secretFile.getAbsolutePath());

        GoogleClientSecrets clientSecrets;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(secretFile))) {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
        }

        File tokensDir = new File(tokensDirectory);
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                clientSecrets,
                List.of(YouTubeScopes.YOUTUBE_READONLY))
                .setDataStoreFactory(new FileDataStoreFactory(tokensDir))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8789)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        logger.info("YouTube API authorized successfully. Tokens stored in: {}", tokensDir.getAbsolutePath());
        return credential;
    }

    private File findClientSecretFile() throws IOException {
        // 1. Check explicit config property
        if (clientSecretFile != null && !clientSecretFile.isEmpty()) {
            File file = new File(clientSecretFile);
            if (file.exists()) {
                return file;
            }
        }

        // 2. Auto-detect client_secret*.json in the working directory
        File[] matches = new File(".").listFiles(
                (dir, name) -> name.startsWith("client_secret") && name.endsWith(".json"));

        if (matches != null && matches.length > 0) {
            return matches[0];
        }

        throw new IOException(
                "Client secret JSON file not found. Download it from Google Cloud Console " +
                "and place it in the project root directory, or set youtube.api.client-secret-file property.");
    }
}
