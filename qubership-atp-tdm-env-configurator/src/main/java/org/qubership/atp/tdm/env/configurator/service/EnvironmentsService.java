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

import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Project;

import jakarta.annotation.Nonnull;

public interface EnvironmentsService {

    Project getFullProject(@Nonnull UUID projectId);

    LazyProject getLazyProjectById(@Nonnull UUID projectId);

    LazyProject getLazyProjectByName(@Nonnull String projectName);

    List<LazyProject> getLazyProjects();

    List<LazyEnvironment> getLazyEnvironments(@Nonnull UUID projectId);

    List<LazyEnvironment> getLazyEnvironmentsFromCache(@Nonnull UUID projectId);

    List<LazyEnvironment> getLazyEnvironmentsRefresh(@Nonnull UUID projectId);

    LazyEnvironment getLazyEnvironment(@Nonnull UUID environmentId);

    String getEnvNameById(@Nonnull UUID environmentId);

    LazyEnvironment getLazyEnvironmentByName(@Nonnull UUID projectId, @Nonnull String environmentName);

    List<Connection> getConnectionsSystemById(UUID environmentId, UUID systemId);

    LazySystem getLazySystemByName(@Nonnull UUID projectId, @Nonnull UUID environmentId, @Nonnull String systemName);

    LazySystem getLazySystemById(@Nonnull UUID environmentId, @Nonnull UUID systemId);

    List<LazySystem> getLazySystemsByProjectWithEnvIds(@Nonnull UUID projectId);

    List<LazySystem> getLazySystems(@Nonnull UUID environmentId);

    List<LazySystem> getLazySystemsByProjectIdWithConnections(UUID projectId);

    boolean resetCaches();
}
