# Qubership Testing Platform TDM3 Service

## About

The main goal of TDM (Test Data Management) Service is to simplify test data usage and management on the project for manual and automated Standalone/End-to-End testing.

The concept of test data management assumes usage of TDM tool as single centralized data storage, for creation, updating and tracking of test data usage on different environments.

This approach gives a user the only entry point for test data usage on different environments. New scripts for test data collecting or updating can be performed in few clicks on different servers.

Differences of TDM3 Service from TDM Service are the following:
- Internal PostgreSQL Database is replaced to H2 file database,
- Instead of QSTP Environments Service, EnvGene Tool is used,
- Auth functionality is reduced.

## How to start Backend

1. Main class `org.qubership.atp.tdm.Main`
2. VM options (contains links, can be edited in parent-db pom.xml):
   `
   -Dspring.config.location=C:\atp-tdm\qubership-atp-tdm-backend\target\config\application.properties
   -Dspring.cloud.bootstrap.location=C:\atp-tdm\qubership-atp-tdm-backend\target\config\bootstrap.properties
   `
3. Select "Working directory" `$MODULE_WORKING_DIRS$`

Just run Main#main with args from step above

## How to start Tests with Docker
Prerequisites
- Docker installed local
- VM options: -DLOCAL_DOCKER_START=true

## How to start development of frontend

1. Download and install [Node.js](https://nodejs.org/en/download/)
2. Install node modules from package.json with `npm i`

Run `npm start` for a dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

Run `npm run hmr` for a dev server with hot module replacement. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files but won't reload the page.

Run `npm run svg` for injecting SVG bundle from svg-icons folder to index.html.

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

Run `npm run build` to build the project. The build artifacts will be stored in the `dist/` directory.

Run `npm run report` to see the report about bundle.

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

Run `ng e2e` to execute the end-to-end tests via [Protractor](https://www.protractortest.org/).

To get more help on the Angular CLI use `ng help` or go check out the [Angular CLI readme](https://github.com/angular/angular-cli/blob/main/README.md).

## How to run UI with backend

1. Build project first: build by maven "clean" and "package", run as backend on port 8080.

## How to deploy tool

1. Navigate to the builder job
2. Click "Build with Parameters"
3. Fill required parameters:

    * CLOUD_URL = **dev-atp-cloud.com:8443**
    * OPENSHIFT_WORKSPACE = **dev1**
    * OPENSHIFT_USER = **{domain_login}**
    * OPENSHIFT_PASSWORD = **{domain_password}**
    * ARTIFACT_DESCRIPTOR_GROUP_ID = **org.qubership.deploy.product**
    * ARTIFACT_DESCRIPTOR_ARTIFACT_ID = **prod.ta_atp-tdm**
    * ARTIFACT_DESCRIPTOR_VERSION = **master_20191112-002747**
    * DEPLOYMENT_MODE = **update**

4. Click button "Build"
5. Navigate to the openshift
6. Navigate to the "Applications" -> "Routes"
7. Find a link to the tool with the specified project name
8. Check the tool - open the URL from the column "Hostname"

## Deploy parameters

The following parameters are passed to `application.properties` during deployment:

| Parameter | Type | Mandatory | Default value | Description |
|-----------|------|-----------|---------------|-------------|
| `SERVICE_NAME` | `string` | `yes` | `"atp3-tdm-be"` | Deployment Config/Service name. |
| `LOG_LEVEL` | `string` | `no` | `"INFO"` | Logging level for the application. |
| `SPRING_PROFILES` | `string` | `no` | `"disable-security"` | Spring profile for security configuration. |
| `ENVGENE_GIT_REPO_URL` | `string` | `yes` | `""` | Full Git repository URL including project path. |
| `ENVGENE_GIT_REPO_TOKEN` | `string` | `yes` | `""` | Access token for private Git repositories. |
| `ENVGENE_GIT_REPO_BRANCH` | `string` | `no` | `"master"` | Git branch or tag reference for environments configuration. |
| `ENVGENE_GIT_REPO_DEPLOYMENT_PATH` | `string` | `no` | `"effective-set/deployment"` | Base path to deployment configuration in Git repository. Used to construct full paths to deployment files. |
| `ENVGENE_GIT_REPO_NC_APP_PATH` | `string` | `no` | `"atp/atp3-playwright-runner"` | Application-specific path within deployment configuration. Used to construct full paths to deployment files. |
| `ENVGENE_GIT_REPO_CREDENTIALS_PATH` | `string` | `no` | `"values/credentials.yaml"` | Relative path to credentials file within the application deployment path. Combined with `ENVGENE_GIT_REPO_DEPLOYMENT_PATH` and `ENVGENE_GIT_REPO_NC_APP_PATH` to form the full path. |
| `ENVGENE_GIT_REPO_DEPLOYMENT_PARAMETERS_PATH` | `string` | `no` | `"values/deployment-parameters.yaml"` | Relative path to deployment parameters file within the application deployment path. Combined with `ENVGENE_GIT_REPO_DEPLOYMENT_PATH` and `ENVGENE_GIT_REPO_NC_APP_PATH` to form the full path. |
| `PROJECTS_INFO` | `json` | `no` | `{}` | JSON object mapping project IDs to project names. |
| `KEYCLOAK_ENABLED` | `boolean` | `no` | `false` | Enable or disable Keycloak authentication. |
| `KEYCLOAK_AUTH_URL` | `string` | `no` | `""` | Keycloak authentication server URL. |
| `KEYCLOAK_REALM` | `string` | `no` | `"atp3"` | Keycloak realm name. |
| `SERVICE_REGISTRY_URL` | `string` | `no` | `""` | Eureka service registry URL. |
| `EUREKA_CLIENT_ENABLED` | `boolean` | `no` | `false` | Enable or disable Eureka client. |
| `MAIL_SENDER_ENABLE` | `boolean` | `no` | `true` | Enable or disable mail sender functionality. |
| `MAIL_SENDER_URL` | `string` | `no` | `""` | Mail sender service URL. |
| `FROM_EMAIL_ADDRESS` | `string` | `no` | `"example@example.com"` | Sender email address for notifications. |
| `ENVIRONMENTS_SPRING_CACHE_TYPE` | `string` | `no` | `"GENERIC"` | Spring cache type (e.g., GENERIC, NONE). |
| `ENVIRONMENTS_CACHE_DURATIONS` | `integer` | `no` | `15` | Cache duration in minutes for environments. |
| `EXTERNAL_QUERY_DEFAULT_TIMEOUT` | `integer` | `no` | `1800` | Default timeout for external queries in seconds. |
| `EXTERNAL_QUERY_MAX_TIMEOUT` | `integer` | `no` | `3600` | Maximum timeout for external queries in seconds. |
| `LOG_GRAYLOG_ON` | `boolean` | `no` | `true` | Enable or disable Graylog logging. |
| `LOG_GRAYLOG_HOST` | `string` | `no` | `""` | Graylog server host. |
| `LOG_GRAYLOG_PORT` | `integer` | `no` | `12201` | Graylog server port. |
| `ATP_CRYPTO_KEY` | `string` | `yes` | `""` | Public key for encryption (when ENCRYPT=secrets). |
| `ATP_CRYPTO_PRIVATE_KEY` | `string` | `yes` | `""` | Private key for encryption (when ENCRYPT=secrets). |
| `ATP_TDM_URL` | `string` | `no` | `"localhost:8080"` | URL of the ATP TDM service. |
| `JDBC_URL` | `string` | `yes` | `""` | JDBC connection URL for the database. |
| `TDM_DB_USER` | `string` | `yes` | `""` | Database username (stored in secrets). |
| `TDM_DB_PASSWORD` | `string` | `yes` | `""` | Database password (stored in secrets). |
| `ATP_SERVICE_PUBLIC` | `boolean` | `no` | `true` | Register service as public in gateway. |
| `ATP_SERVICE_PATH` | `string` | `no` | `"/api/atp-tdm/v1/**"` | Service API path pattern. |
| `LOCALE_RESOLVER` | `string` | `no` | `"en"` | Locale resolver language code. |
| `KEYCLOAK_CLIENT_NAME` | `string` | `no` | `"atp-tdm"` | Keycloak client name. |
| `KEYCLOAK_SECRET` | `string` | `no` | `""` | Keycloak client secret (stored in secrets). |
| `PROJECT_INFO_ENDPOINT` | `string` | `yes` | `""` | Endpoint URL for project information. |
| `CONTENT_SECURITY_POLICY` | `string` | `no` | `"default-src 'self' *"` | Content Security Policy header value. |
| `ZIPKIN_ENABLE` | `boolean` | `no` | `false` | Enable or disable Zipkin tracing. |
| `ZIPKIN_PROBABILITY` | `float` | `no` | `1.0` | Zipkin sampling probability. |
| `ZIPKIN_URL` | `string` | `no` | `"http://127.0.0.1:9411"` | Zipkin server URL. |
| `MONITOR_PORT` | `integer` | `no` | `8090` | Monitoring endpoints port. |
| `MAX_FILE_SIZE` | `string` | `no` | `"100MB"` | Maximum file size for uploads. |
| `MAX_REQUEST_SIZE` | `string` | `no` | `"100MB"` | Maximum request size. |
| `ATP_INTERNAL_GATEWAY_ENABLED` | `boolean` | `no` | `false` | Enable internal gateway routing. |
| `FEIGN_CONNECT_TIMEOUT` | `integer` | `no` | `300000` | Feign client connection timeout in milliseconds. |
| `FEIGN_READ_TIMEOUT` | `integer` | `no` | `300000` | Feign client read timeout in milliseconds. |
| `LIQUIBASE_LAUNCH_ENABLED` | `boolean` | `no` | `true` | Enable or disable Liquibase migrations. |
| `ATP_HTTP_LOGGING_HEADERS` | `boolean` | `no` | `true` | Enable HTTP headers logging. |
| `ATP_HTTP_LOGGING_HEADERS_IGNORE` | `string` | `no` | `""` | Comma-separated list of headers to ignore in logging. |
| `ATP_HTTP_LOGGING_URI_IGNORE` | `string` | `no` | `"/rest/deployment/readiness /rest/deployment/liveness"` | URI patterns to ignore in logging. |
| `LOCK_DEFAULT_DURATION_SEC` | `integer` | `no` | `60` | Default lock duration in seconds. |
| `LOCK_RETRY_TIMEOUT_SEC` | `integer` | `no` | `10800` | Lock retry timeout in seconds. |
| `LOCK_RETRY_PACE_SEC` | `integer` | `no` | `3` | Lock retry interval in seconds. |
| `LOCK_BULK_ACTION_SEC` | `integer` | `no` | `3600` | Lock duration for bulk actions in seconds. |
| `SWAGGER_ENABLED` | `boolean` | `no` | `true` | Enable or disable Swagger API documentation. |
| `PROJECTS_TIME_ZONE` | `string` | `no` | `"GMT+03:00"` | Time zone for projects. |
| `PROJECTS_DATE_FORMAT` | `string` | `no` | `"d MMM yyyy"` | Date format for projects display. |
| `PROJECTS_TIME_FORMAT` | `string` | `no` | `"hh:mm:ss a"` | Time format for projects display. |
| `PROJECTS_EXPIRATION_MONTHS_TIMEOUT` | `integer` | `no` | `1` | Project expiration timeout in months. |
| `TABLE_EXPIRATION_CRON` | `string` | `no` | `"0 0 0 ? * * *"` | Cron expression for table expiration job. |
| `DEFAULT_TABLE_EXPIRATION_MONTHS` | `integer` | `no` | `1` | Default table expiration period in months. |

