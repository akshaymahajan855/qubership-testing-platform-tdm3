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

package org.qubership.atp.tdm.model.rest;

import org.apache.commons.lang3.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseMessage {

    private ResponseType type;
    private String content;
    private Object contentObject;
    private String link;

    /**
     * Class constructor.
     */
    public ResponseMessage(ResponseType type, String content) {
        this.type = type;
        this.content = content;
        link = StringUtils.EMPTY;
    }

    /**
     * Class constructor.
     */
    public ResponseMessage(ResponseType type, String content, String link) {
        this.type = type;
        this.content = content;
        this.link = link;
    }

    /**
     * Class constructor.
     */
    public ResponseMessage(ResponseType type, Object contentObject) {
        this.type = type;
        this.contentObject = contentObject;
        link = StringUtils.EMPTY;
    }

    /**
     * Class constructor.
     */
    public ResponseMessage(ResponseType type, String content, Object contentObject) {
        this.type = type;
        this.content = content;
        this.contentObject = contentObject;
        link = StringUtils.EMPTY;
    }

    /**
     * Class constructor.
     */
    public ResponseMessage(ResponseType type, Object contentObject, String link) {
        this.type = type;
        this.contentObject = contentObject;
        this.link = link;
    }
}
