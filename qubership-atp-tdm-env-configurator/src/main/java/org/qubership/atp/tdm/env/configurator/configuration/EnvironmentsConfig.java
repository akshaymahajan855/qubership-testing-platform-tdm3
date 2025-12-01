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

package org.qubership.atp.tdm.env.configurator.configuration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.qubership.atp.tdm.env.configurator.exceptions.internal.TdmEnvInitiateCacheException;
import org.qubership.atp.tdm.env.configurator.utils.CacheNames;
import org.qubership.atp.tdm.env.configurator.utils.decryptor.Decryptor;
import org.qubership.atp.tdm.env.configurator.utils.decryptor.SopsDecryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.cache.CacheBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableAutoConfiguration
@RequiredArgsConstructor
public class EnvironmentsConfig {

    @Value("${environments.cache.duration:15}")
    private Integer cacheDuration;

    @Value("${envgene.age.private.key:}")
    private String agePrivateKey;

    /**
     * Creates SopsDecryptor bean if age private key is configured.
     * @return SopsDecryptor instance or null if key is not configured
     */
    @Bean
    @ConditionalOnExpression("!'${envgene.age.private.key:}'.isEmpty()")
    public Decryptor sopsDecryptor() {
        log.info("Initializing SopsDecryptor with age private key from configuration");
        return new SopsDecryptor(agePrivateKey);
    }

    /**
     * Cache manager.
     * @return - ConcurrentMapCache.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "GENERIC")
    public CacheManager cacheManager() {
        log.info("Environments cache manage is enabled. Cache duration: {}", cacheDuration);

        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager() {
            @Override
            protected Cache createConcurrentMapCache(String name) {
                return new ConcurrentMapCache(
                        name,
                        CacheBuilder.newBuilder()
                                .expireAfterWrite(cacheDuration, TimeUnit.MINUTES)
                                .build().asMap(),
                        false);
            }
        };

        List<String> cacheNames = new ArrayList<>();
        try {
            Field[] fields = CacheNames.class.getDeclaredFields();
            for (Field field : fields) {
                cacheNames.add(field.get(String.class).toString());
            }
        } catch (Exception e) {
            log.error(TdmEnvInitiateCacheException.DEFAULT_MESSAGE, e);
            throw new TdmEnvInitiateCacheException();
        }

        cacheManager.setCacheNames(cacheNames);

        return cacheManager;
    }
}
