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
import org.qubership.atp.tdm.model.refresh.RefreshResults;
import org.qubership.atp.tdm.model.refresh.TestDataRefreshConfig;
import org.qubership.atp.tdm.service.DataRefreshService;
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

@RequestMapping("/api/tdm/data/refresh")
@RestController()
public class DataRefreshController /* implements DataRefreshControllerApi */ {

    private final DataRefreshService dataRefreshService;

    @Autowired
    public DataRefreshController(@Nonnull DataRefreshService dataRefreshService) {
        this.dataRefreshService = dataRefreshService;
    }

    /**
     * Get refresh configuration for specified dataset / table ID.
     *
     * @param id - refresh config id
     * @return refresh configuration object.
     */
    @Operation(description = "Get refresh configuration for specified dataset / table ID.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByRefreshConfigId(#id).getProjectId(), 'READ')")
    @AuditAction(auditAction = "Get refresh configuration by id {{#id}}")
    @GetMapping(path = {"/config/{id}"})
    public ResponseEntity<TestDataRefreshConfig> getRefreshConfig(@PathVariable("id") UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(dataRefreshService.getRefreshConfig(id));
    }

    /**
     * Save / update data refresh settings.
     *
     * @param tableName Name of table
     * @param queryTimeout Query timeout value in seconds
     * @param refreshConfig TestDataRefreshConfig object
     * @return TestDataRefreshConfig object after saving
     * @throws Exception in case errors while config saving.
     */
    @Operation(description = "Save / update data refresh settings.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Save / update data refresh settings. Table {{#tableName}}")
    @PostMapping(value = "/config")
    public TestDataRefreshConfig saveRefreshConfig(@RequestParam("tableName") String tableName,
                                                   @RequestParam Integer queryTimeout,
                                                   @RequestBody TestDataRefreshConfig refreshConfig) throws Exception {
        return dataRefreshService.saveRefreshConfig(tableName, queryTimeout, refreshConfig);
    }

    /**
     * Force run data refresh.
     *
     * @param tableName Name of table
     * @param queryTimeout Query timeout value in seconds
     * @param allEnv Flag if the action should be applied to all environments (true) or not
     * @return List of RefreshResults after refresh running
     * @throws Exception in case errors while refresh running.
     */
    @Operation(description = "Force run data refresh.")
    @PreAuthorize("@entityAccess.checkAccess("
            + "T(org.qubership.atp.tdm.utils.UsersManagementEntities).TEST_DATA.getName(),"
            + "@catalogRepository.findByTableName(#tableName).getProjectId(), 'CREATE')")
    @AuditAction(auditAction = "Force run data refresh. Table {{#tableName}}")
    @PostMapping(value = "/run")
    public List<RefreshResults> runDataRefresh(@RequestParam("tableName") String tableName,
                                               @RequestParam Integer queryTimeout,
                                               @RequestParam boolean allEnv) throws Exception {
        return dataRefreshService.runRefresh(tableName, queryTimeout, allEnv, false);
    }

    /**
     * Get next run's date / time details.
     *
     * @param cronExpression cron expression to calculate next run based on
     * @return ResponseMessage that contains the details
     * @throws ParseException Thrown in case if invalid cron expression was provided.
     */
    @Operation(description = "Get next run's date / time details.")
    @AuditAction(auditAction = "Get next run's date. cron {{#cronExpression}}")
    @GetMapping(value = "/next/run")
    public ResponseEntity<String> getNextScheduledRun(@RequestParam("cronExpression") String cronExpression)
            throws ParseException {
        return ResponseEntity.ok(new Gson().toJson(dataRefreshService.getNextScheduledRun(cronExpression)));
    }
}
