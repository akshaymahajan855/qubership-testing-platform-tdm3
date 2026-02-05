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

package org.qubership.atp.tdm.model.statistics;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString
@Entity
public class TestDataTableMonitoring implements ScheduleConfig {
    @Id
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "enabled")
    private boolean enabled;
    @Column(name = "cron_expression")
    private String cronExpression;
    @Column(name = "threshold")
    private int threshold;
    @Column(name = "recipients")
    private String recipients;
    @Transient
    @JsonIgnore
    private UUID id;
    @Transient
    @JsonIgnore
    private String schedule;
    @Transient
    @JsonIgnore
    private String scheduled;

    @Override
    public UUID getId() {
        return projectId;
    }

    @Override
    public String getSchedule() {
        return cronExpression;
    }

    @Override
    public boolean isScheduled() {
        return enabled && StringUtils.isNotEmpty(cronExpression);
    }
}
