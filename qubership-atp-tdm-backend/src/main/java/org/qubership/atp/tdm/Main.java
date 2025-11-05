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

package org.qubership.atp.tdm;

import org.qubership.atp.auth.springbootstarter.security.oauth2.client.config.annotation.EnableM2MRestTemplate;
import org.qubership.atp.auth.springbootstarter.security.oauth2.client.config.annotation.EnableOauth2FeignClientInterceptor;
import org.qubership.atp.crypt.config.annotation.AtpDecryptorEnable;
import org.qubership.atp.tdm.env.configurator.configuration.EnvironmentsConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@AtpDecryptorEnable
@Import({WebMvcAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        ServletWebServerFactoryAutoConfiguration.class,
        WebSocketServletAutoConfiguration.class,
        EnvironmentsConfig.class
})
@EnableM2MRestTemplate
@EnableFeignClients(basePackages = {
        "org.qubership.atp.integration.configuration.feign",
        "org.qubership.atp.tdm.service.client"
})
@EnableOauth2FeignClientInterceptor
@SpringBootApplication(scanBasePackages = {
        "org.qubership.atp.tdm",
        "org.qubership.atp.tdm.env.configurator",
        "org.qubership.atp.common.probes.configuration",
        "org.qubership.atp.common",
        "org.qubership.atp.integration.configuration"
})
public class Main {

    /**
     * Start application.
     */
    public static void main(String[] args) {
        SpringApplicationBuilder app = new SpringApplicationBuilder(Main.class);
        app.build().addListeners(
                new ApplicationPidFileWriter("application.pid")
        );
        app.run(args);
    }
}
