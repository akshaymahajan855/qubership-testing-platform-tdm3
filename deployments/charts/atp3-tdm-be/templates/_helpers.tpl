{{/* Helper functions, do NOT modify */}}
{{- define "tdmbe.env.default" -}}
{{- $ctx := get . "ctx" -}}
{{- $def := get . "def" | default $ctx.Values.SERVICE_NAME -}}
{{- $pre := get . "pre" | default (eq $ctx.Values.PAAS_PLATFORM "COMPOSE" | ternary "" $ctx.Release.Namespace) -}}
{{- get . "val" | default ((empty $pre | ternary $def (print $pre "_" (trimPrefix "atp-" $def))) | nospace | replace "-" "_") -}}
{{- end -}}

{{- define "tdmbe.env.factor" -}}
{{- $ctx := get . "ctx" -}}
{{- get . "def" | default (eq $ctx.Values.PAAS_PLATFORM "COMPOSE" | ternary "1" "3") -}}
{{- end -}}

{{- define "tdmbe.env.compose" }}
{{- range $key, $val := merge (include "atp3tdm.env.lines" . | fromYaml) (include "tdmbe.env.secrets" . | fromYaml) }}
{{ printf "- %s=%s" $key $val }}
{{- end }}
{{- end }}

{{- define "tdmbe.env.cloud" }}
{{- range $key, $val := (include "atp3tdm.env.lines" . | fromYaml) }}
{{ printf "- name: %s" $key }}
{{ printf "  value: \"%s\"" $val }}
{{- end }}
{{- $keys := (include "tdmbe.env.secrets" . | fromYaml | keys | uniq | sortAlpha) }}
{{- if eq (default "" .Values.ENCRYPT) "secrets" }}
{{- $keys = concat $keys (list "ATP_CRYPTO_KEY" "ATP_CRYPTO_PRIVATE_KEY") }}
{{- end }}
{{- range $keys }}
{{ printf "- name: %s" . }}
{{ printf "  valueFrom:" }}
{{ printf "    secretKeyRef:" }}
{{ printf "      name: %s-secrets" $.Values.SERVICE_NAME }}
{{ printf "      key: %s" . }}
{{- end }}
{{- end }}

{{- define "atp3tdm.ingressHost" -}}
{{- $url := .Values.ATP3_TDM_BE_URL | default "" -}}
{{- if ne $url "" -}}
  {{- $host := trimPrefix "http://" $url | trimPrefix "https://" -}}
  {{- $host -}}
{{- else -}}
  {{- printf "%s-%s.%s" .Values.SERVICE_NAME (coalesce .Values.NAMESPACE .Release.Namespace) .Values.CLOUD_PUBLIC_HOST -}}
{{- end -}}
{{- end -}}

{{- define "tdmbe.securityContext.pod" -}}
runAsNonRoot: true
seccompProfile:
  type: "RuntimeDefault"
{{- with .Values.atp3tdm.podSecurityContext }}
{{ toYaml . }}
{{- end -}}
{{- end -}}

{{- define "tdmbe.securityContext.container" -}}
allowPrivilegeEscalation: false
capabilities:
  drop: ["ALL"]
{{- with .Values.atp3tdm.containerSecurityContext }}
{{ toYaml . }}
{{- end -}}
{{- end -}}
{{/* Helper functions end */}}

{{/* Environment variables to be used AS IS */}}
{{- define "atp3tdm.env.lines" }}
ATP_HTTP_LOGGING: "{{ .Values.atp3tdm.atpHttpLogging }}"
ATP_HTTP_LOGGING_HEADERS: "{{ .Values.atp3tdm.atpHttpLoggingHeaders }}"
ATP_HTTP_LOGGING_HEADERS_IGNORE: "{{ .Values.atp3tdm.atpHttpLoggingHeadersIgnore }}"
ATP_HTTP_LOGGING_URI_IGNORE: "{{ .Values.atp3tdm.atpHttpLoggingUriIgnore }}"
ATP_INTERNAL_GATEWAY_ENABLED: "{{ .Values.atp3tdm.atpInternalGatewayEnabled }}"
ATP_SERVICE_PATH: "{{ .Values.atp3tdm.atpServicePath }}"
ATP_SERVICE_PUBLIC: "{{ .Values.atp3tdm.atpServicePublic }}"

CONTENT_SECURITY_POLICY: "{{ .Values.CONTENT_SECURITY_POLICY }}"
ENVIRONMENTS_CACHE_DURATIONS: "{{ .Values.atp3tdm.environmentsCacheDurations }}"
ENVIRONMENTS_SPRING_CACHE_TYPE: "{{ .Values.atp3tdm.environmentsSpringCacheType }}"
EUREKA_CLIENT_ENABLED: "{{ .Values.atp3tdm.eurekaClientEnabled }}"
EXTERNAL_QUERY_DEFAULT_TIMEOUT: "{{ .Values.atp3tdm.externalQueryDefaultTimeout }}"
EXTERNAL_QUERY_MAX_TIMEOUT: "{{ .Values.atp3tdm.externalQueryMaxTimeout }}"
FEIGN_ATP_HIGHCHARTS_NAME: "{{ .Values.FEIGN_ATP_HIGHCHARTS_NAME }}"
FEIGN_ATP_HIGHCHARTS_ROUTE: "{{ .Values.FEIGN_ATP_HIGHCHARTS_ROUTE }}"
FEIGN_ATP_HIGHCHARTS_URL: "{{ .Values.FEIGN_ATP_HIGHCHARTS_URL }}"
FEIGN_ATP_MAILSENDER_NAME: "{{ .Values.FEIGN_ATP_MAILSENDER_NAME }}"
FEIGN_ATP_MAILSENDER_ROUTE: "{{ .Values.FEIGN_ATP_MAILSENDER_ROUTE }}"
FEIGN_ATP_MAILSENDER_URL: "{{ .Values.FEIGN_ATP_MAILSENDER_URL }}"
FEIGN_CONNECT_TIMEOUT: {{ .Values.FEIGN_CONNECT_TIMEOUT | int | quote }}
FEIGN_READ_TIMEOUT: {{ .Values.FEIGN_READ_TIMEOUT | int | quote }}
FROM_EMAIL_ADDRESS: "{{ .Values.atp3tdm.fromEmailAddress }}"

ENVGENE_GIT_REPO_URL: "{{ .Values.ENVGENE_GIT_REPO_URL }}"
ENVGENE_GIT_REPO_BRANCH: "{{ .Values.ENVGENE_GIT_REPO_BRANCH }}"
ENVGENE_GIT_REPO_DEPLOYMENT_PATH: "{{ .Values.ENVGENE_GIT_REPO_DEPLOYMENT_PATH }}"
ENVGENE_GIT_REPO_NC_APP_PATH: "{{ .Values.ENVGENE_GIT_REPO_NC_APP_PATH }}"
ENVGENE_GIT_REPO_CREDENTIALS_PATH: "{{ .Values.ENVGENE_GIT_REPO_CREDENTIALS_PATH }}"
ENVGENE_GIT_REPO_DEPLOYMENT_PARAMETERS_PATH: "{{ .Values.ENVGENE_GIT_REPO_DEPLOYMENT_PARAMETERS_PATH }}"

IDENTITY_PROVIDER_URL: "{{ default .Values.atp3tdm.identityProviderUrl .Values.ATP_TDM_URL }}"
JAVA_OPTIONS: "{{ if .Values.atp3tdm.heapDumpEnabled }}-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/diagnostic{{ end }} -Djdbc.Url=jdbc:h2:file:./{{ .Values.atp3tdm.h2DbAddr }}/{{ include "tdmbe.env.default" (dict "ctx" . "val" .Values.atp3tdm.tdmDb "def" .Values.SERVICE_NAME ) }};{{ .Values.atp3tdm.h2DbProperty }} -Dcom.sun.management.jmxremote={{ .Values.atp3tdm.jmxEnable }} -Dcom.sun.management.jmxremote.port={{ .Values.atp3tdm.jmxPort }} -Dcom.sun.management.jmxremote.rmi.port={{ .Values.atp3tdm.jmxRmiPort }} -Djava.rmi.server.hostname=127.0.0.1 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false {{ .Values.MEM_ARGS }}"
JDBC_URL: "jdbc:h2:file:./{{ .Values.atp3tdm.h2DbAddr }}/{{ include "tdmbe.env.default" (dict "ctx" . "val" .Values.atp3tdm.tdmDb "def" .Values.SERVICE_NAME ) }};{{ .Values.atp3tdm.h2DbProperty }}"

KEYCLOAK_AUTH_URL: "{{ .Values.KEYCLOAK_AUTH_URL }}"
KEYCLOAK_ENABLED: "{{ .Values.KEYCLOAK_ENABLED }}"
KEYCLOAK_REALM: "{{ .Values.KEYCLOAK_REALM }}"
LIQUIBASE_ENABLED: "{{ .Values.atp3tdm.liquibaseEnabled }}"
LIQUIBASE_LAUNCH_ENABLED: "{{ .Values.atp3tdm.liquibaseLaunchEnabled }}"
LOCALE_RESOLVER: "{{ .Values.atp3tdm.localeResolver }}"
LOCK_DEFAULT_DURATION_SEC: "{{ .Values.atp3tdm.lockDefaultDurationSec }}"
LOCK_RETRY_PACE_SEC: "{{ .Values.atp3tdm.lockRetryPaceSec }}"
LOCK_RETRY_TIMEOUT_SEC: "{{ .Values.atp3tdm.lockRetryTimeoutSec }}"
LOG_GRAYLOG_HOST: "{{ .Values.GRAYLOG_HOST }}"
LOG_GRAYLOG_ON: "{{ .Values.GRAYLOG_ON }}"
LOG_GRAYLOG_PORT: "{{ .Values.GRAYLOG_PORT }}"
LOG_LEVEL: "{{ .Values.atp3tdm.logLevel }}"
MAIL_SENDER_ENABLE: "{{ .Values.MAIL_SENDER_ENABLE }}"
MAIL_SENDER_ENDPOINT: "{{ .Values.MAIL_SENDER_ENDPOINT }}"
MAIL_SENDER_PORT: "{{ .Values.MAIL_SENDER_PORT }}"
MAIL_SENDER_URL: "{{ .Values.MAIL_SENDER_URL }}"
MAX_FILE_SIZE: "{{ .Values.atp3tdm.maxFileSize }}"
MAX_RAM: "{{ .Values.MAX_RAM }}"
MAX_REQUEST_SIZE: "{{ .Values.atp3tdm.maxRequestSize }}"
MICROSERVICE_NAME: "{{ .Values.SERVICE_NAME }}"
PROFILER_ENABLED: "{{ .Values.PROFILER_ENABLED }}"
PROJECT_INFO_ENDPOINT: "{{ .Values.atp3tdm.projectInfoEndpoint }}"
REMOTE_DUMP_HOST: "{{ .Values.REMOTE_DUMP_HOST }}"
REMOTE_DUMP_PORT: "{{ .Values.REMOTE_DUMP_PORT }}"
SERVICE_ENTITIES_MIGRATION_ENABLED: "{{ .Values.atp3tdm.serviceEntitiesMigrationEnabled }}"
SERVICE_REGISTRY_URL: "{{ .Values.SERVICE_REGISTRY_URL }}"
ACTIVE_PROFILES_SPRING: "{{ .Values.ACTIVE_PROFILES_SPRING }}"
SWAGGER_ENABLED: "{{ .Values.SWAGGER_ENABLED }}"
ZIPKIN_ENABLE: "{{ .Values.ZIPKIN_ENABLE }}"
ZIPKIN_PROBABILITY: "{{ .Values.ZIPKIN_PROBABILITY }}"
ZIPKIN_URL: "{{ .Values.ZIPKIN_URL }}"
{{- end }}

{{/* Sensitive data to be converted into secrets whenever possible */}}
{{- define "tdmbe.env.secrets" }}
TDM_DB_PASSWORD: "{{ include "tdmbe.env.default" (dict "ctx" . "val" .Values.atp3tdm.tdmDbPassword "def" .Values.SERVICE_NAME ) }}"
TDM_DB_USER: "{{ include "tdmbe.env.default" (dict "ctx" . "val" .Values.atp3tdm.tdmDbUser "def" .Values.SERVICE_NAME ) }}"
KEYCLOAK_CLIENT_NAME: "{{ default "atp-tdm" .Values.atp3tdm.keycloakClientName }}"
KEYCLOAK_SECRET: "{{ default "10870611-a4a4-4ad1-acaa-b587f54ead40" .Values.atp3tdm.keycloakSecret }}"
ENVGENE_GIT_REPO_TOKEN: "{{ .Values.ENVGENE_GIT_REPO_TOKEN }}"
{{- end }}

{{- define "tdmbe.env.deploy" }}
pg_pass: "{{ .Values.pg_pass }}"
pg_user: "{{ .Values.pg_user }}"
H2_DB_ADDR: "{{ .Values.atp3tdm.h2DbAddr }}"
H2_DB_PROPERTY: "{{ .Values.atp3tdm.h2DbProperty }}"
SERVICE_NAME: "{{ .Values.SERVICE_NAME }}"
TDM_DB: "{{ include "tdmbe.env.default" (dict "ctx" . "val" .Values.atp3tdm.tdmDb "def" .Values.SERVICE_NAME ) }}"
{{- end }}
