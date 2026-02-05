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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.CronExpression;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.env.configurator.model.Server;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.db.TdmDbExecuteQueryException;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchImportInfoException;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchRefreshConfigException;
import org.qubership.atp.tdm.mdc.MdcField;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.TestDataTableImportInfo;
import org.qubership.atp.tdm.model.refresh.RefreshResults;
import org.qubership.atp.tdm.model.refresh.TestDataRefreshConfig;
import org.qubership.atp.tdm.model.scheduler.DataRefreshJob;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.ImportInfoRepository;
import org.qubership.atp.tdm.repo.RefreshConfigRepository;
import org.qubership.atp.tdm.repo.SqlRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.DataRefreshService;
import org.qubership.atp.tdm.service.SchedulerService;
import org.qubership.atp.tdm.utils.ValidateCronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DataRefreshServiceImpl implements DataRefreshService {

    private static final String SCHED_GROUP = "refresh";
    private final EnvironmentsService environmentsService;
    private final SchedulerService schedulerService;
    private final RefreshConfigRepository refreshConfigRepository;
    private final TestDataTableRepository testDataTableRepository;
    private final ImportInfoRepository importInfoRepository;
    private final CatalogRepository catalogRepository;
    private final SqlRepository sqlRepository;
    private final MetricService metricService;
    private final TdmMdcHelper tdmMdcHelper;
    @Value("${external.query.default.timeout:1800}")
    private Integer defaultQueryTimeout;
    @Value("${external.query.max.timeout:3600}")
    private Integer maxQueryTimeout;

    /**
     * Default constructor.
     */
    @Autowired
    public DataRefreshServiceImpl(@Nonnull EnvironmentsService environmentsService,
                                  @Nonnull SchedulerService schedulerService,
                                  @Nonnull RefreshConfigRepository repository,
                                  @Nonnull TestDataTableRepository testDataTableRepository,
                                  @Nonnull ImportInfoRepository importInfoRepository,
                                  @Nonnull CatalogRepository catalogRepository,
                                  @Nonnull SqlRepository sqlRepository,
                                  @Nonnull MetricService metricService, TdmMdcHelper helper) {
        this.environmentsService = environmentsService;
        this.schedulerService = schedulerService;
        this.refreshConfigRepository = repository;
        this.testDataTableRepository = testDataTableRepository;
        this.importInfoRepository = importInfoRepository;
        this.catalogRepository = catalogRepository;
        this.sqlRepository = sqlRepository;
        this.metricService = metricService;
        tdmMdcHelper = helper;
    }

    @Override
    public TestDataRefreshConfig getRefreshConfig(@Nonnull UUID id) {
        TestDataRefreshConfig testDataRefreshConfig = refreshConfigRepository.findById(id)
                .orElseThrow(() -> new TdmSearchRefreshConfigException(id.toString()));
        testDataRefreshConfig.setQueryTimout(getQueryTimeout(id));
        return testDataRefreshConfig;
    }

    private Integer getQueryTimeout(UUID id) {
        TestDataTableCatalog tableCatalog = catalogRepository.findByRefreshConfigId(id);
        TestDataTableImportInfo importInfo = importInfoRepository.findByTableName(tableCatalog.getTableName());
        return ObjectUtils.defaultIfNull(importInfo.getQueryTimeout(), defaultQueryTimeout);
    }

    @Override
    public TestDataRefreshConfig saveRefreshConfig(@Nonnull String tableName,
                                                   @Nonnull Integer queryTimeout,
                                                   @Nonnull TestDataRefreshConfig config) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(tableName), "Table Name is null");
        Preconditions.checkArgument(checkSqlAvailability(tableName),
                "Environment isn't set up properly to execute SQL queries");
        Preconditions.checkArgument(queryTimeout > 0 && queryTimeout <= maxQueryTimeout,
                "The timeout is not within the allowed range.\nRange: [1:3600]");
        ValidateCronExpression.validate(config.getSchedule());
        return saveRefreshConfiguration(tableName, queryTimeout, config);
    }

    private TestDataRefreshConfig saveRefreshConfiguration(@Nonnull String tableName, @Nonnull Integer queryTimeout,
                                                           @Nonnull TestDataRefreshConfig config) {
        log.info("Saving refresh configuration for table: {}", tableName);
        List<TestDataTableCatalog> catalogList = getTableWithSameTitleAndQuery(tableName, config.isAllEnv());

        for (TestDataTableCatalog catalog : catalogList) {
            saveQueryTimeout(catalog.getTableName(), queryTimeout);
            config.setId(getConfigId(catalog));
            setRefreshConfig(catalog, config.getId());
            refreshConfigRepository.save(config);
            schedule(Collections.singletonList(config));
        }

        config.setQueryTimout(queryTimeout);
        log.info("Refresh configuration successfully saved.");
        return config;
    }

    private List<TestDataTableCatalog> getTableWithSameTitleAndQuery(String tableName, boolean allEnv) {
        TestDataTableCatalog tableCatalog = catalogRepository.findByTableName(tableName);

        if (!allEnv) {
            return Collections.singletonList(tableCatalog);
        }

        String tableQuery = importInfoRepository.findByTableName(tableCatalog.getTableName()).getTableQuery();

        List<TestDataTableCatalog> catalogList = catalogRepository.findAllByProjectIdAndTableTitle(
                tableCatalog.getProjectId(), tableCatalog.getTableTitle());

        List<String> tableNameList =
                catalogList.stream().map(TestDataTableCatalog::getTableName).collect(Collectors.toList());

        List<String> importInfoList = importInfoRepository.findAllById(tableNameList)
                .stream()
                .filter(importInfo -> importInfo.getTableQuery().equals(tableQuery))
                .map(TestDataTableImportInfo::getTableName)
                .collect(Collectors.toList());

        return catalogList.stream().filter(c -> importInfoList.contains(c.getTableName())).collect(Collectors.toList());
    }

    private UUID getConfigId(TestDataTableCatalog tableCatalog) {
        if (Objects.nonNull(tableCatalog.getRefreshConfigId())) {
            return tableCatalog.getRefreshConfigId();
        } else {
            return UUID.randomUUID();
        }
    }

    private void saveQueryTimeout(String tableName, Integer queryTimeout) {
        TestDataTableImportInfo importInfo = importInfoRepository.findByTableName(tableName);
        importInfo.setQueryTimeout(queryTimeout);
        importInfoRepository.save(importInfo);
    }

    private void setRefreshConfig(@Nonnull TestDataTableCatalog tableCatalog, @Nonnull UUID refreshConfigId) {
        tableCatalog.setRefreshConfigId(refreshConfigId);
        catalogRepository.save(tableCatalog);
    }

    @Override
    public RefreshResults runRefresh(@Nonnull UUID configId) {
        log.info("Run refresh data by using config with id: {}", configId);
        TestDataRefreshConfig config = getRefreshConfig(configId);
        TestDataTableCatalog catalog = catalogRepository.findByRefreshConfigId(config.getId());
        MdcUtils.put(MdcField.PROJECT_ID.toString(), catalog.getProjectId());
        tdmMdcHelper.putConfigFields(catalog);
        metricService.executeRefreshJob(configId.toString(), catalog.getProjectId(), catalog.getTableTitle());
        RefreshResults results = new RefreshResults();
        if (config.isEnabled()) {
            String tableName = catalog.getTableName();
            log.info("Preparing to refresh. Table: {}", tableName);
            try {
                results = runRefresh(tableName, false);
            } catch (Exception e) {
                log.error("Error while executing refresh for table: {}", tableName, e);
            }
            log.info("Refresh data complete successful.");
            return results;
        }
        log.info("Refresh config not enabled.");
        return results;
    }

    /**
     * Run refresh, saving/not saving occupied rows.
     *
     * @param tableName        - table name.
     * @param saveOccupiedData - save occupied rows.
     * @return refresh results.
     * @throws Exception RuntimeException if can't find table by name.
     */
    @Override
    public List<RefreshResults> runRefresh(@Nonnull String tableName,
                                           @Nonnull Integer queryTimeout,
                                           boolean allEnv,
                                           boolean saveOccupiedData) throws Exception {
        List<RefreshResults> refreshResultsList = new ArrayList<>();
        List<TestDataTableCatalog> catalogList = getTableWithSameTitleAndQuery(tableName, allEnv);

        for (TestDataTableCatalog tableCatalog : catalogList) {
            testDataTableRepository.updateLastUsage(tableName);
            refreshResultsList.add(runRefresh(tableCatalog.getTableName(), saveOccupiedData));
        }
        return refreshResultsList;
    }

    @Override
    @Transactional
    @SuppressWarnings("PMD.TooManyStaticImports")
    public RefreshResults runRefresh(@Nonnull String tableName, boolean saveOccupiedData) throws Exception {
        log.info("Run data refresh for table with name: {}, save occupied data: {}", tableName, saveOccupiedData);
        Server server = sqlRepository.getServer(tableName, catalogRepository, environmentsService);
        Optional<TestDataTableImportInfo> importInfo = importInfoRepository.findById(tableName);
        if (!importInfo.isPresent()) {
            throw new RuntimeException("Import info not exist for table: " + tableName);
        }
        if (saveOccupiedData) {
            testDataTableRepository.deleteUnoccupiedRows(tableName);
        } else {
            testDataTableRepository.deleteAllRows(tableName);
        }
        Integer queryTimeout = ObjectUtils.defaultIfNull(importInfo.get().getQueryTimeout(),
                defaultQueryTimeout);

        JdbcTemplate userJdbcTemplate = sqlRepository.createJdbcTemplate(server, queryTimeout);
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rowsBuffer = new ArrayList<>();
        int batchSize = 100;
        AtomicReference<Integer> refreshedRows = new AtomicReference<>(0);
        try {
            userJdbcTemplate.query(importInfo.get().getTableQuery(), new RowCallbackHandler() {
                @Override
                public void processRow(ResultSet resultSet) throws SQLException {
                    if (columns.isEmpty()) {
                        ResultSetMetaData metaData = resultSet.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                            columns.add(metaData.getColumnName(columnIndex));
                        }
                    }
                    Map<String, Object> row = new HashMap<>();
                    for (String column : columns) {
                        row.put(column, resultSet.getObject(column));
                    }
                    rowsBuffer.add(row);
                    if (rowsBuffer.size() == batchSize) {
                        testDataTableRepository
                                .insertRows(tableName, true, rowsBuffer, refreshedRows.get() > 0);
                        refreshedRows.updateAndGet(v -> v + batchSize);
                        rowsBuffer.clear();
                    }
                }
            });
        } catch (Exception e) {
            log.error(TdmDbExecuteQueryException.DEFAULT_MESSAGE, e);
            throw new TdmDbExecuteQueryException(e.getMessage());
        }
        if (!rowsBuffer.isEmpty()) {
            testDataTableRepository.insertRows(tableName, true, rowsBuffer, refreshedRows.get() > 0);
            refreshedRows.updateAndGet(v -> v + rowsBuffer.size());
        }
        if (refreshedRows.get() == 0) {
            throw new TdmSearchImportInfoException(tableName);
        }
        log.info("Total refreshed records: {}", refreshedRows.get());
        RefreshResults results = new RefreshResults();
        results.setRecordsTotal(refreshedRows.get());
        log.info("Data refresh has been finished");
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
     * Removes unused refresh configs.
     */
    public void removeUnused() {
        refreshConfigRepository.findAll().stream()
                .map(TestDataRefreshConfig::getId)
                .forEach(id -> {
                    if (catalogRepository.findByRefreshConfigId(id).getTableTitle().isEmpty()) {
                        refreshConfigRepository.deleteById(id);
                        removeJob(id);
                    }
                });
    }

    @Override
    public void removeJob(@Nonnull UUID configId) {
        log.info("Removing active refresh job for config with id: {}", configId);
        JobKey jobKey = new JobKey(configId.toString(), SCHED_GROUP);
        schedulerService.deleteJob(jobKey);
        log.info("Stored refresh job with id [{}] successfully removed.", configId);
    }

    private boolean checkSqlAvailability(@Nonnull String tableName) {
        Server server = sqlRepository.getServer(tableName, catalogRepository, environmentsService);
        sqlRepository.createJdbcTemplate(server);
        return true;
    }

    private void schedule(@Nonnull List<TestDataRefreshConfig> configs) {
        log.info("Processing [{}] refresh schedule request(s)", configs.size());
        for (TestDataRefreshConfig config : configs) {
            UUID configId = config.getId();
            log.info("Scheduling refresh config: {}", config);
            JobDetail job = JobBuilder.newJob(DataRefreshJob.class)
                    .withIdentity(configId.toString(), SCHED_GROUP)
                    .build();
            schedulerService.reschedule(job, config, SCHED_GROUP);
        }
    }

    /**
     * Schedule jobs for stored refresh configurations.
     */
    public void initSchedules() {
        log.info("Starting jobs for stored refresh configurations.");
        List<TestDataRefreshConfig> storedRequests = this.refreshConfigRepository.findAll().stream()
                .filter(TestDataRefreshConfig::isEnabled)
                .collect(Collectors.toList());
        schedule(storedRequests);
        log.info("Stored refresh jobs successfully started.");
    }
}
