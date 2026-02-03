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

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.qubership.atp.tdm.AbstractTest;
import org.qubership.atp.tdm.service.SchedulerService;
import org.qubership.atp.tdm.utils.scheduler.ScheduleConfig;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.Data;

public class SchedulerServiceTest extends AbstractTest {

    private static final String SCHEDULE_GROUP = "SchedulerService";

    @Autowired
    private SchedulerService schedulerService;

    @Test
    public void schedulerService_reschedule_scheduleRescheduled() {
        UUID configId = UUID.randomUUID();
        StubScheduleConfig config = new StubScheduleConfig(configId, "0 0/1 * * * ?", true);
        JobDetail job = JobBuilder.newJob(Job.class)
                .withIdentity(configId.toString(), SCHEDULE_GROUP)
                .build();
        schedulerService.reschedule(job, config, SCHEDULE_GROUP);

        Assertions.assertTrue(schedulerService.checkExists(job.getKey()));
    }

    @Test
    public void schedulerService_rescheduleDisabledJob_scheduleRescheduled() {
        UUID configId = UUID.randomUUID();
        StubScheduleConfig config = new StubScheduleConfig(configId, "0 0/1 * * * ?", true);
        JobDetail job = JobBuilder.newJob(Job.class)
                .withIdentity(configId.toString(), SCHEDULE_GROUP)
                .build();
        schedulerService.reschedule(job, config, SCHEDULE_GROUP);

        Assertions.assertTrue(schedulerService.checkExists(job.getKey()));

        config.setEnabled(false);
        schedulerService.reschedule(job, config, SCHEDULE_GROUP);

        Assertions.assertFalse(schedulerService.checkExists(job.getKey()));
    }

    @Test
    public void schedulerService_deleteJob_jobDeleted() throws Exception {
        UUID configId = UUID.randomUUID();
        ScheduleConfig config = new StubScheduleConfig(configId, "0 0/1 * * * ?", true);
        JobDetail job = JobBuilder.newJob(Job.class)
                .withIdentity(configId.toString(), SCHEDULE_GROUP)
                .build();
        schedulerService.reschedule(job, config, SCHEDULE_GROUP);
        Thread.sleep(3000);
        Assertions.assertTrue(schedulerService.checkExists(job.getKey()));
    }

    @Data
    private class StubScheduleConfig implements ScheduleConfig {
        private UUID id;
        private String schedule;
        private boolean enabled;

        public StubScheduleConfig(UUID id, String schedule, boolean enabled) {
            this.id = id;
            this.schedule = schedule;
            this.enabled = enabled;
        }

        @Override
        public boolean isScheduled() {
            return enabled && StringUtils.isNotEmpty(schedule);
        }
    }
}
