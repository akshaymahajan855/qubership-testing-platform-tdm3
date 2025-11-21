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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.TreeItem;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvDbConnectionException;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.Environment;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.model.envgen.Configuration;
import org.qubership.atp.tdm.env.configurator.model.envgen.ConnectionType;
import org.qubership.atp.tdm.env.configurator.model.envgen.EnvGenProperty;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlConfiguration;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlConnection;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlEnvironment;
import org.qubership.atp.tdm.env.configurator.model.envgen.YamlSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GitService {

    @Value("${git.url}")
    private String gitUrl;

    @Value("${git.token}")
    private String gitToken;

    @Value("${git.environments.ref}")
    private String ref;

    @Value("${git.environments.deployment.path}")
    private String deploymentPath;

    @Value("${git.environments.deployment.nc.app.path}")
    private String ncAppPath;

    @Value("${git.environments.deployment.parameters.path}")
    private String deploymentParametersPath;

    @Value("${git.environments.deployment.credentials.path}")
    private String deploymentCredentialsPath;

    @Value("#{${projects.info}}")
    private Map<UUID, String> projects;

    private CacheService cacheService;
    private ObjectMapper enfConfObjectMapper;
    private static final List<String> EXCLUSIONS = Arrays.asList("credentials", "parameters");


    {
        enfConfObjectMapper = new YAMLMapper();
        enfConfObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        enfConfObjectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    @Autowired
    public GitService(CacheService cacheService) {
        this.cacheService = cacheService;
        log.info("GitService constructor called");
    }

    @PostConstruct
    public void initializeCache() {
        log.info("PostConstruct: Initializing environments cache. Projects map size: {}", 
            projects != null ? projects.size() : "null");
        if (projects != null && !projects.isEmpty()) {
            initializeEnvironmentsCache();
        } else {
            log.warn("Projects map is null or empty, skipping cache initialization");
        }
    }

    /**
     * Extracts base URL from the full git repository URL.
     * The URL format is expected to be: https://git.example.com/path/to/project
     * Returns: https://git.example.com (base URL for Git API)
     */
    private String getBaseUrl() {
        if (gitUrl == null || gitUrl.isEmpty()) {
            throw new IllegalStateException("git.url is not configured");
        }

        try {
            URI uri = new URI(gitUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            StringBuilder baseUrlBuilder = new StringBuilder();
            if (scheme != null) {
                baseUrlBuilder.append(scheme).append("://");
            }
            if (host != null) {
                baseUrlBuilder.append(host);
            }
            if (port != -1) {
                baseUrlBuilder.append(":").append(port);
            }

            return baseUrlBuilder.toString();
        } catch (URISyntaxException e) {
            log.error("Failed to parse git URL: '{}'. Error: {}", gitUrl, e.getMessage());
            throw new IllegalStateException("Invalid git URL format: " + gitUrl, e);
        }
    }

    /**
     * Extracts project path from the full git repository URL.
     * The URL format is expected to be: https://git.example.com/path/to/project
     * Returns: path/to/project (project path in repository)
     */
    private String getProjectPath() {
        if (gitUrl == null || gitUrl.isEmpty()) {
            throw new IllegalStateException("git.url is not configured");
        }

        try {
            URI uri = new URI(gitUrl);
            String path = uri.getPath();
            // Extract project path (remove leading slash if present)
            return (path != null && !path.isEmpty()) ? path.replaceFirst("^/", "") : "";
        } catch (URISyntaxException e) {
            log.error("Failed to parse git URL: '{}'. Error: {}", gitUrl, e.getMessage());
            throw new IllegalStateException("Invalid git URL format: " + gitUrl, e);
        }
    }

    private void initializeEnvironmentsCache() {
        log.info("Initializing environments cache for all projects...");
        try {
            for (Map.Entry<UUID, String> entry : projects.entrySet()) {
                UUID projectId = entry.getKey();
                String projectName = entry.getValue();
                log.info("Pre-loading environments for project: {} ({})", projectName, projectId);
                
                try {
                    List<LazyEnvironment> environments = getLazyEnvironmentsByFileTree(projectId);
                    log.info("Pre-loaded {} environments for project: {}", environments.size(), projectName);
                    
                    // Verify cache was populated
                    List<YamlEnvironment> cachedEnvs = cacheService.getEnvironments();
                    long envsForThisProject = cachedEnvs.stream()
                        .filter(env -> projectId.equals(env.getProjectId()))
                        .count();
                    log.info("Cache now contains {} environments for project {} (total cached: {})", 
                        envsForThisProject, projectName, cachedEnvs.size());
                } catch (Exception e) {
                    log.warn("Failed to pre-load environments for project {}: {}", projectName, e.getMessage());
                }
            }
            log.info("Environments cache initialization completed. Total cached environments: {}", 
                cacheService.getEnvironments().size());
        } catch (Exception e) {
            log.error("Failed to initialize environments cache: {}", e.getMessage());
        }
    }

    public LazyProject getLazyProjectByName(String projectName) {
        LazyProject lazyProject = null;
        for (Map.Entry<UUID, String> entry : projects.entrySet()) {
            if (projectName.equals(entry.getValue())) {
                lazyProject = new LazyProject(entry.getKey(), entry.getValue());
                break;
            }
        }
        return lazyProject;
    }

    public Project getFullProject(UUID projectId) throws Exception {
        Project project = new Project();
        project.setId(projectId);
        project.setName(projects.get(projectId));

        List<Environment> environments = getLazyEnvironments(projectId).stream()
                .map(lazyEnvironment -> {
                    try {
                        return Environment.of(lazyEnvironment, getFullSystems(lazyEnvironment));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
        project.setEnvironments(environments);

        return project;
    }

    public LazyProject getLazyProjectById(UUID projectId) {
        return new LazyProject(projectId, projects.get(projectId));
    }

    public List<LazyProject> getLazyProjects() {
        return projects.entrySet()
                .stream()
                .map(entry -> new LazyProject(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public String getEnvNameById(UUID environmentId) {
        return cacheService.get(environmentId).getName();
    }

    public List<LazyEnvironment> getLazyEnvironments(UUID projectId) throws Exception {
        return getLazyEnvironmentsByFileTree(projectId);
    }

    public List<LazyEnvironment> getLazyEnvironmentsFromCache(UUID projectId) {
        try {
            log.info("Getting environments from cache for project: {}", projectId);
            List<LazyEnvironment> cachedEnvironments = new ArrayList<>();
            
            List<YamlEnvironment> allCachedEnvironments = cacheService.getEnvironments();
            log.info("Total cached environments: {}", allCachedEnvironments.size());
            
            for (YamlEnvironment yamlEnv : allCachedEnvironments) {
                log.debug("Checking cached environment: name={}, projectId={}, targetProjectId={}", 
                    yamlEnv.getName(), yamlEnv.getProjectId(), projectId);
                if (yamlEnv.getProjectId() != null && yamlEnv.getProjectId().equals(projectId)) {
                    log.debug("Found matching environment: {}", yamlEnv.getName());
                    LazyEnvironment lazyEnv = LazyEnvironment.builder()
                            .id(yamlEnv.getId())
                            .name(yamlEnv.getName())
                            .clusterName(yamlEnv.getClusterName())
                            .projectId(projectId)
                            .systems(yamlEnv.getYamlSystems() != null ? 
                                yamlEnv.getYamlSystems().stream()
                                    .map(system -> UUID.nameUUIDFromBytes(String.format("%s/%s", yamlEnv.getName(), system.getName()).getBytes()).toString())
                                    .collect(Collectors.toList()) : new ArrayList<>())
                            .build();
                    cachedEnvironments.add(lazyEnv);
                }
            }
            
            log.info("Found {} cached environments for project: {}", cachedEnvironments.size(), projectId);
            return cachedEnvironments;
        } catch (Exception e) {
            log.error("Failed to get environments from cache for project {}: {}", projectId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<LazyEnvironment> getLazyEnvironmentsRefresh(UUID projectId) {
        try {
            log.info("Refreshing environments for project: {}", projectId);
            List<LazyEnvironment> environments = getLazyEnvironmentsByFileTree(projectId);
            log.info("Successfully refreshed {} environments for project: {}", environments.size(), projectId);
            return environments;
        } catch (Exception e) {
            log.error("Failed to refresh environments for project {}: {}", projectId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<LazySystem> getLazySystems(UUID environmentId) {
        try {
            YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
            if (yamlEnvironment == null) {
                log.warn("Environment not found in cache for ID: {}", environmentId);
                return new ArrayList<>();
            }
            
            String environmentName = yamlEnvironment.getName();
            String clusterName = yamlEnvironment.getClusterName();
            
            String pureEnvironmentName = environmentName.contains(".")
                ? environmentName.substring(environmentName.lastIndexOf('.') + 1)
                : environmentName;

            List<YamlSystem> yamlSystems = new ArrayList<>(yamlEnvironment.getYamlSystems());
            
            try {
                String deploymentCredentialsPath = gitEndpointToGetDeploymentCredentialsFile(clusterName, pureEnvironmentName);
                List<YamlSystem> credentialsSystems = parseYamlFileAndExtractSystems(
                        deploymentCredentialsPath, clusterName, pureEnvironmentName);
                yamlSystems = mergeSystems(yamlSystems, credentialsSystems);
                
                yamlEnvironment.setYamlSystems(yamlSystems);
                cacheService.put(yamlEnvironment);
                
            } catch (Exception e) {
                log.error("Failed to parse deployment credentials for environment {}/{}: {}", 
                        clusterName, environmentName, e.getMessage());
            }

            return yamlSystems.stream()
                    .map(this::convertYamlSystemToLazySystem)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get lazy systems for environment ID {}: {}", environmentId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public LazyEnvironment getLazyEnvironment(UUID environmentId) {
        try {
            YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
            if (yamlEnvironment == null) {
                log.warn("Environment not found in cache for ID: {}", environmentId);
                return null;
            }
            
            return LazyEnvironment.builder()
                    .id(yamlEnvironment.getId())
                    .name(yamlEnvironment.getName())
                    .clusterName(yamlEnvironment.getClusterName())
                    .projectId(yamlEnvironment.getProjectId())
                    .build();
        } catch (Exception e) {
            log.error("Failed to get lazy environment for ID {}: {}", environmentId, e.getMessage());
            return null;
        }
    }

    public LazyEnvironment getLazyEnvironmentByName(UUID projectId, String environmentName) {
        YamlEnvironment yamlEnvironment = cacheService.get(UUID.nameUUIDFromBytes(environmentName.getBytes()));
        return LazyEnvironment.builder()
                .id(yamlEnvironment.getId())
                .name(yamlEnvironment.getName())
                .clusterName(yamlEnvironment.getClusterName())
                .projectId(yamlEnvironment.getProjectId()).build();
    }

    public List<Connection> getConnectionsSystemById(UUID environmentId, UUID systemId) throws Exception {
        YamlSystem yamlSystem = null;
        if (environmentId != null) {
            YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
            yamlSystem = yamlEnvironment.getSystemById(systemId);
        }
        List<YamlEnvironment> yamlEnvironments = cacheService.getEnvironments();
        Optional<YamlEnvironment> systemEnv = yamlEnvironments.stream().filter(yamlEnvironment ->
                yamlEnvironment.getSystemById(systemId) != null).findAny();
        if (systemEnv.isPresent()) {
            yamlSystem = systemEnv.get().getSystemById(systemId);
        }
        if (yamlSystem != null) {
            YamlSystem finalYamlSystem = yamlSystem;
            List<Connection> connections = yamlSystem.getConnections().stream().map(yamlConnection -> {
                Connection connection = new Connection();
                connection.setId(yamlConnection.getId());
                connection.setName(yamlConnection.getName());
                connection.setSystemId(finalYamlSystem.getId());
                connection.setConnectionType(yamlConnection.getType().toString());
                connection.setParameters(yamlConnection.getParameters());
                return connection;
            }).collect(Collectors.toList());

            return connections;
        }
        return new ArrayList<>();

    }

    public LazySystem getLazySystemById(UUID environmentId, UUID systemId) {
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        YamlSystem yamlSystem = yamlEnvironment.getSystemById(systemId);
        LazySystem lazySystem = LazySystem.builder()
                .id(yamlSystem.getId())
                .name(yamlSystem.getName())
                .connections(yamlSystem.getListConnections())
                .build();
        return lazySystem;
    }

    public List<LazySystem> getLazySystemsByProjectIdWithConnections(UUID projectId) {
        List<LazySystem> systems = new ArrayList<>();

        for (YamlEnvironment yamlEnvironment : cacheService.getEnvironments()) {
            List<LazySystem> lazySystems = yamlEnvironment.getYamlSystems().stream().map(yamlSystem -> {
                return LazySystem.builder()
                        .id(yamlSystem.getId())
                        .name(yamlSystem.getName())
                        .connections(yamlSystem.getListConnections())
                        .build();
            }).collect(Collectors.toList());

            systems.addAll(lazySystems);
        }
        return systems;
    }

    public List<LazySystem> getLazySystemsByProjectWithEnvIds(UUID projectId) {
        Map<UUID, LazySystem.LazySystemBuilder> systemBuilders = new HashMap<>();

        for (YamlEnvironment yamlEnvironment : cacheService.getEnvironments()) {
            for (YamlSystem yamlSystem : yamlEnvironment.getYamlSystems()) {
                LazySystem.LazySystemBuilder builder = systemBuilders.computeIfAbsent(
                    yamlSystem.getId(),
                    id -> LazySystem.builder()
                        .id(yamlSystem.getId())
                        .name(yamlSystem.getName())
                        .connections(yamlSystem.getListConnections())
                        .environmentIds(new ArrayList<>())
                );
                
                List<UUID> envIds = new ArrayList<>(builder.build().getEnvironmentIds());
                envIds.add(yamlEnvironment.getId());
                builder.environmentIds(envIds);
            }
        }

        return systemBuilders.values().stream()
            .map(LazySystem.LazySystemBuilder::build)
            .collect(Collectors.toList());
    }

    private Map<String, Object> checkEnvironmentConfiguration(Map<String, Object> mapConfiguration, String endpoint) {
        if (!mapConfiguration.containsKey(EnvGenProperty.ENVIRONMENTS.toString())) {
            String error = String.format("Invalid configuration by path '%s'. "
                            + "Configuration doesn't contains mandatory attribute [%s].",
                    endpoint, EnvGenProperty.ENVIRONMENTS
            );
            throw new IllegalArgumentException(error);
        }
        return (Map<String, Object>) mapConfiguration.get(EnvGenProperty.ENVIRONMENTS.toString());
    }

    public LazySystem getLazySystemByName(UUID projectId, UUID environmentId, String systemName) {
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        YamlSystem yamlSystem = yamlEnvironment.getSystemByName(systemName);
        return LazySystem.builder()
                .id(yamlSystem.getId())
                .name(yamlSystem.getName())
                .connections(yamlSystem.getListConnections())
                .build();
    }

    public System getFullSystemByName(UUID environmentId, String systemName) {
        YamlEnvironment yamlEnvironment = cacheService.get(environmentId);
        YamlSystem yamlSystem = yamlEnvironment.getSystemByName(systemName);

        List<Connection> connections = yamlSystem.getConnections().stream().map(yamlConnection -> {
            Connection connection = new Connection();
            connection.setId(yamlConnection.getId());
            connection.setName(yamlConnection.getName());
            connection.setSystemId(yamlSystem.getId());
            connection.setConnectionType(yamlConnection.getType().toString());
            connection.setParameters(yamlConnection.getParameters());
            return connection;
        }).collect(Collectors.toList());

        boolean hasDbConnection = connections.stream()
                .anyMatch(connection -> "DB".equalsIgnoreCase(connection.getName()));
        if (!hasDbConnection) {
            throw new TdmEnvDbConnectionException("DB");
        }

        System system = System.builder()
                .environmentId(environmentId)
                .connections(connections).build();
        system.setId(yamlSystem.getId());
        system.setName(systemName);

        return system;
    }

    private List<System> getFullSystems(LazyEnvironment lazyEnvironment) throws Exception {
        YamlEnvironment yamlEnvironment = new YamlEnvironment(lazyEnvironment.getName());
        yamlEnvironment.setClusterName(lazyEnvironment.getClusterName());

        List<YamlSystem> yamlSystems = loadAndMergeSystemsFromFiles(
                lazyEnvironment.getClusterName(), lazyEnvironment.getName());

        yamlEnvironment.setYamlSystems(yamlSystems);

        return yamlSystems.stream()
                .map(yamlSystem -> {
                    List<Connection> connections = yamlSystem.getConnections().stream()
                            .map(yamlConnection -> convertYamlConnectionToConnection(yamlConnection, yamlSystem.getId()))
                            .collect(Collectors.toList());

                    System system = System.builder()
                            .environmentId(yamlEnvironment.getId())
                            .connections(connections)
                            .build();
                    system.setId(yamlSystem.getId());
                    system.setName(yamlSystem.getName());
                    return system;
                })
                .collect(Collectors.toList());
    }

    private YamlConfiguration getYamlConfiguration(String endpoint) throws Exception {
        RepositoryFile file = getGitFile(endpoint);
        File tempFile = performRepositoryFile(file);
        return enfConfObjectMapper.readValue(tempFile, YamlConfiguration.class);
    }

    private File performRepositoryFile(RepositoryFile file) throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(file.getContent());

        File tempFile = File.createTempFile(file.getFileName(), null);
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(decodedBytes);
        fos.close();
        return tempFile;
    }

    private RepositoryFile getGitFile(String gitEndpoint) throws Exception {
        RepositoryFile file;
        try (GitLabApi gitLabApi = new GitLabApi(getBaseUrl(), gitToken)) {
            file = gitLabApi.getRepositoryFileApi().getFile(getProjectPath(), gitEndpoint, ref);
        } catch (GitLabApiException e) {
            if ("Not Found".equals(e.getReason()) && e.getHttpStatus() == 404) {
                log.error("Git file not found by - {}.", gitEndpoint, e);
            }
            throw e;
        } catch (Exception e) {
            log.error("Error while occurred get file from git.", e);
            throw e;
        }
        return file;
    }

    private String gitEndpointToGetDeploymentParametersFile(String clusterName, String environmentName) {
        String fullPath = buildPath(deploymentPath, ncAppPath, deploymentParametersPath);
        return buildPath("environments", clusterName, environmentName, fullPath);
    }

    private String gitEndpointToGetDeploymentCredentialsFile(String clusterName, String environmentName) {
        String fullPath = buildPath(deploymentPath, ncAppPath, deploymentCredentialsPath);
        return buildPath("environments", clusterName, environmentName, fullPath);
    }

    private List<YamlSystem> parseYamlFileAndExtractSystems(String filePath, String clusterName, String environmentName) {
        try {
            String content = getFileContentAsString(filePath);
            Map<String, Object> yamlParams = enfConfObjectMapper.readValue(content, Map.class);
            return parseSystemsFromDeploymentParams(yamlParams);
        } catch (Exception e) {
            log.error("Failed to parse file {} for environment {}/{}: {}", 
                    filePath, clusterName, environmentName, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<YamlSystem> loadAndMergeSystemsFromFiles(String clusterName, String environmentName) {
        List<YamlSystem> yamlSystems = new ArrayList<>();
        
        String deploymentParamsPath = gitEndpointToGetDeploymentParametersFile(clusterName, environmentName);
        List<YamlSystem> paramsSystems = parseYamlFileAndExtractSystems(
                deploymentParamsPath, clusterName, environmentName);
        yamlSystems.addAll(paramsSystems);
        
        String deploymentCredentialsPath = gitEndpointToGetDeploymentCredentialsFile(clusterName, environmentName);
        List<YamlSystem> credentialsSystems = parseYamlFileAndExtractSystems(
                deploymentCredentialsPath, clusterName, environmentName);
        yamlSystems = mergeSystems(yamlSystems, credentialsSystems);
        
        return yamlSystems;
    }

    private LazySystem convertYamlSystemToLazySystem(YamlSystem yamlSystem) {
        return LazySystem.builder()
                .id(yamlSystem.getId())
                .name(yamlSystem.getName())
                .connections(yamlSystem.getListConnections())
                .build();
    }

    private Connection convertYamlConnectionToConnection(YamlConnection yamlConnection, UUID systemId) {
        Connection connection = new Connection();
        connection.setId(yamlConnection.getId());
        connection.setName(yamlConnection.getName());
        connection.setSystemId(systemId);
        connection.setConnectionType(yamlConnection.getType().toString());
        connection.setParameters(yamlConnection.getParameters());
        return connection;
    }

    /**
     * Get all files from specified directory in Git repository
     * @param directoryPath path to directory in repository (e.g., "environments" or "environments/cluster1")
     * @return list of files in directory
     * @throws Exception if error occurred while getting files
     */
    public List<RepositoryFile> getAllFilesFromDirectory(String directoryPath) throws Exception {
        List<RepositoryFile> files = new ArrayList<>();
        try (GitLabApi gitLabApi = new GitLabApi(getBaseUrl(), gitToken)) {
            List<TreeItem> treeItems = gitLabApi.getRepositoryApi().getTree(getProjectPath(), directoryPath, ref, true);

            for (TreeItem item : treeItems) {
                if ("blob".equals(item.getType())) { // blob = file, tree = directory
                    try {
                        RepositoryFile file = gitLabApi.getRepositoryFileApi().getFile(getProjectPath(), item.getPath(), ref);
                        files.add(file);
                    } catch (GitLabApiException e) {
                        log.warn("Failed to get file: {}", item.getPath(), e);
                    }
                }
            }
        } catch (GitLabApiException e) {
            log.error("Error getting files from directory: {}", directoryPath, e);
            throw e;
        } catch (Exception e) {
            log.error("Error working with Git API", e);
            throw e;
        }
        return files;
    }

    /**
     * Get all files with recursive directory traversal
     * @param directoryPath path to directory in repository
     * @return list of all files in directory and subdirectories
     * @throws Exception if error occurred while getting files
     */
    public List<RepositoryFile> getAllFilesRecursively(String directoryPath) throws Exception {
        List<RepositoryFile> allFiles = new ArrayList<>();
        try (GitLabApi gitLabApi = new GitLabApi(getBaseUrl(), gitToken)) {
            List<TreeItem> treeItems = gitLabApi.getRepositoryApi().getTree(getProjectPath(), directoryPath, ref, true);

            for (TreeItem item : treeItems) {
                if (TreeItem.Type.BLOB.equals(item.getType())) {
                    try {
                        RepositoryFile file = gitLabApi.getRepositoryFileApi().getFile(getProjectPath(), item.getPath(), ref);
                        allFiles.add(file);
                    } catch (GitLabApiException e) {
                        log.warn("Failed to get file: {}", item.getPath(), e);
                    }
                }
            }
        } catch (GitLabApiException e) {
            log.error("Error getting files from directory: {}", directoryPath, e);
            throw e;
        } catch (Exception e) {
            log.error("Error working with Git API", e);
            throw e;
        }
        return allFiles;
    }



    /**
     * Get list of all files in repository (names and paths only)
     * @param directoryPath path to directory (empty string for repository root)
     * @return list of file tree elements
     * @throws Exception if error occurred while getting file list
     */
    private List<TreeItem> getFileTree(String directoryPath) throws Exception {
        try (GitLabApi gitLabApi = new GitLabApi(getBaseUrl(), gitToken)) {
            return gitLabApi.getRepositoryApi().getTree(getProjectPath(), directoryPath, ref, true);
        } catch (GitLabApiException e) {
            log.error("Error getting file tree: {}", directoryPath, e);
            throw e;
        } catch (Exception e) {
            log.error("Error working with Git API", e);
            throw e;
        }
    }

    /**
     * Get file content as string
     * @param filePath path to file in repository
     * @return file content as string
     * @throws Exception if error occurred while getting file
     */
    public String getFileContentAsString(String filePath) throws Exception {
        RepositoryFile file = getGitFile(filePath);
        byte[] decodedBytes = Base64.getDecoder().decode(file.getContent());
        return new String(decodedBytes, "UTF-8");
    }

    /**
     * Get all directory names in specified directory, excluding EXCLUSIONS
     * @param directoryPath path to directory in repository
     * @return list of directory names
     * @throws Exception if error occurred while getting directories
     */
    public List<String> getDirectoryNames(String directoryPath) throws Exception {
        List<String> directoryNames = new ArrayList<>();
        try (GitLabApi gitLabApi = new GitLabApi(getBaseUrl(), gitToken)) {
            List<TreeItem> treeItems = gitLabApi.getRepositoryApi().getTree(getProjectPath(), directoryPath, ref, false);

            for (TreeItem item : treeItems) {
                if (TreeItem.Type.TREE.equals(item.getType())) {
                    String dirName = item.getName();
                    if (!EXCLUSIONS.contains(dirName)) {
                        directoryNames.add(dirName);
                    }
                }
            }
        } catch (GitLabApiException e) {
            log.error("Error getting directories from: {}", directoryPath, e);
            throw e;
        } catch (Exception e) {
            log.error("Error working with Git API", e);
            throw e;
        }
        return directoryNames;
    }

    public List<LazyEnvironment> getLazyEnvironmentsByFileTree(UUID projectId) {
        try {
            String environmentsPath = "environments/";
            List<String> envClusterNames = getDirectoryNames(environmentsPath);
            List<LazyEnvironment> lazyEnvironments = new ArrayList<>();
            
            for (String envClusterName : envClusterNames) {
                try {
                    String clusterPath = buildPath(environmentsPath, envClusterName);
                    List<String> envNames = getDirectoryNames(clusterPath);
                    
                    for (String envName : envNames) {
                        try {
                            String envPath = buildPath(clusterPath, envName);
                            List<String> envSubDirs = getDirectoryNames(envPath);
                            boolean hasEffectiveSet = envSubDirs.contains("effective-set");
                            
                            if (hasEffectiveSet) {
                                String fullDeploymentPath = buildPath(deploymentPath, ncAppPath, deploymentParametersPath);
                                String deploymentParamsPath = buildPath(envPath, fullDeploymentPath);

                                try {
                                    String paramsContent = getFileContentAsString(deploymentParamsPath);
                                    Map<String, Object> deploymentParams = enfConfObjectMapper.readValue(paramsContent, Map.class);
                                    List<YamlSystem> yamlSystems = parseSystemsFromDeploymentParams(deploymentParams);
                                    
                                    if (!yamlSystems.isEmpty()) {
                                        String fullEnvName = envClusterName + "." + envName;
                                        LazyEnvironment lazyEnvironment = LazyEnvironment.builder()
                                                .id(UUID.nameUUIDFromBytes(fullEnvName.getBytes()))
                                                .name(fullEnvName)
                                                .clusterName(envClusterName)
                                                .projectId(projectId)
                                                .systems(yamlSystems.stream()
                                                        .map(system -> UUID.nameUUIDFromBytes(String.format("%s/%s", fullEnvName, system.getName()).getBytes()).toString())
                                                        .collect(Collectors.toList()))
                                                .build();
                                        lazyEnvironments.add(lazyEnvironment);
                                        
                                        YamlEnvironment yamlEnvironment = new YamlEnvironment(fullEnvName);
                                        yamlEnvironment.setClusterName(envClusterName);
                                        yamlEnvironment.setProjectId(projectId);
                                        yamlEnvironment.setParameters(deploymentParams);
                                        yamlEnvironment.setYamlSystems(yamlSystems);
                                        cacheService.put(yamlEnvironment);
                                    }
                                    
                                } catch (Exception e) {
                                    log.warn("Failed to parse deployment parameters for environment {}/{}: {}", 
                                            envClusterName, envName, e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to process environment {}/{}: {}", 
                                    envClusterName, envName, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to process cluster {}: {}", envClusterName, e.getMessage());
                }
            }
            
            return lazyEnvironments;
        } catch (Exception e) {
            log.error("Failed to get lazy environments by file tree for project {}: {}", projectId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parse systems and connections from deployment-parameters.yaml file using ObjectMapper
     * @param deploymentParams parsed YAML content as Map
     * @return list of YamlSystem objects
     */
    @SuppressWarnings("unchecked")
    private List<YamlSystem> parseSystemsFromDeploymentParams(Map<String, Object> deploymentParams) {
        List<YamlSystem> systems = new ArrayList<>();
        
        try {
            Map<String, Object> atpConfig = (Map<String, Object>) deploymentParams.get("ATP_ENVGENE_CONFIGURATION");
            if (atpConfig != null) {
                List<Map<String, Object>> systemsList = (List<Map<String, Object>>) atpConfig.get("systems");
                
                if (systemsList != null) {
                    for (Map<String, Object> systemMap : systemsList) {
                        for (Map.Entry<String, Object> systemEntry : systemMap.entrySet()) {
                            String systemName = systemEntry.getKey();
                            Map<String, Object> systemData = (Map<String, Object>) systemEntry.getValue();
                            
                            List<Map<String, Object>> connectionsList = (List<Map<String, Object>>) systemData.get("connections");
                            List<YamlConnection> connections = new ArrayList<>();
                            
                            if (connectionsList != null) {
                                for (Map<String, Object> connectionMap : connectionsList) {
                                    for (Map.Entry<String, Object> connectionEntry : connectionMap.entrySet()) {
                                        String connectionType = connectionEntry.getKey();
                                        Map<String, Object> connectionData = (Map<String, Object>) connectionEntry.getValue();
                                        
                                        YamlConnection yamlConnection = new YamlConnection();
                                        yamlConnection.setId(UUID.randomUUID());
                                        yamlConnection.setName(connectionType);
                                        yamlConnection.setType(ConnectionType.fromValue(connectionType));
                                        
                                        Map<String, String> parameters = new HashMap<>();
                                        for (Map.Entry<String, Object> paramEntry : connectionData.entrySet()) {
                                            if (paramEntry.getValue() != null) {
                                                parameters.put(paramEntry.getKey(), paramEntry.getValue().toString());
                                            }
                                        }
                                        yamlConnection.setParameters(parameters);
                                        
                                        connections.add(yamlConnection);
                                    }
                                }
                            }
                            
                            YamlSystem yamlSystem = new YamlSystem();
                            yamlSystem.setId(UUID.randomUUID());
                            yamlSystem.setName(systemName);
                            yamlSystem.setConnections(connections);
                            
                            systems.add(yamlSystem);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing deployment parameters: {}", e.getMessage(), e);
        }
        
        return systems;
    }

    /**
     * Merge two lists of systems, combining connections for systems with the same name
     * @param existingSystems existing systems list
     * @param newSystems new systems to merge
     * @return merged systems list
     */
    private List<YamlSystem> mergeSystems(List<YamlSystem> existingSystems, List<YamlSystem> newSystems) {
        Map<String, YamlSystem> systemMap = new HashMap<>();
        
        for (YamlSystem system : existingSystems) {
            systemMap.put(system.getName(), system);
        }
        
        for (YamlSystem newSystem : newSystems) {
            String systemName = newSystem.getName();
            if (systemMap.containsKey(systemName)) {
                YamlSystem existingSystem = systemMap.get(systemName);
                List<YamlConnection> mergedConnections = new ArrayList<>(existingSystem.getConnections());
                
                for (YamlConnection newConnection : newSystem.getConnections()) {
                    boolean exists = mergedConnections.stream()
                            .anyMatch(existing -> existing.getName().equals(newConnection.getName()));
                    if (!exists) {
                        mergedConnections.add(newConnection);
                    } else {
                        mergedConnections.stream()
                                .filter(existing -> existing.getName().equals(newConnection.getName()))
                                .findFirst()
                                .ifPresent(existing -> {
                                    Map<String, String> mergedParams = new HashMap<>(existing.getParameters());
                                    mergedParams.putAll(newConnection.getParameters());
                                    existing.setParameters(mergedParams);
                                });
                    }
                }
                
                existingSystem.setConnections(mergedConnections);
            } else {
                systemMap.put(systemName, newSystem);
            }
        }
        
        return new ArrayList<>(systemMap.values());
    }

    /**
     * Safely builds a path by joining path segments, handling slashes correctly
     * @param basePath the base path
     * @param additionalPath the additional path to append
     * @return the combined path as a string
     */
    private String buildPath(String basePath, String additionalPath) {
        if (basePath == null || basePath.isEmpty()) {
            return additionalPath;
        }
        if (additionalPath == null || additionalPath.isEmpty()) {
            return basePath;
        }
        
        String normalizedAdditional = additionalPath.startsWith("/") ?
                additionalPath.substring(1) : additionalPath;

        Path base = Paths.get(basePath);
        Path additional = Paths.get(normalizedAdditional);
        Path result = base.resolve(additional);
        
        return result.toString().replace('\\', '/');
    }

    /**
     * Safely builds a path by joining multiple path segments, handling slashes correctly
     * @param paths variable number of path segments to join
     * @return the combined path as a string
     */
    private String buildPath(String... paths) {
        if (paths == null || paths.length == 0) {
            return "";
        }
        
        Path result = null;
        for (String path : paths) {
            if (path != null && !path.isEmpty()) {
                String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
                
                if (result == null) {
                    result = Paths.get(normalizedPath);
                } else {
                    result = result.resolve(normalizedPath);
                }
            }
        }
        
        if (result == null) {
            return "";
        }
        
        return result.toString().replace('\\', '/');
    }
}
