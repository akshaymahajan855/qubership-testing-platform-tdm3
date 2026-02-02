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

public class TestDataQueries {

    public static final String OCCUPIED_CONDITION = "WHERE \"SELECTED\" = ?";

    public static final String GET_OCCUPIED_STATISTICS_GROUP_BY =
            "SELECT NEW org.qubership.atp.tdm.model.table.TestDataOccupyReportGroupBy"
            + "(c.occupiedBy, c.occupiedDate, c.tableName, COUNT(*)) "
            + "FROM TestDataOccupyStatistic as c "
            + "WHERE c.projectId = :projectId "
            + "AND c.occupiedDate >= :date "
            + "GROUP BY c.occupiedBy, c.occupiedDate, c.tableName "
            + "ORDER BY c.tableName";

    public static final String OCCUPY_TEST_DATA =
            "update %s set \"SELECTED\" = true, \"OCCUPIED_BY\" = :user, \"OCCUPIED_DATE\" = '%s' "
                    + "where \"SELECTED\" = false and \"ROW_ID\" IN (:ids)";

    public static final String RELEASE_TEST_DATA =
            "update %s set \"SELECTED\" = false, \"OCCUPIED_BY\" = '' "
                    + "where \"ROW_ID\" IN (:ids)";

    public static final String DROP_TABLE = "DROP TABLE IF EXISTS %s CASCADE";

    public static final String TRUNCATE_TABLE = "TRUNCATE TABLE %s";

    public static final String DATA_TABLE_COLUMNS = "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            + "WHERE UPPER(TABLE_NAME) = UPPER(?)";

    public static final String TABLES_BY_SYSTEM_AND_COLUMN = "SELECT table_name FROM information_schema.COLUMNS "
            + "WHERE LOWER(table_name) IN (SELECT LOWER(table_name) FROM test_data_table_catalog "
            + "WHERE system_id = ? AND environment_id = ?) AND column_name = ?";

    public static final String ADD_NEW_COLUMN_VARCHAR = "ALTER TABLE %s ADD COLUMN IF NOT EXISTS \"%s\" VARCHAR";

    public static final String ADD_NEW_COLUMN_TIMESTAMP = "ALTER TABLE %s ADD COLUMN IF NOT EXISTS \"%s\" TIMESTAMP";

    public static final String DELETE_ROWS_BY_ID = "DELETE FROM %s where \"ROW_ID\" IN (:ids)";

    public static final String DELETE_ALL_TABLE_ROWS = "DELETE FROM %s";

    public static final String DELETE_ROWS_BY_DATE = "DELETE FROM %s WHERE \"CREATED_WHEN\" <= TIMESTAMP '%s 23:59:59'";

    public static final String DELETE_UNOCCUPIED_ROWS = "DELETE FROM %s where \"SELECTED\" = false";

    public static final String GET_TEST_DATA_AVAILABILITY_ITEM = ""
            + "SELECT * FROM "
            + "(SELECT COUNT(*) as available FROM %s WHERE \"SELECTED\" = false) available, "
            + "(SELECT COUNT(*) as occupied FROM %s WHERE \"SELECTED\" = true) occupied,"
            + "(SELECT COUNT(*) FROM %s\n"
            + "WHERE \"SELECTED\" = true\n"
            + "AND \"OCCUPIED_DATE\" >= '%s'::TIMESTAMP WITH TIME ZONE\n"
            + "AND \"OCCUPIED_DATE\" <= '%s'::TIMESTAMP WITH TIME ZONE) occupiedToday,"
            + "(SELECT COUNT(*) as total FROM %s ) total";

    public static final String GET_TEST_DATA_CONSUMPTION_ITEM = ""
            + "SELECT date, SUM(count) as count FROM ( "
            + "SELECT date, count FROM "
            + "( "
            + "SELECT TO_CHAR(occupied_date, 'YYYY-MM-dd') as date, COUNT(*) as count "
            + "FROM test_data_occupy_statistic "
            + "WHERE LOWER(table_name) = ? AND (occupied_date BETWEEN ?::date AND ?::date) "
            + "GROUP BY date ORDER BY date) as existing "
            + ") as statistics "
            + "GROUP BY date ORDER BY date";

    public static final String GET_TEST_DATA_OUTDATED_ITEM = ""
            + "SELECT date, SUM(created) AS created, SUM(consumed) AS consumed, SUM(outdated) AS outdated "
            + "FROM ((SELECT TO_CHAR(\"CREATED_WHEN\", 'YYYY-MM-dd') AS date, COUNT(*) as created, 0 consumed, 0 "
            + "outdated "
            + "FROM %s WHERE \"SELECTED\" = false AND (\"CREATED_WHEN\" BETWEEN ?::date AND ?::date) "
            + "GROUP BY date ORDER BY date) "
            + "UNION ALL "
            + "( "
            + "SELECT TO_CHAR(occupied_date, 'YYYY-MM-dd') as date, 0 created, COUNT(*) as consumed, 0 outdated "
            + "FROM test_data_occupy_statistic "
            + "WHERE LOWER(table_name) = ? "
            + "AND occupied_date is not null "
            + "GROUP BY date ORDER BY date) "
            + "UNION ALL "
            + "( "
            + "SELECT TO_CHAR(occupied_date, 'YYYY-MM-dd') as date,0 created, 0 consumed, COUNT(*) "
            + "AS outdated "
            + "FROM test_data_occupy_statistic WHERE LOWER(table_name) = ? "
            + "AND OCCUPIED_DATE >= ?::date "
            + "GROUP BY date ORDER BY date "
            + ")) AS test GROUP BY date ";

    public static final String ALTER_OCCUPIED_DATE_COLUMN =
            "ALTER TABLE %s ADD COLUMN IF NOT EXISTS \"OCCUPIED_DATE\" TIMESTAMP";

    public static final String UPDATE_OCCUPIED_DATE =
            "UPDATE %s SET \"OCCUPIED_DATE\" = CURRENT_TIMESTAMP "
                    + "WHERE \"SELECTED\" = true AND \"OCCUPIED_DATE\" IS NULL";

    public static final String BEGIN_WORK = "BEGIN WORK;";

    public static final String LOCK_TABLE = "LOCK TABLE %s IN SHARE ROW EXCLUSIVE MODE;";

    public static final String INSERT_DATA = "insert into %s (\"%s\") select " + "\"%s\" from %s";

    public static final String RENAME_TABLE = "ALTER TABLE %s RENAME TO %s;";

    public static final String COMMIT_WORK = "COMMIT WORK;";

    public static final String GET_COLUMN_CHARACTER_LENGTH = "SELECT character_length(\"%s\") FROM %s LIMIT 1";

    public static final String GET_COLUMN_DISTINCT_VALUES = "SELECT DISTINCT \"%s\" FROM %s";

    public static final String GET_COLUMN_DISTINCT_VALUES_COUNT = "SELECT COUNT(DISTINCT \"%s\") FROM %s";

    public static final String GET_COLUMN_DISTINCT_VALUES_BY_OCCUPIED =
            GET_COLUMN_DISTINCT_VALUES + " " + OCCUPIED_CONDITION;

    public static final String GET_COLUMN_DISTINCT_VALUES_BY_OCCUPIED_COUNT =
            GET_COLUMN_DISTINCT_VALUES_COUNT + " " + OCCUPIED_CONDITION;

    public static final String GET_OCCUPIED_STATISTIC_BY_PROJECT =
            "SELECT DISTINCT ON(table_name) "
                    + "row_id, table_name, occupied_by, occupied_date, "
                    + "project_id, system_id, table_title, created_when "
                    + "FROM test_data_occupy_statistic "
                    + "WHERE project_id = :projectId "
                    + "ORDER BY table_name";

    public static final String CHANGE_OCCUPIED_TEST_DATA_TITLE =
            "UPDATE test_data_occupy_statistic SET table_title = :table_title " +
                    "WHERE table_name = :table_name";

    public static final String GET_OCCUPIED_STATISTIC_BY_PROJECT_AND_SYSTEM =
            "SELECT DISTINCT ON(table_name) "
                    + "row_id, table_name, occupied_by, occupied_date, "
                    + "project_id, system_id, table_title, created_when "
                    + "FROM test_data_occupy_statistic "
                    + "WHERE project_id = :projectId AND system_id = :systemId "
                    + "ORDER BY table_name";

    public static final String DELETE_OCCUPIED_STATISTIC = "DELETE FROM test_data_occupy_statistic "
            + "WHERE row_id IN (:rowIds)";

    public static final String GET_STATISTIC_CREATED_WHEN =
            "SELECT TO_CHAR(CREATED_WHEN, 'YYYY-MM-dd') as date, COUNT(*) as count "
                    + "FROM test_data_occupy_statistic "
                    + "WHERE LOWER(table_name) = ? "
                    + "AND CREATED_WHEN BETWEEN ?::date AND ?::date "
                    + "GROUP BY CREATED_WHEN ORDER BY CREATED_WHEN";

    public static final String CHANGE_TEST_DATA_TITLE = "UPDATE test_data_table_catalog "
            + "SET table_title = :table_title WHERE table_name = :table_name";

    public static final String DISTINCT_COLUMN_BY_TABLE_NAME = "SELECT distinct on (table_name) * "
            + "FROM test_data_table_column";

    public static final String GET_TEST_DATA_TABLE_CATALOG_DISCREPANCY_TEST_DATA_FLAGS_TABLE =
            "select tc.table_name "
            + "from test_data_table_catalog tc left join test_data_flags_table tf on tf.table_name = tc.table_name "
            + "where tf.table_name is null";

    public static final String GET_TEST_DATA_FLAGS_TABLE_DISCREPANCY_TEST_DATA_TABLE_CATALOG =
            "select tf.table_name "
            + "from test_data_table_catalog tc right join test_data_flags_table tf on tf.table_name = tc.table_name "
            + "where tc.table_name is null";

    public static final String GET_FIRST_RECORD_FROM_DATA_STORAGE_TABLE =
            "select \"%s\" from %s limit 1";

    public static final String GET_OCCUPIED_BY_USERS_TABLE =
            "SELECT "
            + "sourceTable.table_title, "
            + "sourceTable.table_name, "
            + "sourceTable.occupied_by,"
            + " %s "
            + "FROM ( "
            + "    SELECT stats.table_title, stats.table_name, "
            + "        stats.occupied_by, stats.occupied_date, count(*) as amount "
            + "    FROM test_data_occupy_statistic AS stats "
            + "JOIN test_data_table_catalog AS catalog on stats.table_name = catalog.table_name "
            + "    WHERE stats.project_id ='%s' AND occupied_date BETWEEN '%s' AND '%s' %s "
            + "    GROUP BY stats.table_title, stats.table_name, occupied_by, occupied_date  ) sourceTable "
            + "GROUP BY sourceTable.table_title,sourceTable.table_name, sourceTable.occupied_by "
            + "%s ";

    public static final String GET_COUNT_OF_ROWS =
            "SELECT COUNT(*) FROM (%s) AS foo";

    public static final String GET_COUNT_ROWS =
            "SELECT COUNT(*) FROM %s";

    public static final String GET_ALL_COLUMN_NAMES_BY_SYSTEM_ID =
            "SELECT DISTINCT column_name FROM information_schema.COLUMNS "
            + "WHERE LOWER(table_name) IN (SELECT LOWER(table_name) FROM test_data_table_catalog "
            + "WHERE system_id = ?)";

    public static final String GET_AVAILABLE_DATA_FOR_EACH_VALUE =
            "SELECT \"%s\", count(*) FROM %s "
            + "WHERE \"SELECTED\" = false AND \"%s\" IN ('%s') "
            + "GROUP BY \"%s\"";

}
