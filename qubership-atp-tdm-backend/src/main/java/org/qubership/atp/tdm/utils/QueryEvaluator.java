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

package org.qubership.atp.tdm.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.tdm.exceptions.internal.TdmEvaluateQueryException;
import org.qubership.atp.tdm.model.table.TestDataTable;
import org.qubership.atp.tdm.repo.impl.extractors.TestDataExtractorProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryEvaluator {

    private static final Integer MAX_ITERATIONS = 5;

    private static final String OPEN_BRACKET = "${";
    private static final String CLOSE_BRACKET = "}";

    private static final String COUNT_QUERY = "select 5";
    private static final String TDM_TABLE_ALIAS = "tdm_table";

    private final JdbcTemplate jdbcTemplate;
    private final TestDataExtractorProvider extractorProvider;

    /**
     * Evaluate query.
     */
    public String evaluate(@Nonnull String tableName, @Nonnull String query) {
        return query.contains("${") ? evaluateQuery(tableName, query) : query;
    }

    private String evaluateQuery(@Nonnull String tableName, @Nonnull String inputQuery) {
        List<String> queries = extractQueries(inputQuery);

        for (String query : queries) {
            String finalQuery = query.replace(TDM_TABLE_ALIAS, tableName);
            log.debug("Execute query: {}", finalQuery);
            TestDataTable table = jdbcTemplate.query(finalQuery, extractorProvider.simpleExtractor(tableName,
                    COUNT_QUERY));

            String result;
            if (Objects.isNull(table)) {
                log.error(String.format(TdmEvaluateQueryException.DEFAULT_MESSAGE, query));
                throw new TdmEvaluateQueryException(query);
            } else {
                result = StringUtils.join(table.getData().stream()
                        .map(data -> data.get(table.getColumns().get(0).getIdentity().getColumnName()))
                        .collect(Collectors.toList()), ", ");
            }
            log.debug("Query result: {}", result);
            inputQuery = inputQuery.replace(OPEN_BRACKET + query + CLOSE_BRACKET, result);
        }

        log.debug("Evaluated query: {}", inputQuery);

        return inputQuery;
    }

    private List<String> extractQueries(@Nonnull String inputQuery) {
        List<String> queries = new ArrayList<>();

        int iteration = 0;
        int from;
        int to = 0;

        do {
            iteration++;
            from = inputQuery.indexOf(OPEN_BRACKET, to);
            to = inputQuery.indexOf(CLOSE_BRACKET, from);
            if (from != -1) {
                String query = inputQuery.substring(from + 2, to);
                queries.add(query);
            }
        } while (from != -1 || iteration > MAX_ITERATIONS);

        return queries;
    }
}
