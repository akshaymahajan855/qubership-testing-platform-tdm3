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

package org.qubership.atp.tdm;

import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;

import org.qubership.atp.tdm.env.configurator.service.GitService;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.ColumnType;
import org.qubership.atp.tdm.model.FilterType;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.TestDataTableImportInfo;
import org.qubership.atp.tdm.model.cleanup.CleanupSettings;
import org.qubership.atp.tdm.model.cleanup.CleanupType;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;
import org.qubership.atp.tdm.model.table.TestDataFlagsTable;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumnIdentity;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.ColumnRepository;
import org.qubership.atp.tdm.repo.ImportInfoRepository;
import org.qubership.atp.tdm.repo.TestDataColumnFlagsRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.CleanupService;
import org.qubership.atp.tdm.service.ProjectInformationService;
import org.qubership.atp.tdm.service.StatisticsService;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.utils.CurrentTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.Environment;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.repo.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTestDataTest extends AbstractTest {

    private static final String RESOURCES_PATH = "src/test/resources/";

    protected static final String projectName = "Test Project";
    protected static final String environmentName = "Test Environment";
    protected static final String systemName = "Test System";
    protected static final UUID projectId = UUID.randomUUID();
    protected static final UUID systemId = UUID.randomUUID();
    protected static final UUID environmentId = UUID.randomUUID();

    protected TdmMdcHelper tdmMdcHelper = new TdmMdcHelper();

    protected static final Connection httpConnection = new Connection() {{
        setName("http");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("url", "http://localhost:8080/");
        setParameters(parameters);
    }};

    protected static final Connection dbConnection = new Connection() {{
        setName("DB");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("db_type", "postgresql");
        parameters.put("jdbc_url", "jdbc:h2:file:./database/atptdm");
        parameters.put("db_login", "tdmadmin");
        parameters.put("db_password", "tdmadmin");
        setParameters(parameters);
    }};

    protected static final Connection dbConnectionErrorDbType = new Connection() {{
        setName("DB");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("db_type", "errorType");
        parameters.put("jdbc_url", "jdbc:h2:file:./database/atptdm");
        parameters.put("db_login", "tdmadmin");
        parameters.put("db_password", "tdmadmin");
        setParameters(parameters);
    }};

    protected static final Connection dbConnectionErrorName = new Connection() {{
        setName("ErrorName");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("db_type", "postgresql");
        parameters.put("jdbc_url", "jdbc:h2:file:./database/atptdm");
        parameters.put("db_login", "tdmadmin");
        parameters.put("db_password", "tdmadmin");
        setParameters(parameters);
    }};

    protected static final Connection dbConnectionErrorCredentials = new Connection() {{
        setName("DB");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("db_type", "postgresql");
        parameters.put("jdbc_url", "jdbc:h2:file:./database/atptdm");
        parameters.put("db_login", "incorrect");
        parameters.put("db_password", "incorrect");
        setParameters(parameters);
    }};

    protected static final List<Connection> connections = new ArrayList<Connection>() {{
        add(httpConnection);
        add(dbConnection);
    }};

    protected static final System system = new System() {{
        setName(systemName);
        setId(systemId);
        setConnections(connections);
    }};

    protected static final System systemErrorConnectionName = new System() {{
        setName(systemName);
        setId(systemId);
        List<Connection> connections = new ArrayList<>();
        connections.add(dbConnectionErrorName);
        setConnections(connections);
    }};

    protected static final System systemErrorCredentials = new System() {{
        setName(systemName);
        setId(systemId);
        List<Connection> connections = new ArrayList<>();
        connections.add(dbConnectionErrorCredentials);
        setConnections(connections);
    }};

    protected static final LazySystem lazySystem = new LazySystem() {{
        setName(systemName);
        setId(systemId);
    }};

    protected static final Environment environment = new Environment() {{
        setName(environmentName);
        setId(environmentId);
        setSystems(Collections.singletonList(system));
    }};

    protected static final LazyEnvironment lazyEnvironment = new LazyEnvironment() {{
        setName(environmentName);
        setId(environmentId);
        setProjectId(projectId);
        setSystems(Collections.singletonList(lazySystem.getId().toString()));
    }};

    protected final List<LazyEnvironment> lazyEnvironments = Collections.singletonList(lazyEnvironment);

    protected static final LazyProject lazyProject = new LazyProject() {{
        setName(projectName);
        setId(projectId);
    }};

    @Autowired
    protected ImportInfoRepository importInfoRepository;

    @Autowired
    protected TestDataService testDataService;

    @Autowired
    protected StatisticsService statisticsService;

    @Autowired
    protected CatalogRepository catalogRepository;

    @Autowired
    protected TestDataTableRepository testDataTableRepository;

    @Autowired
    protected CleanupService cleanupService;

    @Autowired
    protected ColumnRepository columnRepository;

    @Autowired
    protected TestDataColumnFlagsRepository flagsRepository;

    @Autowired
    protected ProjectInformationService projectInformationService;

    @Autowired
    protected LockManager lockManager;

    @MockBean
    protected EnvironmentsService environmentsService;

    @MockBean
    protected GitService gitEnvironmentsService;

    @MockBean
    protected CurrentTime currentTime;

    protected TestDataTableCatalog createTestDataTableCatalog(UUID projectId, UUID systemId, UUID environmentId,
                                                              String tableTitle, String tableName, String importQuery) {
        TestDataTableCatalog catalog = createTestDataTableCatalog(projectId, systemId, environmentId,
                tableTitle, tableName);
        catalog.setImportQuery(importQuery);
        importInfoRepository.save(new TestDataTableImportInfo(tableName, importQuery, 30));
        return catalogRepository.save(catalog);
    }

    protected TestDataTableCatalog createTestDataTableCatalog(UUID projectId, UUID systemId, UUID environmentId,
                                                              String tableTitle, String tableName) {
        TestDataTableCatalog catalog = new TestDataTableCatalog();
        catalog.setProjectId(projectId);
        catalog.setSystemId(systemId);
        catalog.setEnvironmentId(environmentId);
        catalog.setTableTitle(tableTitle);
        catalog.setTableName(tableName);
        return catalogRepository.save(catalog);
    }

    protected TestDataTable createTestDataTable(String tableName) {
        return testDataTableRepository.saveTestData(tableName, false, buildTestDataTable());
    }

    protected TestDataTable createTestDataTable(String tableName, boolean createTableCatalog) {
        if (createTableCatalog) {
            createTestDataTableCatalog(projectId, systemId, environmentId, tableName, tableName);
        }
        return createTestDataTable(tableName);
    }

    protected TestDataTable createSmallTestDataTable(String tableName) {
        return testDataTableRepository.saveTestData(tableName, false, buildSmallTestDataTable());
    }

    protected List<TestDataTableColumn> createTestDataTableColumns(String tableName) {
        List<String> columns = Arrays.asList("sim", "Status", "Partner", "Partner category",
                "Partner ID", "Operator ID", "environment", "Assignment");
        return columnRepository.saveAll(getTestDataTableColumnsByColumnsAndTableName(columns, tableName));
    }

    protected void deleteTestDataTable(String tableName) {
        testDataTableRepository.dropTable(tableName);
    }

    protected void deleteTestDataTableIfExists(String tableName) {
        try {
            testDataTableRepository.dropTable(tableName);
        } catch (BadSqlGrammarException e) {
            log.warn("Table:[{}] does not exist", tableName, e);
        }
    }

    protected List<UUID> extractRowIds(List<Map<String, Object>> data) {
        return data.stream()
                .map(row -> UUID.fromString(String.valueOf(row.get("ROW_ID"))))
                .collect(toList());
    }

    protected File getResourcesFile(String fileName) {
        return new File(RESOURCES_PATH + fileName);
    }

    protected MultipartFile toMultipartFile(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        return new MockMultipartFile("file",
                file.getName(), "application/vnd.ms-excel", IOUtils.toByteArray(input));
    }

    protected TestDataTable buildTestDataTable() {
        List<String> columns = Arrays.asList("sim", "Status", "Partner", "Partner category",
                "Partner ID", "Operator ID", "environment", "Assignment");
        TestDataTable table = new TestDataTable();
        table.setColumns(getTestDataTableColumns(columns));
        table.setData(getTestDataTableData(columns));
        table.setRecords(6);
        return table;
    }

    protected TestDataFlagsTable createFlagsTable(String tableName) {
        return flagsRepository.save(new TestDataFlagsTable(tableName, false));
    }

    private TestDataTable buildSmallTestDataTable() {
        List<String> columns = Arrays.asList("sim", "Assignment");
        TestDataTable table = new TestDataTable();
        table.setColumns(getTestDataTableColumns(columns));
        table.setData(getTestDataTableData(columns));
        table.setRecords(6);
        return table;
    }

    protected TestDataCleanupConfig createSqlCleanupConfig(TestDataTableCatalog table, boolean shared) throws Exception {
        TestDataCleanupConfig cleanupConfig = createTestDataDateCleanupConfig();
        cleanupConfig.setType(CleanupType.SQL);
        cleanupConfig.setSearchSql("select * from test_data_table_catalog where table_title = "
                + "${'Partner'}");
        cleanupConfig.setShared(shared);
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setTableName(table.getTableName());
        cleanupSettings.setEnvironmentsList(Collections.singletonList(table.getEnvironmentId()));
        return cleanupService.saveCleanupConfig(cleanupSettings).getTestDataCleanupConfig();
    }

    protected TestDataCleanupConfig createErrorSqlCleanupConfig(TestDataTableCatalog table, boolean shared) throws Exception {
        TestDataCleanupConfig cleanupConfig = createTestDataDateCleanupConfig();
        cleanupConfig.setType(CleanupType.SQL);
        cleanupConfig.setSearchSql("select * from test_data_table_catalog where table_title = "
                + "${'PartnerR'}");
        cleanupConfig.setShared(shared);
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setTableName(table.getTableName());
        cleanupSettings.setEnvironmentsList(Collections.singletonList(table.getEnvironmentId()));
        return cleanupService.saveCleanupConfig(cleanupSettings).getTestDataCleanupConfig();
    }

    protected TestDataCleanupConfig createSqlCleanupConfig(String tableName, List<UUID> envIds) throws Exception {
        TestDataCleanupConfig cleanupConfig = createTestDataDateCleanupConfig();
        cleanupConfig.setType(CleanupType.SQL);
        cleanupConfig.setSearchSql("select * from test_data_table_catalog where table_title = "
                + "${'Partner'}");
        cleanupConfig.setShared(false);
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setTableName(tableName);
        cleanupSettings.setEnvironmentsList(envIds);
        return cleanupService.saveCleanupConfig(cleanupSettings).getTestDataCleanupConfig();
    }

    protected TestDataCleanupConfig createDateCleanupConfig(TestDataTableCatalog table) throws Exception {
        TestDataCleanupConfig cleanupConfig = createTestDataDateCleanupConfig();
        cleanupConfig.setType(CleanupType.DATE);
        cleanupConfig.setSearchDate("3w 4d");
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setEnvironmentsList(Collections.singletonList(table.getEnvironmentId()));
        cleanupSettings.setTableName(table.getTableName());
        return cleanupService.saveCleanupConfig(cleanupSettings).getTestDataCleanupConfig();
    }

    protected TestDataCleanupConfig createDateCleanupConfigToDay(TestDataTableCatalog table) throws Exception {
        TestDataCleanupConfig cleanupConfig = createTestDataDateCleanupConfig();
        cleanupConfig.setType(CleanupType.DATE);
        cleanupConfig.setSearchDate("0w 0d");
        CleanupSettings cleanupSettings = new CleanupSettings();
        cleanupSettings.setTestDataCleanupConfig(cleanupConfig);
        cleanupSettings.setEnvironmentsList(Collections.singletonList(table.getEnvironmentId()));
        cleanupSettings.setTableName(table.getTableName());
        return cleanupService.saveCleanupConfig(cleanupSettings).getTestDataCleanupConfig();
    }

    protected Map<String, Object> createTestDataTableRow(String tableName) {
        Map<String, Object> row = new HashMap<>();
        row.put("Status", "51");
        row.put("Partner ID", "1");
        row.put("Operator ID", "2501");
        row.put("ROW_ID", testDataService.getTestData(tableName).getData().get(0).get("ROW_ID"));
        row.put("SELECTED", false);
        row.put("Assignment", "Test Automation' 1");
        row.put("OCCUPIED_BY", null);
        row.put("sim", "8901260720040140811");
        row.put("CREATED_WHEN", testDataService.getTestData(tableName).getData().get(0).get("CREATED_WHEN"));
        row.put("Partner category", "MVNO");
        row.put("environment", "ZLAB01");
        row.put("OCCUPIED_DATE", null);
        row.put("Partner", "CINTEX");
        return row;
    }

    private TestDataCleanupConfig createTestDataDateCleanupConfig() {
        TestDataCleanupConfig cleanup = new TestDataCleanupConfig();
        cleanup.setEnabled(true);
        cleanup.setSchedule("0 0/1 * * * ?");
        cleanup.setShared(false);
        cleanup.setQueryTimeout(30);
        return cleanup;
    }

    private List<Map<String, Object>> getTestDataTableData(List<String> columns) {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(buildTestDataTableRow(columns, Arrays.asList("8901260720040140811", "51", "CINTEX",
                "MVNO", "1", "2501", "ZLAB01", "Test Automation' 1")));
        data.add(buildTestDataTableRow(columns, Arrays.asList("8901260720040140822", "52", "CINTEX",
                "MVNO", "2", "2502", "ZLAB02", "TeSt AutoMatioN 2")));
        data.add(buildTestDataTableRow(columns, Arrays.asList("8901260720040140973", "53", "CINTEX",
                "MVNO", "3", "2503", "ZLAB03", "Test Automation 3")));
        data.add(buildTestDataTableRow(columns, Arrays.asList("8901260720040141084", "54", "CINTEX",
                "MVNO", "4", "2504", "ZLAB04", "Test Automation 4")));
        data.add(buildTestDataTableRow(columns, Arrays.asList("8901260720040140975", "55", "CINTEX",
                "MVNO", "5", "2505", "ZLAB05", "Test Automation 5")));
        data.add(buildTestDataTableRow(columns, Arrays.asList("8901260720040141106", "56", "CINTEX",
                "MVNO", "6", "2506", "ZLAB06", "Test Automation 6")));
        return data;
    }

    private List<TestDataTableColumn> getTestDataTableColumns(List<String> columnNames) {
        return getTestDataTableColumnsByColumnsAndTableName(columnNames, "");
    }

    private List<TestDataTableColumn> getTestDataTableColumnsByColumnsAndTableName(List<String> columnNames,
                                                                                   String tableName) {
        return columnNames.stream()
                .map(columnName -> new TestDataTableColumn(new TestDataTableColumnIdentity(tableName, columnName),
                        ColumnType.TEXT, FilterType.TEXT, "link", false))
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildTestDataTableRow(List<String> columnNames, List<String> values) {
        return IntStream.range(0, columnNames.size()).boxed()
                .collect(Collectors.toMap(columnNames::get, values::get));
    }

    protected void mockEnvironmentService(List<UUID> envs, UUID systemId1, UUID systemId2) {
        LazySystem lazySystem1 = new LazySystem();
        lazySystem1.setEnvironmentIds(Collections.singletonList(envs.get(0)));
        lazySystem1.setId(systemId1);
        lazySystem1.setName("ENV");
        LazySystem lazySystem2 = new LazySystem();
        List<LazySystem> lazySystemList = new ArrayList<>();
        lazySystemList.add(lazySystem1);
        if (envs.size() > 1) {
            lazySystem2.setEnvironmentIds(Collections.singletonList(envs.get(1)));
            lazySystem2.setId(systemId2);
            lazySystem2.setName("ENV");
            lazySystemList.add(lazySystem2);
        }
        when(environmentsService.getLazySystemById(any(), any())).thenReturn(lazySystem1);
        when(environmentsService.getLazySystemsByProjectWithEnvIds(any())).thenReturn(lazySystemList);
    }
}
