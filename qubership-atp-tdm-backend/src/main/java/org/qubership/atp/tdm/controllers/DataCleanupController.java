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

package org.qubership.atp.tdm.controllers;


import java.text.ParseException;
import java.util.List;
import java.util.UUID;

import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.model.cleanup.CleanupSettings;
import org.qubership.atp.tdm.service.CleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Nonnull;

@RequestMapping("/api/tdm/cleanup")
@RestController()
public class DataCleanupController /* implements DataCleanupControllerApi */ {

    private final CleanupService cleanupService;

    @Autowired
    public DataCleanupController(@Nonnull CleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /**
     * Get cleanup configuration for specified dataset / table ID.
     *
     * @param id - cleanup config id
     * @return cleanup configuration object
     */
    @Operation(description = "Get cleanup configuration for specified dataset / table ID.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findAllByCleanupConfigId(#id).get(0).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Get cleanup configuration by id {{#id}}")
    @GetMapping(path = {"/config/{id}"})
    public ResponseEntity<CleanupSettings> getCleanupConfig(@PathVariable("id") UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(cleanupService.getCleanupSettings(id));
    }

    /**
     * Save / update data cleanup settings.
     */
    @Operation(description = "Save / update data cleanup settings.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#cleanupConfig.tableName).getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Save / update data cleanup settings. "
            + "Table {{#cleanupConfig.tableName}}")
    @PostMapping(value = "/config")
    public CleanupSettings saveCleanupConfig(@RequestBody CleanupSettings cleanupConfig) throws Exception {
        return cleanupService.saveCleanupConfig(cleanupConfig);
    }

    /**
     * Force run data cleanup.
     */
    @Operation(description = "Force run data cleanup.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#cleanupConfig.tableName).getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Force run data cleanup. Table {{#cleanupConfig.tableName}}")
    @PostMapping(value = "/run")
    public List<CleanupResults> runDataCleanup(@RequestBody CleanupSettings cleanupConfig) throws Exception {
        return cleanupService.runCleanup(cleanupConfig);
    }

    /**
     * Get next run's date / time details.
     * @param cronExpression cron expression to calculate next run based on
     * @return ResponseMessage that contains the details
     * @throws ParseException Thrown in case if invalid cron expression was provided
     */
    @Operation(description = "Get next run's date / time details.")
    @AuditAction(auditAction = "Get next run's date. cron {{#cronExpression}}")
    @GetMapping(value = "/next/run")
    public ResponseEntity<String> getNextScheduledRun(@RequestParam("cronExpression") String cronExpression)
            throws ParseException {
        return ResponseEntity.ok(new Gson().toJson(cleanupService.getNextScheduledRun(cronExpression)));
    }

    @Operation(description = "Old update.")
    @AuditAction(auditAction = "Old update.")
    @GetMapping(path = "/fill/cleanup/type")
    public void fillCleanupTypeColumn() {
        cleanupService.fillCleanupTypeColumn();
    }
}
