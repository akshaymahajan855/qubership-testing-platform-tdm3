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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OccupiedDataByUsersStatistics extends StatisticsItem {
        String userName;
        String tableName;
        Map<LocalDate, Long> data;

        /**
         * Create object with tableTitle, usernName and tableName.
         *
         * @param tableTitle table_title field
         * @param userName occupied_by field
         * @param tableName table_name field
         */
        public OccupiedDataByUsersStatistics(String tableTitle, String userName, String tableName) {
                super(StringUtils.EMPTY,StringUtils.EMPTY, tableTitle);
                this.tableName = tableName;
                this.userName = userName;
                this.data = new HashMap<>();
        }

        public void addData(LocalDate date, Long value) {
                data.put(date,value);
        }
}
