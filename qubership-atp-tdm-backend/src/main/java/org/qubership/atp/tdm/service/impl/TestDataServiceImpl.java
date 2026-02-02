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

import static org.qubership.atp.tdm.utils.DateFormatters.FULL_DATE_FORMATTER;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.crypt.api.Decryptor;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.env.configurator.model.AbstractConfiguratorModel;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.Environment;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.model.Server;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.env.configurator.service.GitService;
import org.qubership.atp.tdm.exceptions.internal.TdmEnvironmentSystemException;
import org.qubership.atp.tdm.exceptions.internal.TdmRetrieveTestDataException;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchDataByCriteriaException;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchTableException;
import org.qubership.atp.tdm.mdc.MdcField;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.ColumnValues;
import org.qubership.atp.tdm.model.DropResults;
import org.qubership.atp.tdm.model.EnvsList;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.model.TestDataOccupyStatistic;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.TestDataTableImportInfo;
import org.qubership.atp.tdm.model.ei.TdmDataToExport;
import org.qubership.atp.tdm.model.scheduler.CleanRemovingHistoryJob;
import org.qubership.atp.tdm.model.scheduler.TableCleanerJob;
import org.qubership.atp.tdm.model.statistics.DateStatistics;
import org.qubership.atp.tdm.model.statistics.DateStatisticsItem;
import org.qubership.atp.tdm.model.table.TableColumnValues;
import org.qubership.atp.tdm.model.table.TestDataFlagsTable;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.model.table.conditions.search.SearchConditionType;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.ImportInfoRepository;
import org.qubership.atp.tdm.repo.ProjectInformationRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.repo.impl.SystemColumns;
import org.qubership.atp.tdm.service.CleanupService;
import org.qubership.atp.tdm.service.ColumnService;
import org.qubership.atp.tdm.service.DataRefreshService;
import org.qubership.atp.tdm.service.SchedulerService;
import org.qubership.atp.tdm.service.StatisticsService;
import org.qubership.atp.tdm.service.TestDataFlagsService;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.utils.DataUtils;
import org.qubership.atp.tdm.utils.TestDataTableConvertor;
import org.qubership.atp.tdm.utils.TestDataUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TestDataServiceImpl implements TestDataService {

    private static final List<String> INTERNAL_COLUMNS = new ArrayList<>(Arrays.asList(
            "OCCUPIED_DATE", "ROW_ID", "SELECTED", "OCCUPIED_BY"));
    private static final String DB_CONNECTION_NAME = "DB";
    private static final String SCHED_GROUP = "REMOVING_TABLE";
    private static final Pattern COLUMN_PATTERN = Pattern.compile("\\$\\{'([^']+)'}");
    private final String removingCron;
    private final String historyCleanerCron;
    private final Integer defaultQueryTimeout;
    private final CatalogRepository catalogRepository;
    private final TestDataTableRepository testDataTableRepository;
    private final EnvironmentsService environmentsService;
    private final CleanupService cleanupService;
    private final StatisticsService statisticsService;
    private final DataRefreshService dataRefreshService;
    private final ColumnService columnService;
    private final ImportInfoRepository importInfoRepository;
    private final TestDataFlagsService testDataFlagsService;
    private final ProjectInformationRepository projectInformationRepository;
    private final LockManager lockManager;
    private final TdmMdcHelper tdmMdcHelper;
    private final SchedulerService schedulerService;
    private final GitService gitService;

    /**
     * Constructor for TestDataService.
     */
    @Autowired
    public TestDataServiceImpl(@Nonnull CatalogRepository catalogRepository,
                               @Nonnull TestDataTableRepository testDataTableRepository,
                               @Nonnull EnvironmentsService environmentsService,
                               @Nonnull CleanupService cleanupService,
                               @Nonnull StatisticsService statisticsService,
                               @Nonnull DataRefreshService dataRefreshService,
                               @Nonnull ColumnService columnService,
                               @Nonnull ImportInfoRepository importInfoRepository,
                               @Nonnull Decryptor decryptor,
                               @Nonnull TestDataFlagsService testDataFlagsService,
                               @Nonnull ProjectInformationRepository projectInformationRepository,
                               @Nonnull LockManager lockManager,
                               @Nonnull SchedulerService schedulerService,
                               @Value("${external.query.default.timeout:1800}") Integer defaultQueryTimeout,
                               @Value("${table.expiration.cron}") String removingCron,
                               @Value("${clean.removed.tables.history.cron}") String historyCleanerCron,
                               TdmMdcHelper helper,
                               GitService gitService) {
        this.catalogRepository = catalogRepository;
        this.testDataTableRepository = testDataTableRepository;
        this.environmentsService = environmentsService;
        this.cleanupService = cleanupService;
        this.statisticsService = statisticsService;
        this.dataRefreshService = dataRefreshService;
        this.columnService = columnService;
        this.importInfoRepository = importInfoRepository;
        this.testDataFlagsService = testDataFlagsService;
        this.projectInformationRepository = projectInformationRepository;
        this.lockManager = lockManager;
        this.defaultQueryTimeout = defaultQueryTimeout;
        this.tdmMdcHelper = helper;
        this.schedulerService = schedulerService;
        this.removingCron = removingCron;
        this.historyCleanerCron = historyCleanerCron;
        this.gitService = gitService;
    }

    @Override
    public List<TestDataTableCatalog> getTestDataTablesCatalog(@Nonnull UUID projectId, @Nullable UUID systemId) {
        List<TestDataTableCatalog> tableCatalogs;
        if (systemId != null) {
            tableCatalogs = catalogRepository.findAllByProjectIdAndSystemId(projectId, systemId);
        } else {
            tableCatalogs = catalogRepository.findAllByProjectId(projectId);
        }
        return fillImportInformation(tableCatalogs);
    }

    private List<TestDataTableCatalog> fillImportInformation(List<TestDataTableCatalog> tableCatalogs) {
        List<TestDataTableCatalog> testDataTableCatalogs = new ArrayList<>();
        tableCatalogs.forEach(tableCatalog -> {
            TestDataTableImportInfo importInfo = importInfoRepository.findByTableName(tableCatalog.getTableName());
            if (Objects.nonNull(importInfo)) {
                tableCatalog.setImportQuery(importInfo.getTableQuery());
                tableCatalog.setQueryTimeout(importInfo.getQueryTimeout());
            }
            testDataTableCatalogs.add(tableCatalog);
        });
        return testDataTableCatalogs;
    }

    @Override
    public TdmDataToExport tablesToExport(@Nonnull UUID projectId) {
        List<String> tableIds = catalogRepository.findAllByProjectId(projectId)
                .stream()
                .map(TestDataTableCatalog::getTableName).collect(Collectors.toList());
        return new TdmDataToExport(projectId, tableIds);
    }

    @Override
    public Map<String, String> tablesToExportByEnvironment(@Nonnull UUID projectId, @Nonnull UUID environmentId) {
        return catalogRepository.findAllByProjectIdAndEnvironmentId(projectId, environmentId)
                .stream()
                .collect(Collectors.toMap(TestDataTableCatalog::getTableName, TestDataTableCatalog::getTableTitle));
    }

    @Override
    public TestDataTable getTestData(@Nonnull String tableName) {
        return testDataTableRepository.getTestData(false, tableName, null, null,
                null, null);
    }

    @Override
    public TestDataTable getTestData(@Nonnull String tableName, @Nullable Integer offset, @Nullable Integer limit,
                                     @Nullable List<TestDataTableFilter> filters,
                                     @Nullable TestDataTableOrder testDataTableOrder,
                                     @Nonnull Boolean isOccupied) {
        testDataTableRepository.updateLastUsage(tableName);
        return testDataTableRepository.getTestData(isOccupied, tableName, offset, limit, filters,
                testDataTableOrder);
    }

    @Override
    public TestDataTable getTestData(@Nonnull String tableName, @Nonnull List<String> columnNames,
                                     @Nullable List<TestDataTableFilter> filters) {
        return testDataTableRepository.getTestData(tableName, columnNames, filters);
    }

    @Override
    public List<ImportTestDataStatistic> importExcelTestData(@Nonnull UUID projectId, @Nullable UUID environmentId,
                                                             @Nullable UUID systemId, @Nonnull String tableTitle,
                                                             @Nonnull Boolean runSqlScriptAfterExcelImport,
                                                             @Nonnull MultipartFile file) {
        log.info("Excel import started. Table title [{}]", tableTitle);
        if (Objects.isNull(environmentId) || Objects.isNull(systemId)) {
            throw new TdmEnvironmentSystemException();
        }
        TestDataTableCatalog tableCatalog = catalogRepository
                .findByProjectIdAndSystemIdAndTableTitle(projectId, systemId, tableTitle);
        String tableName = "";
        ImportTestDataStatistic statistic;
        if (Objects.nonNull(tableCatalog)) {
            tableName = tableCatalog.getTableName();
            statistic = testDataTableRepository.importExcelTestData(tableCatalog.getTableName(), true, file);
            testDataTableRepository.updateLastUsage(tableCatalog.getTableName());
        } else {
            tableName = TestDataTableConvertor.generateTestDataTableName();
            statistic = testDataTableRepository.importExcelTestData(tableName, false, file);
            testDataTableRepository.saveTestDataTableCatalog(tableName, tableTitle, projectId, systemId, environmentId);
            testDataFlagsService.setValidateUnoccupiedResourcesFlag(tableName, false, false);
            testDataTableRepository.updateLastUsage(tableName);
            if (Objects.nonNull(systemId)) {
                columnService.setUpLinks(projectId, systemId, tableTitle, tableName);
            }
        }
        if (runSqlScriptAfterExcelImport) {
            if (Objects.nonNull(environmentId) && Objects.nonNull(systemId)) {
                updateImportedData(projectId, environmentId, systemId, tableName);
            } else {
                String message = "Unable to update table [" + tableName + "]. System or environment not specified.";
                log.info(message);
                statistic = new ImportTestDataStatistic("", message, 0);
            }
        }
        log.info("Excel import successfully finished.");
        return Collections.singletonList(statistic);
    }

    private ImportTestDataStatistic updateImportedData(@Nonnull UUID projectId, @Nullable UUID environmentId,
                                                       @Nullable UUID systemId, @Nonnull String tableName) {
        TestDataTableImportInfo importInfo = importInfoRepository.findByTableName(tableName);
        ImportTestDataStatistic statistic = new ImportTestDataStatistic();
        if (Objects.nonNull(importInfo.getTableQuery())) {
            Integer queryTimeout = ObjectUtils.defaultIfNull(importInfo.getQueryTimeout(), defaultQueryTimeout);
            statistic = updateTestDataBySql(projectId, environmentId, systemId, tableName,
                    importInfo.getTableQuery(), queryTimeout);
        }
        return statistic;
    }

    @Override
    public List<ImportTestDataStatistic> importSqlTestData(@Nonnull UUID projectId,
                                                           @Nonnull List<UUID> environmentsIds,
                                                           @Nonnull String systemName, @Nonnull String tableTitle,
                                                           @Nonnull String query, @Nonnull Integer queryTimeout) {
        log.info("SQL import started. Table title: [{}]", tableTitle);
        List<ImportTestDataStatistic> statistics = new ArrayList<>();
        for (UUID environmentId : environmentsIds) {
            MdcUtils.put(MdcField.ENVIRONMENT_ID.toString(), environmentId);
            log.info("Start SQL import for environment with id: " + environmentId);
            ImportTestDataStatistic statistic = importSqlTestData(projectId, environmentId, systemName, tableTitle,
                    query, queryTimeout);
            statistics.add(statistic);
            MDC.remove(MdcField.ENVIRONMENT_ID.toString());
        }
        log.info("SQL import successfully finished.");
        return statistics;
    }

    private ImportTestDataStatistic importSqlTestData(@Nonnull UUID projectId, @Nonnull UUID environmentId,
                                                      @Nonnull String systemName, @Nonnull String tableTitle,
                                                      @Nonnull String query, @Nonnull Integer queryTimeout) {
        ImportTestDataStatistic statistic = new ImportTestDataStatistic();
        String envName;
        System system;

        try {
            envName = gitService.getEnvNameById(environmentId);
        } catch (Exception e) {
            String message = String.format("Environment: [%s] was not found.", environmentId);
            log.error(message);
            statistic.setError(message);
            statistic.setEnvName(environmentId.toString());
            return statistic;
        }

        try {
            system = gitService.getFullSystemByName(environmentId, systemName);
        } catch (Exception e) {
            String message = String.format("System with name[%s] for environment[%s] "
                    + "was not found.", systemName, environmentId);
            log.error(message);
            statistic.setError(message);
            statistic.setEnvName(envName);
            return statistic;
        }
        try {
            UUID systemId = system.getId();
            Server server = system.getServer(DB_CONNECTION_NAME);
            TestDataTableCatalog tableCatalog = catalogRepository
                    .findByProjectIdAndSystemIdAndTableTitle(projectId, systemId, tableTitle);
            if (tableCatalog != null) {
                statistic = testDataTableRepository
                        .importSqlTestData(tableCatalog.getTableName(), true, query, queryTimeout,
                                server);
                importInfoRepository.save(new TestDataTableImportInfo(tableCatalog.getTableName(), query,
                        queryTimeout));
                testDataTableRepository.updateLastUsage(tableCatalog.getTableName());
            } else {
                String tableName = TestDataTableConvertor.generateTestDataTableName();
                statistic = testDataTableRepository.importSqlTestData(tableName, false, query, queryTimeout,
                        server);
                testDataTableRepository.saveTestDataTableCatalog(tableName, tableTitle, projectId, systemId,
                        environmentId);
                testDataTableRepository.updateLastUsage(tableName);
                testDataFlagsService.setValidateUnoccupiedResourcesFlag(tableName,
                        false, false);
                importInfoRepository.save(new TestDataTableImportInfo(tableName, query, queryTimeout));
                columnService.setUpLinks(projectId, systemId, tableTitle, tableName);
            }
        } catch (Exception e) {
            statistic.setError(e.getMessage());
        }
        statistic.setEnvName(envName);
        return statistic;
    }

    @Override
    public void occupyTestData(@Nonnull String tableName, @Nonnull String occupiedBy, @Nonnull List<UUID> rows) {
        String date = testDataTableRepository.occupyTestData(tableName, occupiedBy, rows);
        TestDataTableCatalog catalog = catalogRepository.findByTableName(tableName);
        testDataTableRepository.updateLastUsage(tableName);
        tdmMdcHelper.putConfigFields(catalog);
        LocalDateTime occupyTime = LocalDateTime.parse(date, FULL_DATE_FORMATTER);
        rows.forEach(row -> {
            LocalDateTime createdTime = LocalDateTime.parse(String.valueOf(getTableRow(tableName, "ROW_ID",
                    row.toString(), true).get("CREATED_WHEN")), FULL_DATE_FORMATTER);
            statisticsService.saveOccupyStatistic(new TestDataOccupyStatistic(row, catalog.getProjectId(),
                    catalog.getSystemId(), tableName, catalog.getTableTitle(), occupiedBy, occupyTime, createdTime));
        });
    }

    @Override
    public void releaseTestData(@Nonnull String tableName, @Nonnull List<UUID> rows) {
        testDataTableRepository.releaseTestData(tableName, rows);
        statisticsService.deleteAllOccupyStatisticByRowId(rows);
    }

    @Override
    public DropResults deleteTestData(@Nonnull String tableName) {
        lockManager.executeWithLockWithUniqueLockKey("delete test data: " + tableName, () -> {
            TestDataTableCatalog catalog = catalogRepository.findByTableName(tableName);
            UUID configId = catalog.getRefreshConfigId();
            if (Objects.nonNull(configId)) {
                dataRefreshService.removeJob(configId);
            }
            try {
                statisticsService.fillCreatedWhenStatistics(tableName, catalog);
            } catch (BadSqlGrammarException e) {
                log.error(e.getMessage(), e);
            }
            catalogRepository.deleteByTableName(tableName);
            testDataTableRepository.dropTable(tableName);
            cleanupService.removeUnused();
            statisticsService.removeUnused();
            testDataFlagsService.deleteRowByTableName(tableName);
            columnService.deleteByTableName(tableName);
            importInfoRepository.deleteByTableName(tableName);
        });
        return new DropResults(tableName);
    }

    @Override
    public DropResults truncateDataInTable(@Nonnull String tableName,
                                           @Nonnull UUID projectId,
                                           UUID systemId) {
        lockManager.executeWithLockWithUniqueLockKey("truncate Data In Table : " + tableName, () -> {
            TestDataTableCatalog catalog = getTestDataTablesCatalog(projectId, systemId)
                    .stream().filter(x -> x.getTableName().equals(tableName)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(tableName + " table not found."));
            tdmMdcHelper.putConfigFields(catalog);
            testDataTableRepository.truncateTable(catalog.getTableName());
            testDataTableRepository.updateLastUsage(catalog.getTableName());
            tdmMdcHelper.removeConfigFields();
        });
        return new DropResults(tableName);
    }

    @Override
    public void deleteTestDataTableRows(@Nonnull String tableName, @Nonnull List<UUID> rows) {
        lockManager.executeWithLockWithUniqueLockKey("deleteTestDataTableRows: " + tableName, () -> {
            TestDataTableCatalog catalog = catalogRepository.findByTableName(tableName);
            statisticsService.fillCreatedWhenStatistics(tableName, catalog, rows);
            testDataTableRepository.deleteRows(tableName, rows);
            testDataTableRepository.updateLastUsage(tableName);
        });
    }

    /**
     * Method to convert TestDataTable to excel file.
     *
     * @param tableName table name.
     * @return Excel file.
     */
    @Override
    public File getTestDataTableAsExcelFile(@Nonnull String tableName) {
        return testDataTableRepository.getTestDataTableAsExcel(tableName, null, null, null);
    }

    /**
     * Method to convert TestDataTable to csv excel file.
     *
     * @param tableName table name.
     * @return csv file.
     */
    @Override
    public File getTestDataTableAsCsvFile(@Nonnull String tableName) {
        return testDataTableRepository.getTestDataTableAsCsv(tableName, null, null, null);
    }

    @Override
    public String getPreviewLink(@Nonnull UUID projectId, @Nullable UUID systemId, @Nullable String endpoint,
                                 @Nonnull String columnName, @Nullable String tableName,
                                 @Nonnull Boolean pickUpFullLinkFromTableCell) {
        if (pickUpFullLinkFromTableCell) {
            return testDataTableRepository.getFirstRecordFromDataStorageTable(tableName, columnName);
        } else {
            return columnService.getColumnLink(projectId, systemId, endpoint);
        }
    }

    /**
     * Returns a list of environment IDs that have tables with the requested table title for the specified project.
     *
     * @return list of environment ids.
     */
    @Override
    public EnvsList getTableEnvironments(@Nonnull UUID projectId, @Nonnull String tableTitle) {
        List<TestDataTableCatalog> tableCatalogs = catalogRepository.findAllByProjectIdAndTableTitle(
                projectId, tableTitle);
        List<UUID> environmentIds = tableCatalogs.stream().map(
                TestDataTableCatalog::getEnvironmentId).distinct().collect(Collectors.toList());
        return new EnvsList(environmentIds);
    }

    @Override
    public void alterOccupiedByColumn() {
        testDataTableRepository.alterOccupiedByColumn(catalogRepository.findAll().stream()
                .map(TestDataTableCatalog::getTableName)
                .collect(Collectors.toList()));
    }

    @Override
    public void setupColumnLinks(@Nonnull Boolean isAll, @Nonnull UUID projectId, @Nonnull UUID systemId,
                                 @Nonnull String tableName, @Nonnull String columnName, @Nonnull String endpoint,
                                 @Nonnull Boolean validateUnoccupiedResources,
                                 @Nonnull Boolean pickUpFullLinkFromTableCell) {
        lockManager.executeWithLockWithUniqueLockKey("set up column links: " + tableName, () -> {
            columnService.setupColumnLinks(isAll, projectId, systemId, tableName,
                    columnName, endpoint, pickUpFullLinkFromTableCell);
            testDataTableRepository.updateLastUsage(tableName);
            testDataFlagsService.setValidateUnoccupiedResourcesFlag(tableName, validateUnoccupiedResources, isAll);
        });
    }

    @Override
    public void deleteProjectFromCatalogue(UUID projectId) {
        catalogRepository.findAllByProjectId(projectId).forEach(
                table -> {
                    testDataTableRepository.dropTable(table.getTableName());
                    catalogRepository.delete(table);
                    log.info("Links on table '{}' was deleted from catalogue.", table.getTableName());
                });
        projectInformationRepository.deleteById(projectId);
    }

    @Override
    public void alterCreatedWhenColumn() {
        testDataTableRepository.alterCreatedWhenColumn(catalogRepository.findAll().stream()
                .map(TestDataTableCatalog::getTableName)
                .collect(Collectors.toList()));
    }

    @Override
    public void fillEnvIdColumn() {
        Map<UUID, Project> projects = new HashMap<>();
        List<TestDataTableCatalog> tablesCatalog = catalogRepository.findAll();
        tablesCatalog.forEach(catalog -> {
            log.info(catalog.getTableName() + " " + catalog.getTableTitle());
            Project project = null;
            UUID projectId = catalog.getProjectId();
            if (Objects.nonNull(catalog.getSystemId())) {
                if (projects.containsKey(projectId)) {
                    project = projects.get(projectId);
                    log.info("Get project from storage by id: {}", projectId);
                } else {
                    log.info("Load project by id: {} from env service", projectId);
                    try {
                        project = environmentsService.getFullProject(projectId);
                        projects.put(projectId, project);
                    } catch (Exception e) {
                        if (e.getCause().getMessage().contains("not found")) {
                            log.info("Project with id: {} was not loaded.", projectId);
                            log.info("Delete table: {}", catalog);
                            deleteTestData(catalog.getTableName());
                        } else {
                            log.error("Error loading project by id: {}", projectId, e);
                        }
                    }
                }
                if (Objects.nonNull(project)) {
                    Optional<Environment> environment = project.getEnvironments().stream()
                            .filter(env -> env.getSystems().stream().map(AbstractConfiguratorModel::getId)
                                    .collect(Collectors.toList())
                                    .contains(catalog.getSystemId())).findFirst();
                    environment.ifPresent(rnv -> catalog.setEnvironmentId(rnv.getId()));
                }
            }
        });
        catalogRepository.saveAll(tablesCatalog);
    }

    @Override
    public String evaluateQuery(@Nonnull String tableName, @Nonnull String query) {
        testDataTableRepository.updateLastUsage(tableName);
        return testDataTableRepository.evaluateQuery(tableName, query);
    }

    @Override
    public ColumnValues getColumnDistinctValues(@Nonnull String tableName, @Nonnull String columnName,
                                                Boolean occupied) {
        return testDataTableRepository.getColumnDistinctValues(tableName, columnName, occupied);
    }

    @Override
    public DateStatistics getTableByCreatedWhen(@Nonnull List<TestDataTableCatalog> catalogList,
                                                @Nonnull LocalDate dateFrom,
                                                @Nonnull LocalDate dateTo) {
        DateStatistics dateStatistics = new DateStatistics();
        List<DateStatisticsItem> listStatisticsItems = new ArrayList<>();
        dateStatistics.setDates(DataUtils.getStatisticsInterval(dateFrom, dateTo));
        catalogList.forEach(catalog -> {
            TestDataTable table = testDataTableRepository.getTableByCreatedWhen(catalog.getTableName(),
                    dateFrom, dateTo);
            LocalDate iterDate = dateFrom;
            DateStatisticsItem statisticsItem = new DateStatisticsItem(catalog.getTableTitle());
            List<Long> created = new ArrayList<>();
            long count;
            switch (DataUtils.statisticsInterval) {
                case YEARS:
                    do {
                        count = 0L;
                        for (Map<String, Object> row : table.getData()) {
                            LocalDate createdWhen = LocalDateTime
                                    .parse(
                                            row.get(SystemColumns.CREATED_WHEN.getName()).toString(),
                                            FULL_DATE_FORMATTER
                                    )
                                    .toLocalDate();
                            if (iterDate.getYear() == createdWhen.getYear()) {
                                count++;
                            }
                        }
                        created.add(count);
                        iterDate = iterDate.plusYears(1);
                    } while (!iterDate.isAfter(dateTo));
                    break;
                case WEEKS:
                    do {
                        count = 0L;
                        for (Map<String, Object> row : table.getData()) {
                            LocalDate createdWhen =
                                    LocalDateTime.parse(
                                                    row.get(SystemColumns.CREATED_WHEN.getName()).toString(),
                                                    FULL_DATE_FORMATTER
                                            )
                                            .toLocalDate();
                            if (iterDate.getYear() == createdWhen.getYear()
                                    && iterDate.getMonth() == createdWhen.getMonth()
                                    && (createdWhen.isEqual(iterDate) || createdWhen.isAfter(iterDate))
                                    && createdWhen.isBefore(iterDate.plusWeeks(1))) {
                                count++;
                            }
                        }
                        created.add(count);
                        iterDate = iterDate.plusWeeks(1);
                    } while (!iterDate.isAfter(dateTo));
                    break;
                case DAYS:
                    do {
                        count = 0L;
                        for (Map<String, Object> row : table.getData()) {
                            LocalDate createdWhen = LocalDateTime.parse(
                                            row.get(SystemColumns.CREATED_WHEN.getName()).toString(),
                                            FULL_DATE_FORMATTER
                                    )
                                    .toLocalDate();
                            if (iterDate.getYear() == createdWhen.getYear()
                                    && iterDate.getMonth() == createdWhen.getMonth()
                                    && iterDate.getDayOfMonth() == createdWhen.getDayOfMonth()) {
                                count++;
                            }
                        }
                        created.add(count);
                        iterDate = iterDate.plusDays(1);
                    } while (!iterDate.isAfter(dateTo));
                    break;
                default:
                    do {
                        count = 0L;
                        for (Map<String, Object> row : table.getData()) {
                            LocalDate createdWhen = LocalDateTime.parse(
                                            row.get(SystemColumns.CREATED_WHEN.getName()).toString(),
                                            FULL_DATE_FORMATTER
                                    )
                                    .toLocalDate();
                            if (iterDate.getYear() == createdWhen.getYear()
                                    && iterDate.getMonth() == createdWhen.getMonth()) {
                                count++;
                            }
                        }
                        created.add(count);
                        iterDate = iterDate.plusMonths(1);
                    } while (!iterDate.isAfter(dateTo));
                    break;
            }
            UUID system = catalog.getSystemId();
            if (system != null) {
                statisticsItem.setSystem(system.toString());
            }
            statisticsItem.setCreated(created);
            listStatisticsItems.add(statisticsItem);
        });
        listStatisticsItems.sort(Comparator.comparing(DateStatisticsItem::getContext));
        dateStatistics.setItems(listStatisticsItems);
        return dateStatistics;
    }

    @Override
    public Map<String, Object> getTableRow(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle,
                                           @Nonnull String columnName, @Nonnull String searchValue,
                                           boolean occupied) {
        log.info("Starting search for table {} under project {} and system {}", tableTitle, projectId, systemId);
        TestDataTableCatalog catalog =
                catalogRepository.findByProjectIdAndSystemIdAndTableTitle(projectId, systemId, tableTitle);
        if (Objects.isNull(catalog)) {
            throw new TdmSearchTableException(tableTitle, projectId.toString(), systemId.toString());
        }
        List<TestDataTableFilter> filters = new ArrayList<>();
        TestDataTableFilter filter = new TestDataTableFilter(columnName, SearchConditionType.EQUALS.toString(),
                Collections.singletonList(searchValue), true);
        filters.add(filter);
        TestDataTable table;
        try {
            table = testDataTableRepository.getTestData(occupied, catalog.getTableName(),
                    null, null, filters, null);
            testDataTableRepository.updateLastUsage(catalog.getTableName());
        } catch (Exception e) {
            if (Objects.isNull(systemId)) {
                log.error(String.format("Error while retrieving test data from table %s under project %s.",
                        tableTitle, projectId), e);
                throw new TdmRetrieveTestDataException(tableTitle, projectId.toString());
            } else {
                log.error(String.format("Error while retrieving test data from table %s under project %s and system %s",
                        tableTitle, projectId, systemId), e);
                throw new TdmRetrieveTestDataException(tableTitle, projectId.toString(), systemId.toString());
            }
        }
        Optional<Map<String, Object>> row = table.getData().stream().findFirst();
        if (row.isPresent()) {
            log.info("Successfully retrieved row from table {} under project {} and system {}.", tableTitle,
                    projectId, systemId);
            return row.get();
        } else {
            throw new TdmSearchDataByCriteriaException(tableTitle, projectId.toString(), systemId.toString());
        }
    }

    @Override
    public Map<String, Object> getTableRow(@Nonnull String tableName, @Nonnull String columnName,
                                           @Nonnull String searchValue, boolean occupied) {
        log.info("Starting search for table {}", tableName);
        TestDataTableCatalog catalog = catalogRepository.findByTableName(tableName);
        if (Objects.isNull(catalog)) {
            throw new TdmSearchTableException(tableName);
        }
        List<TestDataTableFilter> filters = new ArrayList<>();
        TestDataTableFilter filter = new TestDataTableFilter(columnName, SearchConditionType.EQUALS.toString(),
                Collections.singletonList(searchValue), true);
        filters.add(filter);
        TestDataTable table;
        try {
            table = testDataTableRepository.getTestData(occupied, catalog.getTableName(),
                    null, null, filters, null);
        } catch (Exception e) {
            log.error(String.format("Error while retrieving test data from table %s", tableName), e);
            throw new TdmRetrieveTestDataException(tableName);
        }
        Optional<Map<String, Object>> row = table.getData().stream().findFirst();
        if (row.isPresent()) {
            log.info("Successfully retrieved row from table {}.", tableName);
            return row.get();
        } else {
            throw new TdmSearchDataByCriteriaException(tableName);
        }
    }

    @Override
    public boolean changeTestDataTitle(@Nonnull String tableName, @Nullable String tableTitle) {
        boolean updated =
                testDataTableRepository.changeTestDataTitle(tableName, tableTitle);
        if (updated && tableTitle!=null ) {
                statisticsService.updateOccupiedStatisticsTableTitle(tableName, tableTitle);
        }
        return updated;
    }

    @Override
    public void alterOccupyStatistic() {
        List<TestDataTableCatalog> tablesCatalog = catalogRepository.findAll();
        tablesCatalog.forEach(catalog -> {
            tdmMdcHelper.putConfigFields(catalog);
            try {
                TestDataTable table = testDataTableRepository.getTestData(true, catalog.getTableName(),
                        null, null, null, null);
                if (table.getData().size() > 0) {
                    List<Map<String, Object>> rows = table.getData();
                    for (Map<String, Object> row : rows) {
                        log.debug("Processing row #{} from table {}", row.get("ROW_ID"), catalog.getTableName());
                        if (Objects.nonNull(row.get("OCCUPIED_BY"))) {
                            String dateOccupied = String.valueOf(row.get("OCCUPIED_DATE"));
                            String dateCreated = String.valueOf(row.get("CREATED_WHEN"));
                            LocalDateTime occupyTime = LocalDateTime.parse(dateOccupied, FULL_DATE_FORMATTER);
                            LocalDateTime createTime = LocalDateTime.parse(dateCreated, FULL_DATE_FORMATTER);
                            statisticsService.saveOccupyStatistic(
                                    new TestDataOccupyStatistic(UUID.fromString(row.get("ROW_ID").toString()),
                                            catalog.getProjectId(), catalog.getSystemId(), catalog.getTableName(),
                                            catalog.getTableTitle(), String.valueOf(row.get("OCCUPIED_BY")),
                                            occupyTime, createTime));
                        }
                    }
                }
            } catch (BadSqlGrammarException e) {
                log.error("Table with name {} does not exist.", catalog.getTableName());
            } finally {
                tdmMdcHelper.removeConfigFields();
            }
        });
    }

    @Override
    public ImportTestDataStatistic updateTestDataBySql(@Nonnull UUID projectId, @Nonnull UUID environmentId,
                                                       @Nonnull UUID systemId, @Nonnull String tableName,
                                                       @Nonnull String query, @Nonnull Integer queryTimeout) {
        List<Connection> connections = environmentsService.getConnectionsSystemById(environmentId, systemId);
        TestDataTableImportInfo importInfo = importInfoRepository.findByTableName(tableName);
        final ImportTestDataStatistic[] statistic = new ImportTestDataStatistic[1];
        lockManager.executeWithLockWithUniqueLockKey("update table by sql " + tableName, () -> {
            if (importInfo != null) {
                importInfo.setQueryTimeout(queryTimeout);
                importInfo.setUpdateByQuery(query);
                importInfoRepository.save(importInfo);
                testDataTableRepository.updateLastUsage(tableName);
            } else {
                importInfoRepository.save(new TestDataTableImportInfo(tableName, null, queryTimeout, query));
                testDataTableRepository.updateLastUsage(tableName);
            }
            statistic[0] = testDataTableRepository.updateTableBySql(tableName, query, queryTimeout,
                    TestDataUtils.getServer(connections, DB_CONNECTION_NAME));
            statistic[0].setEnvName(environmentsService.getEnvNameById(environmentId));
        });
        return statistic[0];
    }

    @Override
    public TestDataFlagsTable getUnoccupiedValidationFlagStatus(@Nonnull String tableName) {
        return testDataFlagsService.getValidateUnoccupiedResourcesFlag(tableName);
    }

    @Override
    public void resolveDiscrepancyTestDataFlagsTableAndTestDataTableCatalog() {
        testDataTableRepository.getTestDataTableCatalogDiscrepancyTestDataFlagsTable()
                .forEach(row -> testDataFlagsService.setValidateUnoccupiedResourcesFlag(row,
                        false, false));
        testDataTableRepository.getTestDataFlagsTableDiscrepancyTestDataTableCatalog()
                .forEach(testDataFlagsService::deleteRowByTableName);
    }

    /**
     * Schedule.
     */
    public void schedule() {
        JobDetail cleanerJob = JobBuilder.newJob(TableCleanerJob.class)
                .withIdentity(SCHED_GROUP + "_delete_tables")
                .build();
        Trigger cleanerTrigger = Optional.of(TriggerBuilder.newTrigger()
                        .withIdentity(SCHED_GROUP + "_delete_tables"))
                .map(builder -> builder.withSchedule(CronScheduleBuilder.cronSchedule(removingCron)))
                .get()
                .build();
        schedulerService.reschedule(cleanerJob, cleanerTrigger, true);

        JobDetail historyCleanerJob = JobBuilder.newJob(CleanRemovingHistoryJob.class)
                .withIdentity(SCHED_GROUP + "_clean_history")
                .build();
        Trigger historyCleanerTrigger = Optional.of(TriggerBuilder.newTrigger()
                        .withIdentity(SCHED_GROUP + "_clean_history"))
                .map(builder -> builder.withSchedule(CronScheduleBuilder.cronSchedule(historyCleanerCron)))
                .get()
                .build();
        schedulerService.reschedule(historyCleanerJob, historyCleanerTrigger, true);
    }

    @Override
    public List<TableColumnValues> getDistinctTablesColumnValues(@Nonnull UUID systemId, @Nonnull UUID environmentId,
                                                                 @Nonnull String columnName) {
        List<TestDataTableCatalog> tableCatalogs = catalogRepository
                .findAllByEnvironmentIdAndSystemId(environmentId, systemId);
        List<String> tablesWithColumn = testDataTableRepository
                .getTablesBySystemIdAndExistingColumn(systemId, environmentId, columnName);
        List<TableColumnValues> columnValues = new ArrayList<>();
        for (String tableName : tablesWithColumn) {
            TableColumnValues columnValue = new TableColumnValues();
            columnValue.setTableName(tableName);
            columnValue.setValues(testDataTableRepository
                    .getColumnDistinctValues(tableName, columnName, null).getItems());
            columnValue.setTableTitle(tableCatalogs
                    .stream()
                    .filter(table -> table.getTableName().equalsIgnoreCase(tableName))
                    .map(TestDataTableCatalog::getTableTitle).findFirst().orElse(""));
            columnValues.add(columnValue);
        }
        return columnValues;
    }

    @Override
    public List<String> getAllColumnNamesBySystemId(@Nonnull UUID systemId) {
        List<String> allColumnsBySystemId = testDataTableRepository.getAllColumnNamesBySystemId(systemId);
        allColumnsBySystemId.removeAll(INTERNAL_COLUMNS);
        return allColumnsBySystemId;
    }
}
