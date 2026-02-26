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

import static java.lang.String.format;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.hibernate.boot.model.naming.IllegalIdentifierException;
import org.owasp.esapi.Encoder;
import org.owasp.esapi.codecs.OracleCodec;
import org.owasp.esapi.reference.DefaultEncoder;
import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.env.configurator.model.Server;
import org.qubership.atp.tdm.exceptions.TdmInternalException;
import org.qubership.atp.tdm.exceptions.db.TdmDbExecuteQueryException;
import org.qubership.atp.tdm.exceptions.db.TdmDbRowNotFoundException;
import org.qubership.atp.tdm.exceptions.file.TdmImportExcelTestDataException;
import org.qubership.atp.tdm.exceptions.file.TdmWriteFileException;
import org.qubership.atp.tdm.exceptions.internal.TdmCreateTestDataTableException;
import org.qubership.atp.tdm.exceptions.internal.TdmInsertDataException;
import org.qubership.atp.tdm.exceptions.internal.TdmTestDataOccupiedException;
import org.qubership.atp.tdm.model.ColumnValues;
import org.qubership.atp.tdm.model.DateFormatter;
import org.qubership.atp.tdm.model.ExportFileType;
import org.qubership.atp.tdm.model.ImportTestDataStatistic;
import org.qubership.atp.tdm.model.QueryInfo;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.TestDataTableImportInfo;
import org.qubership.atp.tdm.model.cleanup.TestDataCleanupConfig;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.model.table.TestDataTableFilter;
import org.qubership.atp.tdm.model.table.TestDataTableOrder;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.qubership.atp.tdm.model.table.conditions.factories.SearchConditionFactory;
import org.qubership.atp.tdm.model.table.conditions.search.SearchCondition;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.CleanupConfigRepository;
import org.qubership.atp.tdm.repo.ImportInfoRepository;
import org.qubership.atp.tdm.repo.SqlRepository;
import org.qubership.atp.tdm.repo.TestDataTableRepository;
import org.qubership.atp.tdm.repo.impl.extractors.TestDataExtractorProvider;
import org.qubership.atp.tdm.repo.impl.loader.TestDataExcelLoader;
import org.qubership.atp.tdm.utils.DataUtils;
import org.qubership.atp.tdm.utils.QueryEvaluator;
import org.qubership.atp.tdm.utils.TestDataQueries;
import org.qubership.atp.tdm.utils.TestDataTableCreator;
import org.qubership.atp.tdm.utils.TestDataUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomExpression;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.UpdateQuery;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgBinaryCondition;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class TestDataTableRepositoryImpl implements TestDataTableRepository {

    private static final String ALTER_COLUMN_HARD_MODE = "hard";
    private static final Pattern INDEX_COLUMN_PATTERN = Pattern.compile("\\$\\{'([^']+)'}");
    private static final Integer UPDATE_TEST_DATA_LIMIT = 100;
    private static final String EXCEL_IMPORT_FILE_MASK = "ExcelForImport_%s.xlsx";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final SqlRepository sqlRepository;
    private final ImportInfoRepository importInfoRepository;
    private final TestDataExtractorProvider extractorProvider;
    private final QueryEvaluator queryEvaluator;
    private final CatalogRepository catalogRepository;
    private final CleanupConfigRepository cleanupConfigRepository;
    private final LockManager lockManager;
    private final Encoder esapiEncoder = DefaultEncoder.getInstance();
    private final OracleCodec oracleCodec = new OracleCodec();
    private final ConcurrentHashMap<String, String> cacheLastUsageTable = new ConcurrentHashMap<>();

    @Value("${alter.column.mode}")
    private String alterColumnMode;
    @Value("${excel.import.directory}")
    private String excelImportDirectory;

    /**
     * TestDataTableRepository Constructor.
     */
    @Autowired
    public TestDataTableRepositoryImpl(@Nonnull JdbcTemplate jdbcTemplate,
                                       @Nonnull PlatformTransactionManager transactionManager,
                                       @Nonnull SqlRepository sqlRepository,
                                       @Nonnull ImportInfoRepository importInfoRepository,
                                       @Nonnull TestDataExtractorProvider extractorProvider,
                                       @Nonnull QueryEvaluator queryEvaluator,
                                       @Nonnull CatalogRepository catalogRepository,
                                       @Nonnull CleanupConfigRepository cleanupConfigRepository,
                                       @Nonnull LockManager lockManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.sqlRepository = sqlRepository;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.importInfoRepository = importInfoRepository;
        this.extractorProvider = extractorProvider;
        this.queryEvaluator = queryEvaluator;
        this.catalogRepository = catalogRepository;
        this.cleanupConfigRepository = cleanupConfigRepository;
        this.lockManager = lockManager;
    }

    @Override
    public ImportTestDataStatistic importExcelTestData(@Nonnull String tableName, boolean exists,
                                                       @Nonnull MultipartFile file) {
        DataUtils.checkTableName(tableName);
        long currentTimeMillis = java.lang.System.currentTimeMillis();
        File destination = new File(excelImportDirectory + "/"
                + format(EXCEL_IMPORT_FILE_MASK, currentTimeMillis));
        writeFileOnDiscSpace(file, destination);
        try {
            try (OPCPackage opcPackage = OPCPackage.open(destination)) {
                log.debug("File: {} successfully opened.", destination.getName());
                TestDataExcelLoader loader = new TestDataExcelLoader(opcPackage);
                return importTestData(tableName, exists, loader.process());
            }
        } catch (SQLException | BadSqlGrammarException ex) {
            log.error(format(TdmDbExecuteQueryException.DEFAULT_MESSAGE, ex.getMessage()), ex);
            throw new TdmDbExecuteQueryException(ex.getMessage());
        } catch (TdmInternalException tdmInternalException) {
            throw tdmInternalException;
        } catch (Exception e) {
            log.error(TdmImportExcelTestDataException.DEFAULT_MESSAGE, e);
            throw new TdmImportExcelTestDataException(e.getMessage());
        } finally {
            destination.delete();
        }
    }

    private void writeFileOnDiscSpace(MultipartFile sourceFile, File destinationFile) {
        log.debug("Writing file:{} to: {}", sourceFile.getName(), destinationFile.getName());
        try (OutputStream fileOutputStream = Files.newOutputStream(destinationFile.toPath())) {
            fileOutputStream.write(sourceFile.getBytes());
            log.debug("File writing success");
            sourceFile.getInputStream().close();
        } catch (Exception e) {
            log.error(format(TdmWriteFileException.DEFAULT_MESSAGE,
                    sourceFile.getName(), destinationFile.getName(), e.getMessage()), e);
            throw new TdmWriteFileException(sourceFile.getName(), destinationFile.getName(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public ImportTestDataStatistic importSqlTestData(@Nonnull String tableName, boolean exists,
                                                     @Nonnull String query, @Nonnull Integer queryTimeout,
                                                     @Nonnull Server server) {
        DataUtils.checkQuery(query);
        DataUtils.checkTableName(tableName);
        JdbcTemplate jdbcTemplate = sqlRepository.createJdbcTemplate(server, queryTimeout);
        int batchSize = 100;
        List<String> col = new ArrayList<>();
        List<Map<String, Object>> rowsBuf = new ArrayList<>();
        AtomicReference<Integer> refRows = new AtomicReference<>(0);
        try {
            jdbcTemplate.query(query, new RowCallbackHandler() {
                @Override
                public void processRow(ResultSet resSet) throws SQLException {
                    if (col.isEmpty()) {
                        ResultSetMetaData metData = resSet.getMetaData();
                        int columnCount = metData.getColumnCount();
                        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                            col.add(metData.getColumnName(columnIndex));
                        }
                    }
                    Map<String, Object> row = new HashMap<>();
                    for (String column : col) {
                        row.put(column, resSet.getObject(column));
                    }
                    rowsBuf.add(row);
                    if (rowsBuf.size() == batchSize) {
                        if (refRows.get() > 0) {
                            saveTestData(tableName, true, col, rowsBuf, false);
                        } else {
                            saveTestData(tableName, exists, col, rowsBuf, true);
                        }
                        refRows.updateAndGet(v -> v + batchSize);
                        rowsBuf.clear();
                    }
                }
            });
        } catch (Exception e) {
            log.error(TdmDbExecuteQueryException.DEFAULT_MESSAGE, e);
            throw new TdmDbExecuteQueryException(e.getMessage());
        }
        if (!rowsBuf.isEmpty()) {
            saveTestData(tableName, exists || refRows.get() > 0, col, rowsBuf,
                    refRows.get() > 0);
            refRows.updateAndGet(v -> v + rowsBuf.size());
        }
        if (refRows.get() == 0) {
            log.info(TdmDbRowNotFoundException.DEFAULT_MESSAGE);
            throw new TdmDbRowNotFoundException();
        }
        ImportTestDataStatistic statistic = new ImportTestDataStatistic();
        statistic.setProcessedRows(refRows.get());
        return statistic;
    }

    @Override
    public ImportTestDataStatistic updateTableBySql(@Nonnull String tableName, @Nonnull String query,
                                                    @Nonnull Integer queryTimeout, @Nonnull Server server) {
        log.info("Update test data table:[{}]. Query:[{}]", tableName, query);
        DataUtils.checkQuery(query);
        DataUtils.checkTableName(tableName);
        ImportTestDataStatistic statistic = new ImportTestDataStatistic();

        String conditionColumnNamePattern = "";
        String conditionColumnName = "";
        Matcher indexColumnMatcher = INDEX_COLUMN_PATTERN.matcher(query);
        while (indexColumnMatcher.find()) {
            conditionColumnNamePattern = indexColumnMatcher.group();
            conditionColumnName = indexColumnMatcher.group(1);
        }

        if (!conditionColumnNamePattern.isEmpty()) {
            List<String> queryColumnNames = TestDataUtils.getColumnsNamesFromQuery(query);
            List<String> existingColumns = getTableColumns(tableName);
            for (String queryColumnName : queryColumnNames) {
                if (!existingColumns.contains(queryColumnName)) {
                    String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
                    String sanitizedQueryColumnName = esapiEncoder.encodeForSQL(oracleCodec, queryColumnName);
                    log.debug("Table:[{}]. Alter new column:[{}]", tableName, queryColumnName);
                    jdbcTemplate.execute(String.format(TestDataQueries.ADD_NEW_COLUMN_VARCHAR,
                            sanitizedTableName, sanitizedQueryColumnName));
                }
            }
            TestDataTable testDataTable;
            Long rows = getTestDataSize(tableName, TestDataType.ALL);
            log.debug("Update test data. Column pattern:[{}], Column name:[{}]", conditionColumnNamePattern,
                    conditionColumnName);
            try (Connection connection = sqlRepository.createConnection(server)) {
                int countOfUpdatedRows = 0;
                for (int offset = 0; offset < rows; offset += UPDATE_TEST_DATA_LIMIT) {
                    testDataTable = getTestData(tableName, TestDataType.AVAILABLE, offset, UPDATE_TEST_DATA_LIMIT,
                            null, null);
                    for (Map<String, Object> row : testDataTable.getData()) {
                        String evaluatedQuery = query.replace(conditionColumnNamePattern,
                                String.valueOf(row.get(conditionColumnName)));
                        try (CallableStatement statement = connection.prepareCall(evaluatedQuery)) {
                            ExecutorService executorService = Executors.newSingleThreadExecutor();
                            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
                            try (ResultSet rs = executorService.submit(() -> {
                                MdcUtils.setContextMap(mdcContext);
                                return statement.executeQuery();
                            }).get(queryTimeout, TimeUnit.SECONDS)) {
                                while (rs.next()) {
                                    for (String columnName : queryColumnNames) {
                                        String value = rs.getString(columnName);
                                        row.put(columnName, value);
                                    }
                                }
                            }
                        } catch (TimeoutException e) {
                            statistic = new ImportTestDataStatistic();
                            String message = "SQL execution has been stopped as maximum time of execution in "
                                    + queryTimeout + " sec is exceeded.";
                            statistic.setError(message);
                            return statistic;
                        }
                        countOfUpdatedRows += updateTableRow(tableName, queryColumnNames, row);
                    }
                }
                statistic.setProcessedRows(countOfUpdatedRows);
            } catch (Exception e) {
                statistic = new ImportTestDataStatistic();
                String message = "Error while updating table: " + tableName;
                log.error(message, e);
                statistic.setError(message + ". " + e.getMessage());
            }
            log.info("The update completed successfully.");
            return statistic;
        } else {
            statistic = new ImportTestDataStatistic();
            String message = "Error while updating table: " + tableName
                    + ". You need to specify all new columns (select IMSI, OBJECT_ID) and column,"
                    + " which will be used to compare (where object_id=${'OBJECT_ID'}).";
            statistic.setError(message);
            return statistic;
        }
    }

    private long updateTableRow(@Nonnull String tableName, @Nonnull List<String> columns,
                                @Nonnull Map<String, Object> row) {
        UpdateQuery updateQuery = new UpdateQuery(esapiEncoder.encodeForSQL(oracleCodec, tableName));
        CustomSql customSql = new CustomSql("\"" + SystemColumns.ROW_ID.getName() + "\"");
        BinaryCondition binaryCondition = PgBinaryCondition.equalTo(customSql,
                String.valueOf(row.get(SystemColumns.ROW_ID.getName())));
        updateQuery.addCondition(binaryCondition);
        for (String column : columns) {
            String value = TestDataUtils.escapeCharacters(String.valueOf(row.get(column)));
            updateQuery.addCustomSetClause(new CustomSql("\"" + esapiEncoder.encodeForSQL(oracleCodec, column) + "\""),
                    esapiEncoder.encodeForSQL(oracleCodec, value));
        }
        return jdbcTemplate.update(updateQuery.toString());
    }

    private ImportTestDataStatistic importTestData(@Nonnull String tableName, boolean exists,
                                                   @Nonnull TestDataTable testDataTable) {
        ImportTestDataStatistic statistic = new ImportTestDataStatistic();
        lockManager.executeWithLockWithUniqueLockKey("importTestData" + tableName, () -> {
            statistic.setProcessedRows(testDataTable.getData().size());
            saveTestData(tableName, exists, testDataTable);
        });
        return statistic;
    }

    @Override
    public TestDataTable getTestData(@Nonnull Boolean isOccupied, @Nonnull String tableName, @Nullable Integer offset,
                                     @Nullable Integer limit, @Nullable List<TestDataTableFilter> filters,
                                     @Nullable TestDataTableOrder order) {
        DataUtils.checkTableName(tableName);
        TestDataType testDataType = isOccupied ? TestDataType.OCCUPIED : TestDataType.AVAILABLE;
        return getTestData(tableName, testDataType, offset, limit, filters, order);
    }

    private TestDataTable getTestData(@Nonnull String tableName, @Nonnull TestDataType testDataType,
                                      @Nullable Integer offset, @Nullable Integer limit,
                                      @Nullable List<TestDataTableFilter> filters,
                                      @Nullable TestDataTableOrder testDataTableOrder) {
        QueryInfo.Builder queryInfoBuilder = QueryInfo.newBuilder(tableName, testDataType);
        if (Objects.nonNull(offset)) {
            queryInfoBuilder.setOffset(offset);
        }
        if (Objects.nonNull(limit)) {
            queryInfoBuilder.setLimit(limit);
        }
        if (Objects.nonNull(filters)) {
            queryInfoBuilder.setFilters(filters);
        }
        if (Objects.nonNull(testDataTableOrder)) {
            queryInfoBuilder.setOrder(testDataTableOrder);
        }
        QueryInfo queryInfo = queryInfoBuilder.build();
        TestDataTable table;
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);

        try {
            log.debug("Start DB query.");
            table = jdbcTemplate.query(queryInfo.getQuery().toString(),
                    extractorProvider.simpleExtractor(sanitizedTableName, queryInfo.getCountQuery().toString(),
                            testDataType,
                            testDataTableOrder));
            log.debug("Stop DB query.");
        } catch (Exception e) {
            log.error(TdmDbExecuteQueryException.DEFAULT_MESSAGE, e);
            throw new TdmDbExecuteQueryException(e.getMessage());
        }
        Optional<TestDataTableImportInfo> testDataTableInfo = importInfoRepository.findById(sanitizedTableName);
        assert table != null;
        testDataTableInfo.ifPresent(dataTableInfo -> {
            table.setQuery(dataTableInfo.getTableQuery());
            table.setUpdateByQuery(dataTableInfo.getUpdateByQuery());
        });
        table.setName(tableName);
        table.setType(testDataType);
        return table;
    }

    @Override
    public TestDataTable getTestData(@Nonnull String tableName, @Nonnull List<String> columnNames,
                                     @Nullable List<TestDataTableFilter> filters) {
        DataUtils.checkTableName(tableName);
        columnNames.forEach(DataUtils::checkColumnName);
        QueryInfo.Builder queryInfoBuilder = QueryInfo.newBuilder(tableName, columnNames, TestDataType.ALL);
        if (Objects.nonNull(filters)) {
            queryInfoBuilder.setFilters(filters);
        }
        QueryInfo queryInfo = queryInfoBuilder.build();
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        return jdbcTemplate.query(queryInfo.getQuery().toString(),
                extractorProvider.simpleExtractor(sanitizedTableName, queryInfo.getCountQuery().toString(),
                        TestDataType.ALL, null));
    }

    @Override
    public TestDataTable getTestDataMultiple(@Nonnull String tableName, @Nullable List<TestDataTableFilter> filters) {
        DataUtils.checkTableName(tableName);
        QueryInfo.Builder queryInfoBuilder = QueryInfo.newBuilder(tableName, TestDataType.AVAILABLE);
        queryInfoBuilder.setLimit(1);
        if (Objects.nonNull(filters)) {
            queryInfoBuilder.setFilters(filters);
        }
        QueryInfo queryInfo = queryInfoBuilder.build();
        TestDataTable table;
        try {
            log.debug("Start DB query.");
            table = jdbcTemplate.query(queryInfo.getQuery().toString(),
                    extractorProvider.multipleExtractor(tableName, TestDataType.AVAILABLE));
            log.debug("Finish DB query.");
        } catch (Exception e) {
            log.error(TdmDbExecuteQueryException.DEFAULT_MESSAGE, e);
            throw new TdmDbExecuteQueryException(e.getMessage());
        }
        assert table != null;
        table.setName(tableName);
        return table;
    }

    @Override
    public TestDataTable getFullTestData(@Nonnull String tableName) {
        DataUtils.checkTableName(tableName);
        return getTestData(tableName, TestDataType.ALL, null, null, null, null);
    }

    @Override
    public File getTestDataTableAsExcel(@Nonnull String tableName, @Nullable Integer offset,
                                        @Nullable Integer limit, @Nullable List<TestDataTableFilter> filters) {
        QueryInfo queryInfo = QueryInfo.newBuilder(tableName, TestDataType.ALL).build();
        updateLastUsage(tableName);
        return jdbcTemplate.query(queryInfo.getQuery().toString(), extractorProvider.fileExtractor(tableName,
                ExportFileType.EXCEL));
    }

    @Override
    public File getTestDataTableAsCsv(@Nonnull String tableName, @Nullable Integer offset,
                                      @Nullable Integer limit, @Nullable List<TestDataTableFilter> filters) {
        QueryInfo queryInfo = QueryInfo.newBuilder(tableName, TestDataType.ALL).build();
        updateLastUsage(tableName);
        return jdbcTemplate.query(queryInfo.getQuery().toString(), extractorProvider.fileExtractor(tableName,
                ExportFileType.CSV));
    }

    @Override
    public TestDataTable saveTestData(@Nonnull String tableName, boolean exists, @Nonnull TestDataTable testDataTable) {
        DataUtils.checkTableName(tableName);
        List<String> columns = testDataTable.getColumns()
                .stream().map(c -> c.getIdentity().getColumnName())
                .collect(Collectors.toList());
        saveTestData(tableName, exists, columns, testDataTable.getData(), false);
        return testDataTable;
    }

    private void saveTestData(@Nonnull String tableName, boolean exists,
                              List<String> columns, List<Map<String, Object>> rows, boolean skipSchemaUpdate) {
        if (skipSchemaUpdate) {
            log.info("Saving test data to a database table with the name: [{}]", tableName);
        }
        if (exists && !skipSchemaUpdate) {
            alterMissingColumns(tableName, columns, TestDataQueries.ADD_NEW_COLUMN_VARCHAR);
        }
        TestDataTableCreator tableCreator = new TestDataTableCreator(tableName);
        Map<String, DbColumn> dbColumns = new HashMap<>();
        List<String> sanitizedColumns = new ArrayList<>();
        for (String columnName : columns) {
            String sanitizedColumnName = esapiEncoder.encodeForSQL(oracleCodec, columnName);
            sanitizedColumns.add(sanitizedColumnName);
            dbColumns.put(sanitizedColumnName, tableCreator.buildColumn(sanitizedColumnName));
        }
        if (!exists) {
            log.info("Creating test data table with the name: [{}]", tableName);
            jdbcTemplate.execute(tableCreator.createTableQuery());
        }
        boolean systemColumnsExists = isSystemColumnsExists(rows);
        if (systemColumnsExists) {
            List<String> columnNames = SystemColumns.getColumnNames();
            columnNames.forEach(c -> dbColumns.put(c, tableCreator.getDbColumns().get(c)));
        }
        if (!skipSchemaUpdate) {
            log.info("Saving test data. Processing rows. Table name: [{}]", tableName);
        }
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        jdbcTemplate.batchUpdate(TestDataUtils.generateInsertTemplate(sanitizedTableName, sanitizedColumns,
                        systemColumnsExists),
                rows,
                Math.min(rows.size(), 100),
                (PreparedStatement ps, Map<String, Object> row) -> {
                    for (int ind = 1; ind <= columns.size(); ind++) {
                        Object rowValue = row.get(columns.get(ind - 1));
                        if (rowValue instanceof String && !rowValue.equals("null")) {
                            ps.setObject(ind, String.valueOf(rowValue));
                        } else if (!(rowValue instanceof String) && rowValue != null) {
                            ps.setObject(ind, TestDataUtils.convertToJsonString(rowValue));
                        } else {
                            ps.setObject(ind, "");
                        }
                    }
                });
        if (!skipSchemaUpdate) {
            log.info("Test data table saved.");
        }
    }

    private boolean isSystemColumnsExists(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            log.error(TdmCreateTestDataTableException.DEFAULT_MESSAGE);
            throw new TdmCreateTestDataTableException();
        } else {
            return rows.get(0).containsKey(SystemColumns.ROW_ID.getName());
        }
    }

    @Override
    public String occupyTestData(@Nonnull String tableName, @Nonnull String occupiedBy, @Nonnull List<UUID> rows) {
        DataUtils.checkColumnName(tableName);
        String date = DateFormatter.DB_DATE_FORMATTER.format(new Timestamp(new Date().getTime()));

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("ids", rows);
        parameters.addValue("user", esapiEncoder.encodeForSQL(oracleCodec, occupiedBy));

        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);

        try {
            int updatedRowsCount = namedParameterJdbcTemplate.update(
                    format(TestDataQueries.OCCUPY_TEST_DATA, sanitizedTableName, date), parameters);
            if (updatedRowsCount == 0) {
                throw new TdmTestDataOccupiedException();
            }
        } catch (TdmInternalException atpTdmException) {
            throw atpTdmException;
        } catch (Exception e) {
            log.error(TdmDbExecuteQueryException.DEFAULT_MESSAGE, e);
            throw new TdmDbExecuteQueryException(e.getMessage());
        }
        return DateFormatter.DB_DATE_FORMATTER.format(new Timestamp(new Date().getTime()));
    }

    @Override
    public void releaseTestData(@Nonnull String tableName, @Nonnull List<UUID> rows) {
        DataUtils.checkColumnName(tableName);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("ids", rows);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        namedParameterJdbcTemplate.update(format(TestDataQueries.RELEASE_TEST_DATA, sanitizedTableName), parameters);
        updateLastUsage(sanitizedTableName);
    }

    @Override
    public void insertRows(@Nonnull String tableName, boolean exists, @Nonnull List<Map<String, Object>> rows,
                           boolean skipSchemaUpdate) {
        DataUtils.checkTableName(tableName);
        List<String> columns = new ArrayList<>(rows.stream()
                .findFirst()
                .orElseThrow(TdmInsertDataException::new).keySet());
        saveTestData(tableName, exists, columns, rows, skipSchemaUpdate);
    }

    @Override
    public int updateRows(@Nonnull String tableName, @Nonnull List<TestDataTableFilter> filters,
                          @Nonnull Map<String, String> dataForUpdate) {
        log.info("Updating rows in table with name: [{}]", tableName);
        DataUtils.checkTableName(tableName);
        UpdateQuery query = new UpdateQuery(tableName);
        setWhereCondition(query, filters);
        for (String key : dataForUpdate.keySet()) {
            query.addCustomSetClause(new CustomSql("\"" + key + "\""), dataForUpdate.get(key));
        }
        return jdbcTemplate.update(query.toString());
    }

    @Override
    public int addInfoToRow(@Nonnull String tableName, @Nonnull List<TestDataTableFilter> filters,
                            @Nonnull Map<String, String> dataForUpdate) {
        log.info("Adding info to row in table with name: [{}]", tableName);
        DataUtils.checkTableName(tableName);
        UpdateQuery query = new UpdateQuery(tableName);
        setWhereCondition(query, filters);
        for (String key : dataForUpdate.keySet()) {
            query.addCustomSetClause(new CustomSql("\"" + key + "\""),
                    new CustomExpression("CONCAT(" + "\"" + key + "\",'\r\n" + dataForUpdate.get(key) + "')"));
        }
        return jdbcTemplate.update(query.toString());
    }

    @Override
    public void deleteRows(@Nonnull String tableName, @Nonnull List<UUID> rows) {
        log.info("Deleting rows from table with name: [{}]", tableName);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        DataUtils.checkColumnName(tableName);
        parameters.addValue("ids", rows);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        namedParameterJdbcTemplate.update(format(TestDataQueries.DELETE_ROWS_BY_ID, sanitizedTableName), parameters);
    }

    @Override
    public void deleteAllRows(@Nonnull String tableName) {
        log.info("Deleting all rows from table with name: [{}]", tableName);
        DataUtils.checkColumnName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        jdbcTemplate.execute(format(TestDataQueries.DELETE_ALL_TABLE_ROWS, sanitizedTableName));
    }

    @Override
    public int deleteRowsByDate(@Nonnull String tableName, @Nonnull LocalDate date) {
        log.info("Deleting rows from table with name [{}] by date", tableName);
        DataUtils.checkColumnName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        return jdbcTemplate.update(format(TestDataQueries.DELETE_ROWS_BY_DATE, sanitizedTableName, date));
    }

    @Override
    public int getCountRows(@Nonnull String tableName) {
        DataUtils.checkTableName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        return jdbcTemplate.queryForObject(format(TestDataQueries.GET_COUNT_ROWS, sanitizedTableName), Integer.class);
    }

    @Override
    public void deleteUnoccupiedRows(@Nonnull String tableName) {
        log.info("Deleting unoccupied rows from table with name: [{}]", tableName);
        DataUtils.checkColumnName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        jdbcTemplate.execute(format(TestDataQueries.DELETE_UNOCCUPIED_ROWS, sanitizedTableName));
    }

    @Override
    public void alterCreatedWhenColumn(List<String> tableNames) {
        for (String tableName : tableNames) {
            DataUtils.checkTableName(tableName);
            alterMissingColumns(tableName, Collections.singletonList(SystemColumns.CREATED_WHEN.getName()),
                    TestDataQueries.ADD_NEW_COLUMN_TIMESTAMP);
        }
    }

    @Override
    public String evaluateQuery(@Nonnull String tableName, @Nonnull String query) {
        DataUtils.checkTableName(tableName);
        DataUtils.checkQuery(query);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        return queryEvaluator.evaluate(sanitizedTableName, query);
    }

    @Override
    public void dropTable(@Nonnull String tableName) {
        log.info("Dropping a table with name: [{}]", tableName);
        DataUtils.checkTableName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        jdbcTemplate.execute(format(TestDataQueries.DROP_TABLE, sanitizedTableName));
    }

    @Override
    public void truncateTable(@Nonnull String tableName) {
        log.info("Truncating a table with name: [{}]", tableName);
        DataUtils.checkTableName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        jdbcTemplate.execute(format(TestDataQueries.TRUNCATE_TABLE, sanitizedTableName));
    }

    @Override
    public void alterOccupiedByColumn(List<String> tableNames) {
        for (String tableName : tableNames) {
            DataUtils.checkTableName(tableName);
            String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
            alterMissingColumns(sanitizedTableName, Collections.singletonList("OCCUPIED_BY"),
                    TestDataQueries.ADD_NEW_COLUMN_VARCHAR);
        }
    }

    @Override
    public ColumnValues getColumnDistinctValues(@Nonnull String tableName, @Nonnull String columnName,
                                                Boolean occupied) {
        DataUtils.checkColumnName(columnName);
        DataUtils.checkTableName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        String sanitizedColumnName = esapiEncoder.encodeForSQL(oracleCodec, columnName);

        if (occupied == null) {
            log.debug("GET_COLUMN_DISTINCT_VALUES");
            return new ColumnValues(jdbcTemplate.queryForList(
                    format(TestDataQueries.GET_COLUMN_DISTINCT_VALUES, sanitizedColumnName, sanitizedTableName),
                    String.class));
        } else {
            log.debug("GET_COLUMN_DISTINCT_VALUES_BY_OCCUPIED");
            return new ColumnValues(jdbcTemplate.queryForList(
                    format(TestDataQueries.GET_COLUMN_DISTINCT_VALUES_BY_OCCUPIED,
                            sanitizedColumnName, sanitizedTableName), String.class, occupied));
        }
    }

    @Override
    public int getColumnDistinctValuesCount(@Nonnull String tableName, @Nonnull String columnName,
                                            String columnType, Boolean occupied) {
        DataUtils.checkColumnName(columnName);
        DataUtils.checkTableName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        String sanitizedColumnName = esapiEncoder.encodeForSQL(oracleCodec, columnName);
        try {
            if (columnType.equals("varchar")) {
                Integer columnValueSize = jdbcTemplate.queryForObject(
                        format(TestDataQueries.GET_COLUMN_CHARACTER_LENGTH, sanitizedColumnName, sanitizedTableName),
                        Integer.class);
                if (columnValueSize != null && columnValueSize > 50000) {
                    return 50;
                }
            }
        } catch (Exception e) {
            log.debug(format("Error by get character length for column: %s. Message: %s", columnName, e.getMessage()));
        }

        if (occupied == null) {
            log.debug("GET_COLUMN_DISTINCT_VALUES");
            return jdbcTemplate.queryForObject(
                    format(TestDataQueries.GET_COLUMN_DISTINCT_VALUES_COUNT, sanitizedColumnName, sanitizedTableName),
                    Integer.class);
        } else {
            return jdbcTemplate.queryForObject(
                    format(TestDataQueries.GET_COLUMN_DISTINCT_VALUES_BY_OCCUPIED_COUNT, sanitizedColumnName,
                            sanitizedTableName),
                    Integer.class, occupied);
        }
    }

    /**
     * Get table by created date.
     *
     * @param tableName - table name.
     * @param dateFrom  - beginning date.
     * @param dateTo    - ending date.
     * @return - table.
     */
    public TestDataTable getTableByCreatedWhen(@Nonnull String tableName, @Nonnull LocalDate dateFrom,
                                               @Nonnull LocalDate dateTo) {
        DataUtils.checkTableName(tableName);
        List<TestDataTableFilter> filters = new ArrayList<>();
        TestDataTableFilter filterFrom = new TestDataTableFilter("CREATED_WHEN", "From",
                Collections.singletonList(
                        dateFrom.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))), false);
        TestDataTableFilter filterTo = new TestDataTableFilter("CREATED_WHEN", "To",
                Collections.singletonList(
                        dateTo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))), false);
        filters.add(filterFrom);
        filters.add(filterTo);
        return getTestData(tableName, TestDataType.ALL, null, null, filters, null);
    }

    @Override
    public boolean changeTestDataTitle(@Nonnull String tableName, @Nullable String tableTitle) {
        log.info("Changing  test data table title. Table name: [{}], new title: [{}]", tableName, tableTitle);
        updateLastUsage(tableName);
        DataUtils.checkColumnName(tableTitle);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("table_name", tableName);
        parameters.addValue("table_title", tableTitle);
        if (namedParameterJdbcTemplate.update(TestDataQueries.CHANGE_TEST_DATA_TITLE, parameters) > 0) {
            log.info("Test data title successfully changed.");
            return true;
        } else {
            log.warn("Test data title was not changed.");
            return false;
        }
    }

    @Override
    public Long getTestDataSize(@Nonnull String tableName, @Nonnull TestDataType dataType) {
        DataUtils.checkTableName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        QueryInfo.Builder queryInfoBuilder = QueryInfo.newBuilder(sanitizedTableName, dataType);
        try {
            return jdbcTemplate.queryForObject(queryInfoBuilder.build().getCountQuery().toString(), Long.class);
        } catch (Exception e) {
            return 0L;
        }
    }

    private void setWhereCondition(UpdateQuery query, List<TestDataTableFilter> filters) {
        for (TestDataTableFilter filter : filters) {
            SearchCondition searchCondition = SearchConditionFactory.getCondition(filter.getSearchCondition(),
                    filter.isCaseSensitive());
            CustomSql column = new CustomSql("\"" + filter.getColumn() + "\"");
            if (filter.getValues().isEmpty()) {
                throw new IllegalIdentifierException("There is no values in filter: " + filter);
            } else {
                String filterValue = filter.getValues().get(0); //It's not good, need to do refactor here
                BinaryCondition binaryCondition = searchCondition.create(column, filterValue);
                query.addCondition(binaryCondition);
            }
        }
    }

    /**
     * Add new column to a Data Table.
     *
     * @param tableName - data table name
     * @param columns   - list of columns from the query
     */
    private void alterMissingColumns(@Nonnull String tableName, @Nonnull List<String> columns, @Nonnull String query) {
        log.info("Checking for missing columns. Table: {}", tableName);
        List<String> existedColumnNames = getTableColumns(tableName);
        existedColumnNames.removeAll(SystemColumns.getColumnNames());
        ArrayList<String> columnsBuff = new ArrayList<>(columns);
        columnsBuff.removeAll(existedColumnNames);
        if (!columnsBuff.isEmpty()) {
            log.info("Were found missing columns: {}", columnsBuff);
            if (ALTER_COLUMN_HARD_MODE.equals(alterColumnMode)) {
                recreateTable(tableName, columns);
            } else {
                columnsBuff.forEach(columnName -> {
                    String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
                    String sanitizedColumnName = esapiEncoder.encodeForSQL(oracleCodec, columnName);
                    //TODO remake format of query
                    jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                        try (PreparedStatement ps = conn.prepareStatement(
                                String.format(query, sanitizedTableName, sanitizedColumnName))) {
                            ps.execute();
                        }
                        return null;
                    });
                    jdbcTemplate.execute(format(query, sanitizedTableName, sanitizedColumnName));
                });
            }
            log.info("Missing columns successfully added.");
        }
    }

    /**
     * Get all columns except system columns.
     *
     * @param tableName - data table name
     * @return list of additional table columns
     */
    private List<String> getTableColumns(@Nonnull String tableName) {
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        return jdbcTemplate.queryForList(TestDataQueries.DATA_TABLE_COLUMNS, String.class, sanitizedTableName);
    }

    private void recreateTable(@Nonnull String tableName, @Nonnull List<String> columns) {
        log.info("Recreate Table: [{}], columns: [{}]", tableName, columns);
        List<String> currentColumns = getTableColumns(tableName);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            public void doInTransactionWithoutResult(@Nonnull TransactionStatus status) {
                jdbcTemplate.execute(TestDataQueries.BEGIN_WORK);
                jdbcTemplate.execute(format(TestDataQueries.LOCK_TABLE, tableName));
                String tmpTableName = tableName + "_";
                TestDataTableCreator tableCreator = new TestDataTableCreator(tmpTableName);
                columns.forEach(tableCreator::buildColumn);
                jdbcTemplate.execute(tableCreator.createTableQuery());
                String currentColumnNames = String.join("\" , \"", currentColumns);
                String insertQuery = format(TestDataQueries.INSERT_DATA, tmpTableName, currentColumnNames,
                        currentColumnNames, tableName);
                log.debug("Recreating table:[{}], data insert query:[{}]", tableName, insertQuery);
                jdbcTemplate.execute(insertQuery);
                jdbcTemplate.execute(format(TestDataQueries.DROP_TABLE, tableName));
                jdbcTemplate.execute(format(TestDataQueries.RENAME_TABLE, tmpTableName, tableName));
                jdbcTemplate.execute(TestDataQueries.COMMIT_WORK);
            }
        });
        log.info("Table: [{}] recreated.", tableName);
    }

    @Override
    public List<String> getTestDataTableCatalogDiscrepancyTestDataFlagsTable() {
        return jdbcTemplate.queryForList(TestDataQueries.GET_TEST_DATA_TABLE_CATALOG_DISCREPANCY_TEST_DATA_FLAGS_TABLE,
                String.class);
    }

    @Override
    public List<String> getTestDataFlagsTableDiscrepancyTestDataTableCatalog() {
        return jdbcTemplate.queryForList(TestDataQueries.GET_TEST_DATA_FLAGS_TABLE_DISCREPANCY_TEST_DATA_TABLE_CATALOG,
                String.class);
    }

    @Override
    public void saveTestDataTableCatalog(String tableName, String tableTitle, UUID projectId, UUID systemId,
                                         UUID environmentId) {
        TestDataTableCatalog tableCatalog = new TestDataTableCatalog(tableName, projectId, environmentId,
                systemId, tableTitle);
        tableCatalog.setLastUsage(new Date());
        getCleanupConfig(projectId, tableTitle).ifPresent(config -> tableCatalog.setCleanupConfigId(config.getId()));
        catalogRepository.save(tableCatalog);
    }

    private Optional<TestDataCleanupConfig> getCleanupConfig(@Nonnull UUID projectId, @Nonnull String tableTitle) {
        List<TestDataTableCatalog> catalogList = catalogRepository
                .findAllByProjectIdAndTableTitleAndCleanupConfigIdIsNotNull(projectId, tableTitle);
        return catalogList
                .stream()
                .map(c -> cleanupConfigRepository.findById(c.getCleanupConfigId()).orElse(new TestDataCleanupConfig()))
                .filter(TestDataCleanupConfig::isShared).findFirst();
    }

    @Override
    public String getFirstRecordFromDataStorageTable(@Nonnull String tableName, @Nonnull String columnName) {
        DataUtils.checkColumnName(columnName);
        DataUtils.checkTableName(tableName);
        String sanitizedTableName = esapiEncoder.encodeForSQL(oracleCodec, tableName);
        String sanitizedColumnName = esapiEncoder.encodeForSQL(oracleCodec, columnName);
        return jdbcTemplate.queryForObject(format(TestDataQueries.GET_FIRST_RECORD_FROM_DATA_STORAGE_TABLE,
                sanitizedColumnName, sanitizedTableName), String.class);
    }

    @Override
    public void updateLastUsage(@Nonnull String tableName) {
        DataUtils.checkTableName(tableName);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String currentDate = simpleDateFormat.format(new Date());
        if (cacheLastUsageTable.size() == 2000) {
            cacheLastUsageTable.clear();
        }
        if (!cacheLastUsageTable.containsKey(tableName) || !cacheLastUsageTable.get(tableName).equals(currentDate)) {
            Date lastUsage = catalogRepository.findByTableName(tableName).getLastUsage();
            if (lastUsage == null || !simpleDateFormat.format(lastUsage).equals(currentDate)) {
                catalogRepository.updateLastUsageByTableName(new Date(), tableName);
            }
            cacheLastUsageTable.put(tableName, currentDate);
        }
    }

    @Override
    public List<String> getTablesBySystemIdAndExistingColumn(@Nonnull UUID systemId, @Nonnull UUID environmentId,
                                                             @Nonnull String columnName) {
        DataUtils.checkColumnName(columnName);
        String sanitizedColumnName = esapiEncoder.encodeForSQL(oracleCodec, columnName);
        return jdbcTemplate.queryForList(TestDataQueries.TABLES_BY_SYSTEM_AND_COLUMN,
                String.class, systemId, environmentId, sanitizedColumnName);
    }

    @Override
    public List<String> getAllColumnNamesBySystemId(@Nonnull UUID systemId) {
        return jdbcTemplate.queryForList(TestDataQueries.GET_ALL_COLUMN_NAMES_BY_SYSTEM_ID, String.class, systemId);
    }
}
