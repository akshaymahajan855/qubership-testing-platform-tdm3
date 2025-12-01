/*
 *  Copyright 2024-2025 NetCracker Technology Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.qubership.atp.tdm.env.configurator.utils.decryptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDecryptionException;

/**
 * Decryptor for SOPS-encrypted files using age encryption.
 * <p>
 * This decryptor uses SOPS CLI to decrypt files that were encrypted with SOPS using age keys.
 * The private key must be provided during construction.
 */
@Slf4j
public class SopsDecryptor implements Decryptor {

    private static final String SOPS_AGE_KEY_ENV = "SOPS_AGE_KEY";
    private static final String SOPS_COMMAND = "sops";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    
    // Pattern to match SOPS metadata: "sops:" at the beginning of a line (with optional whitespace)
    private static final Pattern SOPS_METADATA_PATTERN = Pattern.compile("(?m)^\\s*sops\\s*:");
    // Pattern to match encrypted values: ENC[ALGORITHM, data:...]
    private static final Pattern ENCRYPTED_VALUE_PATTERN = Pattern.compile("ENC\\[[^\\]]+\\]");

    private final String privateKey;
    private final int timeoutSeconds;

    /**
     * Creates a new SopsDecryptor instance with the provided private key.
     *
     * @param privateKey the age private key for decryption
     * @throws IllegalArgumentException if the private key is null or empty
     */
    public SopsDecryptor(String privateKey) {
        this(privateKey, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Creates a new SopsDecryptor instance with the provided private key and timeout.
     *
     * @param privateKey the age private key for decryption
     * @param timeoutSeconds timeout for SOPS command execution in seconds
     * @throws IllegalArgumentException if the private key is null or empty
     */
    public SopsDecryptor(String privateKey, int timeoutSeconds) {
        if (privateKey == null || privateKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key cannot be null or empty");
        }
        this.privateKey = privateKey.trim();
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Decrypts a SOPS-encrypted file.
     *
     * @param encryptedFilePath path to the encrypted file
     * @return decrypted content as a string
     * @throws TdmEnvDecryptionException if decryption fails
     */
    @Override
    public String decrypt(Path encryptedFilePath) throws TdmEnvDecryptionException {
        if (encryptedFilePath == null) {
            throw new IllegalArgumentException("Encrypted file path cannot be null");
        }

        if (!Files.exists(encryptedFilePath)) {
            throw new TdmEnvDecryptionException("File does not exist: " + encryptedFilePath);
        }

        if (!Files.isRegularFile(encryptedFilePath)) {
            throw new TdmEnvDecryptionException("Path is not a regular file: " + encryptedFilePath);
        }

        log.debug("Decrypting file: {}", encryptedFilePath);

        try {
            return executeSopsDecrypt(encryptedFilePath);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to decrypt file: " + encryptedFilePath, e);
            throw new TdmEnvDecryptionException("Failed to decrypt file: " + encryptedFilePath);
        }
    }

    /**
     * Decrypts a SOPS-encrypted file from a string path.
     *
     * @param encryptedFilePath path to the encrypted file as a string
     * @return decrypted content as a string
     * @throws TdmEnvDecryptionException if decryption fails
     */
    public String decrypt(String encryptedFilePath) throws TdmEnvDecryptionException {
        return decrypt(Paths.get(encryptedFilePath));
    }

    /**
     * Checks if a file is encrypted with SOPS.
     * SOPS-encrypted files contain:
     * 1. A top-level 'sops' key in their YAML structure (metadata)
     * 2. Encrypted values in the format ENC[ALGORITHM, data:...]
     *
     * @param filePath path to the file to check
     * @return true if the file appears to be SOPS-encrypted, false otherwise
     */
    public boolean isEncrypted(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return false;
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            boolean hasSopsMetadata = SOPS_METADATA_PATTERN.matcher(content).find();
            boolean hasEncryptedValues = ENCRYPTED_VALUE_PATTERN.matcher(content).find();
            
            boolean isEncrypted = hasSopsMetadata || hasEncryptedValues;
            
            if (isEncrypted) {
                log.debug("File {} detected as SOPS-encrypted (metadata: {}, encrypted values: {})", 
                        filePath, hasSopsMetadata, hasEncryptedValues);
            }
            
            return isEncrypted;
        } catch (IOException e) {
            log.warn("Failed to check if file is encrypted: {}", filePath, e);
            return false;
        }
    }

    /**
     * Executes SOPS decrypt command.
     *
     * @param filePath path to the encrypted file
     * @return decrypted content
     * @throws IOException if I/O error occurs
     * @throws InterruptedException if the process is interrupted
     * @throws TdmEnvDecryptionException if decryption fails
     */
    private String executeSopsDecrypt(Path filePath) throws IOException, InterruptedException, TdmEnvDecryptionException {
        List<String> command = new ArrayList<>();
        command.add(SOPS_COMMAND);
        command.add("--decrypt");
        command.add(filePath.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);

        // Set environment variable for SOPS to use the age private key
        Map<String, String> env = processBuilder.environment();
        env.put(SOPS_AGE_KEY_ENV, privateKey);

        log.debug("Executing SOPS command: {}", String.join(" ", command));

        Process process = processBuilder.start();

        // Read output and error streams
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        // Read from both streams concurrently
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                log.warn("Error reading process output", e);
            }
        });

        Thread errorThread = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorOutput.append(errorLine).append("\n");
                }
            } catch (IOException e) {
                log.warn("Error reading process error stream", e);
            }
        });

        outputThread.start();
        errorThread.start();

        try {
            outputThread.join();
            errorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while reading process output", e);
            throw new TdmEnvDecryptionException("Interrupted while reading process output");
        }

        // Wait for process to complete with timeout
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new TdmEnvDecryptionException(
                    String.format("SOPS command timed out after %d seconds", timeoutSeconds));
        }

        int exitCode = process.exitValue();

        if (exitCode != 0) {
            String errorMessage = errorOutput.length() > 0
                    ? errorOutput.toString()
                    : output.toString();

            // Handle specific SOPS error cases
            if (errorMessage.contains("metadata not found")) {
                throw new TdmEnvDecryptionException(
                        "File is not encrypted or already decrypted: " + filePath);
            }

            if (errorMessage.contains("no decryption key")) {
                throw new TdmEnvDecryptionException(
                        "No valid decryption key found. Check that the age private key is correctly configured.");
            }

            throw new TdmEnvDecryptionException(
                    String.format("SOPS decryption failed with exit code %d: %s", exitCode, errorMessage));
        }

        String decryptedContent = output.toString();
        log.debug("Successfully decrypted file: {}", filePath);

        return decryptedContent;
    }
}
