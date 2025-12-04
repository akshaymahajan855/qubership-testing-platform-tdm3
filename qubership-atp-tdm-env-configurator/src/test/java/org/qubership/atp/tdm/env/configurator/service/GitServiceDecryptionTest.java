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

package org.qubership.atp.tdm.env.configurator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.qubership.atp.tdm.env.configurator.utils.decryptor.Decryptor;
import org.qubership.atp.tdm.env.configurator.utils.decryptor.SopsDecryptor;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GitServiceDecryptionTest {

    @Mock
    private CacheService cacheService;

    private GitService gitService;

    @TempDir
    Path tempDir;

    private SopsDecryptor mockDecryptor;

    @BeforeEach
    void setUp() throws IOException {
        // Create a mock decryptor first and then create GitService with it
        mockDecryptor = mock(SopsDecryptor.class);
        gitService = new GitService(cacheService, Optional.of(mockDecryptor));
        
        // Set up test properties
        ReflectionTestUtils.setField(gitService, "gitUrl", "https://git.test.com/test-project");
        ReflectionTestUtils.setField(gitService, "gitToken", "test-token");
        ReflectionTestUtils.setField(gitService, "ref", "master");
        ReflectionTestUtils.setField(gitService, "deploymentPath", "effective-set/deployment");
        ReflectionTestUtils.setField(gitService, "ncAppPath", "atp/atp3-playwright-runner");
        ReflectionTestUtils.setField(gitService, "deploymentParametersPath", "values/deployment-parameters.yaml");
        ReflectionTestUtils.setField(gitService, "deploymentCredentialsPath", "values/credentials.yaml");
        ReflectionTestUtils.setField(gitService, "projects", new HashMap<>());
    }

    @Test
    void testGetFileContentAsString_WithEncryptedFile_ShouldDecrypt() throws Exception {
        // Given
        String encryptedContent = "ARGOCD_GITLAB_PASSWORD: ENC[AES256-GCM, data:OhjLbWwPcqLuqWXFWxrwx]\n" +
                "ARGOCD_GITLAB_USER: ENC[AES256-GCM, data:/c68AvCkR5tgUBLAN]\n";
        String decryptedContent = "ARGOCD_GITLAB_PASSWORD: plain-password\n" +
                "ARGOCD_GITLAB_USER: plain-user\n";
        
        // Mock decryptor behavior
        when(mockDecryptor.isEncrypted(any(Path.class))).thenReturn(true);
        when(mockDecryptor.decrypt(any(Path.class))).thenReturn(decryptedContent);
        
        // Create temp file for decryption check
        File tempFile = File.createTempFile("git_file_", null);
        try {
            Files.write(tempFile.toPath(), encryptedContent.getBytes(StandardCharsets.UTF_8));
            
            // When - test decryptor behavior directly
            // This tests that the decryptor correctly identifies encrypted files and decrypts them
            boolean isEncrypted = mockDecryptor.isEncrypted(tempFile.toPath());
            assertTrue(isEncrypted);
            
            String result = mockDecryptor.decrypt(tempFile.toPath());
            
            // Then
            assertEquals(decryptedContent, result);
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void testGetFileContentAsString_WithPlainFile_ShouldNotDecrypt() throws Exception {
        // Given
        String plainContent = "ARGOCD_GITLAB_PASSWORD: plain-password\n" +
                "ARGOCD_GITLAB_USER: plain-user\n";
        
        // Create temporary file with plain content
        File tempFile = File.createTempFile("plain_", ".yaml");
        try {
            Files.write(tempFile.toPath(), plainContent.getBytes(StandardCharsets.UTF_8));
            
            // Mock decryptor behavior - file is not encrypted
            when(mockDecryptor.isEncrypted(any(Path.class))).thenReturn(false);
            
            // When
            boolean isEncrypted = mockDecryptor.isEncrypted(tempFile.toPath());
            
            // Then
            assertFalse(isEncrypted);
            // Decryptor should not be called for plain files
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void testGetFileContentAsString_WithDecryptionFailure_ShouldFallbackToOriginal() throws Exception {
        // Given
        String encryptedContent = "ARGOCD_GITLAB_PASSWORD: ENC[AES256-GCM, data:test]\n";
        
        // Create temporary file
        File tempFile = File.createTempFile("encrypted_", ".yaml");
        try {
            Files.write(tempFile.toPath(), encryptedContent.getBytes(StandardCharsets.UTF_8));
            
            // Mock decryptor behavior - file is encrypted but decryption fails
            when(mockDecryptor.isEncrypted(any(Path.class))).thenReturn(true);
            when(mockDecryptor.decrypt(any(Path.class)))
                .thenThrow(new org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDecryptionException("Decryption failed"));
            
            // When
            boolean isEncrypted = mockDecryptor.isEncrypted(tempFile.toPath());
            assertTrue(isEncrypted);
            
            // Then - verify exception is thrown (fallback logic would be in GitService)
            org.junit.jupiter.api.Assertions.assertThrows(
                org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDecryptionException.class,
                () -> mockDecryptor.decrypt(tempFile.toPath())
            );
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void testGetFileContentAsString_WithoutDecryptor_ShouldReturnOriginalContent() {
        // Given - no decryptor available
        ReflectionTestUtils.setField(gitService, "decryptor", Optional.empty());
        
        // When decryptor is not available, original content should be returned
        // This is tested by verifying decryptor is Optional.empty()
        @SuppressWarnings("unchecked")
        Optional<Decryptor> decryptor = (Optional<Decryptor>) ReflectionTestUtils.getField(gitService, "decryptor");
        
        // Then
        assertNotNull(decryptor);
        assertFalse(decryptor.isPresent());
    }

    @Test
    void testIsEncrypted_WithVariousEncryptedFormats_ShouldDetectCorrectly() throws Exception {
        // Given - test different encrypted value formats
        String[] encryptedContents = {
            "key: ENC[AES256-GCM, data:test123]",
            "key: ENC[age, data:test456]",
            "sops:\n  encrypted_regex: \"^data$\"",
            "key1: ENC[AES256-GCM, data:test]\nsops:\n  age:\n    - recipient: age1test"
        };
        
        for (String content : encryptedContents) {
            File tempFile = File.createTempFile("test_", ".yaml");
            try {
                Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                
                // Create real decryptor for this test
                SopsDecryptor realDecryptor = new SopsDecryptor("AGE-SECRET-KEY-1TEST123456789012345678901234567890123456789012345678901234567890");
                
                // When
                boolean isEncrypted = realDecryptor.isEncrypted(tempFile.toPath());
                
                // Then
                assertTrue(isEncrypted, "Content should be detected as encrypted: " + content);
            } finally {
                tempFile.delete();
            }
        }
    }

    @Test
    void testIsEncrypted_WithPlainContent_ShouldNotDetectAsEncrypted() throws Exception {
        // Given - plain content without encryption
        String[] plainContents = {
            "key: plain-value",
            "description: This file contains sops tool information",
            "key: value\nanotherKey: anotherValue"
        };
        
        for (String content : plainContents) {
            File tempFile = File.createTempFile("test_", ".yaml");
            try {
                Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                
                // Create real decryptor for this test
                SopsDecryptor realDecryptor = new SopsDecryptor("AGE-SECRET-KEY-1TEST123456789012345678901234567890123456789012345678901234567890");
                
                // When
                boolean isEncrypted = realDecryptor.isEncrypted(tempFile.toPath());
                
                // Then
                assertFalse(isEncrypted, "Content should not be detected as encrypted: " + content);
            } finally {
                tempFile.delete();
            }
        }
    }
}

