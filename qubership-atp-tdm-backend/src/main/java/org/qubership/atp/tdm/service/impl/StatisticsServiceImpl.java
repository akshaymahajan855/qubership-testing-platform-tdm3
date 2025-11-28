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

import static java.time.temporal.ChronoUnit.DAYS;
import static org.qubership.atp.tdm.utils.AvailableStatisticUtils.availableDataQuery;
import static org.qubership.atp.tdm.utils.DateFormatters.FULL_DATE_FORMATTER;
import static org.qubership.atp.tdm.utils.TestDataQueries.GET_COUNT_OF_ROWS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.quartz.CronExpression;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.TdmAvailableStatisticActiveColumnException;
import org.qubership.atp.tdm.exceptions.internal.TdmAvailableStatisticColumnException;
import org.qubership.atp.tdm.exceptions.internal.TdmAvailableStatisticEnvironmentIdException;
import org.qubership.atp.tdm.exceptions.internal.TdmAvailableStatisticSystemIdException;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchAvailableMonitoringConfigException;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchAvailableStatisticConfigException;
import org.qubership.atp.tdm.model.TestDataOccupyStatistic;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.scheduler.AvailableDataStatisticsMailJob;
import org.qubership.atp.tdm.model.scheduler.StatisticsMailJob;
import org.qubership.atp.tdm.model.scheduler.UsersStatisticsMailJob;
import org.qubership.atp.tdm.model.statistics.AvailableDataStatisticsConfig;
import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.model.statistics.ConsumedStatisticsItem;
import org.qubership.atp.tdm.model.statistics.DateStatistics;
import org.qubership.atp.tdm.model.statistics.DateStatisticsItem;
import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.qubership.atp.tdm.model.statistics.OccupiedDataByUsersStatistics;
import org.qubership.atp.tdm.model.statistics.OutdatedStatistics;
import org.qubership.atp.tdm.model.statistics.OutdatedStatisticsItem;
import org.qubership.atp.tdm.model.statistics.StatisticsEnvironment;
import org.qubership.atp.tdm.model.statistics.TestAvailableDataMonitoring;
import org.qubership.atp.tdm.model.statistics.TestDataTableMonitoring;
import org.qubership.atp.tdm.model.statistics.TestDataTableUsersMonitoring;
import org.qubership.atp.tdm.model.statistics.UserGeneralStatisticsItem;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticRequest;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticResponse;
import org.qubership.atp.tdm.model.statistics.available.AvailableDataByColumnStats;
import org.qubership.atp.tdm.model.statistics.available.TableAvailableDataStats;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReport;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReportElement;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReportEnvironment;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReportObject;
import org.qubership.atp.tdm.model.statistics.report.UsersStatisticsReportElement;
import org.qubership.atp.tdm.model.statistics.report.UsersStatisticsReportObject;
import org.qubership.atp.tdm.model.table.TestDataOccupyReportGroupBy;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.conditions.search.SearchConditionType;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.OccupyStatisticRepository;
import org.qubership.atp.tdm.repo.StatisticsRepository;
import org.qubership.atp.tdm.repo.TableColumnValuesRepository;
import org.qubership.atp.tdm.repo.TestAvailableDataMonitoringRepository;
import org.qubership.atp.tdm.repo.TestDataMonitoringRepository;
import org.qubership.atp.tdm.repo.TestDataUsersMonitoringRepository;
import org.qubership.atp.tdm.repo.impl.SystemColumns;
import org.qubership.atp.tdm.service.SchedulerService;
import org.qubership.atp.tdm.service.StatisticsService;
import org.qubership.atp.tdm.service.TestDataService;
import org.qubership.atp.tdm.utils.UsersOccupyStatisticUtils;
import org.qubership.atp.tdm.utils.ValidateCronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private static final List<String> INTERNAL_COLUMNS = new ArrayList<>(Arrays.asList(
            "OCCUPIED_DATE", "ROW_ID", "SELECTED", "OCCUPIED_BY"));
    private static final String SCHEDULE_GROUP = "statistics";
    private static final String SCHEDULE_USERS_GROUP = "user-statistics";
    private static final String SCHEDULE_AVAILABLE_DATA_GROUP = "available-data-statistics";
    private static final String ALL_ENVIRONMENTS = "across all";
    private static final String NA = "N/A";
    private static final String CSV_EXT = ".csv";
    private final StatisticsRepository statisticsRepository;
    private final TestDataMonitoringRepository monitoringRepository;
    private final TestDataUsersMonitoringRepository usersMonitoringRepository;
    private final TestAvailableDataMonitoringRepository availableDataMonitoringRepository;
    private final TableColumnValuesRepository tableColumnValuesRepository;
    private final OccupyStatisticRepository occupyStatisticRepository;
    private final SchedulerService schedulerService;
    private final EnvironmentsService environmentsService;
    private final TestDataService testDataService;
    private final CatalogRepository catalogRepository;
    private final Integer threshold;
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Default constructor.
     */
    @Autowired
    public StatisticsServiceImpl(@Nonnull StatisticsRepository statisticsRepository,
                                 @Nonnull TestDataMonitoringRepository monitoringRepository,
                                 @Nonnull TestDataUsersMonitoringRepository userMonitoringRepository,
                                 @Nonnull SchedulerService schedulerService,
                                 @Nonnull EnvironmentsService environmentsService,
                                 @Lazy TestDataService testDataService,
                                 @Nonnull CatalogRepository catalogRepository,
                                 @Nonnull OccupyStatisticRepository occupyStatisticRepository,
                                 @Nonnull TestAvailableDataMonitoringRepository availableDataMonitoringRepository,
                                 @Nonnull TableColumnValuesRepository tableColumnValuesRepository,
                                 @Value("${test.data.initial.threshold}") Integer threshold) {
        this.statisticsRepository = statisticsRepository;
        this.monitoringRepository = monitoringRepository;
        this.usersMonitoringRepository = userMonitoringRepository;
        this.schedulerService = schedulerService;
        this.environmentsService = environmentsService;
        this.catalogRepository = catalogRepository;
        this.testDataService = testDataService;
        this.occupyStatisticRepository = occupyStatisticRepository;
        this.availableDataMonitoringRepository = availableDataMonitoringRepository;
        this.tableColumnValuesRepository = tableColumnValuesRepository;
        this.threshold = threshold;
    }

    @Override
    public int getThreshold() {
        return threshold;
    }

    @Override
    public List<GeneralStatisticsItem> getTestDataAvailability(@Nonnull UUID projectId, @Nullable UUID systemId) {
        log.info("Get test data availability for project: {}, systemId: {}", projectId, systemId);
        List<TestDataTableCatalog> catalogList = Objects.nonNull(systemId)
                ? catalogRepository.findAllByProjectIdAndSystemId(projectId, systemId)
                : catalogRepository.findAllByProjectId(projectId);
        List<GeneralStatisticsItem> data = statisticsRepository.getTestDataAvailability(catalogList, projectId);
        setEnvironmentsNames(projectId, data);
        if (Objects.nonNull(systemId)) {
            log.info("Test data availability successfully received.");
            return data;
        } else {
            List<GeneralStatisticsItem> listItems = new ArrayList<>();
            List<String> contextList = data.stream().map(GeneralStatisticsItem::getContext)
                    .distinct().collect(Collectors.toList());
            contextList.forEach(contextValue -> {
                List<GeneralStatisticsItem> details = new ArrayList<>();
                GeneralStatisticsItem item = new GeneralStatisticsItem(contextValue, 0L, 0L, 0L, 0L);
                data.forEach(dataItem -> {
                    if (contextValue.equals(dataItem.getContext())) {
                        long available = item.getAvailable() + dataItem.getAvailable();
                        long occupied = item.getOccupied() + dataItem.getOccupied();
                        long total = item.getTotal() + dataItem.getTotal();
                        item.setAvailable(available);
                        item.setOccupied(occupied);
                        item.setTotal(total);
                        details.add(dataItem);
                    }
                });
                item.setSystem(ALL_ENVIRONMENTS);
                item.setEnvironment(ALL_ENVIRONMENTS);
                item.setDetails(details);
                listItems.add(item);
            });
            log.info("Test data availability successfully received.");
            return listItems;
        }
    }

    @Override
    public ConsumedStatistics getTestDataConsumption(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                     @Nonnull LocalDate dateFrom, @Nonnull LocalDate dateTo) {
        log.info("Get consumed test data for project: {}, system: {}, from: {}, to: {}",
                projectId, systemId, dateFrom, dateTo);
        List<ConsumedStatisticsItem> listItems = new ArrayList<>();
        List<TestDataOccupyStatistic> occupyStatisticList = Objects.nonNull(systemId)
                ? occupyStatisticRepository.findAllByProjectIdAndSystemId(projectId, systemId)
                : occupyStatisticRepository.findAllByProjectId(projectId);
        ConsumedStatistics data = statisticsRepository.getTestDataConsumption(occupyStatisticList,
                projectId, dateFrom, dateTo);
        setEnvironmentsNames(projectId, data.getItems());
        if (Objects.isNull(systemId)) {
            List<String> contextList = data.getItems().stream().map(ConsumedStatisticsItem::getContext)
                    .distinct().collect(Collectors.toList());
            contextList.forEach(contextValue -> {
                int datesNumber = data.getDates().size();
                List<ConsumedStatisticsItem> details = new ArrayList<>();
                ConsumedStatisticsItem item = new ConsumedStatisticsItem(contextValue,
                        new ArrayList<>(Collections.nCopies(datesNumber, 0L)));
                data.getItems().forEach(dataItem -> {
                    if (contextValue.equals(dataItem.getContext())) {
                        details.add(dataItem);
                        for (int i = 0; i < datesNumber; i++) {
                            long consumed = item.getConsumed().get(i) + dataItem.getConsumed().get(i);
                            item.getConsumed().set(i, consumed);
                        }
                    }
                });
                item.setSystem(ALL_ENVIRONMENTS);
                item.setEnvironment(ALL_ENVIRONMENTS);
                item.setDetails(details);
                listItems.add(item);
            });
            data.setItems(listItems);
        }
        log.info("Test data consumption successfully received.");
        return data;
    }

    private void setEnvironmentsNames(@Nonnull UUID projectId,
                                      @Nonnull List<? extends StatisticsEnvironment> elements) {
        List<LazyEnvironment> lazyEnvironments = environmentsService.getLazyEnvironments(projectId);
        setEnvironmentsNames(lazyEnvironments, elements);
    }

    private void setEnvironmentsNames(@Nonnull List<LazyEnvironment> environments,
                                      @Nonnull List<? extends StatisticsEnvironment> elements) {
        log.info("Setting environments names.");
        elements.forEach(statElement -> {
            if (!NA.equals(statElement.getSystem())) {
                for (LazyEnvironment environment : environments) {
                    Optional<String> system = environment.getSystems().stream()
                            .filter(s -> s.equals(statElement.getSystem()))
                            .findFirst();
                    if (system.isPresent()) {
                        statElement.setEnvironment(environment.getName());
                        statElement.setSystem(environmentsService
                                .getLazySystemById(environment.getId(), UUID.fromString(system.get())).getName());
                        break;
                    }
                }
            }
        });
        log.info("Environments names successfully set.");
    }

    @Override
    public OutdatedStatistics getTestDataConsumptionWhitOutdated(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                                 @Nonnull LocalDate dateFrom, @Nonnull LocalDate dateTo,
                                                                 int expirationDate) {
        log.info("Get consumed test data for project: {}, system: {}, from: {}, to: {}",
                projectId, systemId, dateFrom, dateTo);
        List<OutdatedStatisticsItem> listItems = new ArrayList<>();
        List<TestDataTableCatalog> catalogList = Objects.nonNull(systemId)
                ? catalogRepository.findAllByProjectIdAndSystemId(projectId, systemId)
                : catalogRepository.findAllByProjectId(projectId);
        OutdatedStatistics data = statisticsRepository.getTestDataOutdatedConsumption(catalogList,
                projectId, dateFrom, dateTo, expirationDate);
        setEnvironmentsNames(projectId, data.getItems());
        if (Objects.isNull(systemId)) {
            List<String> contextList = data.getItems().stream().map(OutdatedStatisticsItem::getContext)
                    .distinct().collect(Collectors.toList());
            contextList.forEach(contextValue -> {
                int datesNumber = data.getDates().size();
                List<OutdatedStatisticsItem> details = new ArrayList<>();
                OutdatedStatisticsItem item = new OutdatedStatisticsItem(contextValue,
                        new ArrayList<>(Collections.nCopies(datesNumber, 0L)),
                        new ArrayList<>(Collections.nCopies(datesNumber, 0L)),
                        new ArrayList<>(Collections.nCopies(datesNumber, 0L)));
                data.getItems().forEach(dataItem -> {
                    if (contextValue.equals(dataItem.getContext())) {
                        details.add(dataItem);
                        for (int i = 0; i < datesNumber; i++) {
                            long created = item.getCreated().get(i) + dataItem.getCreated().get(i);
                            long consumed = item.getConsumed().get(i) + dataItem.getConsumed().get(i);
                            long outdated = item.getOutdated().get(i) + dataItem.getOutdated().get(i);
                            item.getCreated().set(i, created);
                            item.getConsumed().set(i, consumed);
                            item.getOutdated().set(i, outdated);
                        }
                    }
                });
                item.setSystem(ALL_ENVIRONMENTS);
                item.setEnvironment(ALL_ENVIRONMENTS);
                item.setDetails(details);
                listItems.add(item);
            });
            data.setItems(listItems);
        }
        log.info("Test data consumption successfully received.");
        return data;
    }

    @Override
    public DateStatistics getTestDataCreatedWhen(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                 @Nonnull LocalDate dateFrom, @Nonnull LocalDate dateTo) {
        log.info("Get created when test data for project: {}, system: {}, from: {}, to: {}",
                projectId, systemId, dateFrom, dateTo);
        List<DateStatisticsItem> listItems = new ArrayList<>();
        List<TestDataTableCatalog> catalogExistingList = Objects.nonNull(systemId)
                ? catalogRepository.findAllByProjectIdAndSystemId(projectId, systemId)
                : catalogRepository.findAllByProjectId(projectId);
        List<TestDataOccupyStatistic> catalogDeletedList = Objects.nonNull(systemId)
                ? occupyStatisticRepository.findAllByProjectIdAndSystemId(projectId, systemId)
                : occupyStatisticRepository.findAllByProjectId(projectId);
        DateStatistics dataExisting = testDataService.getTableByCreatedWhen(catalogExistingList, dateFrom, dateTo);
        DateStatistics dataDeleted = statisticsRepository.getTestDataCreatedWhen(catalogDeletedList,
                projectId, dateFrom, dateTo);
        setEnvironmentsNames(projectId, dataExisting.getItems());
        setEnvironmentsNames(projectId, dataDeleted.getItems());
        DateStatistics dateStatistics = DateStatistics.concatDateStatistics(dataExisting, dataDeleted);
        if (Objects.isNull(systemId)) {
            List<String> contextList = dateStatistics.getItems().stream().map(DateStatisticsItem::getContext)
                    .distinct().collect(Collectors.toList());
            contextList.forEach(contextValue -> {
                int datesNumber = dateStatistics.getDates().size();
                List<DateStatisticsItem> details = new ArrayList<>();
                DateStatisticsItem item = new DateStatisticsItem(contextValue,
                        new ArrayList<>(Collections.nCopies(datesNumber, 0L)));
                dateStatistics.getItems().forEach(dataItem -> {
                    if (contextValue.equals(dataItem.getContext())) {
                        details.add(dataItem);
                        for (int i = 0; i < datesNumber; i++) {
                            long created = item.getCreated().get(i) + dataItem.getCreated().get(i);
                            item.getCreated().set(i, created);
                        }
                    }
                });
                item.setSystem(ALL_ENVIRONMENTS);
                item.setEnvironment(ALL_ENVIRONMENTS);
                item.setDetails(details);
                listItems.add(item);
            });
            dateStatistics.setItems(listItems);
        }
        return dateStatistics;
    }

    @Override
    public TestDataTableMonitoring getMonitoringSchedule(@Nonnull UUID projectId) {
        return monitoringRepository.findById(projectId)
                .orElse(new TestDataTableMonitoring());
    }

    @Override
    public void saveMonitoringSchedule(@Nonnull TestDataTableMonitoring monitoringItem) {
        log.info("Saving statistics schedule: {}", monitoringItem);
        ValidateCronExpression.validate(monitoringItem.getCronExpression());
        monitoringRepository.save(monitoringItem);
        reschedule(monitoringItem);
        log.info("The statistics schedule successfully saved.");
    }

    @Override
    public void deleteMonitoringSchedule(@Nonnull TestDataTableMonitoring monitoringItem) {
        log.info("Deleting statistics schedule: {}", monitoringItem);
        monitoringRepository.delete(monitoringItem);
        schedulerService.deleteJob(new JobKey(String.valueOf(monitoringItem.getProjectId()), SCHEDULE_GROUP));
        log.info("The statistics schedule successfully deleted.");
    }

    @Override
    public TestDataTableUsersMonitoring getUsersMonitoringSchedule(@Nonnull UUID projectId) {
        return usersMonitoringRepository.findById(projectId)
                .orElse(new TestDataTableUsersMonitoring());
    }

    @Override
    public void saveUsersMonitoringSchedule(@Nonnull TestDataTableUsersMonitoring monitoringItem) {
        log.info("Saving statistics users schedule: {}", monitoringItem);
        ValidateCronExpression.validate(monitoringItem.getCronExpression());
        usersMonitoringRepository.save(monitoringItem);
        rescheduleUsers(monitoringItem);
        log.info("The statistics users schedule successfully saved.");
    }

    @Override
    public void deleteUsersMonitoringSchedule(@Nonnull TestDataTableUsersMonitoring monitoringItem) {
        log.info("Deleting statistics users schedule: {}", monitoringItem);
        usersMonitoringRepository.delete(monitoringItem);
        schedulerService.deleteJob(new JobKey(String.valueOf(monitoringItem.getProjectId()), SCHEDULE_USERS_GROUP));
        log.info("The statistics users schedule successfully deleted.");
    }

    @Override
    public String getNextScheduledRun(@Nonnull String cronExpression) throws ParseException {
        log.info("Calculate next scheduled run, cron expression: {}", cronExpression);
        CronExpression cron = new CronExpression(cronExpression);
        String nextScheduledRun = cron.getNextValidTimeAfter(new Date()).toString();
        log.info("Next scheduled run calculated: {}", nextScheduledRun);
        return nextScheduledRun;
    }

    @Override
    public StatisticsReportObject getTestDataMonitoringStatistics(@Nonnull UUID projectId, int threshold) {
        log.info("Get consumed test data monitoring statistic for project: {}, threshold: {}", projectId, threshold);
        List<TestDataTableCatalog> catalogList = catalogRepository.findAllByProjectId(projectId);
        List<StatisticsReport> statistics = statisticsRepository.getTestDataMonitoringStatistics(catalogList,
                projectId);
        String projectName = environmentsService.getLazyProjectById(projectId).getName();
        List<LazyEnvironment> environments = environmentsService.getLazyEnvironments(projectId);
        setEnvironmentsNames(environments, statistics);
        StatisticsReportObject report = getTestDataMonitoringStatistics(projectName, threshold, statistics);
        log.info("Consumed test data monitoring statistic successfully received.");
        return report;
    }

    private StatisticsReportObject getTestDataMonitoringStatistics(@Nonnull String projectName, int threshold,
                                                                   @Nonnull List<StatisticsReport> reportStatistics) {
        StatisticsReportObject reportObject = new StatisticsReportObject();
        reportObject.setProjectName(projectName);
        List<StatisticsReportElement> upToThreshold = new ArrayList<>();
        List<StatisticsReportElement> downToThreshold = new ArrayList<>();
        List<String> environments = reportStatistics.stream().map(StatisticsReport::getEnvironment).distinct()
                .collect(Collectors.toList());
        environments.forEach(env -> {
            List<String> filterSystem = reportStatistics.stream().filter(el -> env.equals(el.getEnvironment()))
                    .map(StatisticsReport::getSystem)
                    .distinct()
                    .collect(Collectors.toList());
            filterSystem.forEach(system -> {
                List<StatisticsReport> data = reportStatistics.stream()
                        .filter(el -> env.equals(el.getEnvironment()) && system.equals(el.getSystem()))
                        .collect(Collectors.toList());
                List<GeneralStatisticsItem> up = new ArrayList<>();
                List<GeneralStatisticsItem> down = new ArrayList<>();
                data.forEach(it -> {
                    if (it.getStatistics().getAvailable() >= threshold) {
                        up.add(it.getStatistics());
                    } else {
                        down.add(it.getStatistics());
                    }
                });
                StatisticsReportEnvironment environment = new StatisticsReportEnvironment(env, system);
                if (!up.isEmpty()) {
                    up.sort(Comparator.comparing(GeneralStatisticsItem::getContext));
                    upToThreshold.add(new StatisticsReportElement(environment, up));
                }
                if (!down.isEmpty()) {
                    down.sort(Comparator.comparing(GeneralStatisticsItem::getContext));
                    downToThreshold.add(new StatisticsReportElement(environment, down));
                }
            });
        });
        if (!upToThreshold.isEmpty()) {
            reportObject.setUpToThreshold(upToThreshold);
        }
        if (!downToThreshold.isEmpty()) {
            reportObject.setDownToThreshold(downToThreshold);
        }
        return reportObject;
    }

    /**
     * Removes unused statistics configs.
     */
    public void removeUnused() {
        monitoringRepository.findAll().stream()
                .map(TestDataTableMonitoring::getProjectId)
                .forEach(id -> {
                    if (catalogRepository.findAllByProjectId(id).isEmpty()) {
                        monitoringRepository.deleteById(id);
                    }
                });
    }

    @Override
    public List<String> alterOccupiedDateColumn() {
        return statisticsRepository.alterOccupiedDateColumn(catalogRepository.findAll().stream()
                .map(TestDataTableCatalog::getTableName)
                .collect(Collectors.toList()));
    }

    @Override
    public void saveOccupyStatistic(@Nonnull TestDataOccupyStatistic testDataOccupyStatistic) {
        occupyStatisticRepository.save(testDataOccupyStatistic);
    }

    @Override
    public void deleteAllOccupyStatisticByRowId(@Nonnull List<UUID> rows) {
        occupyStatisticRepository.deleteAllByRowId(rows);
    }

    @Override
    public void fillCreatedWhenStatistics(@Nonnull String tableName, @Nonnull TestDataTableCatalog catalog) {
        TestDataTable testDataTable = getCreatedWhenTestDataInfo(tableName);
        fillCreatedWhenStatistics(tableName, catalog, testDataTable);
    }

    @Override
    public void fillCreatedWhenStatistics(@Nonnull String tableName, @Nonnull TestDataTableCatalog catalog,
                                          @Nonnull List<UUID> rows) {
        TestDataTable testDataTable = getCreatedWhenTestDataInfo(tableName, rows);
        fillCreatedWhenStatistics(tableName, catalog, testDataTable);
    }

    private void fillCreatedWhenStatistics(@Nonnull String tableName, @Nonnull TestDataTableCatalog catalog,
                                           @Nonnull TestDataTable testDataTable) {
        log.info("Save created when statistics for table:[{}]", tableName);
        List<TestDataOccupyStatistic> statistics = testDataTable.getData().stream()
                .map(row -> {
                    LocalDateTime createdWhen = LocalDateTime.parse(String.valueOf(row.get(SystemColumns.CREATED_WHEN
                                    .getName())),
                            FULL_DATE_FORMATTER);
                    UUID rowId = UUID.fromString(String.valueOf(row.get(SystemColumns.ROW_ID.getName())));
                    return new TestDataOccupyStatistic(rowId, catalog.getProjectId(), catalog.getSystemId(), tableName,
                            catalog.getTableTitle(), null, null, createdWhen);
                })
                .collect(Collectors.toList());
        occupyStatisticRepository.saveAll(statistics);
        log.info("Created when statistics for table:[{}] successfully saved.", tableName);
    }

    private TestDataTable getCreatedWhenTestDataInfo(@Nonnull String tableName) {
        return testDataService.getTestData(tableName, Arrays.asList(SystemColumns.ROW_ID.getName(),
                SystemColumns.CREATED_WHEN.getName()), null);
    }

    private TestDataTable getCreatedWhenTestDataInfo(@Nonnull String tableName, @Nonnull List<UUID> rows) {
        List<String> rowsStr = rows.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        List<TestDataTableFilter> filters = Collections.singletonList(
                new TestDataTableFilter(SystemColumns.ROW_ID.getName(), SearchConditionType.EQUALS.toString(),
                        rowsStr, false));
        return testDataService.getTestData(tableName, Arrays.asList(SystemColumns.ROW_ID.getName(),
                SystemColumns.CREATED_WHEN.getName()), filters);
    }

    @Override
    public UsersStatisticsReportObject getUsersStatisticsReport(
            @Nonnull TestDataTableUsersMonitoring testDataTableUsersMonitoring) {
        UUID projectId = testDataTableUsersMonitoring.getProjectId();
        int daysCount = testDataTableUsersMonitoring.getDaysCount();
        String shortNameProject = environmentsService.getLazyProjectById(projectId).getName();

        List<TestDataOccupyReportGroupBy> testDataOccupy = occupyStatisticRepository
                .findAllByProjectIdAndOccupiedDateAndCountGroupBy(projectId, LocalDateTime.now().minusDays(daysCount));

        if (!testDataOccupy.isEmpty()) {
            List<UsersStatisticsReportElement> userElements = new ArrayList<>();

            Map<String, TestDataTableCatalog> catalogMap = catalogRepository.findAllByProjectId(projectId)
                    .stream()
                    .collect(Collectors.toMap(TestDataTableCatalog::getTableName, Function.identity()));

            List<String> userNames = testDataOccupy.stream()
                    .map(TestDataOccupyReportGroupBy::getOccupiedBy)
                    .distinct()
                    .collect(Collectors.toList());

            userNames.forEach(userName -> {
                UsersStatisticsReportElement usersStatisticsReportElement = new UsersStatisticsReportElement();
                List<UserGeneralStatisticsItem> listItems = new ArrayList<>();
                usersStatisticsReportElement.setUser(userName);
                usersStatisticsReportElement.setDates(getDateFormatFromDaysCount(daysCount));

                List<TestDataOccupyReportGroupBy> testDataOccupyForUserName = testDataOccupy.stream()
                        .filter(t -> t.getOccupiedBy().equals(userName)).collect(Collectors.toList());

                List<String> tableNames =
                        testDataOccupyForUserName.stream().map(TestDataOccupyReportGroupBy::getTableName)
                                .distinct().collect(Collectors.toList());

                tableNames.forEach(tableName -> {
                    List<TestDataOccupyReportGroupBy> testDataOccupyForTableName = testDataOccupyForUserName.stream()
                            .filter(t -> t.getTableName().equals(tableName)).collect(Collectors.toList());

                    TestDataTableCatalog tableCatalog = catalogMap.get(tableName);

                    String envName;
                    String systemName;

                    try {
                        envName = environmentsService.getEnvNameById(tableCatalog.getEnvironmentId());
                    } catch (Exception e) {
                        log.error("Environment name for id: {} - don't found in EnvService",
                                tableCatalog.getEnvironmentId());
                        envName = tableCatalog.getEnvironmentId().toString();
                    }

                    try {
                        systemName = environmentsService.getLazySystemById(
                                        tableCatalog.getEnvironmentId(), tableCatalog.getSystemId()
                                )
                                .getName();
                    } catch (Exception e) {
                        log.error("System name for id: {} - don't found in EnvService",
                                tableCatalog.getSystemId());
                        systemName = tableCatalog.getSystemId().toString();
                    }

                    List<Long> counts = new ArrayList<>();
                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                    List<LocalDateTime> dates = getDateFromDaysCount(daysCount);
                    dates.forEach(date -> {
                        Long count = testDataOccupyForTableName.stream()
                                .filter(t -> t.getOccupiedDate().format(dateTimeFormatter)
                                        .equals(date.format(dateTimeFormatter)))
                                .findFirst()
                                .orElse(new TestDataOccupyReportGroupBy(null, null,
                                        null, 0L))
                                .getCount();
                        counts.add(count);
                    });

                    UserGeneralStatisticsItem userStatisticsItem = new UserGeneralStatisticsItem(envName, systemName,
                            tableCatalog.getTableTitle(), counts);

                    listItems.add(userStatisticsItem);
                });

                usersStatisticsReportElement.setItems(listItems);
                userElements.add(usersStatisticsReportElement);
            });
            return new UsersStatisticsReportObject(shortNameProject, userElements);
        } else {
            return new UsersStatisticsReportObject(shortNameProject, null);
        }
    }

    private List<LocalDateTime> getDateFromDaysCount(int daysCount) {
        List<LocalDateTime> list = new ArrayList<>();
        list.add(LocalDateTime.now());
        for (int i = 1; i <= daysCount; i++) {
            LocalDateTime buf = LocalDateTime.now().minusDays(i);
            list.add(buf);
        }
        return list.stream().sorted().collect(Collectors.toList());
    }

    private List<String> getDateFormatFromDaysCount(int daysCount) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM");
        return getDateFromDaysCount(daysCount).stream()
                .map(s -> s.format(dateTimeFormatter)).collect(Collectors.toList());
    }

    /**
     * Restart trigger.
     *
     * @param monitoringItem - full users monitoring item data
     */
    private void rescheduleUsers(@Nonnull TestDataTableUsersMonitoring monitoringItem) {
        log.info("Processing users monitoring statistics schedule request");
        JobDetail job = JobBuilder.newJob(UsersStatisticsMailJob.class)
                .withIdentity(String.valueOf(monitoringItem.getProjectId()), SCHEDULE_USERS_GROUP)
                .build();
        schedulerService.reschedule(job, monitoringItem, SCHEDULE_USERS_GROUP);
        String triggerStatus = monitoringItem.isEnabled() ? "ON" : "OFF";
        log.info("Job for project {} and group {} has been scheduled. Trigger status:{}",
                monitoringItem.getProjectId(), SCHEDULE_USERS_GROUP, triggerStatus);
    }

    private void rescheduleAvailableData(@Nonnull TestAvailableDataMonitoring monitoringItem) {
        log.info("Processing available data monitoring statistics schedule request");
        String identityName = monitoringItem.getSystemId().toString()
                + ";" + monitoringItem.getEnvironmentId().toString();
        JobDetail job = JobBuilder.newJob(AvailableDataStatisticsMailJob.class)
                .withIdentity(identityName, SCHEDULE_AVAILABLE_DATA_GROUP)
                .build();
        schedulerService.reschedule(job, monitoringItem, SCHEDULE_AVAILABLE_DATA_GROUP, identityName);
        String triggerStatus = monitoringItem.isScheduled() ? "ON" : "OFF";
        log.info("Job with identity name {} and group {} has been scheduled. Trigger status:{}",
                identityName, SCHEDULE_AVAILABLE_DATA_GROUP, triggerStatus);
    }

    /**
     * Restart trigger.
     *
     * @param monitoringItem - full monitoring item data
     */
    private void reschedule(@Nonnull TestDataTableMonitoring monitoringItem) {
        log.info("Processing monitoring statistics schedule request");
        JobDetail job = JobBuilder.newJob(StatisticsMailJob.class)
                .withIdentity(String.valueOf(monitoringItem.getProjectId()), SCHEDULE_GROUP)
                .build();
        schedulerService.reschedule(job, monitoringItem, SCHEDULE_GROUP);
        String triggerStatus = monitoringItem.isEnabled() ? "ON" : "OFF";
        log.info("Job for project {} and group {} has been scheduled. Trigger status:{}",
                monitoringItem.getProjectId(), SCHEDULE_GROUP, triggerStatus);
    }

    /**
     * Start monitoring at application startup.
     */
    public void startStatisticsMonitoring() {
        log.info("Starting jobs for stored statistic configurations.");
        List<TestDataTableMonitoring> monitoringList = monitoringRepository.findAll();
        monitoringList.forEach(this::reschedule);
        log.info("Stored statistic jobs successfully started.");
    }

    /**
     * Schedule users statistic monitoring.
     */
    public void startUsersStatisticsMonitoring() {
        log.info("Starting jobs for users statistic configurations.");
        List<TestDataTableUsersMonitoring> usersMonitoringList = usersMonitoringRepository.findAll();
        usersMonitoringList.forEach(this::rescheduleUsers);
        log.info("Stored users statistic jobs successfully started.");
    }

    /**
     * Run all monitoring configs.
     */
    public void startAvailableDataStatsMonitoring() {
        log.info("Starting jobs for users statistic configurations.");
        List<TestAvailableDataMonitoring> monitoringList = availableDataMonitoringRepository.findAll();
        monitoringList.forEach(this::rescheduleAvailableData);
        log.info("Stored users statistic jobs successfully started.");
    }

    /**
     * Get statistics about users and table and count of occupied rows.
     *
     * @param request Request to data.
     * @return List of users with statistic and count of rows
     */
    public UsersOccupyStatisticResponse getOccupiedDataByUsers(
            @Nonnull UsersOccupyStatisticRequest request) {
        log.info("Getting occupied data by users. Request: [{}]", request);
        long daysBetween = DAYS.between(LocalDate.parse(request.getDateFrom()), LocalDate.parse(request.getDateTo()));
        Preconditions.checkArgument(daysBetween > 0, "Date from is greater than date to.");
        String generatedQuery = UsersOccupyStatisticUtils.generateRequest(request, environmentsService);
        Query query = entityManager.createNativeQuery(generatedQuery);
        UsersOccupyStatisticUtils.setPagination(query, request);
        List queryResult = query.getResultList();
        Map<String, TestDataTableCatalog> tables = catalogRepository.findAllByProjectId(request.getProjectId())
                .stream().collect(Collectors.toMap(TestDataTableCatalog::getTableName, Function.identity()));
        List<OccupiedDataByUsersStatistics> tableValues = UsersOccupyStatisticUtils.mapObjectsToEntity(
                queryResult,
                LocalDate.parse(request.getDateFrom()));
        fillEnvironmentsAndSystems(tableValues, tables);
        int countOfRows = ((Long) entityManager
                .createNativeQuery(String.format(GET_COUNT_OF_ROWS, generatedQuery)).getResultList().get(0)).intValue();
        return new UsersOccupyStatisticResponse(tableValues, countOfRows);
    }

    private void fillEnvironmentsAndSystems(
            List<OccupiedDataByUsersStatistics> occupiedTables,
            Map<String, TestDataTableCatalog> tables) {
        occupiedTables.forEach(tableValue -> {
            TestDataTableCatalog tableTemp = tables.get(tableValue.getTableName());
            if (tableTemp != null) {
                String systemName = "Not found";
                String environmentName = "Not found";
                try {
                    tableValue.setSystem(
                            environmentsService.getLazySystemById(
                                            tableTemp.getEnvironmentId(), tableTemp.getSystemId()
                                    )
                                    .getName()
                    );
                } catch (Exception e) {
                    tableValue.setSystem(systemName);
                }
                try {
                    tableValue.setEnvironment(environmentsService.getEnvNameById(tableTemp.getEnvironmentId()));
                } catch (Exception e) {
                    tableValue.setEnvironment(environmentName);
                }
            }
        });
    }

    /**
     * Generate CSV report about users occupying.
     * @param projectId project id from request
     * @param days Days' count
     * @return CSV report
     */
    public File getCsvReportByUsers(UUID projectId, int days) throws IOException {
        LocalDate dateTo = LocalDate.now();
        LocalDate dateFrom = dateTo.minusDays(days);

        UsersOccupyStatisticResponse response = getOccupiedDataByUsers(new UsersOccupyStatisticRequest(
                projectId,
                0L,
                100L,
                null,
                null,
                dateFrom.toString(),
                dateTo.toString()
        ));
        String projectName = environmentsService.getLazyProjectById(projectId).getName();
        String period = LocalDate.now().minusDays(days) + "-" + LocalDate.now();
        File csvFile = new File(Files.createTempFile("[Statistic by User][" + projectName + "][" + period + "]",
                CSV_EXT).toString());
        try (FileWriter fileWriter = new FileWriter(csvFile)) {
            List<String> datesBetween = UsersOccupyStatisticUtils.getDatesBetween(dateFrom, dateTo);
            datesBetween = datesBetween.stream()
                    .map(LocalDate::parse)
                    .map(date -> date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .collect(Collectors.toList());
            StringBuilder csvHeader = new StringBuilder("User Name,Table,System,Environment,");
            String datesString = String.join(",", datesBetween);
            csvHeader.append(datesString).append(",\n");
            fileWriter.write(csvHeader.toString());
            for (OccupiedDataByUsersStatistics table : response.getData()) {
                SortedSet<LocalDate> keys = new TreeSet<LocalDate>(table.getData().keySet());
                StringBuilder valuesString = new StringBuilder();
                keys.forEach(date -> {
                    valuesString.append(table.getData().get(date)).append(",");
                });

                StringBuilder build = new StringBuilder();
                build
                        .append(table.getUserName())
                        .append(",")
                        .append(table.getContext())
                        .append(",")
                        .append(StringUtils.defaultIfEmpty(table.getSystem(), "Not found"))
                        .append(",")
                        .append(StringUtils.defaultIfEmpty(table.getEnvironment(), "Not found"))
                        .append(",");
                build.append(valuesString);
                build.append("\n");
                fileWriter.write(build.toString());
            }
        } catch (IOException e) {
            log.error("Error write csv file for report by users", e);
            throw e;
        }
        return csvFile;
    }

    @Override
    public AvailableDataStatisticsConfig getAvailableStatsConfig(@Nonnull UUID systemId, @Nonnull UUID environmentId) {
        log.info("Getting available statistic configuration for environmentId {} and systemId {}",
                environmentId, systemId);
        AvailableDataStatisticsConfig statsConfig = new AvailableDataStatisticsConfig(systemId, environmentId);
        statsConfig.setColumnKeys(getAllColumnNamesBySystemId(systemId));
        TestAvailableDataMonitoring optionalMonitoringConfig = availableDataMonitoringRepository
                .findByEnvironmentIdAndSystemId(environmentId, systemId);

        if (optionalMonitoringConfig != null) {
            List<String> tableNames = catalogRepository
                    .findBySystemId(systemId)
                    .stream()
                    .map(table -> table.getTableName().toUpperCase()).collect(Collectors.toList());
            if (StringUtils.isNotEmpty(optionalMonitoringConfig.getDescription())) {
                statsConfig.setDescription(optionalMonitoringConfig.getDescription());
            }
            if (StringUtils.isNotEmpty(optionalMonitoringConfig.getActiveColumn())) {
                statsConfig.setActiveColumnKey(optionalMonitoringConfig.getActiveColumn());
            }
            if (!CollectionUtils.isEmpty(tableNames)) {
                statsConfig.setTablesColumns(tableColumnValuesRepository
                        .findTableColumnValuesByTableNameIn(tableNames));
            }
        }
        log.debug("Received config {}", statsConfig);
        return statsConfig;
    }

    @Override
    public void saveAvailableStatsConfig(@Nonnull AvailableDataStatisticsConfig config) {
        log.info("Saving available stats config {}", config);
        if (config.getEnvironmentId() == null) {
            log.error(TdmAvailableStatisticEnvironmentIdException.DEFAULT_MESSAGE);
            throw new TdmAvailableStatisticEnvironmentIdException();
        }
        if (config.getSystemId() == null) {
            log.error(TdmAvailableStatisticSystemIdException.DEFAULT_MESSAGE);
            throw new TdmAvailableStatisticSystemIdException();
        }
        TestAvailableDataMonitoring monitoringConfig = availableDataMonitoringRepository
                .findByEnvironmentIdAndSystemId(config.getEnvironmentId(), config.getSystemId());
        if (monitoringConfig == null) {
            monitoringConfig = new TestAvailableDataMonitoring(config.getSystemId(), config.getEnvironmentId());
        }
        if (!config.getColumnKeys().contains(config.getActiveColumnKey())) {
            log.error(String.format(TdmAvailableStatisticColumnException.DEFAULT_MESSAGE, config.getActiveColumnKey()));
            throw new TdmAvailableStatisticColumnException(config.getActiveColumnKey());
        }
        if (StringUtils.isNotEmpty(config.getActiveColumnKey())) {
            monitoringConfig.setActiveColumn(config.getActiveColumnKey());
        } else {
            log.error(TdmAvailableStatisticActiveColumnException.DEFAULT_MESSAGE);
            throw new TdmAvailableStatisticActiveColumnException();
        }
        if (StringUtils.isNotEmpty(config.getDescription())) {
            monitoringConfig.setDescription(config.getDescription());
        } else {
            monitoringConfig.setDescription("Description is empty");
        }
        if (!CollectionUtils.isEmpty(config.getTablesColumns())) {
            log.trace("Deleting available data monitoring for environmentId {} and systemId {}",
                    config.getEnvironmentId(), config.getSystemId());
            availableDataMonitoringRepository
                    .deleteTestAvailableDataMonitoringsByEnvironmentIdAndSystemId(config.getEnvironmentId(),
                            config.getSystemId());
            schedulerService.deleteJob(new JobKey(String.valueOf(config.getSystemId()), SCHEDULE_AVAILABLE_DATA_GROUP));
            log.trace("Saving monitoring config {}", monitoringConfig);
            availableDataMonitoringRepository.save(monitoringConfig);
            List<String> tableNames = catalogRepository
                    .findAllByEnvironmentIdAndSystemId(config.getEnvironmentId(), config.getSystemId())
                    .stream()
                    .map(table -> table.getTableName().toLowerCase())
                    .collect(Collectors.toList());
            log.trace("Deleting all table by names in tableColumnValuesRepository. Table names: {}", tableNames);
            tableColumnValuesRepository.deleteAllByTableNameIn(tableNames);
            log.trace("Saving table column values by config {}", config);
            tableColumnValuesRepository.saveAll(config.getTablesColumns());
        } else {
            throw new NullPointerException("Tables columns cannot be empty.");
        }
        log.debug("Available stats config was saved {}", config);
    }

    private List<String> getAllColumnNamesBySystemId(@Nonnull UUID systemId) {
        log.debug("Getting all column names by systemId {}", systemId);
        List<String> allColumnsBySystemId = testDataService.getAllColumnNamesBySystemId(systemId);
        allColumnsBySystemId.removeAll(INTERNAL_COLUMNS);
        log.debug("Received list of column names by systemId {}", allColumnsBySystemId);
        return allColumnsBySystemId;
    }

    @Override
    public AvailableDataByColumnStats getAvailableDataInColumn(@Nonnull UUID systemId, @Nonnull UUID environmentId) {
        log.info("Getting available data in column by environmentId {} and systemId {}", environmentId, systemId);
        AvailableDataByColumnStats statistic = new AvailableDataByColumnStats();
        AvailableDataStatisticsConfig config = getAvailableStatsConfig(systemId, environmentId);
        statistic.setDescription(config.getDescription());
        if (CollectionUtils.isEmpty(config.getTablesColumns())) {
            throw new TdmSearchAvailableStatisticConfigException();
        }
        config.getTablesColumns().stream().forEach(columnValues -> {
            TableAvailableDataStats tableStats = new TableAvailableDataStats();
            tableStats.setTableName(columnValues.getTableName());
            tableStats.setTableTitle(columnValues.getTableTitle());
            List<Object[]> result = entityManager
                    .createNativeQuery(availableDataQuery(columnValues, config.getActiveColumnKey())).getResultList();
            result.forEach(resultRow -> {
                String key = String.valueOf(resultRow[0]);
                if (columnValues.getValues().contains(key)) {
                    tableStats.getOptions().put(key, ((Number) resultRow[1]).intValue());
                }
            });
            columnValues.getValues().stream().forEach(value -> tableStats.getOptions().putIfAbsent(value, 0));
            statistic.addTableStatistics(tableStats);
        });
        log.debug("Received available data statistic: {}", statistic);
        return statistic;
    }

    @Override
    public TestAvailableDataMonitoring getAvailableDataMonitoringConfig(
            @Nonnull UUID systemId, @Nonnull UUID environmentId) {
        log.info("Getting available data monitoring config by environmentId {} and systemId {}", environmentId,
                systemId);
        TestAvailableDataMonitoring monitoringItem = availableDataMonitoringRepository
                .findByEnvironmentIdAndSystemId(environmentId, systemId);
        if (monitoringItem == null) {
            throw new TdmSearchAvailableMonitoringConfigException();
        }
        log.debug("Available data monitoring config for envId {} and systemId {}: {}", environmentId,
                systemId, monitoringItem);
        return monitoringItem;
    }

    @Override
    public void saveAvailableDataMonitoringConfig(@Nonnull TestAvailableDataMonitoring monitoringConfig)
            throws Exception {
        log.info("Saving available data monitoring config {}", monitoringConfig);
        if (getAvailableDataMonitoringConfig(
                monitoringConfig.getSystemId(),
                monitoringConfig.getEnvironmentId()) == null) {
            log.error(TdmSearchAvailableMonitoringConfigException.DEFAULT_MESSAGE);
            throw new TdmSearchAvailableMonitoringConfigException();
        }
        CronExpression.validateExpression(monitoringConfig.getSchedule());
        ValidateCronExpression.validate(monitoringConfig.getSchedule());
        availableDataMonitoringRepository.save(monitoringConfig);
        rescheduleAvailableData(monitoringConfig);
        log.info("Available data monitoring was rescheduled.");
    }

    @Override
    public void deleteAvailableDataMonitoringConfig(@Nonnull UUID systemId, @Nonnull UUID environmentId) {
        log.info("Deleting available data statistics schedule for system ID: {}", systemId);
        TestAvailableDataMonitoring config = getAvailableDataMonitoringConfig(systemId, environmentId);
        config.setSchedule(StringUtils.EMPTY);
        config.setScheduled(false);
        config.setRecipients(StringUtils.EMPTY);
        availableDataMonitoringRepository.save(config);
        schedulerService.deleteJob(new JobKey(String.valueOf(systemId), SCHEDULE_AVAILABLE_DATA_GROUP));
        log.info("The statistics available data schedule successfully deleted.");
    }
}
