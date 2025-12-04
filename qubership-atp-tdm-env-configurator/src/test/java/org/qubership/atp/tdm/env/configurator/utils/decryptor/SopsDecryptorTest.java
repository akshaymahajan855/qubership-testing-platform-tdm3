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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDecryptionException;

class SopsDecryptorTest {

    private static final String TEST_PRIVATE_KEY = "AGE-SECRET-KEY-1TEST123456789012345678901234567890123456789012345678901234567890";
    private static final int TEST_TIMEOUT = 10;

    private SopsDecryptor decryptor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        decryptor = new SopsDecryptor(TEST_PRIVATE_KEY, TEST_TIMEOUT);
    }

    @Test
    void testConstructor_WithValidKey_ShouldCreateInstance() {
        // When
        SopsDecryptor instance = new SopsDecryptor(TEST_PRIVATE_KEY);

        // Then
        assertNotNull(instance);
    }

    @Test
    void testConstructor_WithValidKeyAndTimeout_ShouldCreateInstance() {
        // When
        SopsDecryptor instance = new SopsDecryptor(TEST_PRIVATE_KEY, TEST_TIMEOUT);

        // Then
        assertNotNull(instance);
    }

    @Test
    void testConstructor_WithNullKey_ShouldThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new SopsDecryptor(null));
    }

    @Test
    void testConstructor_WithEmptyKey_ShouldThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new SopsDecryptor(""));
    }

    @Test
    void testConstructor_WithWhitespaceOnlyKey_ShouldThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new SopsDecryptor("   "));
    }

    @Test
    void testIsEncrypted_WithSopsMetadata_ShouldReturnTrue() throws IOException {
        // Given
        String content = "someKey: someValue\n" +
                "sops:\n" +
                "  encrypted_regex: \"^(data|password)$\"\n" +
                "  age:\n" +
                "    - recipient: age1test123\n";
        Path testFile = createTestFile("test-encrypted.yaml", content);

        // When
        boolean result = decryptor.isEncrypted(testFile);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsEncrypted_WithEncryptedValues_ShouldReturnTrue() throws IOException {
        // Given - file with ENC[...] values like in the screenshot
        String content = "ARGOCD_GITLAB_PASSWORD: ENC[AES256-GCM, data:OhjLbWwPcqLuqWXFWxrwx]\n" +
                "ARGOCD_GITLAB_USER: ENC[AES256-GCM, data:/c68AvCkR5tgUBLAN]\n" +
                "public-gateway:\n" +
                "  login: ENC[AES256-GCM, data:test123]\n" +
                "  password: ENC[AES256-GCM, data:test456]\n";
        Path testFile = createTestFile("test-encrypted-values.yaml", content);

        // When
        boolean result = decryptor.isEncrypted(testFile);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsEncrypted_WithBothSopsMetadataAndEncryptedValues_ShouldReturnTrue() throws IOException {
        // Given
        String content = "sops:\n" +
                "  encrypted_regex: \"^(data|password)$\"\n" +
                "ARGOCD_PASSWORD: ENC[AES256-GCM, data:test123]\n" +
                "someOtherKey: plainValue\n";
        Path testFile = createTestFile("test-both.yaml", content);

        // When
        boolean result = decryptor.isEncrypted(testFile);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsEncrypted_WithPlainTextFile_ShouldReturnFalse() throws IOException {
        // Given
        String content = "ARGOCD_GITLAB_PASSWORD: plain-password\n" +
                "ARGOCD_GITLAB_USER: plain-user\n" +
                "public-gateway:\n" +
                "  login: user@example.com\n" +
                "  password: plainPassword123\n";
        Path testFile = createTestFile("test-plain.yaml", content);

        // When
        boolean result = decryptor.isEncrypted(testFile);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsEncrypted_WithFileContainingWordSopsButNotMetadata_ShouldReturnFalse() throws IOException {
        // Given - file contains word "sops" but not as metadata key
        String content = "description: This file is about sops encryption tool\n" +
                "someKey: someValue\n";
        Path testFile = createTestFile("test-sops-word.yaml", content);

        // When
        boolean result = decryptor.isEncrypted(testFile);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsEncrypted_WithFileContainingWordEncButNotEncryptedValue_ShouldReturnFalse() throws IOException {
        // Given - file contains word "enc" but not in ENC[...] format
        String content = "description: This file contains encrypted data\n" +
                "someKey: someValue\n";
        Path testFile = createTestFile("test-enc-word.yaml", content);

        // When
        boolean result = decryptor.isEncrypted(testFile);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsEncrypted_WithNullPath_ShouldReturnFalse() {
        // When
        boolean result = decryptor.isEncrypted((Path) null);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsEncrypted_WithNonExistentFile_ShouldReturnFalse() {
        // Given
        Path nonExistentFile = tempDir.resolve("non-existent.yaml");

        // When
        boolean result = decryptor.isEncrypted(nonExistentFile);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsEncrypted_WithEmptyFile_ShouldReturnFalse() throws IOException {
        // Given
        Path emptyFile = createTestFile("empty.yaml", "");

        // When
        boolean result = decryptor.isEncrypted(emptyFile);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsEncrypted_WithVariousEncryptedFormats_ShouldReturnTrue() throws IOException {
        // Given - test different ENC formats
        String content = "key1: ENC[AES256-GCM, data:test1]\n" +
                "key2: ENC[age, data:test2]\n" +
                "key3: ENC[PGP, data:test3]\n" +
                "key4: ENC[KMS, data:test4]\n";
        Path testFile = createTestFile("test-various-enc.yaml", content);

        // When
        boolean result = decryptor.isEncrypted(testFile);

        // Then
        assertTrue(result);
    }

    @Test
    void testDecrypt_WithNullPath_ShouldThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> decryptor.decrypt((Path) null));
    }

    @Test
    void testDecrypt_WithNonExistentFile_ShouldThrowException() {
        // Given
        Path nonExistentFile = tempDir.resolve("non-existent.yaml");

        // When & Then
        assertThrows(TdmEnvDecryptionException.class, () -> decryptor.decrypt(nonExistentFile));
    }

    @Test
    void testDecrypt_WithStringPath_ShouldWork() {
        // Given
        Path testFile = tempDir.resolve("test.yaml");
        try {
            Files.write(testFile, "test content".getBytes());
        } catch (IOException e) {
            // Ignore for this test
        }

        // When & Then - should not throw exception for method call
        // Note: actual decryption will fail without real SOPS, but method signature is tested
        assertThrows(TdmEnvDecryptionException.class, () -> decryptor.decrypt(testFile.toString()));
    }

    @Test
    void testDecrypt_WithDirectory_ShouldThrowException() {
        // Given
        Path directory = tempDir.resolve("directory");
        try {
            Files.createDirectory(directory);
        } catch (IOException e) {
            // Ignore
        }

        // When & Then
        assertThrows(TdmEnvDecryptionException.class, () -> decryptor.decrypt(directory));
    }

    // Helper method to create test files
    private Path createTestFile(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.write(file, content.getBytes());
        return file;
    }
}

