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

package org.qubership.atp.tdm.model.cleanup;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.Data;

@Data
@Entity
public class TestDataCleanupConfig implements ScheduleConfig {
    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "enabled")
    private boolean enabled;
    @Column(name = "schedule")
    private String schedule;
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private CleanupType type;
    @Column(name = "query_timeout")
    private Integer queryTimeout;
    @Column(name = "search_sql")
    private String searchSql;
    @Column(name = "search_class")
    private String searchClass;
    @Column(name = "search_date")
    private String searchDate;
    @Column(name = "shared")
    private boolean shared;

    @Transient
    @JsonIgnore
    private String scheduled;

    @Override
    public boolean isScheduled() {
        return enabled && StringUtils.isNotEmpty(schedule);
    }
}
