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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.model.LazyProject;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.model.Project;
import org.qubership.atp.tdm.env.configurator.model.System;
import org.qubership.atp.tdm.model.ProjectInformation;
import org.qubership.atp.tdm.model.mail.charts.ChartSeries;
import org.qubership.atp.tdm.model.statistics.AvailableDataStatisticsConfig;
import org.qubership.atp.tdm.model.statistics.ConsumedStatistics;
import org.qubership.atp.tdm.model.statistics.ConsumedStatisticsItem;
import org.qubership.atp.tdm.model.statistics.DateStatistics;
import org.qubership.atp.tdm.model.statistics.DateStatisticsItem;
import org.qubership.atp.tdm.model.statistics.GeneralStatisticsItem;
import org.qubership.atp.tdm.model.statistics.OutdatedStatistics;
import org.qubership.atp.tdm.model.statistics.OutdatedStatisticsItem;
import org.qubership.atp.tdm.model.statistics.TestAvailableDataMonitoring;
import org.qubership.atp.tdm.model.statistics.TestDataTableMonitoring;
import org.qubership.atp.tdm.model.statistics.TestDataTableUsersMonitoring;
import org.qubership.atp.tdm.model.statistics.UserGeneralStatisticsItem;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticRequest;
import org.qubership.atp.tdm.model.statistics.UsersOccupyStatisticResponse;
import org.qubership.atp.tdm.model.statistics.available.AvailableDataByColumnStats;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReportElement;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReportEnvironment;
import org.qubership.atp.tdm.model.statistics.report.StatisticsReportObject;
import org.qubership.atp.tdm.model.statistics.report.UsersStatisticsReportElement;
import org.qubership.atp.tdm.model.statistics.report.UsersStatisticsReportObject;
import org.qubership.atp.tdm.model.table.TableColumnValues;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.repo.TestAvailableDataMonitoringRepository;
import org.qubership.atp.tdm.repo.TestDataUsersMonitoringRepository;
import org.qubership.atp.tdm.utils.AvailableStatisticUtils;
import org.qubership.atp.tdm.utils.DataUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.jdbc.Sql;

@Sql({"/scripts.sql"})
public class StatisticsServiceTest extends AbstractTestDataTest {

    private static final List<String> COLUMN_NAMES = Arrays.asList("SIM", "Status", "Partner", "Partner category",
            "Partner ID", "Operator ID", "ENVIRONMENT", "Assignment");
    private static final UUID projectId = UUID.randomUUID();

    private static final String TABLE_TITLE = "Test Data";
    private static final String DEFAULT_ASSIGNMENT = "across all";
    private static final String TABLE_NAME_FIRST = "test_table_statistic_availability_first";
    private static final String TABLE_NAME_SECOND = "test_table_statistic_availability_second";
    private final String cron = "0 0 9 ? * *";

    private static final UUID systemIdSecond = UUID.randomUUID();
    private static final UUID environmentIdSecond = UUID.randomUUID();

    private static final System systemSecond = new System() {{
        setName("Test System second");
        setId(systemIdSecond);
        List<Connection> connections = new ArrayList<>();
        connections.add(dbConnection);
        connections.add(httpConnection);
        setConnections(connections);
    }};

    private static final LazySystem lazySystemSecond = new LazySystem() {{
        setName("Test System second");
        setId(systemIdSecond);
    }};

    private static final LazyEnvironment lazyEnvironmentSecond = new LazyEnvironment() {{
        setName("Test Environment second");
        setId(environmentIdSecond);
        setProjectId(projectId);
        setSystems(Collections.singletonList(systemSecond.getId().toString()));
    }};

    private static final List<LazyEnvironment> lazyEnvironments = Arrays.asList(lazyEnvironment, lazyEnvironmentSecond);

    private static final LazyProject lazyProject = new LazyProject() {{
        setName("Test Statistics Project");
        setId(projectId);
    }};

    private static final Project project = new Project() {{
        setName("Test Statistics Project");
        setId(projectId);
        setEnvironments(Collections.singletonList(environment));
    }};

    private UsersOccupyStatisticRequest usersOccupyStatisticRequest;
    private GeneralStatisticsItem availableItemFirst;
    private GeneralStatisticsItem availableItemSecond;
    private ConsumedStatisticsItem consumedItemFirstDays;
    private ConsumedStatisticsItem consumedItemFirstWeeks;
    private ConsumedStatisticsItem consumedItemFirstMonths;
    private ConsumedStatisticsItem consumedItemSecond;
    private OutdatedStatisticsItem outdatedItemFirst;
    private OutdatedStatisticsItem outdatedItemFirstWeeks;
    private OutdatedStatisticsItem outdatedItemFirstMonths;
    private OutdatedStatisticsItem outdatedItemSecond;
    private DateStatisticsItem createdWhenItemFirst;

    @Value("${highcharts.template.path}")
    String highchartJsonPath;
    @Value("${highcharts.template}")
    String highchartJsonTemplate;

    @Autowired
    protected TestDataUsersMonitoringRepository testDataUsersMonitoringRepository;

    @Autowired
    private TestAvailableDataMonitoringRepository availableDataMonitoringRepository;

    public void setUp() throws RuntimeException {
        deleteTestDataTableIfExists(TABLE_NAME_FIRST);
        deleteTestDataTableIfExists(TABLE_NAME_SECOND);
        catalogRepository.deleteByTableName(TABLE_NAME_FIRST);
        catalogRepository.deleteByTableName(TABLE_NAME_SECOND);

        projectInformationService.saveProjectInformation(new ProjectInformation(projectId, "GMT+03:00", "d MMM yyyy", "hh:mm:ss a", 1));
        when(environmentsService.getFullProject(any())).thenReturn(project);
        try {
            when(gitEnvironmentsService.getFullProject(any())).thenReturn(project);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // create table for env first
        createTestDataTableCatalog(projectId, systemId, environmentId, TABLE_TITLE, TABLE_NAME_FIRST);
        createTestDataTable(TABLE_NAME_FIRST);
        // occupy data in the table
        TestDataTable tableFirst = testDataService.getTestData(TABLE_NAME_FIRST);
        List<UUID> rowIdsToOccupyFirst = extractRowIds(tableFirst.getData().subList(0, 1));
        testDataService.occupyTestData(TABLE_NAME_FIRST, "TestUser", rowIdsToOccupyFirst);
        // create test objects
        availableItemFirst = new GeneralStatisticsItem(TABLE_TITLE, 5L, 1L, 1L, 6L);
        availableItemFirst.setEnvironment(environment.getName());
        availableItemFirst.setSystem(system.getName());
        consumedItemFirstDays = new ConsumedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(1L);
                    add(0L);
                }});
        consumedItemFirstDays.setEnvironment(environment.getName());
        consumedItemFirstDays.setSystem(system.getName());
        consumedItemFirstWeeks = new ConsumedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(1L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                }});
        consumedItemFirstWeeks.setEnvironment(environment.getName());
        consumedItemFirstWeeks.setSystem(system.getName());
        consumedItemFirstMonths = new ConsumedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(1L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                }});
        consumedItemFirstMonths.setEnvironment(environment.getName());
        consumedItemFirstMonths.setSystem(system.getName());
        outdatedItemFirst = new OutdatedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(5L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(1L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(0L);
                    add(0L);
                }});
        outdatedItemFirst.setEnvironment(environment.getName());
        outdatedItemFirst.setSystem(system.getName());
        outdatedItemFirstWeeks = new OutdatedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(5L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(1L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                }});
        outdatedItemFirstWeeks.setEnvironment(environment.getName());
        outdatedItemFirstWeeks.setSystem(system.getName());
        outdatedItemFirstMonths = new OutdatedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(5L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(1L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                    add(0L);
                }});
        outdatedItemFirstMonths.setEnvironment(environment.getName());
        outdatedItemFirstMonths.setSystem(system.getName());
        createdWhenItemFirst = new DateStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(7L);
                    add(0L);
                }});
        createdWhenItemFirst.setEnvironment(environment.getName());
        createdWhenItemFirst.setSystem(system.getName());
        // create table for env second
        createTestDataTableCatalog(projectId, systemIdSecond, environmentIdSecond, TABLE_TITLE, TABLE_NAME_SECOND);
        createTestDataTable(TABLE_NAME_SECOND);
        // occupy data in the table
        TestDataTable tableSecond = testDataService.getTestData(TABLE_NAME_SECOND);
        List<UUID> rowIdsToOccupySecond = extractRowIds(tableSecond.getData().subList(0, 2));
        testDataService.occupyTestData(TABLE_NAME_SECOND, "TestUser", rowIdsToOccupySecond);
        // create test objects
        availableItemSecond = new GeneralStatisticsItem(TABLE_TITLE, 4L, 2L, 2L, 6L);
        availableItemSecond.setEnvironment(lazyEnvironmentSecond.getName());
        availableItemSecond.setSystem(systemSecond.getName());
        consumedItemSecond = new ConsumedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(2L);
                    add(0L);
                }});
        consumedItemSecond.setEnvironment(lazyEnvironmentSecond.getName());
        consumedItemSecond.setSystem(systemSecond.getName());
        outdatedItemSecond = new OutdatedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(4L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(2L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(0L);
                    add(0L);
                }});
        outdatedItemSecond.setEnvironment(lazyEnvironmentSecond.getName());
        outdatedItemSecond.setSystem(systemSecond.getName());
        DateStatisticsItem createdWhenItemSecond = new DateStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(2L);
                    add(0L);
                }});
        createdWhenItemSecond.setEnvironment(lazyEnvironmentSecond.getName());
        createdWhenItemSecond.setSystem(systemSecond.getName());

        usersOccupyStatisticRequest = new UsersOccupyStatisticRequest(
                projectId,
                0L,
                10L,
                null,
                null,
                LocalDate.now().minusDays(1L).toString(),
                LocalDate.now().plusDays(1L).toString());

        when(environmentsService.getLazyProjectById(any())).thenReturn(lazyProject);
        when(environmentsService.getLazyEnvironments(any())).thenReturn(lazyEnvironments);
        when(environmentsService.getLazySystemById(environmentId, systemId)).thenReturn(lazySystem);
        when(environmentsService.getLazySystemById(environmentIdSecond, systemIdSecond)).thenReturn(lazySystemSecond);
        when(environmentsService.getLazyEnvironment(environmentId)).thenReturn(lazyEnvironment);
        when(environmentsService.getLazyEnvironment(environmentIdSecond)).thenReturn(lazyEnvironmentSecond);
        when(environmentsService.getEnvNameById(environmentId)).thenReturn("Test Environment");
        when(environmentsService.getEnvNameById(environmentIdSecond)).thenReturn("Test Environment second");

        when(gitEnvironmentsService.getLazyProjectById(any())).thenReturn(lazyProject);
        try {
            when(gitEnvironmentsService.getLazyEnvironments(any())).thenReturn(lazyEnvironments);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(gitEnvironmentsService.getLazySystemById(environmentId, systemId)).thenReturn(lazySystem);
        when(gitEnvironmentsService.getLazySystemById(environmentIdSecond, systemIdSecond)).thenReturn(lazySystemSecond);
        when(gitEnvironmentsService.getLazyEnvironment(environmentId)).thenReturn(lazyEnvironment);
        when(gitEnvironmentsService.getLazyEnvironment(environmentIdSecond)).thenReturn(lazyEnvironmentSecond);
        when(gitEnvironmentsService.getEnvNameById(environmentId)).thenReturn("Test Environment");
        when(gitEnvironmentsService.getEnvNameById(environmentIdSecond)).thenReturn("Test Environment second");
    }

    protected void cleanUp() {
        deleteTestDataTable(TABLE_NAME_FIRST);
        deleteTestDataTable(TABLE_NAME_SECOND);
        catalogRepository.deleteByTableName(TABLE_NAME_FIRST);
        catalogRepository.deleteByTableName(TABLE_NAME_SECOND);
    }

    protected void mockEnvForStatistics() {
        when(environmentsService.getLazyEnvironment(any())).thenReturn(lazyEnvironment);
        when(environmentsService.getLazySystemById(any(), any())).thenReturn(lazySystem);
        when(environmentsService.getEnvNameById(environmentId)).thenReturn("Test Environment");

        when(gitEnvironmentsService.getLazyEnvironment(any())).thenReturn(lazyEnvironment);
        when(gitEnvironmentsService.getLazySystemById(any(), any())).thenReturn(lazySystem);
        when(gitEnvironmentsService.getEnvNameById(environmentId)).thenReturn("Test Environment");
    }

    @Test
    public void statisticsService_checkAvailabilityOnEnvironment_returnsAvailabilityStatistics() {
        setUp();
        List<GeneralStatisticsItem> expectedStatistics = new ArrayList<>();
        expectedStatistics.add(availableItemFirst);
        List<GeneralStatisticsItem> actualStatistics = statisticsService.getTestDataAvailability(projectId, systemId);
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void availableStatisticsUtils_buildConfiguration_bodyCorrect() throws IOException {
        ArrayList<ChartSeries> charts = new ArrayList<>();
        charts.add(new ChartSeries(true, Arrays.asList(1, 2, 3), "columnaName1", "#000FFF"));
        charts.add(new ChartSeries(true, Arrays.asList(4, 5, 6), "columnaName2", "#FFF000"));
        String act = AvailableStatisticUtils.buildHighChartConfigurationBody(
                getResourcesFile(highchartJsonTemplate).getAbsolutePath(),
                Arrays.asList("Category1", "Category2"), charts);

        String er = readErFromFile(getResourcesFile("highchartRequestBody.json").getAbsolutePath());
        Assertions.assertEquals(er, act);
    }

    private String readErFromFile(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)));
    }

    @Test
    public void statisticsService_checkAvailabilityForProjectD_returnsAvailabilityStatistics() {
        setUp();
        List<GeneralStatisticsItem> expectedStatistics = new ArrayList<>();
        GeneralStatisticsItem statistics = new GeneralStatisticsItem(TABLE_TITLE, 9L, 3L, 0L, 12L);
        statistics.setEnvironment(DEFAULT_ASSIGNMENT);
        statistics.setSystem(DEFAULT_ASSIGNMENT);
        statistics.setDetails(Arrays.asList(availableItemFirst, availableItemSecond));
        expectedStatistics.add(statistics);
        List<GeneralStatisticsItem> actualStatistics = statisticsService.getTestDataAvailability(projectId, null);
        List<GeneralStatisticsItem> details =
                actualStatistics.get(0).getDetails().stream()
                        .sorted(Comparator.comparingLong(GeneralStatisticsItem::getOccupied))
                        .collect(Collectors.toList());
        actualStatistics.forEach(x -> x.setDetails(details));

        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOnEnvironmentDays_returnsConsumptionStatistics() {
        setUp();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().format(formatter));
        dates.add(LocalDate.now().plusDays(1).format(formatter));
        List<ConsumedStatisticsItem> expectedConsumedList = new ArrayList<>();
        expectedConsumedList.add(consumedItemFirstDays);
        cleanUp();
        ConsumedStatistics expectedStatistics = new ConsumedStatistics(dates, expectedConsumedList);
        ConsumedStatistics actualStatistics = statisticsService.getTestDataConsumption(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusDays(1));
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOnEnvironmentWeeks_returnsConsumptionStatistics() {
        setUp();
        List<String> dates = DataUtils.getStatisticsInterval(LocalDate.now(),
                LocalDate.now().plusWeeks(1));
        List<ConsumedStatisticsItem> expectedConsumedList = new ArrayList<>();
        expectedConsumedList.add(consumedItemFirstWeeks);
        cleanUp();
        ConsumedStatistics expectedStatistics = new ConsumedStatistics(dates, expectedConsumedList);
        ConsumedStatistics actualStatistics = statisticsService.getTestDataConsumption(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusWeeks(1));
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOnEnvironmentMonths_returnsConsumptionStatistics() {
        setUp();
        List<String> dates = DataUtils.getStatisticsInterval(LocalDate.now(),
                LocalDate.now().plusMonths(1));
        List<ConsumedStatisticsItem> expectedConsumedList = new ArrayList<>();
        expectedConsumedList.add(consumedItemFirstMonths);
        cleanUp();
        ConsumedStatistics expectedStatistics = new ConsumedStatistics(dates, expectedConsumedList);
        ConsumedStatistics actualStatistics = statisticsService.getTestDataConsumption(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOnEnvironmentYears_returnsConsumptionStatistics() {
        setUp();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().format(formatter) + " year");
        dates.add(LocalDate.now().plusYears(1).format(formatter) + " year");
        List<ConsumedStatisticsItem> expectedConsumedList = new ArrayList<>();
        expectedConsumedList.add(consumedItemFirstDays);
        cleanUp();
        ConsumedStatistics expectedStatistics = new ConsumedStatistics(dates, expectedConsumedList);
        ConsumedStatistics actualStatistics = statisticsService.getTestDataConsumption(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusYears(1));
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingForProjectDays_returnsConsumptionStatistics() {
        setUp();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().format(formatter));
        dates.add(LocalDate.now().plusDays(1).format(formatter));
        ConsumedStatisticsItem statisticsItem = new ConsumedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(3L);
                    add(0L);
                }});
        statisticsItem.setEnvironment(DEFAULT_ASSIGNMENT);
        statisticsItem.setSystem(DEFAULT_ASSIGNMENT);
        statisticsItem.setDetails(Arrays.asList(consumedItemFirstDays, consumedItemSecond));
        List<ConsumedStatisticsItem> statistics = new ArrayList<>();
        statistics.add(statisticsItem);
        cleanUp();
        ConsumedStatistics expectedStatistics = new ConsumedStatistics(dates, statistics);
        ConsumedStatistics actualStatistics = statisticsService.getTestDataConsumption(projectId, null,
                LocalDate.now(), LocalDate.now().plusDays(1));
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOutdatedOnEnvironmentDays_returnsConsumptionStatistics() {
        setUp();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().format(formatter));
        dates.add(LocalDate.now().plusDays(1).format(formatter));
        List<OutdatedStatisticsItem> expectedConsumedOutdatedList = new ArrayList<>();
        expectedConsumedOutdatedList.add(outdatedItemFirst);
        OutdatedStatistics expectedStatistics = new OutdatedStatistics(dates, expectedConsumedOutdatedList);
        OutdatedStatistics actualStatistics = statisticsService.getTestDataConsumptionWhitOutdated(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusDays(1), 1);
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOutdatedOnEnvironmentWeeks_returnsConsumptionStatistics() {
        setUp();
        List<String> dates = DataUtils.getStatisticsInterval(LocalDate.now(),
                LocalDate.now().plusWeeks(1));
        List<OutdatedStatisticsItem> expectedConsumedOutdatedList = new ArrayList<>();
        expectedConsumedOutdatedList.add(outdatedItemFirstWeeks);
        OutdatedStatistics expectedStatistics = new OutdatedStatistics(dates, expectedConsumedOutdatedList);
        OutdatedStatistics actualStatistics = statisticsService.getTestDataConsumptionWhitOutdated(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusWeeks(1), 1);
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOutdatedOnEnvironmentMonths_returnsConsumptionStatistics() {
        setUp();
        List<String> dates = DataUtils.getStatisticsInterval(LocalDate.now(),
                LocalDate.now().plusMonths(1));
        List<OutdatedStatisticsItem> expectedConsumedOutdatedList = new ArrayList<>();
        expectedConsumedOutdatedList.add(outdatedItemFirstMonths);
        OutdatedStatistics expectedStatistics = new OutdatedStatistics(dates, expectedConsumedOutdatedList);
        OutdatedStatistics actualStatistics = statisticsService.getTestDataConsumptionWhitOutdated(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusMonths(1), 1);
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOutdatedOnEnvironmentYears_returnsConsumptionStatistics() {
        setUp();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().format(formatter) + " year");
        dates.add(LocalDate.now().plusYears(1).format(formatter) + " year");
        List<OutdatedStatisticsItem> expectedConsumedOutdatedList = new ArrayList<>();
        expectedConsumedOutdatedList.add(outdatedItemFirst);
        OutdatedStatistics expectedStatistics = new OutdatedStatistics(dates, expectedConsumedOutdatedList);
        OutdatedStatistics actualStatistics = statisticsService.getTestDataConsumptionWhitOutdated(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusYears(1), 1);
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkConsumingOutdatedForProjectDays_returnsConsumptionStatistics() {
        setUp();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().format(formatter));
        dates.add(LocalDate.now().plusDays(1).format(formatter));
        OutdatedStatisticsItem statisticsItem = new OutdatedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(9L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(3L);
                    add(0L);
                }},
                new ArrayList<Long>() {{
                    add(0L);
                    add(0L);
                }});
        statisticsItem.setEnvironment(DEFAULT_ASSIGNMENT);
        statisticsItem.setSystem(DEFAULT_ASSIGNMENT);
        statisticsItem.setDetails(Arrays.asList(outdatedItemFirst, outdatedItemSecond));
        List<OutdatedStatisticsItem> statistics = new ArrayList<>();
        statistics.add(statisticsItem);
        OutdatedStatistics expectedStatistics = new OutdatedStatistics(dates, statistics);
        OutdatedStatistics actualStatistics = statisticsService.getTestDataConsumptionWhitOutdated(projectId, null,
                LocalDate.now(), LocalDate.now().plusDays(1), 1);

        List<OutdatedStatisticsItem> details = actualStatistics.getItems().get(0).getDetails();
        List<OutdatedStatisticsItem> detailsSorted = new ArrayList<>();
        OutdatedStatisticsItem buf = null;
        for (OutdatedStatisticsItem x : details) {
            if (x.getConsumed().get(0) == 1L) {
                detailsSorted.add(x);
            } else {
                buf = x;
            }
        }
        detailsSorted.add(buf);
        actualStatistics.getItems().get(0).setDetails(detailsSorted);

        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkCreatedWhenOnEnvironmentDays_returnsCreatedWhenStatistics() {
        setUp();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().format(formatter));
        dates.add(LocalDate.now().plusDays(1).format(formatter));
        List<DateStatisticsItem> expectedCreatedWhenList = new ArrayList<>();
        expectedCreatedWhenList.add(createdWhenItemFirst);
        deleteTestDataTable(TABLE_NAME_SECOND);
        DateStatistics expectedStatistics = new DateStatistics(dates, expectedCreatedWhenList);
        DateStatistics actualStatistics = statisticsService.getTestDataCreatedWhen(projectId, systemId,
                LocalDate.now(), LocalDate.now().plusDays(1));
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkCreatedWhenForProjectDays_returnsCreatedWhenStatistics() {
        setUp();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().format(formatter));
        dates.add(LocalDate.now().plusDays(1).format(formatter));
        ConsumedStatisticsItem statisticsItem = new ConsumedStatisticsItem(TABLE_TITLE,
                new ArrayList<Long>() {{
                    add(3L);
                    add(0L);
                }});
        statisticsItem.setEnvironment(DEFAULT_ASSIGNMENT);
        statisticsItem.setSystem(DEFAULT_ASSIGNMENT);
        statisticsItem.setDetails(Arrays.asList(consumedItemFirstDays, consumedItemSecond));
        List<ConsumedStatisticsItem> statistics = new ArrayList<>();
        statistics.add(statisticsItem);
        cleanUp();
        ConsumedStatistics expectedStatistics = new ConsumedStatistics(dates, statistics);
        ConsumedStatistics actualStatistics = statisticsService.getTestDataConsumption(projectId, null,
                LocalDate.now(), LocalDate.now().plusDays(1));
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkReportStatistics_returnsReportStatistics() {
        setUp();
        final int threshold = 5;
        List<StatisticsReportElement> upToThreshold = new ArrayList<>();
        StatisticsReportElement reportElementFirst = new StatisticsReportElement();
        StatisticsReportEnvironment envFirst = new StatisticsReportEnvironment(
                environment.getName(), system.getName());

        GeneralStatisticsItem dataFirst = new GeneralStatisticsItem(TABLE_TITLE, 5L, 1L, 1L, 6L);
        reportElementFirst.setEnvironment(envFirst);
        reportElementFirst.setData(Collections.singletonList(dataFirst));
        upToThreshold.add(reportElementFirst);
        List<StatisticsReportElement> downToThreshold = new ArrayList<>();
        StatisticsReportElement reportElementSecond = new StatisticsReportElement();
        StatisticsReportEnvironment envSecond = new StatisticsReportEnvironment(
                lazyEnvironmentSecond.getName(), systemSecond.getName());
        GeneralStatisticsItem dataSecond = new GeneralStatisticsItem(TABLE_TITLE, 4L, 2L, 2L, 6L);
        reportElementSecond.setEnvironment(envSecond);
        reportElementSecond.setData(Collections.singletonList(dataSecond));
        downToThreshold.add(reportElementSecond);
        StatisticsReportObject expectedStatistics = new StatisticsReportObject();
        expectedStatistics.setProjectName(lazyProject.getName());
        expectedStatistics.setUpToThreshold(upToThreshold);
        expectedStatistics.setDownToThreshold(downToThreshold);
        StatisticsReportObject actualStatistics = statisticsService.getTestDataMonitoringStatistics(projectId,
                threshold);
        cleanUp();
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticsService_checkUserStatisticsReport_returnsUserStatisticsReport() {
        setUp();
        TestDataTableUsersMonitoring usersMonitoring = getTestDataTableUsersMonitoring(cron);

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
        List<String> dates = new ArrayList<>();
        dates.add(LocalDate.now().minusDays(2).format(formatter));
        dates.add(LocalDate.now().minusDays(1).format(formatter));
        dates.add(LocalDate.now().format(formatter));

        UserGeneralStatisticsItem itemFirst = new UserGeneralStatisticsItem(
                lazyEnvironment.getName(), lazySystem.getName(),
                TABLE_TITLE, new ArrayList<Long>() {{
            add(0L);
            add(0L);
            add(1L);
        }});
        UserGeneralStatisticsItem itemSecond = new UserGeneralStatisticsItem(
                lazyEnvironmentSecond.getName(), lazySystemSecond.getName(),
                TABLE_TITLE, new ArrayList<Long>() {{
            add(0L);
            add(0L);
            add(2L);
        }});
        List<UserGeneralStatisticsItem> items = new ArrayList<>();
        items.add(itemFirst);
        items.add(itemSecond);

        UsersStatisticsReportElement usersStatisticsReportElement =
                new UsersStatisticsReportElement("TestUser", dates, items);
        List<UsersStatisticsReportElement> elements = new ArrayList<>();
        elements.add(usersStatisticsReportElement);

        UsersStatisticsReportObject expect = new UsersStatisticsReportObject();
        expect.setProjectName("Test Statistics Project");
        expect.setElements(elements);

        UsersStatisticsReportObject actual = statisticsService.getUsersStatisticsReport(usersMonitoring);
        cleanUp();
        Assertions.assertEquals(expect, actual);
    }

    @Test
    public void statisticsService_checkUserStatisticsReport_returnsEmptyUserStatisticsReport() {
        when(environmentsService.getLazyProjectById(any())).thenReturn(lazyProject);
        when(gitEnvironmentsService.getLazyProjectById(any())).thenReturn(lazyProject);
        TestDataTableUsersMonitoring usersMonitoring = getTestDataTableUsersMonitoring(cron);

        UsersStatisticsReportObject expect = new UsersStatisticsReportObject();
        expect.setProjectName("Test Statistics Project");
        expect.setElements(null);

        UsersStatisticsReportObject actual = statisticsService.getUsersStatisticsReport(usersMonitoring);
        cleanUp();
        Assertions.assertEquals(expect, actual);
    }

    @Test
    public void statisticsService_checkNextScheduledRun_returnsNextScheduledRun() throws ParseException {
        final String cronExpression = "0 0 6 * * ?";
        final String pattern = "E MMM dd HH:mm:ss z yyyy";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, Locale.US);
        LocalDateTime expectedDateTime = LocalDate.now().atTime(6, 0);
        if (LocalDateTime.now().isAfter(expectedDateTime)) {
            expectedDateTime = expectedDateTime.plusDays(1);
        }
        String expectedNextRun = simpleDateFormat.format(convertToDateViaInstant(expectedDateTime));
        String actualNextRun = statisticsService.getNextScheduledRun(cronExpression);
        Assertions.assertEquals(expectedNextRun, actualNextRun);
    }

    private Date convertToDateViaInstant(LocalDateTime dateToConvert) {
        return Date.from(dateToConvert.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    public void statisticsService_saveAndDeleteMonitoringSchedule_scheduleSavedAndDeleted() {
        TestDataTableMonitoring monitoring = getTestDataTableMonitoring(cron);
        statisticsService.saveMonitoringSchedule(monitoring);
        Assertions.assertEquals(monitoring, statisticsService.getMonitoringSchedule(projectId));
        statisticsService.deleteMonitoringSchedule(monitoring);
        Assertions.assertEquals(new TestDataTableMonitoring(), statisticsService.getMonitoringSchedule(projectId));
    }

    @Test
    public void statisticsService_checkConsumingForDroppedTable_returnsConsumptionStatistics() {
        setUp();
        ConsumedStatistics expectedStatistics = statisticsService.getTestDataConsumption(projectId, null,
                LocalDate.now(), LocalDate.now().plusDays(1));
        cleanUp();
        ConsumedStatistics actualStatistics = statisticsService.getTestDataConsumption(projectId, null,
                LocalDate.now(), LocalDate.now().plusDays(1));
        Assertions.assertEquals(expectedStatistics, actualStatistics);
    }

    @Test
    public void statisticService_getStatisticsByUsers_returnRightRowsCount() {
        setUp();
        mockEnvForStatistics();
        UsersOccupyStatisticResponse respone = statisticsService.getOccupiedDataByUsers(usersOccupyStatisticRequest);

        Assertions.assertEquals(2, respone.getRecords());
    }

    @Test
    public void statisticService_getStatisticsByUsers_returnOrderedTableTitle() {
        setUp();
        mockEnvForStatistics();
        UsersOccupyStatisticResponse response = statisticsService.getOccupiedDataByUsers(usersOccupyStatisticRequest);

        Assertions.assertEquals("Test Data", response.getData().get(0).getContext());
    }

    @Test
    public void statisticService_getStatisticsByUsers_returnUsersStatistics() {
        setUp();
        mockEnvForStatistics();
        UsersOccupyStatisticResponse response = statisticsService.getOccupiedDataByUsers(usersOccupyStatisticRequest);

        Assertions.assertEquals("TestUser", response.getData().get(1).getUserName());
    }

    @Test
    public void statisticsService_getUsersMonitoringSchedule_successfulGet() {
        TestDataTableUsersMonitoring usersMonitoring = getTestDataTableUsersMonitoring(cron);
        statisticsService.saveUsersMonitoringSchedule(usersMonitoring);
        TestDataTableUsersMonitoring actual = statisticsService.getUsersMonitoringSchedule(projectId);
        Assertions.assertEquals(usersMonitoring, actual);
    }

    @Test
    public void statisticsService_saveUsersMonitoringSchedule_successfulGet() {
        UUID newProject = UUID.randomUUID();
        TestDataTableUsersMonitoring usersMonitoring = getTestDataTableUsersMonitoring(cron);
        usersMonitoring.setProjectId(newProject);
        statisticsService.saveUsersMonitoringSchedule(usersMonitoring);
        Assertions.assertEquals(statisticsService.getUsersMonitoringSchedule(newProject), usersMonitoring);
    }

    @Test
    public void deleteUsersMonitoringSchedule() {
        TestDataTableUsersMonitoring usersMonitoring = getTestDataTableUsersMonitoring(cron);
        statisticsService.saveUsersMonitoringSchedule(usersMonitoring);
        statisticsService.deleteUsersMonitoringSchedule(usersMonitoring);
        TestDataTableUsersMonitoring actual = statisticsService.getUsersMonitoringSchedule(projectId);
        Assertions.assertEquals(new TestDataTableUsersMonitoring(), actual);
    }

    private TestDataTableMonitoring getTestDataTableMonitoring(String cron) {
        TestDataTableMonitoring monitoring = new TestDataTableMonitoring();
        monitoring.setProjectId(projectId);
        monitoring.setEnabled(true);
        monitoring.setCronExpression(cron);
        monitoring.setThreshold(10);
        monitoring.setRecipients("example@example.com");
        return monitoring;
    }

    private TestDataTableUsersMonitoring getTestDataTableUsersMonitoring(String cron) {
        TestDataTableUsersMonitoring usersMonitoring = new TestDataTableUsersMonitoring();
        usersMonitoring.setProjectId(projectId);
        usersMonitoring.setEnabled(true);
        usersMonitoring.setCronExpression(cron);
        usersMonitoring.setRecipients("example@example.com,example1@example.com");
        usersMonitoring.setDaysCount(2);
        usersMonitoring.setHtmlReport(true);
        usersMonitoring.setCsvReport(true);
        return usersMonitoring;
    }

    @Test
    public void availableDataMonitoring_saveConfig_configInDb() throws Exception {
        setUp();
        createEmptyMonitoringConfig();
        TestAvailableDataMonitoring config = createMonitoringConfig();
        statisticsService.saveAvailableDataMonitoringConfig(config);
        TestAvailableDataMonitoring savedConfig = statisticsService
                .getAvailableDataMonitoringConfig(config.getSystemId(), config.getEnvironmentId());

        Assertions.assertEquals(config, savedConfig);
    }

    @Test
    public void availableDataMonitoring_deleteConfig_dataCleaned() throws Exception {
        setUp();
        createEmptyMonitoringConfig();
        TestAvailableDataMonitoring config = createMonitoringConfig();
        statisticsService.saveAvailableDataMonitoringConfig(config);
        statisticsService.deleteAvailableDataMonitoringConfig(systemId, environmentId);
        TestAvailableDataMonitoring configFromDb = statisticsService
                .getAvailableDataMonitoringConfig(systemId, environmentId);

        Assertions.assertTrue(StringUtils.isEmpty(configFromDb.getRecipients()));
        Assertions.assertTrue(StringUtils.isEmpty(configFromDb.getSchedule()));
        Assertions.assertFalse(configFromDb.isScheduled());
    }

    @Test
    public void availableStatistic_getNewConfig_getConfig() {
        String tableName = "availabledata_" + java.lang.System.currentTimeMillis();
        UUID system2 = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        try {
            List<String> columns = Arrays.asList("sim", "Status", "Partner", "Partner category",
                    "Partner ID", "Operator ID", "environment", "Assignment");
            createTestDataTable(tableName);
            createTestDataTableCatalog(project, system2, environmentId, "availableData", tableName);
            AvailableDataStatisticsConfig config = statisticsService.getAvailableStatsConfig(system2, environmentId);

            Assertions.assertTrue(config.getColumnKeys().containsAll(columns));
            Assertions.assertNull(config.getDescription());
            Assertions.assertNull(config.getActiveColumnKey());
            Assertions.assertNull(config.getTablesColumns());
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void availableStatistic_saveConfig_saved() {
        UUID system2 = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        String tableName = "availabledata2_" + java.lang.System.currentTimeMillis();
        try {
            createTestDataTable(tableName);
            createTestDataTableCatalog(project, system2, environmentId, "availableData2", tableName);
            AvailableDataStatisticsConfig config = statisticsService.getAvailableStatsConfig(system2, environmentId);
            List<TableColumnValues> values = testDataService.getDistinctTablesColumnValues(system2, environmentId, "sim");
            config.setTablesColumns(values);
            config.setDescription("Descirption");
            config.setActiveColumnKey("sim");
            statisticsService.saveAvailableStatsConfig(config);
            AvailableDataStatisticsConfig actConfig = statisticsService.getAvailableStatsConfig(system2, environmentId);

            Assertions.assertEquals(config, actConfig);
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }

    @Test
    public void availableData_getAvailableData_returnStatistic() {
        UUID system2 = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        String tableName = "gettingdata_" + java.lang.System.currentTimeMillis();
        try {
            createTestDataTable(tableName);
            createTestDataTableCatalog(project, system2, environmentId, "gettingData", tableName);
            AvailableDataStatisticsConfig config = statisticsService.getAvailableStatsConfig(system2, environmentId);
            List<TableColumnValues> values = testDataService.getDistinctTablesColumnValues(system2, environmentId, "sim");
            values.get(0).getValues().add("Empty stat");
            config.setTablesColumns(values);
            config.setDescription("Description");
            config.setActiveColumnKey("sim");
            statisticsService.saveAvailableStatsConfig(config);
            AvailableDataByColumnStats stats = statisticsService.getAvailableDataInColumn(system2, environmentId);

            Assertions.assertEquals(Integer.valueOf(0), stats.getStatistics().get(0).getOptions().get("Empty stat"));
        } finally {
            deleteTestDataTableIfExists(tableName);
            catalogRepository.deleteByTableName(tableName);
        }
    }

    private static TestAvailableDataMonitoring createMonitoringConfig() {
        TestAvailableDataMonitoring availableDataMonitoring = new TestAvailableDataMonitoring();
        availableDataMonitoring.setSystemId(systemId);
        availableDataMonitoring.setEnvironmentId(environmentId);
        availableDataMonitoring.setActiveColumn("SIM");
        availableDataMonitoring.setDescription("Config name");
        availableDataMonitoring.setThreshold(10);
        availableDataMonitoring.setScheduled(true);
        availableDataMonitoring.setSchedule("0 0 9 ? * *");
        availableDataMonitoring.setRecipients("example@example.com");
        return availableDataMonitoring;
    }

    private TestAvailableDataMonitoring createEmptyMonitoringConfig() {
        TestAvailableDataMonitoring availableDataMonitoring = new TestAvailableDataMonitoring();
        availableDataMonitoring.setSystemId(systemId);
        availableDataMonitoring.setEnvironmentId(environmentId);
        availableDataMonitoring.setActiveColumn("SIM");
        availableDataMonitoring.setDescription("Config name");
        availableDataMonitoring.setThreshold(10);
        availableDataMonitoringRepository.save(availableDataMonitoring);
        return availableDataMonitoring;
    }
}
