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

package org.qubership.atp.tdm.model.cleanup.cleaner.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.owasp.esapi.Encoder;
import org.owasp.esapi.codecs.OracleCodec;
import org.owasp.esapi.reference.DefaultEncoder;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.exceptions.internal.TdmDeleteRowException;
import org.qubership.atp.tdm.model.cleanup.cleaner.TestDataCleaner;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.utils.TestDataUtils;
import org.slf4j.MDC;

import liquibase.repackaged.net.sf.jsqlparser.parser.CCJSqlParserUtil;
import liquibase.repackaged.net.sf.jsqlparser.statement.Statement;
import liquibase.repackaged.net.sf.jsqlparser.statement.select.Select;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SqlTestDataCleaner implements TestDataCleaner {
    private static final Pattern COLUMN_PATTERN = Pattern.compile("\\$\\{'([^']+)'}");

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private int queryTimeout;
    private final Connection connection;
    @Getter
    private String query;

    private final Encoder esapiEncoder = DefaultEncoder.getInstance();
    private final OracleCodec oracleCodec = new OracleCodec();

    /**
     * Constructor with queryTimeout.
     */
    public SqlTestDataCleaner(@Nonnull Connection connection, @Nullable String query, int queryTimeout) {
        this.connection = connection;
        this.query = query;
        this.queryTimeout = queryTimeout;
    }

    @Override
    @Nonnull
    public List<Map<String, Object>> runCleanup(@Nonnull TestDataTable testDataTable) throws Exception {
        List<String> columns = collectParameterColumnsList(testDataTable);

        /*
            All column placeholders are replaced with '?' character, and columns list is populated.
            Let's parse the resulting query.
         */
        parseQuery();

        /*
            Check rows existence in the testDataTable.
            This checking could be the 1st in the method,
            but... let's leave it here, to perform query parsing in any case.
         */
        List<Map<String, Object>> rows = testDataTable.getData();
        if (rows.isEmpty()) {
            log.warn("Table body has no rows");
            return new ArrayList<>();
        }

        /*
            Prepare statement and execute it in the loop through all rows of testDataTable.
            As a result, collect rows-to-be-deleted into rowsToBeDeleted list.
            TODO: Should be re-analyzed, because it can be very resource-consuming in case wide target table.
         */
        List<Map<String, Object>> rowsToBeDeleted = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            log.info("Cleanup query: {}", query);
            for (Map<String, Object> row : rows) {
                log.debug("Processing row #{}", row.get("ROW_ID"));
                for (int i = 0; i < columns.size(); i++) {
                    String columnName = columns.get(i);
                    preparedStatement.setString(i + 1,
                            esapiEncoder.encodeForSQL(oracleCodec, String.valueOf(row.get(columnName))));
                }
                Map<String, String> mdcContext = MDC.getCopyOfContextMap();
                try (ResultSet rs = executorService.submit(() -> {
                            MdcUtils.setContextMap(mdcContext);
                            return preparedStatement.executeQuery();
                        })
                        .get(queryTimeout, TimeUnit.SECONDS)) {
                    if (!rs.next()) {
                        log.debug("Row with id: {} will be marked for deleting", row.get("ROW_ID"));
                        rowsToBeDeleted.add(row);
                    } else {
                        log.debug("Row with id: {} will be skipped", row.get("ROW_ID"));
                    }
                } catch (TimeoutException e) {
                    throw new TimeoutException("SQL execution has been stopped as maximum time of execution in "
                            + queryTimeout + " sec is exceeded.");
                } catch (SQLSyntaxErrorException e) {
                    throw new SQLSyntaxErrorException("Incorrect SQL syntax.", e);
                } catch (Exception e) {
                    log.error(String.format(TdmDeleteRowException.DEFAULT_MESSAGE,
                            row.get("ROW_ID"), testDataTable.getName()), e);
                    throw new TdmDeleteRowException(row.get("ROW_ID").toString(), testDataTable.getName());
                }
            }
            return rowsToBeDeleted;
        }
    }

    public List<String> collectParameterColumnsList(@Nonnull TestDataTable testDataTable) {
        Matcher m = COLUMN_PATTERN.matcher(query);
        List<String> columns = new ArrayList<>();
        String target;
        String column;
        int index;

        log.info("Original cleanup query: {}", query);

        /*
            catch and replace column placeholders to build a PreparedStatement
         */
        while (m.find()) {
            target = m.group();
            column = m.group(1);
            validateIdentifier(column);
            index = TestDataUtils.getIndexHeaderColumnByName(testDataTable, column);
            if (index < 0) {
                throw new IllegalArgumentException("Column '" + column + "' doesn't exist");
            }

            query = query.replace(target, "?").trim();
            log.debug("Formatted cleanup query: {}", query);

            String sanitizedColumnName = esapiEncoder.encodeForSQL(oracleCodec, column);
            columns.add(sanitizedColumnName);
        }
        return columns;
    }

    public boolean parseQuery() {
        Statement statement = CCJSqlParserUtil.parse(query.trim().toUpperCase(Locale.ROOT));
        if (statement instanceof Select) {
            log.debug("This is a SELECT query.");
        } else {
            throw new SecurityException("Only SELECT statements are allowed!");
        }
        if (query.contains(";") || query.contains("--") || query.contains("/*")) {
            throw new SecurityException("Potentially dangerous SQL syntax");
        }
        return true;
    }

    private void validateIdentifier(String input) {
        if (!IDENTIFIER_PATTERN.matcher(input).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + input);
        }
    }
}
