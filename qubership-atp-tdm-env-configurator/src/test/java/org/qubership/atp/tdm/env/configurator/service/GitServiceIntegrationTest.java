package org.qubership.atp.tdm.env.configurator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GitServiceIntegrationTest {

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private GitService gitService;

    private ObjectMapper yamlMapper;

    @BeforeEach
    void setUp() {
        // Initialize YAML mapper
        yamlMapper = new YAMLMapper();
        yamlMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        yamlMapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);

        // Set up test properties
        ReflectionTestUtils.setField(gitService, "gitUrl", "https://git.test.com");
        ReflectionTestUtils.setField(gitService, "gitToken", "test-token");
        ReflectionTestUtils.setField(gitService, "ref", "master");
        ReflectionTestUtils.setField(gitService, "pathToGitProject", "test-project");
        ReflectionTestUtils.setField(gitService, "pathToFileTopologyParameters", "topology");
        ReflectionTestUtils.setField(gitService, "pathToFileParameters", "parameters.yaml");
        ReflectionTestUtils.setField(gitService, "pathToFileCredentials", "credentials.yaml");
        ReflectionTestUtils.setField(gitService, "pathToDeploymentParameters", "deployment-parameters.yaml");
        ReflectionTestUtils.setField(gitService, "pathToDeploymentCredentials", "deployment-credentials.yaml");
        ReflectionTestUtils.setField(gitService, "projects", new HashMap<>());
    }

    @Test
    void testParseRealDeploymentParametersFile_ShouldParseAllSystemsAndConnections() throws Exception {
        // Given
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/test-deployment-parameters.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> deploymentParams = yamlMapper.readValue(content, Map.class);

        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(deploymentParams);

        // Then
        assertNotNull(systems);
        assertEquals(3, systems.size());

        // Verify test-system-1
        YamlSystem system1 = findSystemByName(systems, "test-system-1");
        assertNotNull(system1);
        assertEquals(2, system1.getConnections().size());
        
        // Verify HTTP connection
        YamlConnection httpConn = findConnectionByName(system1.getConnections(), "HTTP");
        assertNotNull(httpConn);
        assertEquals(ConnectionType.HTTP, httpConn.getType());
        assertEquals("https://test-system-1.example.com", httpConn.getParameters().get("url"));
        assertEquals("testuser1", httpConn.getParameters().get("login"));
        assertEquals("testpass1", httpConn.getParameters().get("password"));
        assertEquals("30000", httpConn.getParameters().get("timeout"));

        // Verify DATABASE connection
        YamlConnection dbConn = findConnectionByName(system1.getConnections(), "DB");
        assertNotNull(dbConn);
        assertEquals(ConnectionType.fromValue("DB"), dbConn.getType());
        assertEquals("db1.example.com", dbConn.getParameters().get("host"));
        assertEquals("5432", dbConn.getParameters().get("port"));
        assertEquals("testdb1", dbConn.getParameters().get("database"));
        assertEquals("dbuser1", dbConn.getParameters().get("username"));
        assertEquals("dbpass1", dbConn.getParameters().get("password"));

        // Verify test-system-2
        YamlSystem system2 = findSystemByName(systems, "test-system-2");
        assertNotNull(system2);
        assertEquals(2, system2.getConnections().size());
        
        // Verify MQ connection
        YamlConnection mqConn = findConnectionByName(system2.getConnections(), "JMS Asynchronous");
        assertNotNull(mqConn);
        assertEquals(ConnectionType.fromValue("JMS Asynchronous"), mqConn.getType());
        assertEquals("tcp://mq.example.com:61616", mqConn.getParameters().get("broker_url"));
        assertEquals("test.queue", mqConn.getParameters().get("queue_name"));
        assertEquals("mquser", mqConn.getParameters().get("username"));
        assertEquals("mqpass", mqConn.getParameters().get("password"));

        // Verify test-system-3
        YamlSystem system3 = findSystemByName(systems, "test-system-3");
        assertNotNull(system3);
        assertEquals(2, system3.getConnections().size());
        
        // Verify REST connection
        YamlConnection restConn = findConnectionByName(system3.getConnections(), "REST Synchronous");
        assertNotNull(restConn);
        assertEquals(ConnectionType.fromValue("REST Synchronous"), restConn.getType());
        assertEquals("https://api.example.com/v1", restConn.getParameters().get("base_url"));
        assertEquals("test-api-key-123", restConn.getParameters().get("api_key"));
        assertEquals("45000", restConn.getParameters().get("timeout"));

        // Verify FTP connection
        YamlConnection ftpConn = findConnectionByName(system3.getConnections(), "File over FTP");
        assertNotNull(ftpConn);
        assertEquals(ConnectionType.fromValue("File over FTP"), ftpConn.getType());
        assertEquals("ftp.example.com", ftpConn.getParameters().get("host"));
        assertEquals("21", ftpConn.getParameters().get("port"));
        assertEquals("ftpuser", ftpConn.getParameters().get("username"));
        assertEquals("ftppass", ftpConn.getParameters().get("password"));
        assertEquals("true", ftpConn.getParameters().get("passive_mode"));
    }

    @Test
    void testParseRealCredentialsFile_ShouldExtractCredentials() throws Exception {
        // Given
        String content = new String(Files.readAllBytes(
                Paths.get("src/test/resources/test-credentials.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = yamlMapper.readValue(content, Map.class);

        // When & Then
        assertNotNull(credentials);
        assertTrue(credentials.containsKey("TEST_SYSTEM_1_HTTP_USERNAME"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_1_HTTP_PASSWORD"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_1_DATABASE_USERNAME"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_1_DATABASE_PASSWORD"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_2_HTTP_USERNAME"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_2_HTTP_PASSWORD"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_2_MQ_USERNAME"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_2_MQ_PASSWORD"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_3_REST_API_KEY"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_3_FTP_USERNAME"));
        assertTrue(credentials.containsKey("TEST_SYSTEM_3_FTP_PASSWORD"));
        assertTrue(credentials.containsKey("GIT_TOKEN"));
        assertTrue(credentials.containsKey("STORAGE_USERNAME"));
        assertTrue(credentials.containsKey("STORAGE_PASSWORD"));

        // Verify values
        assertEquals("testuser1", credentials.get("TEST_SYSTEM_1_HTTP_USERNAME"));
        assertEquals("testpass1", credentials.get("TEST_SYSTEM_1_HTTP_PASSWORD"));
        assertEquals("dbuser1", credentials.get("TEST_SYSTEM_1_DATABASE_USERNAME"));
        assertEquals("dbpass1", credentials.get("TEST_SYSTEM_1_DATABASE_PASSWORD"));
        assertEquals("testuser2", credentials.get("TEST_SYSTEM_2_HTTP_USERNAME"));
        assertEquals("testpass2", credentials.get("TEST_SYSTEM_2_HTTP_PASSWORD"));
        assertEquals("mquser", credentials.get("TEST_SYSTEM_2_MQ_USERNAME"));
        assertEquals("mqpass", credentials.get("TEST_SYSTEM_2_MQ_PASSWORD"));
        assertEquals("test-api-key-123", credentials.get("TEST_SYSTEM_3_REST_API_KEY"));
        assertEquals("ftpuser", credentials.get("TEST_SYSTEM_3_FTP_USERNAME"));
        assertEquals("ftppass", credentials.get("TEST_SYSTEM_3_FTP_PASSWORD"));
        assertEquals("test-git-token-12345", credentials.get("GIT_TOKEN"));
        assertEquals("storageuser", credentials.get("STORAGE_USERNAME"));
        assertEquals("storagepass", credentials.get("STORAGE_PASSWORD"));
    }

    @Test
    void testParseComplexYamlStructure_ShouldHandleNestedObjects() throws Exception {
        // Given - Create a complex YAML structure similar to real deployment parameters
        String complexYaml = "APPLICATION_NAME: complex-test-app\n" +
                "ATP_ENVGENE_CONFIGURATION: &id001\n" +
                "  systems:\n" +
                "  - complex-system:\n" +
                "      connections:\n" +
                "      - HTTP:\n" +
                "          url: https://complex.example.com/api\n" +
                "          login: complexuser\n" +
                "          password: complexpass\n" +
                "          timeout: 60000\n" +
                "          retry_count: 3\n" +
                "          headers:\n" +
                "            Content-Type: application/json\n" +
                "            Authorization: Bearer token123\n" +
                "      - DB:\n" +
                "          host: complex-db.example.com\n" +
                "          port: 5432\n" +
                "          database: complex_db\n" +
                "          username: dbuser\n" +
                "          password: dbpass\n" +
                "          ssl_mode: require\n" +
                "          connection_pool:\n" +
                "            min_size: 5\n" +
                "            max_size: 20\n" +
                "            timeout: 30000\n" +
                "  - messaging-system:\n" +
                "      connections:\n" +
                "      - \"JMS Asynchronous\":\n" +
                "          broker_url: tcp://mq.example.com:61616\n" +
                "          queue_name: complex.queue\n" +
                "          username: mquser\n" +
                "          password: mqpass\n" +
                "          durable: true\n" +
                "          persistent: true\n" +
                "          delivery_mode: 2\n" +
                "global: *id001\n" +
                "complex-test-app: *id001\n";

        @SuppressWarnings("unchecked")
        Map<String, Object> complexParams = yamlMapper.readValue(complexYaml, Map.class);

        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(complexParams);

        // Then
        assertNotNull(systems);
        assertEquals(2, systems.size());

        // Verify complex-system
        YamlSystem complexSystem = findSystemByName(systems, "complex-system");
        assertNotNull(complexSystem);
        assertEquals(2, complexSystem.getConnections().size());

        // Verify HTTP connection with complex parameters
        YamlConnection httpConn = findConnectionByName(complexSystem.getConnections(), "HTTP");
        assertNotNull(httpConn);
        assertEquals(ConnectionType.HTTP, httpConn.getType());
        assertEquals("https://complex.example.com/api", httpConn.getParameters().get("url"));
        assertEquals("complexuser", httpConn.getParameters().get("login"));
        assertEquals("complexpass", httpConn.getParameters().get("password"));
        assertEquals("60000", httpConn.getParameters().get("timeout"));
        assertEquals("3", httpConn.getParameters().get("retry_count"));

        // Verify DATABASE connection with complex parameters
        YamlConnection dbConn = findConnectionByName(complexSystem.getConnections(), "DB");
        assertNotNull(dbConn);
        assertEquals(ConnectionType.fromValue("DB"), dbConn.getType());
        assertEquals("complex-db.example.com", dbConn.getParameters().get("host"));
        assertEquals("5432", dbConn.getParameters().get("port"));
        assertEquals("complex_db", dbConn.getParameters().get("database"));
        assertEquals("dbuser", dbConn.getParameters().get("username"));
        assertEquals("dbpass", dbConn.getParameters().get("password"));
        assertEquals("require", dbConn.getParameters().get("ssl_mode"));

        // Verify messaging-system
        YamlSystem messagingSystem = findSystemByName(systems, "messaging-system");
        assertNotNull(messagingSystem);
        assertEquals(1, messagingSystem.getConnections().size());

        // Verify MQ connection with complex parameters
        YamlConnection mqConn = findConnectionByName(messagingSystem.getConnections(), "JMS Asynchronous");
        assertNotNull(mqConn);
        assertEquals(ConnectionType.fromValue("JMS Asynchronous"), mqConn.getType());
        assertEquals("tcp://mq.example.com:61616", mqConn.getParameters().get("broker_url"));
        assertEquals("complex.queue", mqConn.getParameters().get("queue_name"));
        assertEquals("mquser", mqConn.getParameters().get("username"));
        assertEquals("mqpass", mqConn.getParameters().get("password"));
        assertEquals("true", mqConn.getParameters().get("durable"));
        assertEquals("true", mqConn.getParameters().get("persistent"));
        assertEquals("2", mqConn.getParameters().get("delivery_mode"));
    }

    @Test
    void testParseEmptyYamlFile_ShouldReturnEmptyList() throws Exception {
        // Given
        String emptyYaml = "APPLICATION_NAME: empty-app\n";
        @SuppressWarnings("unchecked")
        Map<String, Object> emptyParams = yamlMapper.readValue(emptyYaml, Map.class);

        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(emptyParams);

        // Then
        assertNotNull(systems);
        assertTrue(systems.isEmpty());
    }

    @Test
    void testParseYamlWithOnlyGlobalSection_ShouldReturnEmptyList() throws Exception {
        // Given
        String globalOnlyYaml = "APPLICATION_NAME: global-only-app\n" +
                "global:\n" +
                "  SOME_PARAM: some_value\n";
        @SuppressWarnings("unchecked")
        Map<String, Object> globalOnlyParams = yamlMapper.readValue(globalOnlyYaml, Map.class);

        // When
        List<YamlSystem> systems = invokeParseSystemsFromDeploymentParams(globalOnlyParams);

        // Then
        assertNotNull(systems);
        assertTrue(systems.isEmpty());
    }

    // Helper methods
    @SuppressWarnings("unchecked")
    private List<YamlSystem> invokeParseSystemsFromDeploymentParams(Map<String, Object> deploymentParams) {
        try {
            return (List<YamlSystem>) ReflectionTestUtils.invokeMethod(gitService, 
                    "parseSystemsFromDeploymentParams", deploymentParams);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private YamlSystem findSystemByName(List<YamlSystem> systems, String name) {
        return systems.stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }

    private YamlConnection findConnectionByName(List<YamlConnection> connections, String name) {
        return connections.stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElse(null);
    }
}
