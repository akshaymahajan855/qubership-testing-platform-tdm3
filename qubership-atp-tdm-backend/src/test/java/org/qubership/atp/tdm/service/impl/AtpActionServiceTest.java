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

package org.qubership.atp.tdm.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;
import org.qubership.atp.tdm.model.rest.ApiDataFilter;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.AddInfoToRowRequest;
import org.qubership.atp.tdm.model.rest.requests.ChangeRowRequest;
import org.qubership.atp.tdm.model.rest.requests.GetRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyFullRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyRowRequest;
import org.qubership.atp.tdm.model.rest.requests.ReleaseRowRequest;
import org.qubership.atp.tdm.model.rest.requests.UpdateRowRequest;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.service.AtpActionService;
import org.springframework.beans.factory.annotation.Autowired;

public class AtpActionServiceTest extends AbstractTestDataTest {

    @Autowired
    protected AtpActionService atpActionService;

    @BeforeEach
    public void setUp() {
        when(environmentsService.getLazyProjectByName(any())).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyEnvironment);
        when(environmentsService.getLazySystemByName(any(), any(), any())).thenReturn(lazySystem);
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);
    }

    @Test
    public void atpRefreshTestData_testDataForInsertExist_responseMessageWithSuccessRefreshTestData() {
        String tableTitle = "TDM API Test Refresh Exist Test Data";
        String tableName = "tdm_api_test_refresh_exist_test_data";
        TestDataTable testDataTable = createTestDataTable(tableName);
        String importQuery = "select \"sim\" from " + tableName;
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName, importQuery);

        String msg = String.format("Successfully refreshed %s records fot table: %s.",
                testDataTable.getRecords(), tableTitle);
        String dataRefreshLink = "%s/project/%s/tdm/TEST%%20DATA/%s/%s";
        String tdmUrl = "localhost:8080";
        String resultLink = String.format(dataRefreshLink, tdmUrl, projectId, environmentId, systemId);
        ResponseMessage expectedResponseMessage = new ResponseMessage(ResponseType.SUCCESS, msg, resultLink);

        List<ResponseMessage> responseMessages = atpActionService.refreshTables(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle);
        ResponseMessage actualResponseMessage = responseMessages.get(0);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseMessage, actualResponseMessage);
    }

    @Test
    public void atpRefreshTestData_testDataForInsertNotExist_responseMessageWithError() {
        String tableTitle = "TDM API Test Refresh Not Exist Test Data";
        String tableName = "tdm_api_test_refresh_not_exist_test_data";
        createTestDataTable(tableName);
        String importQuery = "select \"sim\" from " + tableName;
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName, importQuery);
        testDataTableRepository.deleteAllRows(tableName);

        String msg = "Failed to refresh table with title:" + tableTitle + ". " +
                "Root cause: Import info don't exist for table: " + tableName;
        String dataRefreshLink = "%s/project/%s/tdm/TEST%%20DATA/%s/%s";
        String tdmUrl = "localhost:8080";
        String resultLink = String.format(dataRefreshLink, tdmUrl, projectId, environmentId, systemId);
        ResponseMessage expectedResponseMessage = new ResponseMessage(ResponseType.ERROR, msg, resultLink);

        List<ResponseMessage> responseMessages = atpActionService.refreshTables(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle);
        ResponseMessage actualResponseMessage = responseMessages.get(0);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseMessage, actualResponseMessage);
    }

    @Test
    public void atpInsertTestData_projectAndSystemSelected_newTableCreated() {
        String tableTitle = "TDM API Test Insert Under System";
        String expectedResponseMessage = "A new test data table has been created. Test data was inserted.";

        verifyCreationNewTableWithRecords(tableTitle, projectId, systemId, expectedResponseMessage);
    }

    @Test
    public void atpInsertTestData_projectAndSystemSelectedAndTableExists_dataInserted() {
        String tableName = "tdm_api_test_insert_precreated_table";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Insert with pre-created table", tableName);
        createTestDataTable(catalog.getTableName());
        String expectedResponseMessage = "Test data table with specified name already exists. Test data was inserted.";

        verifyCreationPreCreatedTableWithRecords(catalog.getTableTitle(), catalog.getProjectId(),
                catalog.getSystemId(), expectedResponseMessage);
        deleteTestDataTableIfExists(tableName);
    }

    @Test
    public void atpInsertTestData_temporaryEnvironmentSelected_newTableCreated() {
        String tableTitle = "TDM API Test Insert Under Temporary Environment";
        LazyEnvironment lazyTemporaryEnvironment = new LazyEnvironment() {{
            setName("CI2 2020-04-23T12:29:30.589Z");
            setId(UUID.randomUUID());
        }};

        when(environmentsService.getLazyEnvironmentByName(any(), any())).thenReturn(lazyTemporaryEnvironment);

        String link = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/";
        link = String.format(link, projectId, lazyTemporaryEnvironment.getId(), systemId);
        String expectedResponseMessage = "A new test data table has been created. Test data was inserted.";

        verifyCreationTableWithRecords(tableTitle, projectId, lazyProject.getName(),
                lazyTemporaryEnvironment.getName(), systemId, system.getName(), link, expectedResponseMessage);
    }

    @Test
    public void atpInsertTestData_projectOnlySelected_newTableCreated() {
        String tableTitle = "TDM API Test Insert Under Project";
        String expectedResponseMessage = "A new test data table has been created. Test data was inserted.";

        verifyCreationNewTableWithRecords(tableTitle, projectId, null, expectedResponseMessage);
    }

    @Test
    public void atpOccupyTestData_applyFilterTypeContains_successfullyFind() {
        String tableName = "tdm_api_test_occupy_contains_filter";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Occupy", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Contains", "12607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
        String expectedResponseMessage = "Test Automation 4";
        String link = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/tdm_api_test_occupy_contains_filter";

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseMessage, responseMessage.getContent());
        Assertions.assertEquals(String.format(link, projectId, lazyEnvironment.getId(), systemId),
                responseMessage.getLink());
    }

    @Test
    public void atpOccupyTestDataFullRow_applyFilterTypeContains_successfullyFind() {
        String tableName = "tdm_api_test_occupy_full_row_contains_filter";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Occupy Full Row", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyFullRowRequest occupyRowRequest = buildOccupyFullRowRequest(Collections.singletonList("Assignment"),
                "sim", "Contains", "12607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestDataFullRow(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        Map<String, String> expectedResponseValues = new HashMap<>();
        expectedResponseValues.put("Assignment", "Test Automation 4");
        Object actualResponseMap = responseMessage.getContentObject();

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseValues, actualResponseMap);
        String link = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/tdm_api_test_occupy_full_row_contains_filter";
        Assertions.assertEquals(String.format(link, projectId, lazyEnvironment.getId(), systemId),
                responseMessage.getLink());
    }

    @Test
    public void atpOccupyTestData_applyFilterTypeStartWith_successfullyFind() {
        String tableName = "tdm_api_test_occupy_stat_with_filter";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Occupy - Start With", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Start With", "89012607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
        String expectedResponseMessage = "Test Automation 4";
        String link = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/tdm_api_test_occupy_stat_with_filter";
        Assertions.assertEquals(expectedResponseMessage, responseMessage.getContent());
        Assertions.assertEquals(String.format(link, projectId, lazyEnvironment.getId(), systemId),
                responseMessage.getLink());
    }

    @Test
    public void atpOccupyTestDataFullRow_applyFilterTypeStartWith_successfullyFind() {
        String tableName = "tdm_api_test_occupy_full_row_stat_with_filter";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Occupy Full Row - Start With", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyFullRowRequest occupyRowRequest = buildOccupyFullRowRequest(Collections.singletonList("Assignment"),
                "sim", "Start With", "89012607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestDataFullRow(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        Map<String, String> expectedResponseValues = new HashMap<>();
        expectedResponseValues.put("Assignment", "Test Automation 4");
        Object actualResponseMap = responseMessage.getContentObject();

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseValues, actualResponseMap);
        String link = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/tdm_api_test_occupy_full_row_stat_with_filter";
        Assertions.assertEquals(String.format(link, projectId, lazyEnvironment.getId(), systemId),
                responseMessage.getLink());
    }

    @Test
    public void atpOccupyTestData_applyFilterTypeEqualsAndSelectedOnlyProject_successfullyFind() {
        String tableName = "tdm_api_test_occupy_equals_filter_under_project";
        verifyOccupationTableRow("TDM API Test Occupy Under Project",
                tableName, projectId, null, null);
    }

    @Test
    public void atpOccupyTestDataFullRow_applyFilterTypeEqualsAndSelectedOnlyProject_successfullyFind() {
        String tableName = "tdm_api_test_occupy_full_row_equals_filter_under_project";
        verifyOccupationTableFullRow("TDM API Test Occupy Full Under Project",
                tableName, projectId, null, null);
    }

    @Test
    public void atpOccupyTestData_applyFilterTypeEqualsAndSelectedProjectAndSystem_successfullyFind() {
        String tableName = "tdm_api_test_occupy_equals_filter_under_system";
        verifyOccupationTableRow("TDM API Test Occupy Under System",
                tableName, projectId, systemId, environmentId);
    }

    @Test
    public void atpOccupyTestDataFullRow_applyFilterTypeEqualsAndSelectedProjectAndSystem_successfullyFind()  {
        String tableName = "tdm_api_test_occupy_full_row_equals_filter_under_system";
        verifyOccupationTableFullRow("TDM API Test Occupy Full Under System",
                tableName, projectId, systemId, environmentId);
    }

    @Test
    public void atpOccupyTestData_wrongColumnNameInput_returnErrorMessage() {
        String tableName = "tdm_api_test_occupy_wrong_response_column_name";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Occupy - Wrong Column Name", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Wrong Column Name",
                "Assignment", "contains", "Test Automation 4");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("Column with name \"Wrong Column Name\" was not found!",
                responseMessage.getContent());
    }

    @Test
    public void atpOccupyTestDataFullRow_wrongColumnNameInput_returnErrorMessage() {
        String tableName = "tdm_api_test_occupy_full_row_wrong_response_column_name";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Occupy Full Row - Wrong Column Name", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyFullRowRequest occupyRowRequest = buildOccupyFullRowRequest(Collections.singletonList("Wrong Column "
                + "Name"), "Assignment", "contains", "Test Automation 4");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestDataFullRow(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage =
                responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("Column with name \"Wrong Column Name\" was not found!",
                responseMessage.getContent());
    }

    @Test
    public void atpOccupyTestDataFullRow_wrongSearchValueInput_nothingFoundErrorMessage() {
        String tableName = "tdm_api_test_occupy_full_row_wrong_search_value";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Occupy Full Row With Wrong Search Value", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyFullRowRequest occupyRowRequest = buildOccupyFullRowRequest(Collections.singletonList("sim"),
                "Assignment", "contains", "Test Automation 124");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestDataFullRow(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage =
                responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("No test data available for requested criteria!", responseMessage.getContent());
    }

    @Test
    public void atpOccupyTestData_wrongSearchValueInput_nothingFoundErrorMessage() {
        String tableName = "tdm_api_test_occupy_wrong_search_value";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Occupy With Wrong Search Value", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("SIM", "Assignment",
                "contains", "Test Automation 124");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("No test data available for requested criteria!", responseMessage.getContent());
    }

    @Test
    public void atpOccupyTestData_wrongTable_tableWasNotFoundErrorMessage() {
        String tableTitle = "Wrong Table Title";
        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle, Collections.emptyList());

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals(String.format("Table with title \"%s\" was not found!", tableTitle),
                responseMessage.getContent());
    }

    @Test
    public void atpOccupyTestDataFullRow_wrongTable_tableWasNotFoundErrorMessage() {
        String tableTitle = "Wrong Table Title";
        List<ResponseMessage> responseMessages = atpActionService.occupyTestDataFullRow(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle, Collections.emptyList());

        ResponseMessage responseMessage =
                responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals(String.format("Table with title \"%s\" was not found!", tableTitle),
                responseMessage.getContent());
    }

    @Test
    public void atpReleaseTestData_applyFilterTypeContains_successfullyFind() {
        String tableName = "tdm_api_test_release_contains_filter";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Release", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Contains", "12607200401410");

        ReleaseRowRequest releaseRowRequest = buildReleaseRowRequest("Assignment",
                "sim", "Contains", "12607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        List<ResponseMessage> releaseResponseMessages = atpActionService.releaseTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(releaseRowRequest));

        ResponseMessage releaseResponseMessage =
                releaseResponseMessages.stream().findFirst().orElse(new ResponseMessage());

        String expectedResponseMessage = "Test Automation 4";

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseMessage, releaseResponseMessage.getContent());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
    }

    @Test
    public void atpReleaseTestData_applyFilterTypeStartWith_successfullyFind() {
        String tableName = "tdm_api_test_release_start_with_filter";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Release - Start With", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Start With", "89012607200401410");

        ReleaseRowRequest releaseRowRequest = buildReleaseRowRequest("Assignment",
                "sim", "Start With", "89012607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        List<ResponseMessage> releaseResponseMessages = atpActionService.releaseTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(releaseRowRequest));

        ResponseMessage releaseResponseMessage =
                releaseResponseMessages.stream().findFirst().orElse(new ResponseMessage());
        String expectedResponseMessage = "Test Automation 4";

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseMessage, releaseResponseMessage.getContent());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
    }

    @Test
    public void atpReleaseTestData_applyFilterForMultipleRows_moreThanOneRowError() {
        String tableName = "tdm_api_test_release_multiple_rows";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Release - Multiple Rows", tableName);
        createTestDataTable(catalog.getTableName());
        OccupyRowRequest occupyRowRequestFirst = buildOccupyRowRequest("Assignment",
                "sim", "Start With", "89012607200401410");
        OccupyRowRequest occupyRowRequestSecond = buildOccupyRowRequest("Assignment",
                "sim", "Start With", "8901260720040140975");
        ReleaseRowRequest releaseRowRequest = buildReleaseRowRequest("Assignment",
                "sim", "Start With", "89");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequestFirst));
        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        responseMessages.clear();

        responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequestSecond));
        responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        List<ResponseMessage> releaseResponseMessages = atpActionService.releaseTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(releaseRowRequest));
        ResponseMessage releaseResponseMessage =
                releaseResponseMessages.stream().findFirst().orElse(new ResponseMessage());
        String expectedResponseMessage = "More than one value was found using the specified search criteria!";

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseMessage, releaseResponseMessage.getContent());
        Assertions.assertEquals(ResponseType.ERROR, releaseResponseMessage.getType());
    }

    @Test
    public void atpReleaseTestData_wrongColumnNameInput_returnErrorMessage() {
        String tableName = "tdm_api_test_release_wrong_response_column_name";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Release - Wrong Column Name", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Start With", "89012607200401410");

        ReleaseRowRequest releaseRowRequest = buildReleaseRowRequest("Wrong Column Name",
                "sim", "Start With", "89012607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        List<ResponseMessage> releaseResponseMessages = atpActionService.releaseTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(releaseRowRequest));
        ResponseMessage releaseResponseMessage =
                releaseResponseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, releaseResponseMessage.getType());
        Assertions.assertEquals("Column with name \"Wrong Column Name\" was not found!",
                releaseResponseMessage.getContent());
    }

    @Test
    public void atpReleaseTestData_wrongSearchValueInput_nothingFoundErrorMessage() {
        String tableName = "tdm_api_test_release_wrong_search_value";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Release With Wrong Search Value", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Start With", "89012607200401410");

        ReleaseRowRequest releaseRowRequest = buildReleaseRowRequest("Assignment",
                "sim", "Start With", "123");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        List<ResponseMessage> releaseResponseMessages = atpActionService.releaseTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(releaseRowRequest));
        ResponseMessage releaseResponseMessage =
                releaseResponseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, releaseResponseMessage.getType());
        Assertions.assertEquals("No test data available for requested criteria!",
                releaseResponseMessage.getContent());
    }

    @Test
    public void atpUpdateTestData_onlyProjectSelected_successfulUpdate() {
        String tableName = "tdm_api_test_update_under_project";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, null, null,
                "TDM API Test Update Under Project", tableName);
        createTestDataTable(catalog.getTableName());
        UpdateRowRequest updateRowRequest = new UpdateRowRequest();
        verifyChangingTableRow(catalog, updateRowRequest);
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void atpUpdateTestData_projectAndSystemSelected_successfulUpdate() {
        String tableName = "tdm_api_test_update_under_system";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Update Under System", tableName);
        createTestDataTable(catalog.getTableName());
        UpdateRowRequest updateRowRequest = new UpdateRowRequest();
        verifyChangingTableRow(catalog, updateRowRequest);
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void atpUpdateTestData_wrongFilters_noTestDataAvailableErrorMessage() {
        String tableTitle = "TDM API Test Update Wrong Filters";
        String tableName = "tdm_api_update_wrong_filters";

        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        createTestDataTable(tableName);

        List<ApiDataFilter> wrongFilters = Collections.singletonList(new ApiDataFilter("Assignment",
                "equals", "Test Automation 194", false));
        Map<String, String> recordWithDataForUpdate = new HashMap<>();
        recordWithDataForUpdate.put("environment", "ZLAB109");
        List<UpdateRowRequest> updateRowRequests = new ArrayList<>();
        UpdateRowRequest updateRowRequest = new UpdateRowRequest();
        updateRowRequest.setFilters(wrongFilters);
        updateRowRequest.setRecordWithDataForUpdate(recordWithDataForUpdate);
        updateRowRequests.add(updateRowRequest);

        List<ResponseMessage> responseMessages = atpActionService.updateTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle, updateRowRequests);

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("No test data available for requested criteria!", responseMessage.getContent());
    }

    @Test
    public void atpUpdateTestData_wrongTable_tableWasNotFoundErrorMessage() {
        String tableTitle = "TDM API Test Update Wrong Table Title";
        List<ResponseMessage> responseMessages = atpActionService.updateTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle, Collections.emptyList());

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals(String.format("Table with title \"%s\" was not found!", tableTitle),
                responseMessage.getContent());
    }

    @Test
    public void atpGetTestData_onlyProjectSelected_returnTable() {
        String tableName = "tdm_api_test_get_under_project";
        verifyGettingTableRecord("TDM API Test Get Under Project", tableName,
                projectId, null, null);
    }

    @Test
    public void atpGetTestData_systemAndProjectSelected_returnTable() {
        String tableName = "tdm_api_test_get_under_system";
        verifyGettingTableRecord("TDM API Test Get Under System",
                tableName, projectId, systemId, environmentId);
    }

    @Test
    public void atpGetTestData_wrongTable_tableWasNotFoundErrorMessage() {
        String tableName = "tdm_api_test_get_date_wrong_table";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, null, null,
                "TDM API Test Get Data Wrong Table", tableName);
        createTestDataTable(catalog.getTableName());

        GetRowRequest getRowRequest = buildGetRowRequest("Assignment",
                "sim", "Contains", "12607200401410");

        String wrongTableTitle = "TDM API Test Get Data Wrong";
        List<ResponseMessage> responseMessages = atpActionService.getTestData(lazyProject.getName(),
                lazyEnvironment.getName(), null, wrongTableTitle, Collections.singletonList(getRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals(String.format("Table with title \"%s\" was not found!", wrongTableTitle),
                responseMessage.getContent());
    }

    @Test
    public void atpGetTestData_wrongFilters_rowsWereNotFoundErrorMessage() {
        String tableName = "tdm_api_test_get_date_wrong_filter";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, null, null,
                "TDM API Test Get Data Wrong Filter", tableName);
        createTestDataTable(catalog.getTableName());

        GetRowRequest getRowRequest = buildGetRowRequest("Assignment",
                "sim", "Contains", "126072004014133");

        List<ResponseMessage> responseMessages = atpActionService.getTestData(lazyProject.getName(),
                lazyEnvironment.getName(), null, catalog.getTableTitle(),
                Collections.singletonList(getRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("No test data available for requested criteria!", responseMessage.getContent());
    }

    @Test
    public void atpGetTestData_wrongColumnName_columnWasNotFoundErrorMessage() {
        String tableName = "tdm_api_test_get_date_wrong_column";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, null, null,
                "TDM API Test Get Data Wrong Column", tableName);
        createTestDataTable(catalog.getTableName());

        GetRowRequest getRowRequest = buildGetRowRequest("Assignment 123",
                "sim", "Contains", "12607200401410");

        List<ResponseMessage> responseMessages = atpActionService.getTestData(lazyProject.getName(),
                lazyEnvironment.getName(), null, catalog.getTableTitle(),
                Collections.singletonList(getRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("Column with name \"Assignment 123\" was not found!", responseMessage.getContent());
    }

    @Test
    public void atpAddInfoToTestData_onlyProjectSelected_addedSuccessfully() {
        String tableName = "tdm_api_test_add_info_under_project";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, null, null,
                "TDM API Test Add info Under Project", tableName);
        createTestDataTable(catalog.getTableName());
        AddInfoToRowRequest addInfoToRowRequest = new AddInfoToRowRequest();
        verifyChangingTableRow(catalog, addInfoToRowRequest);
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void atpAddInfoToTestData_systemAndProjectSelected_addedSuccessfully() {
        String tableName = "tdm_api_test_add_info_under_system";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Add info Under System", tableName);
        createTestDataTable(catalog.getTableName());
        AddInfoToRowRequest addInfoToRowRequest = new AddInfoToRowRequest();
        verifyChangingTableRow(catalog, addInfoToRowRequest);
        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void atpAddInfoToTestData_wrongFilters_noTestDataAvailableErrorMessage() {
        String tableTitle = "TDM API Test Add info Wrong Filters";
        String tableName = "tdm_api_test_add_info_wrong_filters";

        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        createTestDataTable(tableName);

        List<ApiDataFilter> wrongFilters = Collections.singletonList(new ApiDataFilter("Assignment",
                "equals", "Test Automation 194", false));
        Map<String, String> recordWithDataForUpdate = new HashMap<>();
        recordWithDataForUpdate.put("environment", "ZLAB109");
        List<AddInfoToRowRequest> addInfoToRowRequests = new ArrayList<>();
        AddInfoToRowRequest addInfoToRowRequest = new AddInfoToRowRequest();
        addInfoToRowRequest.setFilters(wrongFilters);
        addInfoToRowRequest.setRecordWithDataForUpdate(recordWithDataForUpdate);
        addInfoToRowRequests.add(addInfoToRowRequest);

        List<ResponseMessage> responseMessages = atpActionService.addInfoToRow(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle, addInfoToRowRequests);

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("No test data available for requested criteria!", responseMessage.getContent());
    }

    @Test
    public void atpAddInfoToTestData_wrongTable_tableWasNotFoundErrorMessage() {
        String tableName = "tdm_api_test_add_info_wrong_table";
        TestDataTableCatalog catalog = createTestDataTableCatalog(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "TDM API Test Add info Wrong Table", tableName);
        createTestDataTable(catalog.getTableName());

        String wrongTableTitle = "Wrong Table Title";
        List<ResponseMessage> responseMessages = atpActionService.addInfoToRow(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), wrongTableTitle, Collections.emptyList());
        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals(String.format("Table with title \"%s\" was not found!", wrongTableTitle),
                responseMessage.getContent());
    }

    @Test
    public void atpTruncateTable_truncateTable_tableTruncated() {
        String tableTitle =  "tableTitleForTruncateTable";
        String tableName = "tdm_api_test_truncate_table";
        TestDataTableCatalog catalog = createTestDataTableCatalog(lazyProject.getId(), system.getId(),
                lazyEnvironment.getId(), tableTitle, tableName);
        TestDataTable testDataTable = createTestDataTable(catalog.getTableName());
        testDataTable.setTitle(tableTitle);
        atpActionService.truncateTable(lazyProject.getId().toString(),
                lazyEnvironment.getName(), system.getName(), tableTitle);
        boolean isDataRemoved = testDataTableRepository.getFullTestData(tableName).getData().isEmpty();

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertTrue(isDataRemoved);
    }

    @Test
    public void atpTruncateTable_truncateTable_correctMessageHasBeenGot() {
        String tableTitle =  "tableTitleForTruncateTableCorrectMessage";
        String tableName = "tdm_api_test_truncate_table_correct_message";
        TestDataTableCatalog catalog = createTestDataTableCatalog(lazyProject.getId(), system.getId(),
                lazyEnvironment.getId(), tableTitle, tableName);
        TestDataTable testDataTable = createTestDataTable(catalog.getTableName());
        testDataTable.setTitle(tableTitle);
        List<ResponseMessage> responseMessages = atpActionService.truncateTable(lazyProject.getId().toString(),
                lazyEnvironment.getName(), system.getName(), tableTitle);
        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(String.format("Table %s has been truncated.", tableName),
                responseMessage.getContent());
    }

    @Test
    public void atpTruncateTable_truncateTable_tableWasNotFound() {
        String wrongTableTitle = "Wrong table title";
        List<ResponseMessage> responseMessages = atpActionService.truncateTable(lazyProject.getId().toString(),
                lazyEnvironment.getName(), system.getName(), wrongTableTitle);
        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        Assertions.assertEquals(String.format("Tables with title: %s was not found under project with id: %s",
                wrongTableTitle, lazyProject.getId()), responseMessage.getContent());
    }

    @Test
    public void atpRunCleanupForTable_runCleanup_cleanupCompletedWithSuccessMessage() throws Exception {
        String tableTitle =  "tableTitleForRunCleanupTableCorrectMessage";
        String tableName = "tdm_api_test_run_cleanup_table_correct_message";
        TestDataTableCatalog catalog = createTestDataTableCatalog(lazyProject.getId(), system.getId(),
                lazyEnvironment.getId(), tableTitle, tableName);
        TestDataTable testDataTable = createTestDataTable(catalog.getTableName());
        testDataTable.setTitle(tableTitle);
        mockEnvironmentService(Collections.singletonList(lazyEnvironment.getId()),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createDateCleanupConfig(catalog);
        catalog.setCleanupConfigId(cleanupConfig.getId());
        List<ResponseMessage> responseMessages = atpActionService.runCleanupForTable(lazyProject.getId().toString(),
                lazyEnvironment.getName(), system.getName(), tableTitle);
        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(String.format("For table \"%s\" with total records %s has been removed %s records.",
                tableName, 6, 0),
                responseMessage.getContent());
    }

    @Test//er createErrorSqlCleanupConfig(catalog, false);
    public void atpRunCleanupForTable_runCleanup_cleanupCompletedWithErrorMessage() throws Exception {
        String tableTitle =  "tableTitleForRunCleanupTableBySql";
        String tableName = "tdm_api_test_run_cleanup_table_by_sql";
        TestDataTableCatalog catalog = createTestDataTableCatalog(lazyProject.getId(), system.getId(),
                lazyEnvironment.getId(), tableTitle, tableName);
        TestDataTable testDataTable = createTestDataTable(catalog.getTableName());
        testDataTable.setTitle(tableTitle);
        mockEnvironmentService(Collections.singletonList(lazyEnvironment.getId()),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createErrorSqlCleanupConfig(catalog, false);
        catalog.setCleanupConfigId(cleanupConfig.getId());
        List<ResponseMessage> responseMessages = atpActionService.runCleanupForTable(lazyProject.getId().toString(),
                lazyEnvironment.getName(), system.getName(), tableTitle);
        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(String.format("Cleanup %s for table %s failed.\n" +
                        "Error while run cleanup. Column 'PartnerR' doesn't exist", cleanupConfig, tableName),
                responseMessage.getContent());
    }


    @Test
    public void atpRunCleanupForTable_prepareDataWithoutCleanupConfRunCleanup_cleanupNotFound() {
        String tableTitle =  "tableTitleForRunCleanupTableErrorMessage";
        String tableName = "tdm_api_test_run_cleanup_table_error_message";
        TestDataTableCatalog catalog = createTestDataTableCatalog(lazyProject.getId(), system.getId(),
                lazyEnvironment.getId(), tableTitle, tableName);
        TestDataTable testDataTable = createTestDataTable(catalog.getTableName());
        testDataTable.setTitle(tableTitle);
        List<ResponseMessage> responseMessages = atpActionService.runCleanupForTable(lazyProject.getId().toString(),
                lazyEnvironment.getName(), system.getName(), tableTitle);
        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(String.format("Cleanup hasn't been configured for table \"%s\".", tableTitle),
                responseMessage.getContent());
    }

    @Test
    public void atpRunCleanupForTable_runCleanupWithoutPreparation_tableNotFound() {
        String tableTitle =  "tableTitleForNotExistTable";
        List<ResponseMessage> responseMessages = atpActionService.runCleanupForTable(lazyProject.getId().toString(),
                lazyEnvironment.getName(), system.getName(), tableTitle);
        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(String.format("Table \"%s\" hasn't been found. Could you please check provided data.",
                tableTitle),
                responseMessage.getContent());
    }

    protected OccupyRowRequest buildOccupyRowRequest(String nameColumnResponse, String columnName,
                                                     String searchType, String searchValue) {
        OccupyRowRequest occupyRowRequest = new OccupyRowRequest();
        occupyRowRequest.setNameColumnResponse(nameColumnResponse);

        ApiDataFilter filter = new ApiDataFilter(columnName, searchType, searchValue, false);

        occupyRowRequest.setFilters(Collections.singletonList(filter));

        return occupyRowRequest;
    }

    protected OccupyFullRowRequest buildOccupyFullRowRequest(List<String> nameColumnResponse, String columnName,
                                                     String searchType, String searchValue) {
        OccupyFullRowRequest occupyFullRowRequest = new OccupyFullRowRequest();
        occupyFullRowRequest.setResponseColumnNames(nameColumnResponse);

        ApiDataFilter filter = new ApiDataFilter(columnName, searchType, searchValue, false);

        occupyFullRowRequest.setFilters(Collections.singletonList(filter));

        return occupyFullRowRequest;
    }

    private ReleaseRowRequest buildReleaseRowRequest(String nameColumnResponse, String columnName,
                                                       String searchType, String searchValue) {
        ReleaseRowRequest releaseRowRequest = new ReleaseRowRequest();
        releaseRowRequest.setNameColumnResponse(nameColumnResponse);

        ApiDataFilter filter = new ApiDataFilter(columnName, searchType, searchValue, false);

        releaseRowRequest.setFilters(Collections.singletonList(filter));

        return releaseRowRequest;
    }

    protected GetRowRequest buildGetRowRequest(String nameColumnResponse, String columnName,
                                               String searchType, String searchValue) {
        GetRowRequest getRowRequest = new GetRowRequest();
        getRowRequest.setNameColumnResponse(nameColumnResponse);

        ApiDataFilter filter = new ApiDataFilter(columnName, searchType, searchValue, false);

        getRowRequest.setFilters(Collections.singletonList(filter));

        return getRowRequest;
    }

    private void verifyCreationNewTableWithRecords(String tableTitle, UUID projectId, UUID systemId,
                                                   String expectedResponseMessage) {
        String linkFull = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/";
        String linkNullSystem = "localhost:8080/project/%s/";
        UUID lazyEnvironmentId;
        if (Objects.nonNull(systemId)) {
            lazyEnvironmentId = lazyEnvironment.getId();
            verifyCreationTableWithRecords(tableTitle, projectId, systemId,
                    String.format(linkFull, AtpActionServiceTest.projectId, lazyEnvironmentId, systemId), expectedResponseMessage);
        } else {
            verifyCreationTableWithRecords(tableTitle, projectId, systemId,
                    String.format(linkNullSystem, AtpActionServiceTest.projectId), expectedResponseMessage);
        }
    }

    private void verifyCreationPreCreatedTableWithRecords(String tableTitle, UUID projectId,
                                                          UUID systemId, String expectedResponseMessage) {
        String link = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/";

        verifyCreationTableWithRecords(tableTitle, projectId, systemId,
                String.format(link, AtpActionServiceTest.projectId, lazyEnvironment.getId(), systemId), expectedResponseMessage);
    }

    private void verifyCreationTableWithRecords(String tableTitle, UUID projectId, UUID systemId,
                                                String link, String expectedResponseMessage) {
        String systemName = Objects.nonNull(systemId) ? system.getName() : null;
        verifyCreationTableWithRecords(tableTitle, projectId, lazyProject.getName(),
                lazyEnvironment.getName(), systemId, systemName, link, expectedResponseMessage);
    }

    private void verifyCreationTableWithRecords(String tableTitle, UUID projectId, String projectName, String envName,
                                                UUID systemId, String systemName, String link,
                                                String expectedResponseMessage) {
        List<Map<String, Object>> records = buildTestDataTable().getData();

        ResponseMessage responseMessage = atpActionService.insertTestData(projectName, envName, systemName,
                tableTitle, records);

        TestDataTableCatalog tableCatalog = catalogRepository
                .findByProjectIdAndSystemIdAndTableTitle(projectId, systemId, tableTitle);

        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
        Assertions.assertEquals(expectedResponseMessage, responseMessage.getContent());
        Assertions.assertEquals(link + tableCatalog.getTableName(),
                responseMessage.getLink());

        TestDataTable testDataTable = testDataService.getTestData(tableCatalog.getTableName());

        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> erRecord = records.get(i);
            Map<String, Object> arRecord = testDataTable.getData().get(i);

            for (String key : erRecord.keySet()) {
                Object erValue = erRecord.get(key);
                Object arValue = arRecord.get(key);
                Assertions.assertEquals(erValue, arValue);
            }
        }
    }

    private void verifyOccupationTableRow(String tableTitle, String tableName,
                                          UUID projectId, UUID systemId, UUID envId) {
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, envId, tableTitle, tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Contains", "12607200401410");

        String systemName = Objects.nonNull(systemId) ? system.getName() : null;
        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), systemName, catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
        UUID lazyEnvironmentId = Objects.nonNull(systemId) ? lazyEnvironment.getId() : null;
        String expectedResponseMessage = "Test Automation 4";
        String linkFull = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/%s";
        String linkNullSystem = "localhost:8080/project/%s/%s";
        Assertions.assertEquals(expectedResponseMessage, responseMessage.getContent());
        if (Objects.nonNull(systemId)) {
            Assertions.assertEquals(String.format(linkFull, AtpActionServiceTest.projectId, lazyEnvironmentId, systemId,
                    catalog.getTableName()),
                    responseMessage.getLink());
        } else {
            Assertions.assertEquals(String.format(linkNullSystem, AtpActionServiceTest.projectId, catalog.getTableName()),
                    responseMessage.getLink());
        }

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    private void verifyOccupationTableFullRow(String tableTitle, String tableName,
                                          UUID projectId, UUID systemId, UUID envId) {
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, envId, tableTitle, tableName);
        createTestDataTable(catalog.getTableName());

        OccupyFullRowRequest occupyRowRequest = buildOccupyFullRowRequest(Collections.singletonList("Assignment"),
                "sim", "Contains", "12607200401410");

        String systemName = Objects.nonNull(systemId) ? system.getName() : null;
        List<ResponseMessage> responseMessages = atpActionService.occupyTestDataFullRow(lazyProject.getName(),
                lazyEnvironment.getName(), systemName, catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage =
                responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
        UUID lazyEnvironmentId = Objects.nonNull(systemId) ? lazyEnvironment.getId() : null;
        Map<String, String> expectedResponseValues = new HashMap<>();
        expectedResponseValues.put("Assignment", "Test Automation 4");
        Object actualResponseMap = responseMessage.getContentObject();
        Assertions.assertEquals(expectedResponseValues, actualResponseMap);
        if (Objects.nonNull(systemId)) {
            String linkFull = "localhost:8080/project/%s/tdm/TEST%%20DATA/%s/%s/%s";
            Assertions.assertEquals(String.format(linkFull, AtpActionServiceTest.projectId, lazyEnvironmentId, systemId,
                    catalog.getTableName()),
                    responseMessage.getLink());
        } else {
            String linkNullSystem = "localhost:8080/project/%s/%s";
            Assertions.assertEquals(String.format(linkNullSystem, AtpActionServiceTest.projectId, catalog.getTableName()),
                    responseMessage.getLink());
        }

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    private void verifyChangingTableRow(TestDataTableCatalog catalog, ChangeRowRequest changeRowRequest) {
        List<ApiDataFilter> apiDataFilters = Collections.singletonList(new ApiDataFilter("Assignment",
                "contains", "Test Automation 4", false));
        changeRowRequest.setFilters(apiDataFilters);
        Map<String, String> recordWithDataForUpdate = new HashMap<>();
        recordWithDataForUpdate.put("environment", "ZLAB109");
        changeRowRequest.setRecordWithDataForUpdate(recordWithDataForUpdate);
        List<ResponseMessage> responseMessages;

        String systemName = Objects.nonNull(catalog.getSystemId()) ? system.getName() : null;

        List<TestDataTableFilter> filters = Collections.singletonList(new TestDataTableFilter("Assignment",
                "contains", Collections.singletonList("Test Automation 4"), false));
        if (changeRowRequest instanceof AddInfoToRowRequest) {
            AddInfoToRowRequest addInfoToRow = (AddInfoToRowRequest) changeRowRequest;
            responseMessages = atpActionService.addInfoToRow(lazyProject.getName(),
                    lazyEnvironment.getName(), systemName, catalog.getTableTitle(),
                    Collections.singletonList(addInfoToRow));
            TestDataTable table = testDataService.getTestData(catalog.getTableName(), null, null,
                    filters, null, false);
            Assertions.assertEquals("ZLAB04\r\nZLAB109", table.getData().get(0).get("environment"));
        } else {
            UpdateRowRequest updateRow = (UpdateRowRequest) changeRowRequest;
            responseMessages = atpActionService.updateTestData(lazyProject.getName(),
                    lazyEnvironment.getName(), systemName, catalog.getTableTitle(),
                    Collections.singletonList(updateRow));
            TestDataTable table = testDataService.getTestData(catalog.getTableName(), null, null,
                    filters, null, false);
            Assertions.assertEquals("ZLAB109", table.getData().get(0).get("environment"));
        }

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
    }

    private void verifyGettingTableRecord(String tableTitle, String tableName, UUID projectId, UUID systemId,
                                          UUID envId) {
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, envId, tableTitle, tableName);
        createTestDataTable(catalog.getTableName());

        GetRowRequest getRowRequest = buildGetRowRequest("Assignment",
                "sim", "Contains", "12607200401410");

        String systemName = Objects.nonNull(systemId) ? system.getName() : null;
        List<ResponseMessage> responseMessages = atpActionService.getTestData(lazyProject.getName(),
                lazyEnvironment.getName(), systemName, catalog.getTableTitle(),
                Collections.singletonList(getRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
        Assertions.assertEquals("Test Automation 4", responseMessage.getContent());

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void atpReleaseAllTestData_rightTableName_successfullyRelease() {
        String tableName = "tdm_api_test_release_all_success";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Full Release", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Contains", "12607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));


        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        List<ResponseMessage> releaseResponseMessages = atpActionService.releaseFullTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle());

        ResponseMessage releaseResponseMessage =
                releaseResponseMessages.stream().findFirst().orElse(new ResponseMessage());

        String expectedResponseMessage = "All occupied data in table with title \"TDM API Test Full Release\" "
                + "released.";

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseMessage, releaseResponseMessage.getContent());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
    }

    @Test
    public void atpReleaseAllTestData_wrongTableName_failRelease() {
        String tableName = "tdm_api_test_release_all_wrong";
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM API Test Full Release Wrong", tableName);
        createTestDataTable(catalog.getTableName());

        OccupyRowRequest occupyRowRequest = buildOccupyRowRequest("Assignment",
                "sim", "Contains", "12607200401410");

        List<ResponseMessage> responseMessages = atpActionService.occupyTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), catalog.getTableTitle(),
                Collections.singletonList(occupyRowRequest));

        ResponseMessage responseMessage = responseMessages.stream().findFirst().orElse(new ResponseMessage());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());

        List<ResponseMessage> releaseResponseMessages = atpActionService.releaseFullTestData(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), "wrong");

        ResponseMessage releaseResponseMessage =
                releaseResponseMessages.stream().findFirst().orElse(new ResponseMessage());

        String expectedResponseMessage = "Table with title \"wrong\" was not found!";

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(expectedResponseMessage, releaseResponseMessage.getContent());
        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
    }

    @Test
    public void resolveTableName_catalogRowExists_success() {
        String tableTitle = "TDM API Test Resolve Table Name";
        String tableName = "tdm_api_test_resolve_table_name";
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);

        ResponseMessage responseMessage = atpActionService.resolveTableName(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);

        Assertions.assertEquals(ResponseType.SUCCESS, responseMessage.getType());
        Assertions.assertEquals(tableName, responseMessage.getContent());
    }

    @Test
    public void resolveTableName_catalogRowNotFound_error() {
        String tableTitle = "TDM API Test Resolve Table Name - Not Found";

        ResponseMessage responseMessage = atpActionService.resolveTableName(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), tableTitle);

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals(String.format("Table with title \"%s\" was not found!", tableTitle),
                responseMessage.getContent());
    }

    @Test
    public void resolveTableName_missingEnvName_validationError() {
        ResponseMessage responseMessage = atpActionService.resolveTableName(lazyProject.getName(),
                "   ", system.getName(), "someTitle");

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("Environment name is missed", responseMessage.getContent());
    }

    @Test
    public void resolveTableName_missingSystemName_validationError() {
        ResponseMessage responseMessage = atpActionService.resolveTableName(lazyProject.getName(),
                lazyEnvironment.getName(), "", "someTitle");

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("System name is missed", responseMessage.getContent());
    }

    @Test
    public void resolveTableName_missingTitleTable_validationError() {
        ResponseMessage responseMessage = atpActionService.resolveTableName(lazyProject.getName(),
                lazyEnvironment.getName(), system.getName(), " ");

        Assertions.assertEquals(ResponseType.ERROR, responseMessage.getType());
        Assertions.assertEquals("Title table name is missed", responseMessage.getContent());
    }

}
