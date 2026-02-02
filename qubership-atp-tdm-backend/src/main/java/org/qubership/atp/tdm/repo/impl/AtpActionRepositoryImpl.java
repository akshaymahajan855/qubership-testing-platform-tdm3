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

package org.qubership.atp.tdm.repo.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.tdm.exceptions.internal.TdmOccupyDataIncorrectlyException;
import org.qubership.atp.tdm.exceptions.internal.TdmOccupyDataResponseMessageException;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchCleanupConfigException;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;
import org.qubership.atp.tdm.model.refresh.RefreshResults;
import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.ResponseType;
import org.qubership.atp.tdm.model.rest.requests.AddInfoToRowRequest;
import org.qubership.atp.tdm.model.rest.requests.GetRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyFullRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyRowRequest;
import org.qubership.atp.tdm.model.rest.requests.ReleaseRowRequest;
import org.qubership.atp.tdm.model.rest.requests.UpdateRowRequest;
import org.qubership.atp.tdm.model.table.TableDetails;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.qubership.atp.tdm.repo.AtpActionRepository;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.CleanupConfigRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.service.ColumnService;
import org.qubership.atp.tdm.service.DataRefreshService;
import org.qubership.atp.tdm.service.TestDataFlagsService;
import org.qubership.atp.tdm.service.impl.CleanupServiceImpl;
import org.qubership.atp.tdm.utils.TestDataTableConvertor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class AtpActionRepositoryImpl implements AtpActionRepository {

    private static final String DATA_REFRESH_LINK = "%s/project/%s/tdm/TEST%%20DATA/%s/%s";
    private static final Integer UPDATE_TEST_DATA_LIMIT = 100;

    private final CatalogRepository catalogRepository;
    private final TestDataTableRepository testDataTableRepository;
    private final CleanupConfigRepository cleanupConfigRepository;
    private final ColumnService columnService;
    private final DataRefreshService dataRefreshService;
    private final TestDataFlagsService testDataFlagsService;
    private final CleanupServiceImpl cleanupService;
    private final LockManager lockManager;

    /**
     * AtpActionRepository Constructor.
     */
    @Autowired
    public AtpActionRepositoryImpl(@Nonnull CatalogRepository catalogRepository,
                                   @Nonnull TestDataTableRepository testDataTableRepository,
                                   @Nonnull CleanupConfigRepository cleanupConfigRepository,
                                   @Nonnull ColumnService columnService,
                                   @Nonnull DataRefreshService dataRefreshService,
                                   @Nonnull TestDataFlagsService testDataFlagsService,
                                   @Nonnull CleanupServiceImpl cleanupService,
                                   @Nonnull LockManager lockManager) {
        this.catalogRepository = catalogRepository;
        this.testDataTableRepository = testDataTableRepository;
        this.cleanupConfigRepository = cleanupConfigRepository;
        this.columnService = columnService;
        this.dataRefreshService = dataRefreshService;
        this.testDataFlagsService = testDataFlagsService;
        this.cleanupService = cleanupService;
        this.lockManager = lockManager;
    }

    @Override
    public ResponseMessage insertTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                          @Nullable UUID environmentId, @Nonnull String tableTitle,
                                          List<Map<String, Object>> records, @Nonnull String resultLink) {
        ResponseMessage responseMessage = new ResponseMessage();
        lockManager.executeWithLockWithUniqueLockKey("Insert. sys:" + systemId, () -> {
            TableDetails tableDetails = getTableDetails(projectId, systemId, tableTitle);
            final String finalResultLink = resultLink + "/" + tableDetails.getTableName();
            if (tableDetails.isExists()) {
                responseMessage.setContent("Test data table with specified name already exists. "
                        + "Test data was inserted.");
                responseMessage.setLink(finalResultLink);
                testDataTableRepository.insertRows(tableDetails.getTableName(), true, records, false);
                testDataTableRepository.updateLastUsage(tableDetails.getTableName());
            } else {
                responseMessage.setContent("A new test data table has been created. Test data was inserted.");
                responseMessage.setLink(finalResultLink);
                testDataTableRepository.insertRows(tableDetails.getTableName(), false, records, false);
                testDataTableRepository.saveTestDataTableCatalog(tableDetails.getTableName(), tableTitle, projectId,
                        systemId, environmentId);
                testDataFlagsService.setValidateUnoccupiedResourcesFlag(tableDetails.getTableName(),
                        false, false);
                if (Objects.nonNull(systemId)) {
                    columnService.setUpLinks(projectId, systemId, tableTitle, tableDetails.getTableName());
                }
            }
            responseMessage.setType(ResponseType.SUCCESS);
        });
        return responseMessage;
    }

    @Override
    public List<ResponseMessage> occupyTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                @Nonnull String tableTitle, @Nonnull String occupiedBy,
                                                @Nonnull List<OccupyRowRequest> occupyRowRequests,
                                                @Nonnull String resultLink) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        TableDetails tableDetails = getTableDetails(projectId, systemId, tableTitle);
        String finalResultLink = resultLink + "/" + tableDetails.getTableName();
        if (tableDetails.isExists()) {
            lockManager.executeWithLockWithUniqueLockKey("occupyTestData: " + projectId + " " + tableTitle, () -> {
                for (OccupyRowRequest occupyRowRequest : occupyRowRequests) {
                    TestDataTable table = testDataTableRepository.getTestData(false, tableDetails.getTableName(),
                            null, 1, occupyRowRequest.getFilters(), null);
                    Optional<Map<String, Object>> row = table.getData().stream().findFirst();
                    if (row.isPresent()) {
                        String nameColumnResponse = occupyRowRequest.getNameColumnResponse();
                        if (row.get().containsKey(nameColumnResponse)) {
                            UUID rowId = table.getData().stream()
                                    .map(r -> UUID.fromString(String.valueOf(r.get("ROW_ID"))))
                                    .findFirst().orElseThrow(() -> new TdmOccupyDataIncorrectlyException(tableTitle));
                            testDataTableRepository.occupyTestData(tableDetails.getTableName(), occupiedBy,
                                    Collections.singletonList(rowId));
                            testDataTableRepository.updateLastUsage(tableDetails.getTableName());
                            String value = row.get().get(nameColumnResponse).toString();
                            responseMessages.add(new ResponseMessage(ResponseType.SUCCESS, value, finalResultLink));
                        } else {
                            log.warn("Occupation test data. Response column with name: [{}] was not found.",
                                    nameColumnResponse);
                            responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                                    String.format("Column with name \"%s\" was not found!", nameColumnResponse)));
                        }
                    } else {
                        log.warn("Occupation test data. Rows were not found. Filters: {}",
                                occupyRowRequest.getFilters());
                        responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                                "No test data available for requested criteria!"));
                    }
                }
            });
        } else {
            log.warn("Occupation test data. Table with title:  [{}] was not found.", tableTitle);
            responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                    String.format("Table with title \"%s\" was not found!", tableTitle)));
            return responseMessages;
        }
        return responseMessages;
    }

    @Override
    public List<ResponseMessage> occupyTestDataFullRow(@Nonnull UUID projectId,
                                                       @Nullable UUID systemId,
                                                       @Nonnull String tableTitle,
                                                       @Nonnull String occupiedBy,
                                                       List<OccupyFullRowRequest> occupyRowRequests,
                                                       @Nonnull String resultLink) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        TableDetails tableDetails = getTableDetails(projectId, systemId, tableTitle);
        String finalResultLink = resultLink + "/" + tableDetails.getTableName();
        if (tableDetails.isExists()) {
            lockManager.executeWithLockWithUniqueLockKey("Occupy data in table: " + tableDetails.getTableName(), () -> {
                for (OccupyFullRowRequest occupyRowRequest : occupyRowRequests) {
                    TestDataTable table = testDataTableRepository.getTestData(false, tableDetails.getTableName(),
                            null, 1, occupyRowRequest.getFilters(), null);
                    Optional<Map<String, Object>> row = table.getData().stream().findFirst();
                    if (row.isPresent()) {
                        UUID rowId = table.getData().stream()
                                .map(r -> UUID.fromString(String.valueOf(r.get("ROW_ID"))))
                                .findFirst().orElseThrow(() -> new TdmOccupyDataIncorrectlyException(tableTitle));
                        boolean columnsExists = true;
                        Map<String, String> responseValues = new HashMap<>();
                        for (String responseColumnName : occupyRowRequest.getResponseColumnNames()) {
                            if (row.get().containsKey(responseColumnName)) {
                                String columnValue = String.valueOf(row.get().get(responseColumnName));
                                responseValues.put(responseColumnName, columnValue);
                            } else {
                                columnsExists = false;
                                log.warn("Occupation test data to return several rows. Response column with name: [{}] "
                                                + "was not found.",
                                        responseColumnName);
                                responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                                        String.format("Column with name \"%s\" was not found!",
                                                responseColumnName)));
                            }
                        }
                        if (columnsExists) {
                            testDataTableRepository.occupyTestData(tableDetails.getTableName(), occupiedBy,
                                    Collections.singletonList(rowId));
                            testDataTableRepository.updateLastUsage(tableDetails.getTableName());
                            try {
                                responseMessages.add(new ResponseMessage(ResponseType.SUCCESS,
                                        new ObjectMapper().writeValueAsString(responseValues),
                                        responseValues,
                                        finalResultLink));
                            } catch (Exception e) {
                                log.error(TdmOccupyDataResponseMessageException.DEFAULT_MESSAGE, e);
                                throw new TdmOccupyDataResponseMessageException();
                            }
                        }
                    } else {
                        log.warn("Occupation test data to return several rows. Rows were not found. Filters: {}",
                                occupyRowRequest.getFilters());
                        responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                                "No test data available for requested criteria!"));
                    }
                }
            });
        } else {
            log.warn("Occupation test data to return several rows. Table with title:  [{}] was not found.", tableTitle);
            responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                    String.format("Table with title \"%s\" was not found!", tableTitle)));
            return responseMessages;
        }
        return responseMessages;
    }

    @Override
    public List<ResponseMessage> releaseTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                 @Nonnull String tableTitle,
                                                 @Nonnull List<ReleaseRowRequest> releaseRowRequests) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        TableDetails tableDetails = getTableDetails(projectId, systemId, tableTitle);
        if (tableDetails.isExists()) {
            for (ReleaseRowRequest releaseRowRequest : releaseRowRequests) {
                TestDataTable table = testDataTableRepository.getTestData(true, tableDetails.getTableName(),
                        null, null, releaseRowRequest.getFilters(), null);
                List<Map<String, Object>> data = table.getData();
                if (data.size() == 1) {
                    Map<String, Object> row = data.stream().findFirst().get();
                    String nameColumnResponse = releaseRowRequest.getNameColumnResponse();
                    if (row.containsKey(nameColumnResponse)) {
                        UUID rowId = UUID.fromString(String.valueOf(row.get("ROW_ID")));
                        testDataTableRepository.releaseTestData(tableDetails.getTableName(),
                                Collections.singletonList(rowId));
                        testDataTableRepository.updateLastUsage(tableDetails.getTableName());
                        String value = row.get(nameColumnResponse).toString();
                        responseMessages.add(new ResponseMessage(ResponseType.SUCCESS, value));
                    } else {
                        log.warn("Release test data. Response column with name: [{}] was not found.",
                                nameColumnResponse);
                        responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                                String.format("Column with name \"%s\" was not found!", nameColumnResponse)));
                    }
                } else if (data.size() > 1) {
                    log.warn("Release test data. More then one row were found. Filters: {}",
                            releaseRowRequest.getFilters());
                    responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                            "More than one value was found using the specified search criteria!"));
                } else {
                    log.warn("Release test data. Rows were not found. Filters: {}",
                            releaseRowRequest.getFilters());
                    responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                            "No test data available for requested criteria!"));
                }
            }
        } else {
            log.warn("Release test data. Table with title:  [{}] was not found.", tableTitle);
            responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                    String.format("Table with title \"%s\" was not found!", tableTitle)));
            return responseMessages;
        }
        return responseMessages;
    }

    @Override
    public List<ResponseMessage> releaseFullTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                     @Nonnull String tableTitle) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        TableDetails tableDetails = getTableDetails(projectId, systemId, tableTitle);
        if (tableDetails.isExists()) {
            Long rowCount = testDataTableRepository.getTestDataSize(tableDetails.getTableName(), TestDataType.OCCUPIED);
            for (int offset = 0; offset < rowCount; offset += UPDATE_TEST_DATA_LIMIT) {
                List<Map<String, Object>> testDataTable = testDataTableRepository.getTestData(true,
                                tableDetails.getTableName(), offset, UPDATE_TEST_DATA_LIMIT, null, null)
                        .getData();
                List<UUID> rowIds =
                        testDataTable.stream()
                                .map(row ->
                                        UUID.fromString(String.valueOf(row.get("ROW_ID"))))
                                .collect(Collectors.toList());
                testDataTableRepository.releaseTestData(tableDetails.getTableName(), rowIds);
                testDataTableRepository.updateLastUsage(tableDetails.getTableName());
            }
            responseMessages.add(new ResponseMessage(ResponseType.SUCCESS,
                    String.format("All occupied data in table with title \"%s\" released.", tableTitle)));
        } else {
            log.warn("Release test data. Table with title:  [{}] was not found.", tableTitle);
            responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                    String.format("Table with title \"%s\" was not found!", tableTitle)));
            return responseMessages;
        }
        return responseMessages;
    }

    @Override
    public List<ResponseMessage> updateTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                @Nonnull String tableTitle,
                                                @Nonnull List<UpdateRowRequest> updateRowRequests) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        TableDetails table = getTableDetails(projectId, systemId, tableTitle);
        if (table.isExists()) {
            for (UpdateRowRequest updateRowRequest : updateRowRequests) {
                int updatedRowsCount = testDataTableRepository.updateRows(table.getTableName(),
                        updateRowRequest.getFilters(), updateRowRequest.getRecordWithDataForUpdate());
                testDataTableRepository.updateLastUsage(table.getTableName());
                if (updatedRowsCount > 0) {
                    String msg = String.format("\"%s\" rows were successfully updated", updatedRowsCount);
                    responseMessages.add(new ResponseMessage(ResponseType.SUCCESS, msg));
                } else {
                    responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                            "No test data available for requested criteria!"));
                }
            }
        } else {
            String msg = String.format("Table with title \"%s\" was not found!", tableTitle);
            log.warn("Updating test data. {}", msg);
            responseMessages.add(new ResponseMessage(ResponseType.ERROR, msg));
            return responseMessages;
        }
        return responseMessages;
    }

    /**
     * Getting data from DB and creating ResponseMessage.
     *
     * @param tableTitle Displayed title of table.
     * @param getRowRequests Getting data description.
     * @param resultLink Link to TDM table.
     * @return Response with multiple column values.
     */
    @Override
    public List<ResponseMessage> getMultipleColumnTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                           @Nonnull String tableTitle,
                                                           List<GetRowRequest> getRowRequests,
                                                           @Nonnull String resultLink) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        TableDetails tableDetails = getTableDetails(projectId, systemId, tableTitle);
        String finalResultLink = resultLink + "/" + tableDetails.getTableName();
        if (tableDetails.isExists()) {
            for (GetRowRequest getRowRequest : getRowRequests) {
                TestDataTable table = testDataTableRepository.getTestDataMultiple(tableDetails.getTableName(),
                        getRowRequest.getFilters());
                testDataTableRepository.updateLastUsage(tableDetails.getTableName());
                Optional<Map<String, Object>> row = table.getData().stream().findFirst();
                if (row.isPresent()) {
                    List<String> nameColumnResponse = getRowRequest.getResponseColumnNames();
                    boolean allColumnsExist = true;
                    Map<String, String> responseValues = new HashMap<>();
                    for (String responseColumnName : nameColumnResponse) {
                        if (row.get().containsKey(responseColumnName)) {
                            String columnValue = String.valueOf(row.get().get(responseColumnName));
                            responseValues.put(responseColumnName, columnValue);
                        } else {
                            allColumnsExist = false;
                            log.warn("Getting test data. Response column with name: [{}] was not found.",
                                    responseColumnName);
                            responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                                    String.format("Column with name \"%s\" was not found!", nameColumnResponse)));
                        }
                    }
                    if (allColumnsExist) {
                        try {
                            responseMessages.add(new ResponseMessage(ResponseType.SUCCESS,
                                    new ObjectMapper().writeValueAsString(responseValues),
                                    responseValues,
                                    finalResultLink));
                        } catch (Exception e) {
                            log.error(TdmOccupyDataResponseMessageException.DEFAULT_MESSAGE, e);
                            throw new TdmOccupyDataResponseMessageException();
                        }
                    }
                } else {
                    log.warn("Getting test data. Rows were not found. Filters: {}",
                            getRowRequest.getFilters());
                    responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                            "No test data available for requested criteria!"));
                }
            }
        } else {
            log.warn("Getting test data. Table with title:  [{}] was not found.", tableTitle);
            responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                    String.format("Table with title \"%s\" was not found!", tableTitle)));
            return responseMessages;
        }
        return responseMessages;
    }

    @Override
    public List<ResponseMessage> getTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                             @Nonnull String tableTitle,
                                             @Nonnull List<GetRowRequest> getRowRequests) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        TableDetails tableDetails = getTableDetails(projectId, systemId, tableTitle);
        if (tableDetails.isExists()) {
            for (GetRowRequest getRowRequest : getRowRequests) {
                TestDataTable table = testDataTableRepository.getTestData(false, tableDetails.getTableName(),
                        null, 1, getRowRequest.getFilters(), null);
                testDataTableRepository.updateLastUsage(tableDetails.getTableName());
                Optional<Map<String, Object>> row = table.getData().stream().findFirst();
                if (row.isPresent()) {
                    String nameColumnResponse = getRowRequest.getNameColumnResponse();
                    if (row.get().containsKey(nameColumnResponse)) {
                        String value = row.get().get(nameColumnResponse).toString();
                        responseMessages.add(new ResponseMessage(ResponseType.SUCCESS, value));
                    } else {
                        log.warn("Getting test data. Response column with name: [{}] was not found.",
                                nameColumnResponse);
                        responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                                String.format("Column with name \"%s\" was not found!", nameColumnResponse)));
                    }
                } else {
                    log.warn("Getting test data. Rows were not found. Filters: {}",
                            getRowRequest.getFilters());
                    responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                            "No test data available for requested criteria!"));
                }
            }
        } else {
            log.warn("Getting test data. Table with title:  [{}] was not found.", tableTitle);
            responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                    String.format("Table with title \"%s\" was not found!", tableTitle)));
            return responseMessages;
        }
        return responseMessages;
    }

    @Override
    public List<ResponseMessage> addInfoToRow(@Nonnull UUID projectId, @Nullable UUID systemId,
                                              @Nonnull String tableTitle,
                                              List<AddInfoToRowRequest> addInfoToRowRequests) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        TableDetails table = getTableDetails(projectId, systemId, tableTitle);
        if (table.isExists()) {
            for (AddInfoToRowRequest addInfoToRowRequest : addInfoToRowRequests) {
                int updatedRowsCount = testDataTableRepository.addInfoToRow(table.getTableName(),
                        addInfoToRowRequest.getFilters(), addInfoToRowRequest.getRecordWithDataForUpdate());
                testDataTableRepository.updateLastUsage(table.getTableName());
                if (updatedRowsCount > 0) {
                    String msg = String.format("\"%s\" rows were successfully updated", updatedRowsCount);
                    responseMessages.add(new ResponseMessage(ResponseType.SUCCESS, msg));
                } else {
                    responseMessages.add(new ResponseMessage(ResponseType.ERROR,
                            "No test data available for requested criteria!"));
                }
            }
        } else {
            String msg = String.format("Table with title \"%s\" was not found!", tableTitle);
            log.warn("Add info to row in test data table. {}", msg);
            responseMessages.add(new ResponseMessage(ResponseType.ERROR, msg));
            return responseMessages;
        }
        return responseMessages;
    }

    @Override
    public List<ResponseMessage> refreshTables(@Nonnull UUID projectId, @Nullable UUID systemId,
                                               @Nonnull String tableTitle, @Nonnull String tdmUrl) {
        List<TestDataTableCatalog> catalogList;
        if (Objects.nonNull(systemId)) {
            catalogList = Collections.singletonList(catalogRepository
                    .findByProjectIdAndSystemIdAndTableTitle(projectId, systemId, tableTitle));
        } else {
            catalogList = catalogRepository.findAllByProjectIdAndTableTitle(projectId, tableTitle);
        }
        if (catalogList.isEmpty()) {
            String message = "Tables with title: " + tableTitle + " was not found under project with id: " + projectId;
            log.warn(message);
            return Collections.singletonList(new ResponseMessage(ResponseType.ERROR, message));
        }
        return refreshTables(catalogList, tdmUrl);
    }

    private List<ResponseMessage> refreshTables(@Nonnull List<TestDataTableCatalog> tableCatalogs,
                                                @Nonnull String tdmUrl) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        tableCatalogs.forEach(tableCatalog -> {
            String resultLink = formResultLink(tableCatalog.getProjectId(), tableCatalog.getEnvironmentId(),
                    tableCatalog.getSystemId(), tdmUrl);
            try {
                RefreshResults refreshResults = dataRefreshService
                        .runRefresh(tableCatalog.getTableName(), false);
                String msg = String.format("Successfully refreshed %s records fot table: %s.",
                        refreshResults.getRecordsTotal(), tableCatalog.getTableTitle());
                responseMessages.add(new ResponseMessage(ResponseType.SUCCESS, msg, resultLink));
                testDataTableRepository.updateLastUsage(tableCatalog.getTableName());
            } catch (Exception e) {
                String message = "Failed to refresh table with title:" + tableCatalog.getTableTitle()
                        + ". Root cause: " + e.getMessage();
                log.error(message, e);
                responseMessages.add(new ResponseMessage(ResponseType.ERROR, message, resultLink));
            }
        });
        return responseMessages;
    }

    @Override
    public List<ResponseMessage> truncateTable(@Nonnull UUID projectId, @Nullable UUID systemId,
                                               @Nonnull String tableTitle) {
        TableDetails tableDetails = getTableDetails(projectId, systemId, tableTitle);
        ResponseMessage responseMessage = new ResponseMessage();
        lockManager.executeWithLockWithUniqueLockKey("truncate table: " + tableDetails.getTableName(), () -> {
            TestDataTableCatalog catalog = catalogRepository
                    .findByProjectIdAndSystemIdAndTableTitle(projectId, systemId, tableTitle);
            if (Objects.isNull(catalog)) {
                String message = String.format("Tables with title: %s was not found under project with id: %s",
                        tableTitle, projectId);
                log.warn(message);
                responseMessage.setType(ResponseType.ERROR);
                responseMessage.setContent(message);
                responseMessage.setLink(StringUtils.EMPTY);
            } else {
                String tableName = catalog.getTableName();
                String message = String.format("Table %s has been truncated.", tableName);
                testDataTableRepository.truncateTable(tableName);
                testDataTableRepository.updateLastUsage(tableName);
                log.info(message);
                responseMessage.setType(ResponseType.SUCCESS);
                responseMessage.setContent(message);
                responseMessage.setLink(StringUtils.EMPTY);
            }
        });
        return Collections.singletonList(responseMessage);
    }

    @Override
    public List<ResponseMessage> runCleanupForTable(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                    @Nonnull String tableTitle) {
        TestDataTableCatalog tableCatalog = catalogRepository.findByProjectIdAndSystemIdAndTableTitle(projectId,
                systemId, tableTitle);
        String message;
        if (tableCatalog == null) {
            message = String.format("Table \"%s\" hasn't been found. Could you please check provided data.",
                    tableTitle);
            return Collections.singletonList(new ResponseMessage(ResponseType.ERROR, message));
        } else if (tableCatalog.getCleanupConfigId() == null) {
            message = String.format("Cleanup hasn't been configured for table \"%s\".",
                    tableTitle);
            return Collections.singletonList(new ResponseMessage(ResponseType.ERROR, message));
        }
        TestDataCleanupConfig cleanupConfigId =
                cleanupConfigRepository.findById(tableCatalog.getCleanupConfigId())
                        .orElseThrow(() ->
                                new TdmSearchCleanupConfigException(tableCatalog.getCleanupConfigId().toString()));
        CleanupResults cleanupResults;
        try {
            cleanupResults = cleanupService.runCleanup(tableCatalog.getTableName(), cleanupConfigId);
            testDataTableRepository.updateLastUsage(tableCatalog.getTableName());
        } catch (Exception e) {
            message = String.format("Cleanup %s for table %s failed.\n" + e.getMessage(), cleanupConfigId,
                    tableCatalog.getTableName());
            log.info(message);
            return Collections.singletonList(new ResponseMessage(ResponseType.ERROR, message));
        }
        message = String.format("For table \"%s\" with total records %s has been removed %s records.",
                cleanupResults.getTableName(),
                cleanupResults.getRecordsTotal(),
                cleanupResults.getRecordsRemoved());
        log.info(message);
        return Collections.singletonList(new ResponseMessage(ResponseType.SUCCESS, message));
    }

    @Override
    public ResponseMessage resolveTableName(@Nonnull UUID projectId, @Nonnull UUID systemId,
                                            @Nonnull String tableTitle) {
        return Optional.ofNullable(
                catalogRepository.findByProjectIdAndSystemIdAndTableTitle(projectId, systemId, tableTitle))
                .map(table -> new ResponseMessage(ResponseType.SUCCESS, table.getTableName()))
                .orElse(new ResponseMessage(ResponseType.ERROR,
                        String.format("Table with title \"%s\" was not found!", tableTitle))
                );
    }

    private String formResultLink(UUID projectId, UUID environmentId, UUID systemId, String tdmUrl) {
        return String.format(DATA_REFRESH_LINK, tdmUrl, projectId, environmentId, systemId);
    }

    private TableDetails getTableDetails(@Nonnull UUID projectId, @Nullable UUID systemId,
                                         @Nonnull String tableTitle) {
        TestDataTableCatalog tableCatalog = null;
        if (systemId == null) {
            Optional<TestDataTableCatalog> existedCatalog =
                    catalogRepository.findAllByProjectIdAndTableTitle(projectId, tableTitle).stream().findFirst();
            if (existedCatalog.isPresent()) {
                tableCatalog = existedCatalog.get();
            }
        } else {
            tableCatalog = catalogRepository.findByProjectIdAndSystemIdAndTableTitle(projectId,
                    systemId, tableTitle);
        }
        if (tableCatalog != null) {
            return new TableDetails(tableCatalog.getTableName(), true);
        } else {
            return new TableDetails(TestDataTableConvertor.generateTestDataTableName(), false);
        }
    }
}
