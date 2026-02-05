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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.tdm.env.configurator.model.Connection;
import org.qubership.atp.tdm.env.configurator.model.LazySystem;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.internal.TdmSearchColumnException;
import org.qubership.atp.tdm.model.ColumnType;
import org.qubership.atp.tdm.model.FilterType;
import org.qubership.atp.tdm.model.LinkSetupResult;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.table.OrderType;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumnIdentity;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.ColumnRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.repo.impl.SystemColumns;
import org.qubership.atp.tdm.service.ColumnService;
import org.qubership.atp.tdm.utils.TestDataUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ColumnServiceImpl implements ColumnService {

    private static final String CONNECTION_NAME = "HTTP";
    private static final String PARAMETER_NAME_URL = "url";
    private static final String PARAMETER_NAME_EXTERNAL_URL = "external_url";
    private static final int COUNT_OF_DISTINCT_VALUES_FOR_LIST_FILTER_TYPE = 10;

    private final CatalogRepository catalogRepository;
    private final ColumnRepository columnRepository;
    private final EnvironmentsService environmentsService;
    private final TestDataTableRepository testDataTableRepository;
    private final LockManager lockManager;

    @Value("${tdm.linker.property.external.url}")
    private Boolean externalUrl;

    /**
     * Default constructor.
     */
    @Lazy
    @Autowired
    public ColumnServiceImpl(@Nonnull CatalogRepository catalogRepository, @Nonnull ColumnRepository columnRepository,
                             @Nonnull EnvironmentsService environmentsService,
                             @Nonnull TestDataTableRepository testDataTableRepository,
                             @Nonnull LockManager lockManager) {
        this.catalogRepository = catalogRepository;
        this.columnRepository = columnRepository;
        this.environmentsService = environmentsService;
        this.testDataTableRepository = testDataTableRepository;
        this.lockManager = lockManager;
    }

    @Override
    public List<TestDataTableColumn> extractColumns(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                                    @Nonnull ResultSet resultSet,
                                                    @Nullable TestDataTableOrder testDataTableOrder)
            throws SQLException {
        List<TestDataTableColumn> columns = extractColumns(tableName, testDataType, resultSet);
        setColumnTypes(columns, tableName);
        setColumnOrder(columns, testDataTableOrder);
        return columns;
    }

    @Override
    public List<TestDataTableColumn> extractColumns(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                                    @Nonnull ResultSet resultSet) throws SQLException {
        log.info("ExtractColumn start");
        ResultSetMetaData metaData = resultSet.getMetaData();
        int colCount = metaData.getColumnCount();
        List<TestDataTableColumn> columns = new ArrayList<>();
        for (int c = 1; c <= colCount; c++) {
            String columnName = metaData.getColumnName(c);
            String columnType = metaData.getColumnTypeName(c);
            TestDataTableColumn column = new TestDataTableColumn();
            column.setIdentity(new TestDataTableColumnIdentity(tableName, columnName));
            if (SystemColumns.CREATED_WHEN.getName().equalsIgnoreCase(columnName)
                    || SystemColumns.OCCUPIED_DATE.getName().equalsIgnoreCase(columnName)) {
                column.setColumnType(ColumnType.DATE);
                column.setFilterType(FilterType.DATE);
            } else {
                boolean occupied = TestDataType.OCCUPIED.equals(testDataType);
                log.debug("GetColumnDistinctValues start");
                int columnDistinctValuesCount = testDataTableRepository.getColumnDistinctValuesCount(tableName,
                        columnName, columnType, occupied);
                log.debug("GetColumnDistinctValues finish");
                if (columnDistinctValuesCount < 1) {
                    column.setFilterType(FilterType.NONE);
                } else {
                    if (columnDistinctValuesCount < COUNT_OF_DISTINCT_VALUES_FOR_LIST_FILTER_TYPE) {
                        column.setFilterType(FilterType.LIST);
                    } else {
                        column.setFilterType(FilterType.TEXT);
                    }
                }
            }
            columns.add(column);
        }
        log.info("ExtractColumn finish");
        return columns;
    }

    @Override
    public List<TestDataTableColumn> extractColumnsMultiple(@Nonnull String tableName,
                                                            @Nonnull TestDataType testDataType,
                                                            @Nonnull ResultSet resultSet) throws SQLException {
        log.debug("extractColumnsMultiple start");
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        List<TestDataTableColumn> columns = new ArrayList<>();
        for (int columnIdx = 1; columnIdx <= resultSetMetaData.getColumnCount(); columnIdx++) {
            String columnName = resultSetMetaData.getColumnName(columnIdx);
            TestDataTableColumn column = new TestDataTableColumn();
            column.setIdentity(new TestDataTableColumnIdentity(tableName, columnName));
            if (SystemColumns.CREATED_WHEN.getName().equalsIgnoreCase(columnName)
                    || SystemColumns.OCCUPIED_DATE.getName().equalsIgnoreCase(columnName)) {
                column.setColumnType(ColumnType.DATE);
                column.setFilterType(FilterType.DATE);
            }
            columns.add(column);
        }
        log.debug("extractColumnsMultiple finish");
        return columns;
    }

    private void setColumnTypes(@Nonnull List<TestDataTableColumn> columns, @Nonnull String tableName) {
        List<TestDataTableColumn> columnsTypes = columnRepository
                .findAllByIdentityTableNameAndIdentityColumnNameIn(tableName, columns.stream()
                        .map(c -> c.getIdentity().getColumnName()).collect(Collectors.toList()));
        for (TestDataTableColumn linkColumn : columnsTypes) {
            String columnName = linkColumn.getIdentity().getColumnName();
            TestDataTableColumn column = columns.stream()
                    .filter(c -> columnName.equals(c.getIdentity().getColumnName()))
                    .findFirst()
                    .orElseGet(TestDataTableColumn::new);
            column.setColumnType(linkColumn.getColumnType());
            column.setColumnLink(linkColumn.getColumnLink());
        }
    }

    private void setColumnOrder(@Nonnull List<TestDataTableColumn> columns, @Nullable TestDataTableOrder order) {
        if (Objects.nonNull(order)) {
            columns.stream()
                    .filter(c -> c.getIdentity().getColumnName().equals(order.getColumnName()))
                    .findFirst()
                    .orElseThrow(() -> new TdmSearchColumnException(order.getColumnName()))
                    .setOrderType(order.getOrderType(OrderType.ASC));
        } else {
            setDefaultColumnOrder(columns);
        }
    }

    private void setDefaultColumnOrder(@Nonnull List<TestDataTableColumn> columns) {
        List<String> systemColumnNames = SystemColumns.getColumnNames();
        columns.stream()
                .filter(c -> !systemColumnNames.contains(c.getIdentity().getColumnName()))
                .forEach(c -> c.setOrderType(OrderType.TRUE));
    }

    @Override
    public String getColumnLink(@Nonnull UUID projectId, @Nonnull UUID systemId, @Nonnull String endpoint) {
        log.info("Getting link for project [{}], system [{}] with endpoint [{}]", projectId, systemId, endpoint);
        List<Connection> connections = environmentsService.getConnectionsSystemById(null, systemId);
        Map<String, String> parameters = TestDataUtils.getConnection(connections, CONNECTION_NAME).getParameters();

        String serverUrl = getHttpUrlParamValue(parameters,
                externalUrl ? PARAMETER_NAME_EXTERNAL_URL : PARAMETER_NAME_URL);
        log.info("Got link for project [{}], system [{}] with endpoint [{}]", projectId, systemId, endpoint);
        return serverUrl + "/" + endpoint;
    }

    private String getHttpUrlParamValue(Map<String, String> parameters, String httpUrlParam) {
        if (!parameters.containsKey(httpUrlParam)) {
            throw new IllegalArgumentException(String.format("Parameter [%s] was not found.", httpUrlParam));
        }
        return parameters.get(httpUrlParam);
    }

    /**
     * Setup links for current table.
     *
     * @param projectId  - project id.
     * @param systemId   - system id.
     * @param tableName  - current table id.
     * @param columnName - column name.
     * @param endpoint   - endpoint for link.
     */
    @Override
    public void setupColumnLinks(@Nonnull Boolean isAll, @Nonnull UUID projectId, @Nonnull UUID systemId,
                                 @Nonnull String tableName, @Nonnull String columnName, @Nonnull String endpoint,
                                 @Nonnull Boolean pickUpFullLinkFromTableCell) {
        if (isAll) {
            setupColumnLinksForAllEnvTable(projectId, tableName, columnName, endpoint, pickUpFullLinkFromTableCell);
        } else {
            setupLinkForOneTable(projectId, systemId, tableName, columnName,
                    endpoint, false, pickUpFullLinkFromTableCell);
        }
    }

    /**
     * Refresh links for current table.
     *
     * @param projectId - project id.
     * @param systemId  - system id.
     * @param tableName - current table id.
     */
    public LinkSetupResult setUpLinks(@Nonnull UUID projectId, @Nonnull UUID systemId, @Nonnull String tableName) {
        log.info("Link refresh.");
        List<String> columnNames = new ArrayList<>();
        lockManager.executeWithLockWithUniqueLockKey("set links " + tableName, () -> {
            List<TestDataTableColumn> columns =
                    columnRepository.findAllByIdentityTableName(tableName);
            columns.forEach(column -> {
                String endpoint = extractEndpointFromFullUrl(column.getColumnLink());
                boolean pickUpFullLinkFromTableCell = endpoint.equals(StringUtils.EMPTY);
                String columnName = column.getIdentity().getColumnName();
                setupLinkForOneTable(projectId, systemId, tableName, columnName, endpoint,
                        column.isBulkLink(), pickUpFullLinkFromTableCell);
                columnNames.add(columnName);
            });
        });
        log.info("Refreshed links for table: {}", tableName);
        return new LinkSetupResult(StringUtils.join(columnNames, ", "));
    }

    /**
     * Renew bulk links by table title, project id and system id.
     *
     * @param projectId  - project id.
     * @param systemId   - system id.
     * @param tableTitle - table title.
     */
    @Override
    public void setUpLinks(@Nonnull UUID projectId, @Nonnull UUID systemId, @Nonnull String tableTitle,
                           @Nonnull String tableName) {
        log.info("Resolving links for new table with title: {}, project id: {}, system id {}.",
                tableTitle, projectId, systemId);
        Optional<TestDataTableCatalog> tableCatalog = catalogRepository.findFirstByProjectIdAndTableTitle(projectId,
                tableTitle);
        if (tableCatalog.isPresent()) {
            List<TestDataTableColumn> columns =
                    columnRepository.findAllByIdentityTableName(tableCatalog.get().getTableName());
            columns.forEach(column -> {
                if (column.isBulkLink()) {
                    String endpoint = extractEndpointFromFullUrl(column.getColumnLink());
                    boolean pickUpFullLinkFromTableCell = endpoint.equals(StringUtils.EMPTY);
                    String columnName = column.getIdentity().getColumnName();
                    setupLinkForOneTable(projectId, systemId, tableName, columnName,
                            endpoint, true, pickUpFullLinkFromTableCell);
                }
            });
        }
        log.info("Links for table with title: {} successfully updated.", tableTitle);
    }

    /**
     * Get catalog of all tables with links.
     *
     * @param projectId - project id.
     * @param systemId  - system id.
     * @return List of TestDataTableCatalog.
     */
    public List<TestDataTableCatalog> getAllTablesWithLinks(@Nonnull UUID projectId, @Nonnull UUID systemId) {
        List<TestDataTableCatalog> catalogList = catalogRepository.findAllByProjectIdAndSystemId(projectId, systemId);
        return catalogList
                .stream()
                .filter(catalog -> !columnRepository.findAllByIdentityTableName(catalog.getTableName()).isEmpty())
                .collect(Collectors.toList());
    }

    public List<TestDataTableColumn> getDistinctTableNames() {
        return columnRepository.findDistinctByIdentityTableName();
    }

    @Override
    public List<TestDataTableColumn> getAllColumnsByTableName(@Nonnull String tableName) {
        return columnRepository.findAllByIdentityTableName(tableName);
    }

    private String extractEndpointFromFullUrl(@Nonnull String url) {
        int endpointArrayIndex = 3;
        int lastHostSlashPosition = 4;
        if (StringUtils.EMPTY.equals(url)) {
            return StringUtils.EMPTY;
        } else {
            String[] endpointArray = url.split("/", lastHostSlashPosition);
            return endpointArray[endpointArrayIndex];
        }
    }

    /**
     * Setup links for current table.
     *
     * @param projectId  - project id.
     * @param systemId   - system id.
     * @param tableName  - current table id.
     * @param columnName - column name.
     * @param endpoint   - endpoint for link.
     * @param pickUpFullLinkFromTableCell   - get full link from table cell.
     */
    private void setupLinkForOneTable(@Nullable UUID projectId, @Nullable UUID systemId,
                                      @Nonnull String tableName,
                                      @Nonnull String columnName, @Nullable String endpoint,
                                      @Nonnull Boolean isAll, @Nonnull Boolean pickUpFullLinkFromTableCell) {
        log.info("Setting links up.");
        TestDataTableColumn column = new TestDataTableColumn();
        column.setColumnType(ColumnType.LINK);
        if (pickUpFullLinkFromTableCell) {
            column.setColumnLink(StringUtils.EMPTY);
        } else {
            column.setColumnLink(getColumnLink(projectId, systemId, endpoint));
        }
        column.setIdentity(new TestDataTableColumnIdentity(tableName, columnName));
        column.setBulkLink(isAll);
        columnRepository.save(column);
        log.info("Set up links for table: {}", tableName);
    }

    /**
     * Setup links for all tables with same title under current project.
     *
     * @param projectId  - project id.
     * @param tableName  - current table id.
     * @param columnName - column name.
     * @param endpoint   - endpoint for link.
     */

    private void setupColumnLinksForAllEnvTable(@Nonnull UUID projectId, @Nonnull String tableName,
                                                @Nonnull String columnName, @Nonnull String endpoint,
                                                @Nonnull Boolean pickUpFullLinkFromTableCell) {
        log.info("Setting bulk links");
        TestDataTableCatalog catalog = catalogRepository.findTableByProjectIdAndTableName(projectId, tableName);
        List<LazySystem> systems = environmentsService.getLazySystems(catalog.getEnvironmentId());
        List<TestDataTableCatalog> catalogList = catalogRepository.findAllByProjectIdAndTableTitle(projectId,
                catalog.getTableTitle());
        catalogList
                .stream()
                .filter(element -> Objects.nonNull(element.getSystemId()))
                .filter(item -> systems.stream().anyMatch(s -> item.getSystemId().equals(s.getId())))
                .forEach(catalogItem -> {
                    setupLinkForOneTable(projectId, catalogItem.getSystemId(), catalogItem.getTableName(),
                            columnName, endpoint, true, pickUpFullLinkFromTableCell);
                });
        log.info("Setup bulk links for all tables with title [{}] under project [{}]", tableName, projectId);
    }

    /**
     * Delete rows in column table by table name. Required when deleting a table.
     *
     * @param tableName TDM table name.
     */
    @Override
    public void deleteByTableName(@Nonnull String tableName) {
        log.info("Delete rows by table name {} in ColumnRepository", tableName);
        columnRepository.deleteByIdentity_TableName(tableName);
        log.info("Rows was deleted by table name {}.", tableName);
    }
}
