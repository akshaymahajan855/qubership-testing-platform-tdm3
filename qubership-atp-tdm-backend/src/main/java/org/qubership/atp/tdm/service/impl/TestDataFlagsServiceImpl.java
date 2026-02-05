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
import java.util.stream.Collectors;

import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.table.TestDataFlagsTable;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.TestDataColumnFlagsRepository;
import org.qubership.atp.tdm.service.TestDataFlagsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TestDataFlagsServiceImpl implements TestDataFlagsService {

    private final TestDataColumnFlagsRepository testDataColumnFlagsRepository;
    private final CatalogRepository catalogRepository;

    /**
     * Default constructor.
     */
    @Lazy
    @Autowired
    public TestDataFlagsServiceImpl(@Nonnull TestDataColumnFlagsRepository testDataColumnFlagsRepository,
                                    @Nonnull CatalogRepository catalogRepository) {
        this.testDataColumnFlagsRepository = testDataColumnFlagsRepository;
        this.catalogRepository = catalogRepository;
    }

    /**
     * Set Validate Unoccupied Resources Flag for test_data_column_table.
     *
     * @param tableName                   Table name
     * @param validateUnoccupiedResources Flag for unoccupied objects
     */
    @Override
    public void setValidateUnoccupiedResourcesFlag(@Nonnull String tableName,
                                                   @Nonnull Boolean validateUnoccupiedResources,
                                                   @Nonnull Boolean isAll) {
        if (isAll) {
            bulkSetValidateUnoccupiedResourcesFlag(tableName, validateUnoccupiedResources);
        } else {
            TestDataFlagsTable testDataFlagsTable = new TestDataFlagsTable(tableName, validateUnoccupiedResources);
            testDataColumnFlagsRepository.save(testDataFlagsTable);
            log.info("Flag {} has been installed for table: {}", validateUnoccupiedResources, tableName);
        }
    }

    private void bulkSetValidateUnoccupiedResourcesFlag(@Nonnull String tableName,
                                                        @Nonnull Boolean validateUnoccupiedResources) {
        log.info("Setting bulk Validate Unoccupied Resources Flag.");
        TestDataTableCatalog testDataCatalog = catalogRepository.findByTableName(tableName);
        List<TestDataFlagsTable> flagsTableList = catalogRepository.findAllByProjectIdAndTableTitle(
                testDataCatalog.getProjectId(),
                testDataCatalog.getTableTitle()).stream().map(catalog -> new TestDataFlagsTable(catalog.getTableName(),
                validateUnoccupiedResources)).collect(Collectors.toList());
        flagsTableList.forEach(flagsTable -> {
            testDataColumnFlagsRepository.save(flagsTable);
            log.info("Flag {} has been installed for table: {}", validateUnoccupiedResources, flagsTable.getTableName());
        });
    }

    @Override
    public TestDataFlagsTable getValidateUnoccupiedResourcesFlag(@Nonnull String tableName) {
        return testDataColumnFlagsRepository.findRowByTableName(tableName);
    }

    @Override
    public void deleteRowByTableName(@Nonnull String tableName) {
        testDataColumnFlagsRepository.deleteRowByTableName(tableName);
    }
}
