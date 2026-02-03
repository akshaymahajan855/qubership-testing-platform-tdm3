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

import java.sql.Connection;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.CronExpression;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Server;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.TdmRunCleanupException;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchCleanupConfigException;
import org.qubership.atp.tdm.exceptions.internal.TdmUndefinedCleanupCriteriaException;
import org.qubership.atp.tdm.mdc.MdcField;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.model.cleanup.CleanupSettings;
import org.qubership.atp.tdm.model.cleanup.CleanupType;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;
import org.qubership.atp.tdm.model.cleanup.cleaner.TestDataCleaner;
import org.qubership.atp.tdm.model.cleanup.cleaner.impl.SqlTestDataCleaner;
import org.qubership.atp.tdm.model.scheduler.DataCleanupJob;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.CleanupConfigRepository;
import org.qubership.atp.tdm.repo.ImportInfoRepository;
import org.qubership.atp.tdm.repo.SqlRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.CleanupService;
import org.qubership.atp.tdm.service.SchedulerService;
import org.qubership.atp.tdm.utils.DataUtils;
import org.qubership.atp.tdm.utils.ValidateCronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CleanupServiceImpl implements CleanupService {

    private static final String SCHEDULE_GROUP = "cleanup";
    private final EnvironmentsService environmentsService;
    private final SchedulerService schedulerService;
    private final CleanupConfigRepository cleanupConfigRepository;
    private final TestDataTableRepository testDataTableRepository;
    private final CatalogRepository catalogRepository;
    private final SqlRepository sqlRepository;
    private final ImportInfoRepository importInfoRepository;
    private final MetricService metricService;
    private final TdmMdcHelper tdmMdcHelper;
    private final Map<String, Class<? extends TestDataCleaner>> classMethodWhiteList = new HashMap<>();
    @Value("${external.query.max.timeout:3600}")
    private Integer maxQueryTimeout;
    @Value("${external.query.default.timeout:1800}")
    private Integer defaultQueryTimeout;

    /**
     * Default constructor.
     */
    @Autowired
    public CleanupServiceImpl(@Nonnull EnvironmentsService environmentsService,
                              @Nonnull SchedulerService schedulerService,
                              @Nonnull CleanupConfigRepository repository,
                              @Nonnull TestDataTableRepository testDataTableRepository,
                              @Nonnull CatalogRepository catalogRepository,
                              @Nonnull SqlRepository sqlRepository,
                              @Nonnull ImportInfoRepository importInfoRepository,
                              @Nonnull MetricService metricService,
                              TdmMdcHelper helper,
                              List<TestDataCleaner> implementations) {
        this.environmentsService = environmentsService;
        this.schedulerService = schedulerService;
        this.cleanupConfigRepository = repository;
        this.testDataTableRepository = testDataTableRepository;
        this.catalogRepository = catalogRepository;
        this.sqlRepository = sqlRepository;
        this.importInfoRepository = importInfoRepository;
        this.metricService = metricService;
        tdmMdcHelper = helper;
        for (TestDataCleaner impl : implementations) {
            classMethodWhiteList.put(impl.getClass().getSimpleName(), impl.getClass());
        }
    }

    /**
     * Get cleanup configuration object for specified dataset / table.
     *
     * @param id - cleanup config name
     * @return cleanup configuration object
     */
    @Override
    public TestDataCleanupConfig getCleanupConfig(@Nonnull UUID id) {
        return cleanupConfigRepository.findById(id)
                .orElseThrow(() -> new TdmSearchCleanupConfigException(id.toString()));
    }

    @Override
    public CleanupSettings getCleanupSettings(@Nonnull UUID id) {
        TestDataCleanupConfig cleanupConfig = getCleanupConfig(id);
        List<UUID> envId = catalogRepository.findAllByCleanupConfigId(id).stream()
                .map(TestDataTableCatalog::getEnvironmentId)
                .collect(Collectors.toList());
        return new CleanupSettings(cleanupConfig, envId, null);
    }

    @Override
    public CleanupSettings saveCleanupConfig(@Nonnull CleanupSettings cleanupSettings) throws Exception {
        log.info("Saving cleanup for table with name: {}", cleanupSettings.getTableName());
        TestDataCleanupConfig config = cleanupSettings.getTestDataCleanupConfig();
        Preconditions.checkArgument(StringUtils.isNotEmpty(cleanupSettings.getTableName()), "Table Name is null");
        Preconditions.checkArgument(
                config.getQueryTimeout() != null
                        && config.getQueryTimeout() > 0
                        && config.getQueryTimeout() <= maxQueryTimeout,
                "The timeout is not within the allowed range.\nRange: [1:3600]");
        ValidateCronExpression.validate(config.getSchedule());
        if (CleanupType.CLASS.equals(config.getType())) {
            String searchClass = config.getSearchClass();
            if (classMethodWhiteList.containsKey(searchClass)) {
                initCleaner(searchClass);
            } else {
                throw new SecurityException("Current search Class is not allowed here " + config.getSearchClass());
            }
        }
        if (CleanupType.SQL.equals(config.getType())) {
            Preconditions.checkArgument(checkSqlAvailability(cleanupSettings.getTableName()),
                    "Environment ins't set up properly to execute SQL queries");
        }
        log.info("Cleanup saved.");
        return saveCleanupConfiguration(cleanupSettings);
    }

    private CleanupSettings saveCleanupConfiguration(@Nonnull CleanupSettings cleanupSettings) {
        TestDataTableCatalog tableCatalog = catalogRepository.findByTableName(cleanupSettings.getTableName());
        TestDataCleanupConfig config = cleanupSettings.getTestDataCleanupConfig();

        if (Objects.nonNull(tableCatalog.getCleanupConfigId()) && config.isShared()) {
            config.setId(tableCatalog.getCleanupConfigId());
        } else {
            config.setId(UUID.randomUUID());
        }

        List<String> tablesWithSameName = getTablesByTableNameAndEnvironmentsListWithSameSystemName(
                cleanupSettings.getEnvironmentsList(),
                cleanupSettings.getTableName());

        tablesWithSameName.forEach(tableName -> {
            TestDataTableCatalog table = catalogRepository.findByTableName(tableName);
            if (config.isShared()) {
                setSharedCleanupConfig(table.getProjectId(), table.getTableTitle(), config.getId());
            }
            setCleanupConfig(table, config.getId());
        });
        cleanupConfigRepository.delete(config);
        cleanupConfigRepository.save(config);
        removeUnused();
        schedule(Collections.singletonList(config));
        return cleanupSettings;
    }

    private void setCleanupConfig(@Nonnull TestDataTableCatalog tableCatalog, @Nonnull UUID cleanupConfigId) {
        tableCatalog.setCleanupConfigId(cleanupConfigId);
        catalogRepository.save(tableCatalog);
    }

    private void setSharedCleanupConfig(@Nonnull UUID projectId, @Nonnull String tableTitle,
                                        @Nonnull UUID cleanupConfigId) {
        log.info("Setting shared cleanup for table with title: {}", tableTitle);
        List<TestDataTableCatalog> catalogList = catalogRepository
                .findAllByProjectIdAndTableTitle(projectId, tableTitle);
        catalogList.forEach(tableCatalog -> {
            tableCatalog.setCleanupConfigId(cleanupConfigId);
            catalogRepository.save(tableCatalog);
        });
        log.info("Shared cleanup saved.");
    }

    @Override
    public List<CleanupResults> runCleanup(@Nonnull UUID configId) throws Exception {
        List<TestDataTableCatalog> catalogs = catalogRepository.findAllByCleanupConfigId(configId);
        Optional<TestDataTableCatalog> firstTable = catalogs.stream().findFirst();
        if (firstTable.isPresent()) {
            MdcUtils.put(MdcField.PROJECT_ID.toString(), firstTable.get().getProjectId());
            tdmMdcHelper.putConfigFields(firstTable.get());
            metricService.executeCleanupJob(configId.toString(),
                    firstTable.get().getProjectId(), firstTable.get().getTableTitle());
        }

        TestDataCleanupConfig config = getCleanupConfig(configId);
        if (config.isEnabled()) {
            List<CleanupResults> cleanupResults = new ArrayList<>();
            List<UUID> connectionRefusedEnvs = new ArrayList<>();
            catalogs.forEach(catalog -> {
                UUID environmentId = catalog.getEnvironmentId();
                try {
                    String tableName = catalog.getTableName();
                    if (!connectionRefusedEnvs.contains(environmentId)) {
                        log.info("Preparing to clean up with ID {}. Table: {}", configId, tableName);
                        cleanupResults.add(runCleanup(tableName, config));
                    } else {
                        log.warn("Can not establish connection for envId: {}, table: {}, cleanup ID: {}", environmentId,
                                tableName, configId);
                    }
                } catch (Exception e) {
                    log.error("Error during scheduled clean up with ID: {}. Table: {}", configId,
                            catalog.getTableName(), e);
                    connectionRefusedEnvs.add(environmentId);
                    cleanupResults.add(new CleanupResults(catalog.getTableName(),
                            e.getMessage(), 0, 0));
                }
            });
            log.info("Cleanup has been finished.");
            return cleanupResults;
        }
        return new ArrayList<>();
    }

    @Override
    public List<CleanupResults> runCleanup(@Nonnull CleanupSettings cleanupSettings) {
        List<CleanupResults> cleanupResults = new ArrayList<>();
        List<UUID> connectionRefusedEnvs = new ArrayList<>();
        List<String> cleanupTableNames = getTablesByTableNameAndEnvironmentsListWithSameSystemName(
                cleanupSettings.getEnvironmentsList(),
                cleanupSettings.getTableName());

        for (String cleanupTableName : cleanupTableNames) {
            testDataTableRepository.updateLastUsage(cleanupTableName);
            TestDataTableCatalog table = catalogRepository.findByTableName(cleanupTableName);
            UUID envId = table.getEnvironmentId();
            try {
                if (!connectionRefusedEnvs.contains(envId)) {
                    log.info("Preparing to clean up. Table: {}", cleanupTableName);
                    cleanupResults.add(runCleanup(cleanupTableName, cleanupSettings.getTestDataCleanupConfig()));
                } else {
                    log.warn("Can not establish connection for envId: {}, table: {}, cleanup ID", envId,
                            cleanupTableName);
                }
            } catch (Exception e) {
                log.error("Error during scheduled clean up. Table: {}",
                        cleanupTableName, e);
                connectionRefusedEnvs.add(envId);
                cleanupResults.add(new CleanupResults(cleanupTableName, e.getMessage(), 0, 0));
            }
        }
        return cleanupResults;
    }

    /**
     * Force run data cleanup for specified dataset / table ID.
     *
     * @param config - cleanup config
     */
    @Override
    public CleanupResults runCleanup(@Nonnull String tableName,
                                     @Nonnull TestDataCleanupConfig config) throws Exception {
        if (CleanupType.SQL.equals(config.getType())) {
            Server server = sqlRepository.getServer(tableName, catalogRepository, environmentsService);
            try (Connection connection = sqlRepository.createConnection(server)) {
                if (config.getQueryTimeout() == null) {
                    int queryTimeout = ObjectUtils.defaultIfNull(
                            importInfoRepository.findByTableName(tableName).getQueryTimeout(), defaultQueryTimeout);
                    config.setQueryTimeout(queryTimeout);
                }
                return runCleanup(tableName, new SqlTestDataCleaner(connection,
                        config.getSearchSql(), config.getQueryTimeout()));
            } catch (Exception ex) {
                log.error("Error while run cleanup.", ex);
                throw new TdmRunCleanupException(ex.getMessage());
            }
        } else if (CleanupType.CLASS.equals(config.getType())) {
            String searchClass = config.getSearchClass();
            if (classMethodWhiteList.containsKey(searchClass)) {
                TestDataCleaner cleaner = initCleaner(searchClass);
                return runCleanup(tableName, cleaner);
            } else {
                throw new SecurityException("Current search Class is not allowed here " + config.getSearchClass());
            }
        } else if (CleanupType.DATE.equals(config.getType())) {
            LocalDate cleanupDate = DataUtils.calculateExpiredData(config.getSearchDate());
            return runCleanupByDate(tableName, cleanupDate);
        } else {
            log.error(String.format(TdmUndefinedCleanupCriteriaException.DEFAULT_MESSAGE, tableName));
            throw new TdmUndefinedCleanupCriteriaException(tableName);
        }
    }

    @Nonnull
    private CleanupResults runCleanup(@Nonnull String tableName, @Nonnull TestDataCleaner cleaner) throws Exception {
        CleanupResults results = new CleanupResults();
        TestDataTable table = testDataTableRepository.getFullTestData(tableName);
        results.setTableName(tableName);
        results.setRecordsTotal(table.getData().size());
        List<Map<String, Object>> cleanedRows = cleaner.runCleanup(table);
        if (cleanedRows.isEmpty()) {
            log.info("Nothing to clean up");
        } else {
            results.setRecordsRemoved(cleanedRows.size());
            log.info("Following data to be removed from database:\n"
                    + cleanedRows.stream().map(Map::toString).collect(Collectors.joining("; ")));
            List<UUID> rows = cleanedRows.stream()
                    .map(row -> UUID.fromString(String.valueOf(row.get("ROW_ID"))))
                    .collect(Collectors.toList());
            if (!rows.isEmpty()) {
                testDataTableRepository.deleteRows(tableName, rows);
            } else {
                log.info("There are no rows to delete.");
            }
        }
        return results;
    }

    @Nonnull
    private CleanupResults runCleanupByDate(@Nonnull String tableName, @Nonnull LocalDate cleanupDate) {
        CleanupResults results = new CleanupResults();
        results.setTableName(tableName);
        results.setRecordsTotal(testDataTableRepository.getCountRows(tableName));
        results.setRecordsRemoved(testDataTableRepository.deleteRowsByDate(tableName, cleanupDate));
        return results;
    }

    /**
     * Get next run's date / time details.
     *
     * @param cronExpression cron expression to calculate next run based on
     * @return ResponseMessage that contains the details
     * @throws ParseException Thrown in case if invalid cron expression was provided
     */
    @Override
    public String getNextScheduledRun(String cronExpression) throws ParseException {
        CronExpression ce = new CronExpression(cronExpression);
        return ce.getNextValidTimeAfter(new Date()).toString();
    }

    /**
     * Removes unused cleanup configs.
     */
    public void removeUnused() {
        cleanupConfigRepository.findAll().stream()
                .map(TestDataCleanupConfig::getId)
                .forEach(id -> {
                    if (catalogRepository.findAllByCleanupConfigId(id).isEmpty()) {
                        cleanupConfigRepository.deleteById(id);
                        removeJob(id);
                    }
                });
    }

    /**
     * Removes unused cleanup job.
     */
    public void removeJob(@Nonnull UUID configId) {
        log.info("Removing active refresh job for config with id: {}", configId);
        JobKey jobKey = new JobKey(configId.toString(), SCHEDULE_GROUP);
        schedulerService.deleteJob(jobKey);
        log.info("Stored refresh job with id [{}] successfully removed.", configId);
    }

    /**
     * Filling cleanup search type column.
     */
    public void fillCleanupTypeColumn() {
        cleanupConfigRepository.findAll()
                .forEach(config -> {
                    log.info("Trying to update cleanup type for cleanup: {}", config.getId());
                    if (Objects.nonNull(config.getSearchDate())) {
                        config.setType(CleanupType.DATE);
                    } else if (Objects.nonNull(config.getSearchSql())) {
                        config.setType(CleanupType.SQL);
                    } else if (Objects.nonNull(config.getSearchClass())) {
                        config.setType(CleanupType.CLASS);
                    }
                    cleanupConfigRepository.save(config);
                    log.info("Updated cleanup type for cleanup: {}", config.getId());
                });
    }

    private boolean checkSqlAvailability(@Nonnull String tableName) {
        Server server = sqlRepository.getServer(tableName, catalogRepository, environmentsService);
        sqlRepository.createJdbcTemplate(server);
        return true;
    }

    private void schedule(@Nonnull List<TestDataCleanupConfig> configs) {
        log.info("Processing [{}] cleanup schedule request(s)", configs.size());
        for (TestDataCleanupConfig config : configs) {
            UUID configId = config.getId();
            log.info("Scheduling cleanup config {}", config);
            JobDetail job = JobBuilder.newJob(DataCleanupJob.class)
                    .withIdentity(configId.toString(), SCHEDULE_GROUP)
                    .build();
            schedulerService.reschedule(job, config, SCHEDULE_GROUP);
        }
        log.info("Cleanup scheduling successfully finished.");
    }

    @Nonnull
    private TestDataCleaner initCleaner(@Nullable String className)
            throws IllegalAccessException, InstantiationException {
        Preconditions.checkArgument(StringUtils.isNotEmpty(className), "Class name is null or empty");
        Class<?> clazz = classMethodWhiteList.get(className);
        if (!TestDataCleaner.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "Class '" + className + "' doesn't implement TestDataCleaner interface");
        }
        return (TestDataCleaner) clazz.newInstance();
    }

    /**
     * Schedule jobs for stored cleanup configurations.
     */
    public void initSchedules() {
        log.info("Starting jobs for stored cleanup configurations.");
        List<TestDataCleanupConfig> storedRequests = this.cleanupConfigRepository.findAll().stream()
                .filter(TestDataCleanupConfig::isEnabled)
                .collect(Collectors.toList());
        schedule(storedRequests);
        log.info("Stored cleanup jobs successfully started.");
    }

    @Override
    public List<String> getTablesByTableNameAndEnvironmentsListWithSameSystemName(
            @Nonnull List<UUID> environmentsList,
            @Nonnull String tableName) {
        TestDataTableCatalog testDataTable = catalogRepository.findByTableName(tableName);
        LazySystem sourceSystem = environmentsService.getLazySystemById(
                testDataTable.getEnvironmentId(), testDataTable.getSystemId()
        );

        if (sourceSystem == null) {
            log.error("Cannot get system by ID {}", testDataTable.getSystemId());
            return Collections.emptyList();
        }

        List<LazySystem> systemByProject = environmentsService
                .getLazySystemsByProjectWithEnvIds(testDataTable.getProjectId());

        List<UUID> systemsWithSameName = systemByProject.stream()
                .filter(
                        system ->
                                system.getEnvironmentIds().stream().anyMatch(environmentsList::contains)
                                        &&
                                        system.getName().equals(sourceSystem.getName())
                ).map(LazySystem::getId)
                .collect(Collectors.toList());

        return catalogRepository.findAllByProjectIdAndTableTitleAndSystemIdIn(
                        testDataTable.getProjectId(),
                        testDataTable.getTableTitle(),
                        systemsWithSameName
                )
                .stream()
                .map(TestDataTableCatalog::getTableName)
                .collect(Collectors.toList());
    }
}
