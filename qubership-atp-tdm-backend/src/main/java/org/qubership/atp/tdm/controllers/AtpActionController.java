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

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.qubership.atp.integration.configuration.configuration.AuditAction;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.RestApiRequest;
import org.qubership.atp.tdm.service.AtpActionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Nonnull;

@RequestMapping("/api/tdm/rest")
@RestController()
public class AtpActionController /* implements AtpActionControllerApi */ {

    private final AtpActionService service;

    @Autowired
    public AtpActionController(@Nonnull AtpActionService service) {
        this.service = service;
    }

    @Operation(description = "ATP Action. Insert records to table.")
    @AuditAction(auditAction = "ATP Action. Insert records to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/insert-records")
    public ResponseMessage insertTestData(@RequestBody RestApiRequest request) {
        return service.insertTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getRecords());
    }

    /**
     * Allow occupy records under ATP_USER.
     *
     * @param request - RestApiRequest
     * @return List of ResponseMessages
     */
    @Operation(description = "ATP Action. Occupy test data in table.")
    @AuditAction(auditAction = "ATP Action. Occupy test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/occupy-records")
    public List<ResponseMessage> occupyTestData(@RequestBody RestApiRequest request) {
        return service.occupyTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getOccupyRowRequests());
    }

    /**
     * Allow occupy records under ATP_USER.
     *
     * @param request - RestApiRequest
     * @return List of ResponseMessages
     */
    @Operation(description = "ATP Action. Occupy test data in table.")
    @AuditAction(auditAction = "ATP Action. Occupy test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/occupy-records-full-row")
    public List<ResponseMessage> occupyTestDataFullRow(@RequestBody RestApiRequest request) {
        return service.occupyTestDataFullRow(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getOccupyFullRowRequests());
    }

    /**
     * Allow release occupied records.
     *
     * @param request - RestApiRequest
     * @return List of ResponseMessages
     */
    @Operation(description = "ATP Action. Release test data in table.")
    @AuditAction(auditAction = "ATP Action. Release test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/release-records")
    public List<ResponseMessage> releaseTestData(@RequestBody RestApiRequest request) {
        return service.releaseTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getReleaseRowRequests());
    }

    /**
     * Allow release occupied records.
     *
     * @param request - RestApiRequest
     * @return List of ResponseMessages
     */
    @Operation(description = "ATP Action. Release test data in table.")
    @AuditAction(auditAction = "ATP Action. Release test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/release-records/bulk")
    public List<ResponseMessage> releaseFullTestData(@RequestBody RestApiRequest request) {
        return service.releaseFullTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable());
    }

    @Operation(description = "ATP Action. Update test data in table.")
    @AuditAction(auditAction = "ATP Action. Update test data to project {{#request.projectName}} "
            + "to table {{#request.titleTable}}")
    @PostMapping(value = "/update-records")
    public List<ResponseMessage> updateTestData(@RequestBody RestApiRequest request) {
        return service.updateTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getUpdateRowRequests());
    }

    @Operation(description = "ATP Action. Get test data from table.")
    @AuditAction(auditAction = "ATP Action. Get test data from project {{#request.projectName}} "
            + "table {{#request.titleTable}}")
    @PostMapping(value = "/get-record")
    public List<ResponseMessage> getTestData(@RequestBody RestApiRequest request) {
        return service.getTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getGetRowRequests());
    }

    /**
     * Allow return values from multiple columns.
     *
     * @param request Request with looking data criteria.
     * @return Object with multiple columns value.
     */
    @Operation(description = "ATP Action. Get multiple column test data from table.")
    @AuditAction(auditAction = "ATP Action. Get multiple column test data from project {{#request.projectName}} "
            + "table {{#request.titleTable}}")
    @PostMapping(value = "/get-records")
    public List<ResponseMessage> getMultipleColumnTestData(@RequestBody RestApiRequest request) {
        return service.getMultipleColumnTestData(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getGetRowRequests());
    }

    @Operation(description = "ATP Action. Add info to row in table.")
    @AuditAction(auditAction = "ATP Action. Add info to row in table {{#request.titleTable}} "
            + "in project {{#request.projectName}}")
    @PostMapping(value = "/add-info-to-row")
    public List<ResponseMessage> addInfoToRow(@RequestBody RestApiRequest request) {
        return service.addInfoToRow(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable(), request.getAddInfoToRowRequests());
    }

    @Operation(description = "ATP Action. Refresh tables by name.")
    @AuditAction(auditAction = "ATP Action. Refresh tables by name {{#request.titleTable}} "
            + "in project {{#request.projectName}}")
    @PostMapping(value = "/refresh-tables")
    public List<ResponseMessage> refreshTables(@RequestBody RestApiRequest request) {
        return service.refreshTables(request.getProjectName(), request.getEnvName(), request.getSystemName(),
                request.getTitleTable());
    }

    @Operation(description = "ATP Action. Truncate table.")
    @AuditAction(auditAction = "ATP Action. Truncate table {{#request.titleTable}} "
            + "in project {{#request.projectName}}")
    @PostMapping(value = "/truncate-table")
    public List<ResponseMessage> truncateTable(@RequestBody RestApiRequest request) {
        return service.truncateTable(request.getProjectName(), request.getEnvName(), request.getSystemName(),
                request.getTitleTable());
    }

    @Operation(description = "ATP Action. Run cleanup for table.")
    @AuditAction(auditAction = "ATP Action. Run cleanup for table {{#request.titleTable}} "
            + "in project {{#request.projectName}}")
    @PostMapping(value = "/run-cleanup-table")
    public List<ResponseMessage> runCleanupForTable(@RequestBody RestApiRequest request) {
        return service.runCleanupForTable(request.getProjectName(), request.getEnvName(), request.getSystemName(),
                request.getTitleTable());
    }

    @Operation(description = "ATP Action. Resolves Table Name based on environment and table title.")
    @AuditAction(auditAction = "ATP Action. Returns Table name based on {{#request.titleTable}}.")
    @PostMapping(value = "/resolve-table")
    public ResponseMessage resolveTableName(@RequestBody RestApiRequest request) {
        if (StringUtils.isBlank(request.getProjectName())) {
            return new ResponseMessage(ResponseType.ERROR, "Project name is missed");
        }
        if (StringUtils.isBlank(request.getEnvName())) {
            return new ResponseMessage(ResponseType.ERROR, "Environment name is missed");
        }
        if (StringUtils.isBlank(request.getSystemName())) {
            return new ResponseMessage(ResponseType.ERROR, "System name is missed");
        }
        if (StringUtils.isBlank(request.getTitleTable())) {
            return new ResponseMessage(ResponseType.ERROR, "Title table name is missed");
        }
        return service.resolveTableName(request.getProjectName(), request.getEnvName(),
                request.getSystemName(), request.getTitleTable());
    }
}
