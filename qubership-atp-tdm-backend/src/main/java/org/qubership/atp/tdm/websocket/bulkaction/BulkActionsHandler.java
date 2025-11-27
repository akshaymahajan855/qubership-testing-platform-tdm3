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

package org.qubership.atp.tdm.websocket.bulkaction;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.qubership.atp.common.lock.LockManager;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.websocket.TdmGetEnvironmentNameException;
import org.qubership.atp.tdm.exceptions.websocket.TdmParseRequestException;
import org.qubership.atp.tdm.exceptions.websocket.TdmProcessBulkActionFuturesException;
import org.qubership.atp.tdm.exceptions.websocket.TdmSendMessageException;
import org.qubership.atp.tdm.exceptions.websocket.TdmWriteBulkActionResultsAsStringException;
import org.qubership.atp.tdm.mdc.MdcField;
import org.qubership.atp.tdm.mdc.TdmMdcHelper;
import org.qubership.atp.tdm.model.bulkaction.BulkActionConfig;
import org.qubership.atp.tdm.model.bulkaction.BulkActionContext;
import org.qubership.atp.tdm.model.bulkaction.BulkActionResult;
import org.qubership.atp.tdm.model.mail.bulkaction.AbstractBulkActionMailSender;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.utils.CurrentTime;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BulkActionsHandler extends TextWebSocketHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int NUMBER_OF_THREADS = 10;
    private static final String STARTED = "STARTED";
    private static final String NOTHING_FOUND = "NOTHING_FOUND";
    private static final String FINISHED = "FINISHED";
    protected final EnvironmentsService environmentsService;
    protected final CatalogRepository catalogRepository;
    protected final CurrentTime currentTime;
    protected final LockManager lockManager;
    protected final TdmMdcHelper mdcHelper;
    private final ExecutorService executorService;
    private final AbstractBulkActionMailSender mailSender;
    @Value("${atp.lock.bulk.action.duration.sec}")
    private int bulkActionDuration;

    /**
     * Constructor with parameters.
     */

    public BulkActionsHandler(@Qualifier("websocket") ExecutorService executorService,
                              @Nonnull CatalogRepository catalogRepository,
                              @Nonnull EnvironmentsService environmentsService,
                              @Nonnull AbstractBulkActionMailSender mailSender,
                              @Nonnull CurrentTime currentTime,
                              @Nonnull LockManager lockManager,
                              @Nonnull TdmMdcHelper mdcHelper) {
        this.executorService = executorService;
        this.catalogRepository = catalogRepository;
        this.environmentsService = environmentsService;
        this.currentTime = currentTime;
        this.mailSender = mailSender;
        this.lockManager = lockManager;
        this.mdcHelper = mdcHelper;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        executorService.submit(() -> tryProcessRequest(session, message));
    }

    /**
     * Get environment name by environment Id.
     * @param lazyEnvironments - lazy environments list.
     * @param envId - environment Id.
     * @return environment name string.
     */
    public String getEnvName(@Nonnull List<LazyEnvironment> lazyEnvironments,
                             @Nonnull UUID envId) {
        return lazyEnvironments.stream()
                .filter(env -> envId.equals(env.getId()))
                .findFirst()
                .orElseThrow(() -> new
                        TdmGetEnvironmentNameException(envId.toString()))
                .getName();
    }

    private void tryProcessRequest(@Nonnull WebSocketSession session, @Nonnull TextMessage message) {
        try {
            processRequest(session, message);
            closeSessionAsNormal(session);
        } catch (Exception e) {
            log.error("An error occurred while processing the Websocket request. Session: [{}].", session, e);
            closeSessionAsError(session);
        }
    }

    private void processRequest(@Nonnull WebSocketSession session, @Nonnull TextMessage message) {
        log.info("Websocket request processing started. Session: [{}]. TextMessage: [{}].", session, message);
        BulkActionConfig config = parseRequest(message);
        log.info("class name : {}", this.getClass().getName());
        lockManager.executeWithLock(session.getUri().getPath() + " " + config.getProjectId(),
                bulkActionDuration, () -> {
                    MDC.clear();
                    MdcUtils.put(MdcField.PROJECT_ID.toString(), config.getProjectId());
                    long processId = currentTime.getCurrentTimeMillis();
                    sendStatusMsg(session, processId, STARTED);

                    ExecutorService executor = createExecutorService(config.isExecuteInParallel());

                    List<LazyEnvironment> lazyEnvironments = environmentsService
                            .getLazyEnvironments(config.getProjectId());

                    List<Future<BulkActionResult>> futures = runBulkAction(session, executor, lazyEnvironments,
                            config, processId);

                    if (futures.isEmpty()) {
                        sendStatusMsg(session, processId, NOTHING_FOUND);
                    } else {
                        handleResults(session, futures, config, processId);
                        sendStatusMsg(session, processId, FINISHED);
                    }
                });
    }

    private ExecutorService createExecutorService(boolean isExecuteInParallel) {
        if (isExecuteInParallel) {
            return Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        } else {
            return Executors.newSingleThreadExecutor();
        }
    }

    private BulkActionConfig parseRequest(@Nonnull TextMessage message) {
        String payload = message.getPayload();
        try {
            return objectMapper.readValue(payload, BulkActionConfig.class);
        } catch (IOException e) {
            log.error(String.format(TdmParseRequestException.DEFAULT_MESSAGE, message), e);
            throw new TdmParseRequestException(message.toString());
        }
    }

    private void closeSessionAsNormal(@Nonnull WebSocketSession session) {
        tryCloseSession(session, CloseStatus.NORMAL);
    }

    private void closeSessionAsError(@Nonnull WebSocketSession session) {
        tryCloseSession(session, CloseStatus.SERVER_ERROR);
    }

    private void tryCloseSession(@Nonnull WebSocketSession session, @Nonnull CloseStatus status) {
        try {
            session.close(status);
            log.info("Websocket session closed. Session: [{}].", session);
        } catch (Exception e) {
            log.error("Can't close WebSocket session. Session: [{}].", session, e);
        }
    }

    private void sendStatusMsg(@Nonnull WebSocketSession session, long processId, @Nonnull String status) {
        try {
            sendMessage(session, "{\"id\":" + processId + ", \"status\": \"" + status + "\"}");
        } catch (IOException e) {
            log.error("Error while sending a message with process id.", e);
        }
    }

    private void sendMessage(@Nonnull WebSocketSession session, @Nonnull BulkActionResult results) {
        String payloadText = writeBulkActionResultAsString(results);
        try {
            sendMessage(session, payloadText);
        } catch (Exception e) {
            log.error(String.format(TdmSendMessageException.DEFAULT_MESSAGE, payloadText), e);
            throw new TdmSendMessageException(payloadText);
        }
    }

    private void sendMessage(@Nonnull WebSocketSession session, @Nonnull String payloadText) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(payloadText));
            log.info("Message sent. Session: [{}]. Size: [{}].", session, payloadText.getBytes().length);
        } else {
            log.warn("Trying to sent message but session is closed.");
        }
    }

    private String writeBulkActionResultAsString(@Nonnull BulkActionResult results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            log.error(String.format(TdmWriteBulkActionResultsAsStringException.DEFAULT_MESSAGE, results), e);
            throw new TdmWriteBulkActionResultsAsStringException(results.toString());
        }
    }

    private void handleResults(@Nonnull WebSocketSession session,
                               @Nonnull List<Future<BulkActionResult>> futures,
                               @Nonnull BulkActionConfig config,
                               long processId) {
        log.trace("Handle bulk action results, session: {}, id: {}", session.getId(), processId);
        for (Future<BulkActionResult> future : futures) {
            try {
                sendMessage(session, future.get());
            } catch (Exception e) {
                log.error(TdmProcessBulkActionFuturesException.DEFAULT_MESSAGE, e);
                throw new TdmProcessBulkActionFuturesException();
            }
        }
        log.trace("Bulk action results handled.");
        if (config.isSendResult()) {
            log.info("Send email results, session: {}, id: {}", session.getId(), processId);
            sendResultViaMail(mailSender, processId, config, futures);
        }
    }

    private void sendResultViaMail(@Nonnull AbstractBulkActionMailSender mailSender, long id,
                                   @Nonnull BulkActionConfig config, @Nonnull List<Future<BulkActionResult>> futures) {
        log.trace("Collecting bulk action results...");
        Executors.newSingleThreadExecutor().submit(() -> {
            BulkActionContext bulkActionContext = buildBulkActionContext(environmentsService, id, config, futures);
            log.trace("Sending bulk action result to email...");
            mailSender.send(bulkActionContext, config.getProjectId());
            log.info(bulkActionContext.getResults().toString());
            log.trace("Email sent.");
        });
    }

    private BulkActionContext buildBulkActionContext(@Nonnull EnvironmentsService environmentsService,
                                                     long id, @Nonnull BulkActionConfig config,
                                                     @Nonnull List<Future<BulkActionResult>> futures) {
        log.trace("Build bulk action context with id: {}, config: {}", id, config);
        BulkActionContext bulkActionContext = new BulkActionContext();
        bulkActionContext.setId(id);
        bulkActionContext.setProjectName(environmentsService.getLazyProjectById(config.getProjectId()).getName());
        bulkActionContext.setRecipients(config.getRecipients());
        try {
            bulkActionContext.setEnvironmentName(environmentsService.getEnvNameById(config.getEnvironmentId()));
        } catch (Exception e) {
            bulkActionContext.setEnvironmentName("Not Found");
        }
        try {
            bulkActionContext.setSystemName(
                    environmentsService.getLazySystemById(config.getEnvironmentId(), config.getSystemId()).getName()
            );
        } catch (Exception e) {
            bulkActionContext.setSystemName("Not Found");
        }
        bulkActionContext.setResults(futures.stream().map(i -> {
            try {
                return i.get();
            } catch (Exception e) {
                log.error(TdmProcessBulkActionFuturesException.DEFAULT_MESSAGE, e);
                throw new TdmProcessBulkActionFuturesException();
            }
        }).collect(Collectors.toList()));
        log.trace("Build bulk action context has been created.");
        return bulkActionContext;
    }

    public abstract List<Future<BulkActionResult>> runBulkAction(@Nonnull WebSocketSession session,
                                                                 @Nonnull ExecutorService executor,
                                                                 @Nonnull List<LazyEnvironment> lazyEnvironments,
                                                                 @Nonnull BulkActionConfig config, long processId);
}
