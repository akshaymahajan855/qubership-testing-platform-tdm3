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

package org.qubership.atp.tdm.repo.impl;

import static java.lang.String.format;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import javax.sql.DataSource;

import org.qubership.atp.crypt.api.Decryptor;
import org.qubership.atp.crypt.exception.AtpDecryptException;
import org.qubership.atp.tdm.env.configurator.model.Server;
import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
import org.qubership.atp.tdm.exceptions.db.TdmDbConnectionException;
import org.qubership.atp.tdm.exceptions.db.TdmDbDecryptException;
import org.qubership.atp.tdm.exceptions.db.TdmDbDriverException;
import org.qubership.atp.tdm.exceptions.db.TdmDbDriverNotFoundException;
import org.qubership.atp.tdm.exceptions.db.TdmDbJdbsTemplateException;
import org.qubership.atp.tdm.model.TestDataTableCatalog;
import org.qubership.atp.tdm.repo.CatalogRepository;
import org.qubership.atp.tdm.repo.SqlRepository;
import org.qubership.atp.tdm.utils.TestDataUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

import com.google.common.base.Strings;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class SqlRepositoryImpl implements SqlRepository {

    private static final String ORACLE_DB_TYPE = "oracle";
    private static final String POSTGRES_DB_TYPE = "postgresql";
    private static final String H2_DB_TYPE = "h2";
    private static final String DB_CONNECTION_NAME = "DB";
    private final Decryptor decryptor;

    @Autowired
    public SqlRepositoryImpl(@Nonnull Decryptor decryptor) {
        this.decryptor = decryptor;
    }

    /**
     * Create and return java.sql.Connection that is instantiated by the provided server object.
     *
     * @param server server representation object
     * @return java.sql.Connection object created and configured.
     */
    @Override
    public Connection createConnection(Server server) {
        String dbType = server.getProperty("db_type");
        getDbDriverName(dbType);
        String jdbcUrl = server.getProperty("jdbc_url");
        String dbLogin = server.getProperty("db_login");
        String dbPassword = server.getProperty("db_password");
        String connectionString = createConnectionString(dbType, server);
        validateConnectionString(connectionString);
        if (Strings.isNullOrEmpty(jdbcUrl)) {
            return createConnection(connectionString, dbLogin, dbPassword);
        } else {
            return createConnection(jdbcUrl, dbLogin, dbPassword);
        }
    }

    private Connection createConnection(String connectionString, String user, String password) {
        try {
            log.debug("Connection string: {}", connectionString);
            return DriverManager.getConnection(connectionString, decryptor.decryptIfEncrypted(user),
                    decryptor.decryptIfEncrypted(password));
        } catch (Exception e) {
            log.error(format(TdmDbConnectionException.DEFAULT_MESSAGE, connectionString), e);
            throw new TdmDbConnectionException(connectionString);
        }
    }

    private void validateConnectionString(String connectionString) {
        if (!connectionString.startsWith("jdbc:oracle:thin:@")
                && !connectionString.startsWith("jdbc:postgresql://")
                && !connectionString.startsWith("jdbc:h2:")) {
            throw new SecurityException("Unsupported DB type");
        }
    }

    @Override
    public Server getServer(String tableName, CatalogRepository catalogRepository,
                            EnvironmentsService environmentsService) {
        TestDataTableCatalog tableCatalog = catalogRepository.findByTableName(tableName);
        List<org.qubership.atp.tdm.env.configurator.model.Connection> connections =
                environmentsService.getConnectionsSystemById(
                        tableCatalog.getEnvironmentId(), tableCatalog.getSystemId()
                );
        return TestDataUtils.getServer(connections, DB_CONNECTION_NAME);
    }

    @Override
    public JdbcTemplate createJdbcTemplate(Server server) {
        String dbType = server.getProperty("db_type");
        String dbDriverName = getDbDriverName(dbType);
        try (Connection connection = createConnection(server)) {
            JdbcTemplate jdbcTemplate = null;
            if (server.getProperty("jdbc_url").isEmpty()) {
                String jdbcUrl = createConnectionString(dbType, server);
                jdbcTemplate = new JdbcTemplate(createDataSource(dbDriverName, jdbcUrl,
                        server.getProperty("db_login"), server.getProperty("db_password")));
            } else {
                jdbcTemplate = new JdbcTemplate(createDataSource(dbDriverName, server.getProperty("jdbc_url"),
                        server.getProperty("db_login"), server.getProperty("db_password")));
            }
            return jdbcTemplate;
        } catch (Exception e) {
            log.error(TdmDbJdbsTemplateException.DEFAULT_MESSAGE, e);
            throw new TdmDbJdbsTemplateException();
        }
    }

    @Override
    public JdbcTemplate createJdbcTemplate(Server server, int queryTimeout) {
        JdbcTemplate template = createJdbcTemplate(server);
        template.setQueryTimeout(queryTimeout);
        return template;
    }

    private DataSource createDataSource(String driverName, String connectionString, String user, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverName);
        dataSource.setUrl(connectionString);
        dataSource.setUsername(getDecryptIfEncrypted(user));
        dataSource.setPassword(getDecryptIfEncrypted(password));
        return dataSource;
    }

    /**
     * Set db driver.
     */
    private String getDbDriverName(String dbType) {
        try {
            if (ORACLE_DB_TYPE.equals(getDecryptIfEncrypted(dbType))) {
                Class.forName("oracle.jdbc.driver.OracleDriver");
                return "oracle.jdbc.driver.OracleDriver";
            } else if (POSTGRES_DB_TYPE.equals(getDecryptIfEncrypted(dbType))) {
                Class.forName("org.postgresql.Driver");
                return "org.postgresql.Driver";
            } else if (H2_DB_TYPE.equals(getDecryptIfEncrypted(dbType))) {
                Class.forName("org.h2.Driver");
                return "org.postgresql.Driver";
            } else {
                throw new TdmDbDriverException(dbType);
            }
        } catch (ClassNotFoundException e) {
            throw new TdmDbDriverNotFoundException(dbType);
        }
    }

    private String createConnectionString(String dbType, Server server) {
        if (ORACLE_DB_TYPE.equals(getDecryptIfEncrypted(dbType))) {
            return "jdbc:oracle:thin:@"
                    + getDecryptedProperty(server, "db_host") + ":"
                    + getDecryptedProperty(server, "db_port") + "/"
                    + getDecryptedProperty(server, "db_name");
        } else if (POSTGRES_DB_TYPE.equals(getDecryptIfEncrypted(dbType))) {
            return "jdbc:postgresql://"
                    + getDecryptedProperty(server, "db_host") + ":"
                    + getDecryptedProperty(server, "db_port") + "/"
                    + getDecryptedProperty(server, "db_name");
        } else if (H2_DB_TYPE.equals(getDecryptIfEncrypted(dbType))) {
            return "jdbc:h2:" + getDecryptedProperty(server, "db_host");
        }
        throw new TdmDbDriverException(dbType);
    }

    private String getDecryptedProperty(Server server, String key) {
        return getDecryptIfEncrypted(server.getProperty(key));
    }

    private String getDecryptIfEncrypted(String var) {
        try {
            return decryptor.decryptIfEncrypted(var);
        } catch (AtpDecryptException e) {
            log.error(format(TdmDbDecryptException.DEFAULT_MESSAGE, var), e);
            throw new TdmDbDecryptException(var);
        }
    }
}
