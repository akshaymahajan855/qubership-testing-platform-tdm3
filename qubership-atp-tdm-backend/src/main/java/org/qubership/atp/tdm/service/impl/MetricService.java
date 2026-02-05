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

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;

@Component
public class MetricService {
    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    private static final String EXECUTE_CLEANUP_BY_CRON = "atp_tdm_execute_cleanup_by_cron";
    private static final String EXECUTE_REFRESH_BY_CRON = "atp_tdm_execute_refresh_by_cron";
    private static final String EXECUTE_STATISTICS_BY_CRON = "atp_tdm_execute_statistics_by_cron";
    private static final String EXECUTE_STATISTICS_USER_BY_CRON = "atp_tdm_execute_user_statistics_by_cron";
    private static final String CLEANUP_ID = "cleanup_id";
    private static final String REFRESH_ID = "refresh_id";
    private static final String PROJECT_ID = "project_id";
    private static final String TABLE_TITLE = "table_title";
    private static final String TABLES_COUNT_PER_PROJECT = "atp_tdm_tables_count_per_project";
    private static final String TABLES_COUNT = "atp_tdm_tables_count";
    private static final String INSERT_ACTION = "atp_tdm_insert_action";
    private static final String OCCUPY_ACTION = "atp_tdm_occupy_action";
    private static final String RELEASE_ACTION = "atp_tdm_release_action";
    private static final String UPDATE_ACTION = "atp_tdm_update_action";
    private static final String DELETE_ACTION = "atp_tdm_delete_action";
    private static final String GET_ACTION = "atp_tdm_get_action";
    private static final String PROJECT_NOT_FOUND = "UNKNOWN";

    /**
     * MetricService registers custom metrics for incoming requests.
     *
     * @param meterRegistry micrometer registry helps add custom metrics.
     */
    @Autowired
    public MetricService(MeterRegistry meterRegistry, @Nonnull JdbcTemplate jdbcTemplate) {
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
        registerTablesPerProject();
        registerTablesCount();
    }

    /**
     * Register Tables Per Project.
     */
    public void registerTablesPerProject() {
        String queryForProjectIds  = "select DISTINCT project_id from test_data_table_catalog;";
        List<UUID> projectIds = jdbcTemplate.queryForList(queryForProjectIds, UUID.class);

        projectIds.forEach(v -> Gauge.builder(TABLES_COUNT_PER_PROJECT, this,
                        eos -> eos.getTableCountForProject(v))
                .tag(PROJECT_ID, String.valueOf(v)).register(meterRegistry));
    }

    /**
     * Get Table Count For Project.
     */
    public int getTableCountForProject(UUID projectId) {
        String query = String.format("select count(*) from test_data_table_catalog where project_id='%s';", projectId);
        return jdbcTemplate.queryForObject(query, Integer.class);

    }

    public void registerTablesCount() {
        Gauge.builder(TABLES_COUNT, this, MetricService::getTablesCount).register(meterRegistry);
    }

    private int getTablesCount() {
        return jdbcTemplate.queryForObject("select count(*) from test_data_table_catalog;", Integer.class);
    }

    public void executeStatisticsJob(String project) {
        incrementMetricExecuteStatisticsJob(project);
    }

    private void incrementMetricExecuteStatisticsJob(String project) {
        meterRegistry.counter(MetricService.EXECUTE_STATISTICS_BY_CRON, PROJECT_ID, project).increment();
    }

    public void executeStatisticsUserJob(String project) {
        incrementMetricExecuteStatisticsUserJob(project);
    }

    private void incrementMetricExecuteStatisticsUserJob(String project) {
        meterRegistry.counter(MetricService.EXECUTE_STATISTICS_USER_BY_CRON, PROJECT_ID, project).increment();
    }

    public void executeRefreshJob(String refreshId, UUID projectId, String tableTitle) {
        incrementMetricExecuteRefreshJob(refreshId, getProjectIdOrDefault(projectId), tableTitle);
    }

    private void incrementMetricExecuteRefreshJob(String refreshId, String project, String tableTitle) {
        meterRegistry.counter(MetricService.EXECUTE_REFRESH_BY_CRON, PROJECT_ID, project, TABLE_TITLE, tableTitle,
                REFRESH_ID, refreshId).increment();
    }

    public void executeCleanupJob(String cleanupId, UUID projectId, String tableTitle) {
        incrementMetricExecuteCleanupJob(cleanupId, getProjectIdOrDefault(projectId), tableTitle);
    }

    private void incrementMetricExecuteCleanupJob(String cleanupId, String project, String tableTitle) {
        meterRegistry.counter(MetricService.EXECUTE_CLEANUP_BY_CRON, PROJECT_ID, project, TABLE_TITLE, tableTitle,
                CLEANUP_ID, cleanupId).increment();
    }

    public void incrementInsertAction(UUID projectId) {
        meterRegistry.counter(INSERT_ACTION, PROJECT_ID, getProjectIdOrDefault(projectId)).increment();
    }

    public void incrementOccupyAction(String projectId) {
        meterRegistry.counter(OCCUPY_ACTION, PROJECT_ID, getProjectIdOrDefault(projectId)).increment();
    }

    public void incrementReleaseAction(String projectId) {
        meterRegistry.counter(RELEASE_ACTION, PROJECT_ID, getProjectIdOrDefault(projectId)).increment();
    }

    public void incrementUpdateAction(String projectId) {
        meterRegistry.counter(UPDATE_ACTION, PROJECT_ID, getProjectIdOrDefault(projectId)).increment();
    }

    public void incrementDeleteAction(String projectId) {
        meterRegistry.counter(DELETE_ACTION, PROJECT_ID, getProjectIdOrDefault(projectId)).increment();
    }

    public void incrementGetAction(String projectId) {
        meterRegistry.counter(GET_ACTION, PROJECT_ID, getProjectIdOrDefault(projectId)).increment();
    }

    private String getProjectIdOrDefault(String projectId) {
        return StringUtils.isEmpty(projectId) ? PROJECT_NOT_FOUND : projectId;
    }

    private String getProjectIdOrDefault(UUID projectId) {
        return  projectId == null ? PROJECT_NOT_FOUND : projectId.toString();
    }

}
