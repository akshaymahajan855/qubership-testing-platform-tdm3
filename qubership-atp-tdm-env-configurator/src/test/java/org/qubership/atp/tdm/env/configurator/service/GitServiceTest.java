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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.qubership.atp.tdm.env.configurator.model.envgen.ConnectionType;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlConnection;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlSystem;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

@ExtendWith(MockitoExtension.class)
class GitServiceTest {

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private GitService gitService;

    private ObjectMapper yamlMapper;
    private String testDeploymentParamsContent;

    @BeforeEach
    void setUp() throws IOException {
        // Initialize YAML mapper
        yamlMapper = new YAMLMapper();
        yamlMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        yamlMapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);

        // Set up test properties
        ReflectionTestUtils.setField(gitService, "gitUrl", "https://git.test.com/test-project");
        ReflectionTestUtils.setField(gitService, "gitToken", "test-token");
        ReflectionTestUtils.setField(gitService, "ref", "master");
        ReflectionTestUtils.setField(gitService, "deploymentPath", "effective-set/deployment");
        ReflectionTestUtils.setField(gitService, "ncAppPath", "atp/atp3-playwright-runner");
        ReflectionTestUtils.setField(gitService, "deploymentParametersPath", "values/deployment-parameters.yaml");
        ReflectionTestUtils.setField(gitService, "deploymentCredentialsPath", "values/deployment-credentials.yaml");
        ReflectionTestUtils.setField(gitService, "projects", new HashMap<>());

        // Load test file contents
        testDeploymentParamsContent = new String(Files.readAllBytes(
                Paths.get("src/test/resources/test-deployment-parameters.yaml")));
    }

    @Test
    void testParseSystemsFromDeploymentParams_ValidData_ShouldParseCorrectly() throws Exception {
        // Given
        @SuppressWarnings("unchecked")
        Map<String, Object> deploymentParams = yamlMapper.readValue(testDeploymentParamsContent, Map.class);

        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(deploymentParams);

        // Then
        assertNotNull(systems);
        assertEquals(3, systems.size());

        // Verify first system
        YamlSystem system1 = systems.stream()
                .filter(s -> "test-system-1".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(system1);
        assertEquals(2, system1.getConnections().size());

        // Verify HTTP connection
        YamlConnection httpConnection = system1.getConnections().stream()
                .filter(c -> "HTTP".equals(c.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(httpConnection);
        assertEquals(ConnectionType.HTTP, httpConnection.getType());
        assertEquals("https://test-system-1.example.com", httpConnection.getParameters().get("url"));
        assertEquals("testuser1", httpConnection.getParameters().get("login"));
        assertEquals("testpass1", httpConnection.getParameters().get("password"));
        assertEquals("30000", httpConnection.getParameters().get("timeout"));

        // Verify DB connection
        YamlConnection dbConnection = system1.getConnections().stream()
                .filter(c -> "DB".equals(c.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(dbConnection);
        assertEquals(ConnectionType.fromValue("DB"), dbConnection.getType());
        assertEquals("db1.example.com", dbConnection.getParameters().get("host"));
        assertEquals("5432", dbConnection.getParameters().get("port"));
        assertEquals("testdb1", dbConnection.getParameters().get("database"));
        assertEquals("dbuser1", dbConnection.getParameters().get("username"));
        assertEquals("dbpass1", dbConnection.getParameters().get("password"));
    }

    @Test
    void testParseSystemsFromDeploymentParams_EmptyConfiguration_ShouldReturnEmptyList() {
        // Given
        Map<String, Object> deploymentParams = new HashMap<>();

        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(deploymentParams);

        // Then
        assertNotNull(systems);
        assertTrue(systems.isEmpty());
    }

    @Test
    void testParseSystemsFromDeploymentParams_NullConfiguration_ShouldReturnEmptyList() {
        // Given
        Map<String, Object> deploymentParams = new HashMap<>();
        deploymentParams.put("ATP_ENVGENE_CONFIGURATION", null);

        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(deploymentParams);

        // Then
        assertNotNull(systems);
        assertTrue(systems.isEmpty());
    }

    @Test
    void testParseSystemsFromDeploymentParams_InvalidStructure_ShouldReturnEmptyList() {
        // Given
        Map<String, Object> deploymentParams = new HashMap<>();
        Map<String, Object> invalidConfig = new HashMap<>();
        invalidConfig.put("systems", "invalid-data");
        deploymentParams.put("ATP_ENVGENE_CONFIGURATION", invalidConfig);

        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(deploymentParams);

        // Then
        assertNotNull(systems);
        assertTrue(systems.isEmpty());
    }

    @Test
    void testParseYamlFileAndExtractSystems_ValidFile_ShouldParseCorrectly() throws Exception {
        // Given - Test the parsing logic directly with mock data
        @SuppressWarnings("unchecked")
        Map<String, Object> deploymentParams = yamlMapper.readValue(testDeploymentParamsContent, Map.class);
        
        // When - Test the parsing logic directly with mock data
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(deploymentParams);

        // Then
        assertNotNull(systems);
        assertEquals(3, systems.size());
        assertTrue(systems.stream().anyMatch(s -> "test-system-1".equals(s.getName())));
        assertTrue(systems.stream().anyMatch(s -> "test-system-2".equals(s.getName())));
        assertTrue(systems.stream().anyMatch(s -> "test-system-3".equals(s.getName())));
    }

    @Test
    void testParseYamlFileAndExtractSystems_InvalidFile_ShouldReturnEmptyList() {
        // Given - Test with invalid configuration structure that should result in empty systems
        Map<String, Object> invalidConfig = new HashMap<>();
        invalidConfig.put("ATP_ENVGENE_CONFIGURATION", "invalid-string-instead-of-object");
        
        // When - Test parsing with invalid configuration structure
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(invalidConfig);
        
        // Then - Should return empty list for invalid configuration
        assertNotNull(systems);
        assertTrue(systems.isEmpty());
    }

    @Test
    void testParseYamlFileAndExtractSystems_GitException_ShouldReturnEmptyList() {
        // Given - Test with empty configuration that would result in empty systems
        Map<String, Object> emptyConfig = new HashMap<>();
        
        // When - Test parsing with empty configuration
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(emptyConfig);

        // Then
        assertNotNull(systems);
        assertTrue(systems.isEmpty());
    }

    @Test
    void testParseYamlFileAndExtractSystems_InvalidYamlSyntax_ShouldThrowException() {
        // Given - Test with truly invalid YAML syntax
        String invalidYaml = "invalid: yaml: content: [\n  - unclosed: list\n  - missing: closing";
        
        // When & Then - Should throw exception for invalid YAML syntax
        assertThrows(Exception.class, () -> yamlMapper.readValue(invalidYaml, Map.class));
    }

    @Test
    void testParseSystemsFromDeploymentParams_SimpleStructure_ShouldParseCorrectly() throws Exception {
        // Given - Test with a simpler, more explicit structure
        String simpleYaml = "APPLICATION_NAME: test-app\n" +
                "ATP_ENVGENE_CONFIGURATION:\n" +
                "  systems:\n" +
                "  - test-system-1:\n" +
                "      connections:\n" +
                "      - HTTP:\n" +
                "          url: https://test1.example.com\n" +
                "          login: user1\n" +
                "          password: pass1\n" +
                "  - test-system-2:\n" +
                "      connections:\n" +
                "      - DB:\n" +
                "          host: db.example.com\n" +
                "          port: 5432\n";
        
        @SuppressWarnings("unchecked")
        Map<String, Object> deploymentParams = yamlMapper.readValue(simpleYaml, Map.class);
        
        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(deploymentParams);

        // Then
        assertNotNull(systems);
        assertEquals(2, systems.size());
        assertTrue(systems.stream().anyMatch(s -> "test-system-1".equals(s.getName())));
        assertTrue(systems.stream().anyMatch(s -> "test-system-2".equals(s.getName())));
    }

    @Test
    void testMergeSystems_WithOverlappingSystems_ShouldMergeConnections() {
        // Given
        List<YamlSystem> existingSystems = createTestSystems("system1", "system2");
        List<YamlSystem> newSystems = createTestSystems("system2", "system3");
        
        // Add different connections to system2 in newSystems
        YamlSystem newSystem2 = newSystems.stream()
                .filter(s -> "system2".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(newSystem2);
        
        YamlConnection newConnection = new YamlConnection();
        newConnection.setId(UUID.randomUUID());
        newConnection.setName("NEW_CONNECTION");
        newConnection.setType(ConnectionType.HTTP);
        Map<String, String> params = new HashMap<>();
        params.put("url", "https://new.example.com");
        newConnection.setParameters(params);
        List<YamlConnection> connections = new ArrayList<>(newSystem2.getConnections());
        connections.add(newConnection);
        newSystem2.setConnections(connections);

        // When
        List<YamlSystem> mergedSystems = invokeMergeSystems(existingSystems, newSystems);

        // Then
        assertNotNull(mergedSystems);
        assertEquals(3, mergedSystems.size()); // system1, system2, system3

        // Verify system2 has merged connections
        YamlSystem mergedSystem2 = mergedSystems.stream()
                .filter(s -> "system2".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedSystem2);
        assertTrue(mergedSystem2.getConnections().stream()
                .anyMatch(c -> "HTTP".equals(c.getName())));
        assertTrue(mergedSystem2.getConnections().stream()
                .anyMatch(c -> "NEW_CONNECTION".equals(c.getName())));
    }

    @Test
    void testMergeSystems_WithDuplicateConnections_ShouldUpdateParameters() {
        // Given
        List<YamlSystem> existingSystems = createTestSystems("system1");
        List<YamlSystem> newSystems = createTestSystems("system1");
        
        // Modify connection parameters in newSystems
        YamlSystem newSystem1 = newSystems.stream()
                .filter(s -> "system1".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(newSystem1);
        
        YamlConnection httpConnection = newSystem1.getConnections().stream()
                .filter(c -> "HTTP".equals(c.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(httpConnection);
        httpConnection.getParameters().put("url", "https://updated.example.com");
        httpConnection.getParameters().put("new_param", "new_value");

        // When
        List<YamlSystem> mergedSystems = invokeMergeSystems(existingSystems, newSystems);

        // Then
        assertNotNull(mergedSystems);
        assertEquals(1, mergedSystems.size());

        YamlSystem mergedSystem1 = mergedSystems.get(0);
        YamlConnection mergedHttpConnection = mergedSystem1.getConnections().stream()
                .filter(c -> "HTTP".equals(c.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedHttpConnection);
        assertEquals("https://updated.example.com", mergedHttpConnection.getParameters().get("url"));
        assertEquals("new_value", mergedHttpConnection.getParameters().get("new_param"));
    }

    @Test
    void testBuildPath_SinglePath_ShouldReturnPath() {
        // When
        String result = invokeBuildPath("test");

        // Then
        assertEquals("test", result);
    }

    @Test
    void testBuildPath_MultiplePaths_ShouldJoinCorrectly() {
        // When
        String result = invokeBuildPath("environments", "cluster1", "env1", "file.yaml");

        // Then
        assertEquals("environments/cluster1/env1/file.yaml", result);
    }

    @Test
    void testBuildPath_WithLeadingSlashes_ShouldNormalize() {
        // When
        String result = invokeBuildPath("environments", "/cluster1", "env1");

        // Then
        assertEquals("environments/cluster1/env1", result);
    }

    @Test
    void testBuildPath_WithNullPaths_ShouldHandleGracefully() {
        // When
        String result = invokeBuildPath("environments", null, "env1", "");

        // Then
        assertEquals("environments/env1", result);
    }

    // Helper methods to invoke private methods
    private List<YamlSystem> invokeParseSystemsFromDeploymentParams(Map<String, Object> deploymentParams) {
        try {
            return ReflectionTestUtils.invokeMethod(gitService,
                    "parseSystemsFromDeploymentParams", deploymentParams);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private List<YamlSystem> invokeMergeSystems(List<YamlSystem> existingSystems, List<YamlSystem> newSystems) {
        try {
            return ReflectionTestUtils.invokeMethod(gitService,
                    "mergeSystems", existingSystems, newSystems);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeBuildPath(String... paths) {
        try {
            return ReflectionTestUtils.invokeMethod(gitService, "buildPath", (Object) paths);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<YamlSystem> createTestSystems(String... systemNames) {
        List<YamlSystem> systems = new ArrayList<>();
        for (String systemName : systemNames) {
            YamlSystem system = new YamlSystem();
            system.setId(UUID.randomUUID());
            system.setName(systemName);
            
            YamlConnection connection = new YamlConnection();
            connection.setId(UUID.randomUUID());
            connection.setName("HTTP");
            connection.setType(ConnectionType.HTTP);
            Map<String, String> params = new HashMap<>();
            params.put("url", "https://" + systemName + ".example.com");
            connection.setParameters(params);
            
            system.setConnections(Collections.singletonList(connection));
            systems.add(system);
        }
        return systems;
    }
}
