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

package org.qubership.atp.tdm.repo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.qubership.atp.tdm.model.rest.ResponseMessage;
import org.qubership.atp.tdm.model.rest.requests.AddInfoToRowRequest;
import org.qubership.atp.tdm.model.rest.requests.GetRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyFullRowRequest;
import org.qubership.atp.tdm.model.rest.requests.OccupyRowRequest;
import org.qubership.atp.tdm.model.rest.requests.ReleaseRowRequest;
import org.qubership.atp.tdm.model.rest.requests.UpdateRowRequest;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface AtpActionRepository {

    ResponseMessage insertTestData(@Nonnull UUID projectId, @Nullable UUID systemId, @Nullable UUID environmentId,
                                   @Nonnull String tableTitle, List<Map<String, Object>> records,
                                   @Nonnull String resultLink);

    List<ResponseMessage> occupyTestData(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle,
                                         @Nonnull String occupiedBy, List<OccupyRowRequest> occupyRowRequests,
                                         @Nonnull String resultLink);

    List<ResponseMessage> occupyTestDataFullRow(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                @Nonnull String tableTitle, @Nonnull String occupiedBy,
                                                List<OccupyFullRowRequest> occupyRowRequests,
                                                @Nonnull String resultLink);

    List<ResponseMessage> releaseTestData(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle,
                                          List<ReleaseRowRequest> releaseRowRequest);

    List<ResponseMessage> releaseFullTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                            @Nonnull String tableTitle);

    List<ResponseMessage> updateTestData(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle,
                                         List<UpdateRowRequest> updateRowRequests);

    List<ResponseMessage> getTestData(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle,
                                      List<GetRowRequest> getRowRequests);

    List<ResponseMessage> getMultipleColumnTestData(@Nonnull UUID projectId, @Nullable UUID systemId,
                                                    @Nonnull String tableTitle,
                                                    List<GetRowRequest> getRowRequests, @Nonnull String resultLink);

    List<ResponseMessage> addInfoToRow(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle,
                                       List<AddInfoToRowRequest> addInfoToRowRequests);

    List<ResponseMessage> refreshTables(@Nonnull UUID projectId, @Nullable UUID systemId,
                                        @Nonnull String tableTitle, @Nonnull String tdmUrl);

    List<ResponseMessage> truncateTable(@Nonnull UUID projectId, @Nullable UUID systemId, @Nonnull String tableTitle);

    List<ResponseMessage> runCleanupForTable(@Nonnull UUID projectId, @Nullable UUID systemId,
                                           @Nonnull String tableTitle);

    ResponseMessage resolveTableName(@Nonnull UUID projectId, @Nonnull UUID systemId, @Nonnull String tableTitle);
}
