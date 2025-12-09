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

package org.qubership.atp.tdm.websocket.bulkaction.cleanup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.atp.tdm.AbstractTestDataTest;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.cleanup.CleanupResults;
import org.qubership.atp.tdm.model.mail.bulkaction.BulkCleanupMailSender;
import org.qubership.atp.tdm.repo.CleanupConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BulkActionHandlerTest extends AbstractTestDataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    TdmMdcHelper helper = new TdmMdcHelper();

    @Autowired
    ExecutorService executorService;
    @Autowired
    CleanupConfigRepository cleanupConfigRepository;
    @Autowired
    BulkCleanupMailSender bulkCleanupMailSender;
    @MockBean
    WebSocketSession session;
    BulkDataCleanupHandler bulkDataCleanupHandler;

    @BeforeEach
    public void setUp() throws Exception {
        bulkDataCleanupHandler = new BulkDataCleanupHandler(executorService, catalogRepository, environmentsService,
                cleanupService, cleanupConfigRepository, bulkCleanupMailSender, currentTime, lockManager, helper);

        when(session.isOpen()).thenReturn(true);
        when(session.getUri()).thenReturn(new URI("localhost:8080/"));
        when(environmentsService.getLazyProjectById(any())).thenReturn(lazyProject);
        when(environmentsService.getEnvNameById(any())).thenReturn("Test Environment");
        when(environmentsService.getLazyEnvironments(any())).thenReturn(Collections.singletonList(lazyEnvironment));
        when(environmentsService.getConnectionsSystemById(any(), any())).thenReturn(connections);
    }

    @Test
    public void handleTextMessageTest_doNotFoundTableForCleanup_sendMessageNothingFound( ) throws Exception {
        final UUID projectId = UUID.randomUUID();
        String tableName = "tdm_run_handle_cleanup_sql_config_error_project_id";
        String tableTitle = "TDM Run Handler Cleanup SQL Config Error Project Id";
        BulkActionConfig bulkActionConfig = new BulkActionConfig(){{
            setProjectId(UUID.randomUUID());
            setSystemId(systemId);
            setSaveOccupiedData(false);
            setExecuteInParallel(false);
            setSendResult(false);
            setRecipients("example@example.com");
            setTableTitle(tableTitle);
        }};
        deleteTestDataTableIfExists(tableName);
        createTestDataTable(tableName);
        createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        String strConfig = objectMapper.writeValueAsString(bulkActionConfig);

        final long processId = 111;
        when(currentTime.getCurrentTimeMillis()).thenReturn(processId);

        bulkDataCleanupHandler.handleTextMessage(session, new TextMessage(strConfig));

        Thread.sleep(5000);

        deleteTestDataTableIfExists("tdm_run_handle_cleanup_sql_config_error_project_id");

        verify(session).sendMessage(new TextMessage("{\"id\":" + processId + ", \"status\": \"STARTED\"}"));
        verify(session).sendMessage(new TextMessage("{\"id\":" + processId + ", \"status\": \"NOTHING_FOUND\"}"));

        verify(session, times(1)).close(CloseStatus.NORMAL);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

    @Test
    public void handleTextMessageTest_cleanupTable_sendMessageBulkActionResult() throws Exception {
        final UUID projectId = UUID.randomUUID();
        String tableName = "tdm_run_handler_cleanup_sql_config";
        String tableTitle = "TDM Run Handler Cleanup SQL Config";
        BulkActionConfig bulkActionConfig = new BulkActionConfig(){{
            setProjectId(projectId);
            setSystemId(systemId);
            setSaveOccupiedData(false);
            setExecuteInParallel(false);
            setSendResult(false);
            setRecipients("example@example.com");
            setTableTitle(tableTitle);
        }};
        deleteTestDataTableIfExists(tableName);
        createTestDataTable(tableName);
        TestDataTableCatalog tableCatalog =
                createTestDataTableCatalog(projectId, systemId, environmentId, tableTitle, tableName);
        mockEnvironmentService(Collections.singletonList(environmentId),systemId,systemId);
        createSqlCleanupConfig(tableCatalog, true);

        BulkActionResult expectedBulkActionResult = new BulkActionResult(tableTitle, tableName, environmentName,
                new CleanupResults(tableName, 6, 6));

        String strExpectedBulkActionResult = objectMapper.writeValueAsString(expectedBulkActionResult);

        String strBulkActionConfig = objectMapper.writeValueAsString(bulkActionConfig);

        final long processId = 111;
        when(currentTime.getCurrentTimeMillis()).thenReturn(processId);

        bulkDataCleanupHandler.handleTextMessage(session, new TextMessage(strBulkActionConfig));

        Thread.sleep(5000);

        verify(session).sendMessage(new TextMessage("{\"id\":" + processId + ", \"status\": \"STARTED\"}"));

        verify(session).sendMessage(new TextMessage(strExpectedBulkActionResult));

        verify(session).sendMessage(new TextMessage("{\"id\":" + processId + ", \"status\": \"FINISHED\"}"));

        verify(session).close(CloseStatus.NORMAL);

        deleteTestDataTableIfExists(tableName);
        catalogRepository.deleteByTableName(tableName);
    }

}
