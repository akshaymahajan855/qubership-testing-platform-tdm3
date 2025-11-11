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

package org.qubership.atp.tdm.env.configurator.service.impl;

import static java.lang.String.format;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.qubership.atp.auth.springbootstarter.exceptions.AtpException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullProjectByIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertFullSystemBySysIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByEnvIdtException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyEnvironmentsException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazyProjectsException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemBySysIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemsByEnvIdByNameException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemsByEnvIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvConvertLazySystemsByProjectIdException;
import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvResetCachesException;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.env.configurator.service.GitService;
import org.qubership.atp.tdm.env.configurator.utils.CacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentsServiceImpl implements EnvironmentsService {

    private final GitService gitService;
    private final CacheManager cacheManager;

    /**
     * Project:
     * Get full project by ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_FULL_PROJECT_CACHE)
    public Project getFullProject(@Nonnull UUID projectId) {
        log.info("Loading project by id: [{}]", projectId);
        Project project;
        try {
            project = gitService.getFullProject(projectId);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertFullProjectByIdException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertFullProjectByIdException(projectId.toString());
        }
        log.info("Project successfully loaded.");
        return project;
    }

    /**
     * Get lazy project by ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_PROJECT_CACHE)
    public LazyProject getLazyProjectById(@Nonnull UUID projectId) {
        log.info("Loading lazy project by Id.");
        LazyProject lazyProject = gitService.getLazyProjectById(projectId);
        log.info("Lazy project by Id successfully loaded.");
        return lazyProject;
    }

    /**
     * Get lazy project by name.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_PROJECT_BY_NAME_CACHE)
    public LazyProject getLazyProjectByName(@Nonnull String projectName) {
        log.info("Loading lazy project by name: {}.", projectName);
        LazyProject lazyProject = gitService.getLazyProjectByName(projectName);
        log.info("Lazy project by name successfully loaded.");
        return lazyProject;
    }

    /**
     * Get lazy projects.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_PROJECTS_CACHE)
    public List<LazyProject> getLazyProjects() {
        log.info("Loading lazy projects.");
        List<LazyProject> lazyProjects;
        try {
            lazyProjects = gitService.getLazyProjects();
        } catch (Exception e) {
            log.error(TdmEnvConvertLazyProjectsException.DEFAULT_MESSAGE, e);
            throw new TdmEnvConvertLazyProjectsException();
        }
        log.info("Lazy projects successfully loaded.");
        return lazyProjects;
    }

    /**
     * Environment:
     * Get lazy environment by ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_ENVIRONMENT_BY_ID_CACHE)
    public LazyEnvironment getLazyEnvironment(@Nonnull UUID environmentId) {
        log.info("Loading lazy environment by environment id: [{}]", environmentId);
        LazyEnvironment environment;
        try {
            environment = gitService.getLazyEnvironment(environmentId);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazyEnvironmentByEnvIdtException.DEFAULT_MESSAGE,
                    environmentId), e);
            throw new TdmEnvConvertLazyEnvironmentByEnvIdtException(environmentId.toString());
        }
        log.info("Lazy environment successfully loaded.");
        return environment;
    }

    /**
     * Get env name by environment ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_ENV_NAME_BY_ENVIRONMENT_ID_CACHE)
    public String getEnvNameById(@Nonnull UUID environmentId) {
        log.info("Loading environment name by environment id: [{}]", environmentId);
        return gitService.getEnvNameById(environmentId);
    }

    /**
     * Get lazy environments by project ID - with systems.
     */
    @Override
//    @Cacheable(value = CacheNames.TDM_LAZY_ENVIRONMENTS_CACHE)
    public List<LazyEnvironment> getLazyEnvironments(@Nonnull UUID projectId) {
        log.info("Loading lazy environments by project id: [{}]", projectId);
        List<LazyEnvironment> lazyEnvironments;
        try {
            lazyEnvironments = gitService.getLazyEnvironmentsFromCache(projectId);
            if (lazyEnvironments.isEmpty()) {
                // If cache is empty, fallback to Git
                log.info("No cached environments found, loading from Git for project: {}", projectId);
                lazyEnvironments = gitService.getLazyEnvironments(projectId);
            } else {
                log.info("Using cached environments for project: {}", projectId);
            }
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazyEnvironmentsException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazyEnvironmentsException(projectId.toString());
        }
        log.info("Lazy environments successfully loaded.");
        return lazyEnvironments;
    }

    /**
     * Get lazy environments by project ID - from cache only.
     */
    @Override
    public List<LazyEnvironment> getLazyEnvironmentsFromCache(@Nonnull UUID projectId) {
        log.info("Getting lazy environments from cache by project id: [{}]", projectId);
        List<LazyEnvironment> lazyEnvironments = gitService.getLazyEnvironmentsFromCache(projectId);
        log.info("Retrieved {} cached environments for project: {}", lazyEnvironments.size(), projectId);
        return lazyEnvironments;
    }

    /**
     * Get lazy environments by project ID - refresh without cache.
     */
    @Override
    public List<LazyEnvironment> getLazyEnvironmentsRefresh(@Nonnull UUID projectId) {
        log.info("Refreshing lazy environments by project id: [{}]", projectId);
        List<LazyEnvironment> lazyEnvironments = gitService.getLazyEnvironmentsRefresh(projectId);
        log.info("Lazy environments refresh completed. Found {} environments", lazyEnvironments.size());
        return lazyEnvironments;
    }

    /**
     * Get lazy environment by project and environment name.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_ENVIRONMENT_BY_NAME_CACHE)
    public LazyEnvironment getLazyEnvironmentByName(@Nonnull UUID projectId, @Nonnull String environmentName) {
        LazyEnvironment lazyEnvironment;
        try {
            lazyEnvironment = gitService.getLazyEnvironmentByName(projectId, environmentName);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazyEnvironmentByNameException.DEFAULT_MESSAGE,
                    environmentName, projectId), e);
            throw new TdmEnvConvertLazyEnvironmentByNameException(environmentName, projectId.toString());
        }
        return lazyEnvironment;
    }

    /**
     * Get connections by system ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_CONNECTIONS_BY_SYSTEM_ID_CACHE)
    public List<Connection> getConnectionsSystemById(UUID environmentId, UUID systemId) {
        log.info("Loading connections by system ID: {}", systemId);
        List<Connection> connections;
        try {
            connections = gitService.getConnectionsSystemById(environmentId, systemId);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertFullSystemBySysIdException.DEFAULT_MESSAGE, systemId), e);
            throw new TdmEnvConvertFullSystemBySysIdException(systemId.toString());
        }
        log.info("Full systems by system ID successfully loaded.");
        return connections;
    }

    /**
     * Get lazy system by ID.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_SYSTEM_CACHE)
    public LazySystem getLazySystemById(@Nonnull UUID environmentId, @Nonnull UUID systemId) {
        log.info("Loading lazy system by system ID: {}", systemId);
        LazySystem lazySystem;
        try {
            lazySystem = gitService.getLazySystemById(environmentId, systemId);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemBySysIdException.DEFAULT_MESSAGE, systemId), e);
            throw new TdmEnvConvertLazySystemBySysIdException(systemId.toString());
        }
        log.info("Lazy systems by system ID successfully loaded.");
        return lazySystem;
    }

    /**
     * Get lazy system by project ID, environment ID, name.
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_SYSTEM_BY_NAME_CACHE)
    public LazySystem getLazySystemByName(@Nonnull UUID projectId, @Nonnull UUID environmentId,
                                          @Nonnull String systemName) {
        log.info("Loading lazy systems for project id: [{}] by environment id: [{}] and systemName: [{}]", projectId,
                environmentId, systemName);
        LazySystem lazySystem;
        try {
            lazySystem = gitService.getLazySystemByName(projectId, environmentId, systemName);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertFullSystemByNameException.DEFAULT_MESSAGE, systemName), e);
            throw new TdmEnvConvertFullSystemByNameException(systemName);
        }
        log.info("Full systems by name successfully loaded.");
        return lazySystem;
    }

    /**
     * Get lazy systems by env Id.
     * @param environmentId ATP projectId
     * @return list of LazySystem's
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_SYSTEMS_CACHE)
    public List<LazySystem> getLazySystems(@Nonnull UUID environmentId) {
        log.info("Loading lazy systems by env ID: [{}].", environmentId);
        List<LazySystem> systems;
        try {
            systems = gitService.getLazySystems(environmentId);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByEnvIdByNameException.DEFAULT_MESSAGE, environmentId), e);
            throw new TdmEnvConvertLazySystemsByEnvIdException(environmentId);
        }
        log.info("Lazy systems by envId and name successfully loaded.");
        return systems;
    }

    @Override
    @Cacheable(value = CacheNames.TDM_ALL_SHORT_LAZY_SYSTEMS_BY_PROJECT_CACHE)
    public List<LazySystem> getLazySystemsByProjectWithEnvIds(@Nonnull UUID projectId) {
        log.info("Loading lazy systems by project ID: [{}]", projectId);
        List<LazySystem> lazySystems;
        try {
            lazySystems = gitService.getLazySystemsByProjectWithEnvIds(projectId);
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByProjectIdException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazySystemsByProjectIdException(projectId.toString());
        }
        log.info("Lazy systems by project ID successfully loaded.");
        return lazySystems;
    }

    /**
     * Get all systems from feign client by project id.
     * @param projectId ATP projectId
     * @return list of LazySystem's
     */
    @Override
    @Cacheable(value = CacheNames.TDM_LAZY_SYSTEMS_BY_PROJECT_CACHE)
    public List<LazySystem> getLazySystemsByProjectIdWithConnections(@Nonnull UUID projectId) {
        log.info("Loading lazy systems by project ID: [{}]", projectId);
        List<LazySystem> systems;
        try {
            systems = gitService.getLazySystemsByProjectIdWithConnections(projectId);
        } catch (AtpException ae) {
            throw ae;
        } catch (Exception e) {
            log.error(format(TdmEnvConvertLazySystemsByProjectIdException.DEFAULT_MESSAGE, projectId), e);
            throw new TdmEnvConvertLazySystemsByProjectIdException(projectId.toString());
        }
        log.info("Lazy systems by project ID successfully loaded");
        return systems;
    }

    @Override
    public boolean resetCaches() {
        log.info("Reset caches.");
        try {
            Field[] fields = CacheNames.class.getDeclaredFields();
            for (Field field : fields) {
                Cache cache = cacheManager.getCache(field.get(String.class).toString());
                if (Objects.nonNull(cache)) {
                    cache.clear();
                }
            }
        } catch (Exception e) {
            log.error(TdmEnvResetCachesException.DEFAULT_MESSAGE, e);
            throw new TdmEnvResetCachesException();
        }
        log.info("Environment caches have been cleared.");
        return true;
    }
}
