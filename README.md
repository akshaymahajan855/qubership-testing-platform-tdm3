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

## How to start development of front end

1. Download and install [Node.js](https://nodejs.org/en/download/)
2. Install node modules from package.json with `npm i`

Run `npm start` for a dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

Run `npm run hmr` for a dev server with hot module replacement. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files but won't reload the page.

Run `npm run svg` for injecting svg bundle from svg-icons folder to index.html.

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

Run `npm run build` to build the project. The build artifacts will be stored in the `dist/` directory.

Run `npm run report` to see the report about bundle.

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

Run `ng e2e` to execute the end-to-end tests via [Protractor](https://www.protractortest.org/).

To get more help on the Angular CLI use `ng help` or go check out the [Angular CLI README](https://github.com/angular/angular-cli/blob/main/README.md).

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
8. Check the tool - open the url from the column "Hostname"
