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

package org.qubership.atp.tdm.service;

import java.util.List;
import java.util.Map;

import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.requests.AddInfoToRowRequest;
import org.qubership.atp.tdm.model.rest.requests.GetRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyFullRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyRowRequest;
import org.qubership.atp.tdm.model.rest.requests.ReleaseRowRequest;
import org.qubership.atp.tdm.model.rest.requests.UpdateRowRequest;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface AtpActionService {

    ResponseMessage insertTestData(@Nonnull String projectName, @Nullable String envName, @Nullable String systemName,
                                   @Nonnull String tableTitle, List<Map<String, Object>> records);

    List<ResponseMessage> occupyTestData(@Nonnull String projectName, @Nullable String envName,
                                         @Nullable String systemName, @Nonnull String tableTitle,
                                         List<OccupyRowRequest> occupyRowRequests);

    List<ResponseMessage> occupyTestDataFullRow(@Nonnull String projectName, @Nullable String envName,
                                                @Nullable String systemName, @Nonnull String tableTitle,
                                                List<OccupyFullRowRequest> occupyFullRowRequests);

    List<ResponseMessage> releaseTestData(@Nonnull String projectName, @Nullable String envName,
                                          @Nullable String systemName, @Nonnull String tableTitle,
                                          List<ReleaseRowRequest> releaseRowRequests);

    List<ResponseMessage> releaseFullTestData(@Nonnull String projectName, @Nullable String envName,
                                              @Nullable String systemName, @Nonnull String tableTitle);

    List<ResponseMessage> updateTestData(@Nonnull String projectName, @Nullable String envName,
                                         @Nullable String systemName, @Nonnull String tableTitle,
                                         List<UpdateRowRequest> updateRowRequests);

    List<ResponseMessage> getTestData(@Nonnull String projectName, @Nullable String envName,
                                      @Nullable String systemName, @Nonnull String tableTitle,
                                      List<GetRowRequest> getRowRequests);

    List<ResponseMessage> addInfoToRow(@Nonnull String projectName, @Nullable String envName,
                                       @Nullable String systemName, @Nonnull String tableTitle,
                                       List<AddInfoToRowRequest> addInfoToRowRequests);

    List<ResponseMessage> refreshTables(@Nonnull String projectName, @Nullable String envName,
                                        @Nullable String systemName, @Nonnull String tableTitle);

    List<ResponseMessage> truncateTable(@Nonnull String projectName, @Nullable String envName,
                                        @Nullable String systemName, @Nonnull String tableTitle);

    List<ResponseMessage> runCleanupForTable(@Nonnull String projectName, @Nullable String envName,
                                             @Nullable String systemName, @Nonnull String tableTitle);

    List<ResponseMessage> getMultipleColumnTestData(@Nonnull String projectName, @Nullable String envName,
                                                    @Nullable String systemName, @Nonnull String tableTitle,
                                                    @Nonnull List<GetRowRequest> multipleColumnRowRequest);

    ResponseMessage resolveTableName(@Nonnull String projectName, @Nonnull String envName,
                                     @Nonnull String systemName, @Nonnull String titleTable);
}
