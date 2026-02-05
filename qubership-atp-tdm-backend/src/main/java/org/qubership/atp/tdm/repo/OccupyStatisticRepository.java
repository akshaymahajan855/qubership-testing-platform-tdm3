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

package org.qubership.atp.tdm.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.qubership.atp.tdm.model.TestDataOccupyStatistic;
import org.qubership.atp.tdm.model.table.TestDataOccupyReportGroupBy;
import org.qubership.atp.tdm.utils.TestDataQueries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OccupyStatisticRepository extends JpaRepository<TestDataOccupyStatistic, UUID> {

    @Query(value = TestDataQueries.GET_OCCUPIED_STATISTIC_BY_PROJECT, nativeQuery = true)
    List<TestDataOccupyStatistic> findAllByProjectId(@Param("projectId") UUID projectId);

    @Query(value = TestDataQueries.GET_OCCUPIED_STATISTIC_BY_PROJECT_AND_SYSTEM, nativeQuery = true)
    List<TestDataOccupyStatistic> findAllByProjectIdAndSystemId(@Param("projectId") UUID projectId,
                                                                @Param("systemId") UUID systemId);

    @Transactional
    @Modifying
    @Query(value = TestDataQueries.CHANGE_OCCUPIED_TEST_DATA_TITLE, nativeQuery = true)
    void changeOccupiedTestDataTitle(@Param("table_name") String tableName,
                                     @Param("table_title") String tableTitle);

    @Transactional
    @Modifying
    @Query(value = TestDataQueries.DELETE_OCCUPIED_STATISTIC, nativeQuery = true)
    void deleteAllByRowId(@Param("rowIds") List<UUID> rowIds);

    @Query(value = TestDataQueries.GET_OCCUPIED_STATISTICS_GROUP_BY)
    List<TestDataOccupyReportGroupBy> findAllByProjectIdAndOccupiedDateAndCountGroupBy(
            @Param("projectId") UUID projectId, @Param("date") LocalDateTime date);
}
