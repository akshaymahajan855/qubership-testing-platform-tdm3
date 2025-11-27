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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.exceptions.internal.TdmValidateCronException;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.model.cleanup.CleanupSettings;
import org.qubership.atp.tdm.model.cleanup.CleanupType;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;
import org.qubership.atp.tdm.model.cleanup.cleaner.impl.SqlTestDataCleaner;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumnIdentity;
import org.qubership.atp.tdm.repo.CleanupConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class CleanupServiceTest extends AbstractTestDataTest {

    @MockBean
    private MetricService metricServiceMock;

    @Autowired
    protected CleanupConfigRepository cleanupRepository;

    @BeforeEach
    public void setUp() {
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);
    }

    @Test
    public void cleanupConfig_saveAndGetCleanup_returnNormalCleanup() throws Exception {
        String tableName = "tdm_save_and_get_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Save And Get Cleanup Config", tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig expectedConfig = createSqlCleanupConfig(table, false);
        TestDataCleanupConfig actualConfig = cleanupService.getCleanupConfig(expectedConfig.getId());

        Assertions.assertEquals(expectedConfig, actualConfig);
        //needed for removeUnusedCleanupConfig work normally
        catalogRepository.deleteByTableName(tableName);
        cleanupRepository.deleteAll();
    }

    @Test
    public void cleanupConfig_updateAndGetCleanup_returnNormalCleanup() throws Exception {
        String tableName = "tdm_update_and_get_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Update And Get Cleanup Config", tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig saveConfig = createSqlCleanupConfig(table, false);
        TestDataCleanupConfig actualConfig = cleanupService.getCleanupConfig(saveConfig.getId());
        TestDataCleanupConfig updateConfig = createSqlCleanupConfig(table, false);
        TestDataCleanupConfig expectedConfig = cleanupService.getCleanupConfig(updateConfig.getId());
        Assertions.assertNotEquals(actualConfig.getId(), expectedConfig.getId());

        catalogRepository.deleteByTableName(tableName);
        cleanupRepository.deleteAll();
    }

    @Test
    public void cleanupConfig_saveAndGetSomeCleanup_returnNormalCleanup() throws Exception {
        String tableTitle = "TDM Save And Get Some Cleanup";

        String tableNameAtpUat = "tdm_save_and_get_some_cleanup_atp_uat";
        String tableNameAtpDev = "tdm_save_and_get_some_cleanup_atp_dev";
        String tableNameAtpQa = "tdm_save_and_get_some_cleanup_atp_qa";

        UUID atpUat = UUID.randomUUID();
        UUID atpDev = UUID.randomUUID();
        UUID atpQa = UUID.randomUUID();

        TestDataTableCatalog tableCatalogUat =
                createTestDataTableCatalog(projectId, systemId, atpUat, tableTitle, tableNameAtpUat);
        createTestDataTableCatalog(projectId, systemId, atpDev, tableTitle, tableNameAtpDev);
        createTestDataTableCatalog(projectId, systemId, atpQa, tableTitle, tableNameAtpQa);
        List<UUID> envList = new ArrayList<>();
        envList.add(atpUat);
        envList.add(atpDev);
        envList.add(atpQa);
        mockEnvironmentService(envList,systemId,systemId);
        TestDataCleanupConfig expectedConfig = createSqlCleanupConfig(tableCatalogUat, true);
        //TestDataCleanupConfig actualConfig = cleanupService.getCleanupConfig(expectedConfig.getId());
        TestDataTableCatalog catalog = catalogRepository.findAllByCleanupConfigId(expectedConfig.getId())
                .stream().filter(c -> c.getTableName().equals(tableNameAtpQa)).findFirst().orElse(new TestDataTableCatalog());

        catalogRepository.deleteByTableName(tableNameAtpUat);
        catalogRepository.deleteByTableName(tableNameAtpDev);
        catalogRepository.deleteByTableName(tableNameAtpQa);
        cleanupRepository.deleteAll();

        Assertions.assertEquals(catalog.getCleanupConfigId(), expectedConfig.getId());
    }

    @Test
    public void cleanupConfig_updateAndGetSomeCleanup_returnNormalCleanup() throws Exception {
        String tableTitle = "TDM Update And Get Some Cleanup";

        String tableNameAtpUat = "tdm_update_and_get_some_cleanup_atp_uat";
        String tableNameAtpDev = "tdm_update_and_get_some_cleanup_atp_dev";
        String tableNameAtpQa = "tdm_update_and_get_some_cleanup_atp_qa";

        UUID atpUat = UUID.randomUUID();
        UUID atpDev = UUID.randomUUID();
        UUID atpQa = UUID.randomUUID();

        TestDataTableCatalog tableCatalogUat =
                createTestDataTableCatalog(projectId, systemId, atpUat, tableTitle, tableNameAtpUat);
        createTestDataTableCatalog(projectId, systemId, atpDev, tableTitle, tableNameAtpDev);
        createTestDataTableCatalog(projectId, systemId, atpQa, tableTitle, tableNameAtpQa);

        List<UUID> saveEnvList = new ArrayList<>();
        saveEnvList.add(atpUat);
        saveEnvList.add(atpDev);
        saveEnvList.add(atpQa);

        mockEnvironmentService(saveEnvList,systemId,systemId);

        TestDataCleanupConfig savedCleanupConfig = createSqlCleanupConfig(tableCatalogUat, true);

        List<UUID> envForUpdate = new ArrayList<>();
        envForUpdate.add(atpQa);
        envForUpdate.add(atpDev);

        TestDataCleanupConfig updatedCleanupConfig = createSqlCleanupConfig(tableNameAtpQa, saveEnvList);
        TestDataCleanupConfig updatedCleanupConfigChanged = createSqlCleanupConfig(tableNameAtpQa, envForUpdate);

        catalogRepository.deleteByTableName(tableNameAtpUat);
        catalogRepository.deleteByTableName(tableNameAtpDev);
        catalogRepository.deleteByTableName(tableNameAtpQa);
        cleanupRepository.deleteAll();

        Assertions.assertNotEquals(savedCleanupConfig.getId(), updatedCleanupConfig.getId());
        Assertions.assertNotEquals(savedCleanupConfig.getId(), updatedCleanupConfigChanged.getId());
    }

    @Test
    public void saveCleanupConfig_saveCleanupConfigWithIncorrectCronExpression_getError() throws Exception {
        String tableName = "tdm_cleanup_incorrect_cron_expression";
        String incorrectCronExpression = "ADS";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId, "incorrectCronExpression", tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createSqlCleanupConfig(table, false);
        cleanupConfig.setSchedule(incorrectCronExpression);
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setTableName(tableName);
        cleanupSettings.setEnvironmentsList(Collections.singletonList(environmentId));
        try {
            cleanupService.saveCleanupConfig(cleanupSettings);
        } catch (Exception e) {
            String errorMessage = String.format(TdmValidateCronException.DEFAULT_MESSAGE, incorrectCronExpression);
            Assertions.assertEquals(errorMessage, e.getMessage());
        } finally {
            cleanupRepository.deleteAll();
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void saveCleanupConfig_saveCleanupConfigWithNullCronExpression_successfullySaved() throws Exception {
        String tableName = "tdm_cleanup_null_cron_expression";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId, "nullCronExpression", tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createSqlCleanupConfig(table, false);
        cleanupConfig.setSchedule(null);
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setTableName(table.getTableName());
        cleanupSettings.setEnvironmentsList(Collections.singletonList(environmentId));
        try {
            cleanupService.saveCleanupConfig(cleanupSettings);
        } catch (Exception e) {
            String errorMessage = String.format(TdmValidateCronException.DEFAULT_MESSAGE, null);
            Assertions.assertEquals(errorMessage, e.getMessage());
        } finally {
            cleanupRepository.deleteAll();
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void cleanupConfig_saveAndGetDateCleanup_returnNormalCleanup() throws Exception {
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        String tableName = "tdm_save_and_get_date_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Save And Get Date Cleanup Config", tableName);
        TestDataCleanupConfig expectedConfig = createDateCleanupConfig(table);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);

        TestDataCleanupConfig actualConfig = cleanupService.getCleanupConfig(expectedConfig.getId());

        Assertions.assertEquals(expectedConfig, actualConfig);
        //needed for removeUnusedCleanupConfig work normally
        catalogRepository.deleteByTableName(tableName);
        cleanupRepository.deleteAll();
    }

    @Test
    public void cleanupConfig_saveSharedCleanup_returnCleanup() throws Exception {
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        String tableName = "tdm_save_shared_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Save And Get Shared Cleanup Config", tableName);
        TestDataCleanupConfig expectedConfig = createSqlCleanupConfig(table, true);

        TestDataCleanupConfig actualConfig = cleanupService.getCleanupConfig(expectedConfig.getId());

        Assertions.assertEquals(expectedConfig, actualConfig);
        //needed for removeUnusedCleanupConfig work normally
        catalogRepository.deleteByTableName(tableName);
        cleanupRepository.deleteAll();
    }

    @Test
    public void cleanupConfig_runCleanup_returnCleanupResults() throws Exception {
        String tableName = "tdm_run_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Run Cleanup Config", tableName);
        createTestDataTable(tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createSqlCleanupConfig(table, false);
        List<CleanupResults> expectedCleanupResults = new ArrayList<>();
        expectedCleanupResults.add(new CleanupResults(tableName, 6, 6));

        List<CleanupResults> actualCleanupResults = cleanupService.runCleanup(cleanupConfig.getId());

        deleteTestDataTableIfExists(tableName);
        Assertions.assertEquals(expectedCleanupResults, actualCleanupResults);

        //needed for removeUnusedCleanupConfig work normally
        cleanupService.removeUnused();
        cleanupRepository.deleteAll();
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void cleanupConfig_runCleanup_returnRuntimeException() throws Exception {
        String tableName = "tdm_run_cleanup_config_runtime_exception";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Run Cleanup Config Runtime Exception", tableName);
        createTestDataTable(tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createErrorSqlCleanupConfig(table, false);
        try {
            cleanupService.runCleanup(tableName, cleanupConfig);
        } catch (Exception e) {
            Assertions.assertEquals("Error while run cleanup. Column 'PartnerR' doesn't exist", e.getMessage());
        } finally {
            deleteTestDataTableIfExists(tableName);
            cleanupService.removeUnused();
            cleanupRepository.deleteAll();
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void cleanupConfig_runDateCleanup_returnCleanupResultsRemovedZero() throws Exception {
        String tableName = "tdm_run_date_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Run Date Cleanup Config", tableName);
        createTestDataTable(tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createDateCleanupConfig(table);

        List<CleanupResults> expectedCleanupResults = new ArrayList<>();
        expectedCleanupResults.add(
                new CleanupResults("tdm_run_date_cleanup_config", 6, 0));

        List<CleanupResults> actualCleanupResults = cleanupService.runCleanup(cleanupConfig.getId());

        deleteTestDataTableIfExists(tableName);
        Assertions.assertEquals(expectedCleanupResults, actualCleanupResults);

        //needed for removeUnusedCleanupConfig work normally
        cleanupService.removeUnused();
        cleanupRepository.deleteAll();
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void cleanupConfig_runDateCleanup_returnCleanupResultsRemovedSix() throws Exception {
        String tableName = "tdm_run_date_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Run Date Cleanup Config", tableName);
        createTestDataTable(tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createDateCleanupConfigToDay(table);

        List<CleanupResults> expectedCleanupResults = new ArrayList<>();
        expectedCleanupResults.add(
                new CleanupResults("tdm_run_date_cleanup_config", 6, 6));

        List<CleanupResults> actualCleanupResults = cleanupService.runCleanup(cleanupConfig.getId());

        deleteTestDataTableIfExists(tableName);
        Assertions.assertEquals(expectedCleanupResults, actualCleanupResults);

        //needed for removeUnusedCleanupConfig work normally
        cleanupService.removeUnused();
        cleanupRepository.deleteAll();
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void cleanupConfig_getNextScheduledCleanupTime_returnScheduledCleanupTime() throws Exception {
        String nextScheduledRun = cleanupService.getNextScheduledRun("0 25 9 ? * * 2016/83");
        Assertions.assertTrue(nextScheduledRun.contains("09:25:00"));
    }

    @Test
    public void cleanupConfig_removeUnusedCleanupConfig_successfulRemove() throws Exception {
        String tableName = "tdm_remove_unused_cleanup_config";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Remove unused cleanup config", tableName);

        String toBeDeleted = "tdm_remove_unused_cleanup_config_to_be_deleted";
        TestDataTableCatalog tableToBeDeleted = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Remove unused cleanup config 2", toBeDeleted);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig expectedConfig = createSqlCleanupConfig(table, false);
        createSqlCleanupConfig(tableToBeDeleted, false);

        // Two cleanup configs should be saved.
        Assertions.assertEquals(2, cleanupRepository.count());

        catalogRepository.deleteByTableName(toBeDeleted);
        cleanupService.removeUnused();

        Assertions.assertEquals(1, cleanupRepository.count());
        Assertions.assertEquals(expectedConfig, cleanupService.getCleanupConfig(expectedConfig.getId()));

        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void cleanupSettings_getCleanupSettings_returnCleanupSettingsAndEnvId() throws Exception {
        String tableName = "tdm_get_cleanup_settings";
        String tableTitle = "TDM get cleanup settings";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        TestDataCleanupConfig cleanupConfig = createSqlCleanupConfig(table, false);
        CleanupSettings cleanupSettings = cleanupService.getCleanupSettings(cleanupConfig.getId());
        Assertions.assertEquals(cleanupConfig, cleanupSettings.getTestDataCleanupConfig());
        Assertions.assertEquals(environmentId, cleanupSettings.getEnvironmentsList().get(0));
        cleanupRepository.deleteAll();
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void getTablesForCleanupWithEnvList_callFunction_returnTablesList() {
        List<String> expectedList = new ArrayList<>();
        expectedList.add("table_name1");
        expectedList.add("table_name2");
        UUID systemId1 = UUID.randomUUID();
        UUID systemId2 = UUID.randomUUID();
        UUID envId1 = UUID.randomUUID();
        UUID envId2 = UUID.randomUUID();
        createTestDataTableCatalog(projectId,systemId1,envId1,"table_title",expectedList.get(0));
        createTestDataTableCatalog(projectId,systemId2,envId2,"table_title",expectedList.get(1));
        List<UUID> envsList = new ArrayList<>();
        envsList.add(envId1);
        envsList.add(envId2);
        mockEnvironmentService(envsList,systemId1,systemId2);
        List<String> resultTables = cleanupService.getTablesByTableNameAndEnvironmentsListWithSameSystemName(
                envsList,
                expectedList.get(0)
        );
        Collections.sort(resultTables);
        Assertions.assertEquals(expectedList, resultTables);
        cleanupRepository.deleteAll();
        catalogRepository.deleteByTableName("table_name1");
        catalogRepository.deleteByTableName("table_name2");
    }

    @Test
    public void cleanupConfig_runCleanupByCleanupSettings_returnCleanupResults() throws Exception {
        String tableName = "tdm_run_cleanup_config_by_settings";
        TestDataTableCatalog table = createTestDataTableCatalog(projectId, systemId, environmentId,
                "TDM Run Cleanup Config", tableName);
        createTestDataTable(tableName);
        mockEnvironmentService(Collections.singletonList(environmentId), systemId, UUID.randomUUID());
        TestDataCleanupConfig cleanupConfig = createSqlCleanupConfig(table, false);
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setTableName(tableName);
        cleanupSettings.setEnvironmentsList(Collections.singletonList(environmentId));

        List<CleanupResults> expectedCleanupResults = new ArrayList<>();
        expectedCleanupResults.add(new CleanupResults(tableName, 6, 6));

        List<CleanupResults> actualCleanupResults = cleanupService.runCleanup(cleanupSettings);

        //needed for removeUnusedCleanupConfig work normally
        cleanupService.removeUnused();
        cleanupRepository.deleteAll();
        catalogRepository.deleteByTableName(tableName);
        deleteTestDataTableIfExists(tableName);

        Assertions.assertEquals(expectedCleanupResults, actualCleanupResults);
    }

    @Test
    public void cleanupConfig_saveConfigWithOutTableName_returnError() {
        String crone = "0 0 9 ? * *";
        String tableName = "";
        try {
            cleanupService.saveCleanupConfig(createCleanupSettings(crone, tableName));
        } catch (Exception e) {
            Assertions.assertEquals("Table Name is null", e.getMessage());
        }
    }

    @Test
    public void checkSqlCleanupQueryParsing_validQuery() {
        String sqlQuery = "select * from nc_objects where object_id = ${'CUSTOMER_ID'} and"
                + " (select 24 * (sysdate - (INTERVAL '1' HOUR) - to_date(${'CREATED_WHEN'}, 'YYYY-MM-DD hh24:mi:ss'))"
                + " from dual) < 24";
        int queryTimeout = 10000;

        List<String> expectedQueryColumns = new ArrayList<>();
        expectedQueryColumns.add("CUSTOMER_ID");
        expectedQueryColumns.add("CREATED_WHEN");

        TestDataTable testDataTable = initTestDataTable();

        SqlTestDataCleaner cleaner = new SqlTestDataCleaner(null, sqlQuery, queryTimeout);
        List<String> actualQueryColumns = cleaner.collectParameterColumnsList(testDataTable);
        Assertions.assertArrayEquals(expectedQueryColumns.toArray(), actualQueryColumns.toArray(),
                "Query parameters placeholders don't match: " + expectedQueryColumns + " vs. "
                        + actualQueryColumns);
        boolean isValid = cleaner.parseQuery();
        Assertions.assertTrue(isValid, "Query (after replacements) is assumed to be valid, but:\n"
                + cleaner.getQuery());
    }

    @Test
    public void checkSqlCleanupQueryParsing_validQuery_repeatedPlaceHolders() {
        String sqlQuery = "select * from nc_objects where object_id = ${'CUSTOMER_ID'} "
                + " and "
                + " (select 24 * (sysdate - (INTERVAL '1' HOUR) - to_date(${'CREATED_WHEN'}, 'YYYY-MM-DD hh24:mi:ss'))"
                + " from dual) < 24"
                + " and "
                + " parent_id != ${'CUSTOMER_ID'}";
        int queryTimeout = 10000;

        List<String> expectedQueryColumns = new ArrayList<>();
        expectedQueryColumns.add("CUSTOMER_ID");
        expectedQueryColumns.add("CREATED_WHEN");
        expectedQueryColumns.add("CUSTOMER_ID");

        TestDataTable testDataTable = initTestDataTable();

        SqlTestDataCleaner cleaner = new SqlTestDataCleaner(null, sqlQuery, queryTimeout);
        List<String> actualQueryColumns = cleaner.collectParameterColumnsList(testDataTable);
        Assertions.assertArrayEquals(expectedQueryColumns.toArray(), actualQueryColumns.toArray(),
                "Query parameters placeholders don't match: " + expectedQueryColumns + " vs. "
                        + actualQueryColumns);
        boolean isValid = cleaner.parseQuery();
        Assertions.assertTrue(isValid, "Query (after replacements) is assumed to be valid, but:\n"
                + cleaner.getQuery());
    }

    private CleanupSettings createCleanupSettings(String cron, String tableName) {
        TestDataCleanupConfig cleanupConfig = new TestDataCleanupConfig();
        cleanupConfig.setEnabled(true);
        cleanupConfig.setSchedule(cron);
        cleanupConfig.setShared(false);
        cleanupConfig.setQueryTimeout(30);
        cleanupConfig.setType(CleanupType.SQL);
        cleanupConfig.setSearchSql("select * from test_data_table_catalog where table_title = "
                + "${'Partner'}");
        cleanupConfig.setShared(false);
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setTableName(tableName);
        cleanupSettings.setEnvironmentsList(Collections.singletonList(UUID.randomUUID()));
        return cleanupSettings;
    }

    private TestDataTableColumn createColumn(String tableName, String columnName) {
        TestDataTableColumnIdentity columnIdentity = new TestDataTableColumnIdentity();
        columnIdentity.setTableName(tableName);
        columnIdentity.setColumnName(columnName);
        return new TestDataTableColumn(columnIdentity);
    }

    private TestDataTable initTestDataTable() {
        TestDataTable testDataTable = new TestDataTable();
        testDataTable.setName("tdm_test1");
        testDataTable.setTitle("TDM Test 1");
        List<TestDataTableColumn> tableColumns = new ArrayList<>();
        tableColumns.add(createColumn("tdm_test1", "CUSTOMER_ID"));
        tableColumns.add(createColumn("tdm_test1", "CUSTOMER_TYPE"));
        tableColumns.add(createColumn("tdm_test1", "CREATED_WHEN"));
        testDataTable.setColumns(tableColumns);
        return testDataTable;
    }
}
