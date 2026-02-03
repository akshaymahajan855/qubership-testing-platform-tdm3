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

package org.qubership.atp.tdm.repo.impl.extractors;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.qubership.atp.tdm.exceptions.internal.TdmGetTableException;
import org.qubership.atp.tdm.model.ExportFileType;
import org.qubership.atp.tdm.model.table.TestDataType;
import org.qubership.atp.tdm.model.table.column.TestDataTableColumn;
import org.qubership.atp.tdm.service.ColumnService;
import org.qubership.atp.tdm.utils.TestDataTableConvertor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestDataTableAsFileExtractor implements ResultSetExtractor<File> {

    private final ColumnService columnService;
    private final String tableName;
    private final ExportFileType fileType;

    TestDataTableAsFileExtractor(@Nonnull ColumnService columnService, @Nonnull String tableName,
                                 @Nonnull ExportFileType fileType) {
        this.columnService = columnService;
        this.tableName = tableName;
        this.fileType = fileType;
    }

    @Override
    public File extractData(@Nonnull ResultSet resultSet) throws SQLException, DataAccessException {
        List<TestDataTableColumn> columns = columnService.extractColumns(this.tableName, TestDataType.ALL, resultSet);
        try {
            if (ExportFileType.EXCEL.equals(this.fileType)) {
                return TestDataTableConvertor.convertTableToExcelFile(this.tableName, columns, resultSet);
            } else {
                return TestDataTableConvertor.convertTableToCsvFile(this.tableName, columns, resultSet);
            }
        } catch (IOException e) {
            log.error(String.format(TdmGetTableException.DEFAULT_MESSAGE, this.fileType.name()), e);
            throw new TdmGetTableException(this.fileType.name());
        }
    }
}
