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

package org.qubership.atp.tdm.env.configurator.controllers;

import java.util.List;
import java.util.UUID;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "/api/tdm/")
@RestController()
public class EnvironmentsController /* implements EnvironmentsControllerApi */ {

    private EnvironmentsService service;

    @Autowired
    public EnvironmentsController(EnvironmentsService service) {
        this.service = service;
    }

    @AuditAction(auditAction = "Get lazy projects")
    @GetMapping("/projects/lazy")
    public List<LazyProject> getProjects() {
        return service.getLazyProjects();
    }

    @AuditAction(auditAction = "Get lazy environment by projectId {{#projectId}}")
    @GetMapping("/projects/{projectId}/environments/lazy")
    @PreAuthorize("@entityAccess.checkAccess(#projectId, 'READ')")
    public List<LazyEnvironment> getLazyEnvironments(@PathVariable("projectId") UUID projectId) {
        return service.getLazyEnvironments(projectId);
    }

    @AuditAction(auditAction = "Refresh lazy environments by projectId {{#projectId}}")
    @GetMapping("/projects/{projectId}/environments/lazy/refresh")
    @PreAuthorize("@entityAccess.checkAccess(#projectId, 'READ')")
    public List<LazyEnvironment> getLazyEnvironmentsRefresh(@PathVariable("projectId") UUID projectId) {
        return service.getLazyEnvironmentsRefresh(projectId);
    }

    @AuditAction(auditAction = "Get lazy system by environmentId {{#environmentId}}")
    @GetMapping("/environments/{environmentId}/systems/lazy")
    public List<LazySystem> getLazySystems(@PathVariable("environmentId") UUID environmentId) {
        return service.getLazySystems(environmentId);
    }

    @AuditAction(auditAction = "Reset caches")
    @GetMapping("/envs/reset/caches")
    public boolean resetCaches() {
        return service.resetCaches();
    }
}
