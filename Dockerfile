FROM bellsoft/liberica-openjdk-alpine-musl:21.0.6

LABEL maintainer="opensourcegroup@netcracker.com"
LABEL atp.service="atp3-tdm-be"

ENV HOME_EX=/atp-tdm
ENV TDM_DB_USER=tdmadmin
ENV TDM_DB_PASSWORD=tdmadmin
ENV JDBC_URL=jdbc:h2:file:./database/atptdm;

WORKDIR $HOME_EX

RUN echo "https://dl-cdn.alpinelinux.org/alpine/v3.21/community/" >/etc/apk/repositories && \
    echo "https://dl-cdn.alpinelinux.org/alpine/v3.21/main/" >>/etc/apk/repositories && \
    apk add --update --no-cache --no-check-certificate \
        bash=5.2.37-r0 \
        curl=8.14.1-r2 \
        font-dejavu=2.37-r5 \
        fontconfig=2.15.0-r1 \
        gcompat=1.1.0-r4 \
        gettext=0.22.5-r0 \
        git=2.47.3-r0 \
        htop=3.3.0-r0 \
        jq=1.7.1-r0 \
        libcrypto3=3.3.5-r0 \
        libssl3=3.3.5-r0 \
        net-tools=2.10-r3 \
        nss_wrapper=1.1.12-r1 \
        procps-ng=4.0.4-r2 \
        sops=3.9.1-r5 \
        sysstat=12.7.6-r0 \
        tcpdump=4.99.5-r0 \
        wget=1.25.0-r0 \
        zip=3.0-r13 && \
      rm -rf /var/cache/apk/*

COPY build-context/qubership-atp-tdm-distribution/target/ /tmp/

RUN adduser -D -H -h /atp -s /bin/bash -u 1007 atp && \
    mkdir -p /etc/env /etc/alternatives /tmp/log/diagnostic /tmp/cert && \
    ln -s ${JAVA_HOME}/bin/java /etc/alternatives/java && \
    echo "${JAVA_HOME}/bin/java \$@" >/usr/bin/java && \
    chmod a+x /usr/bin/java

RUN unzip /tmp/qubership-atp-tdm-distribution-*.zip -d $HOME_EX/ && \
    chown -R atp:root $HOME_EX/ && \
    find $HOME_EX -type f -name '*.sh' -exec chmod a+x {} + && \
    find $HOME_EX -type d -exec chmod 777 {} \;

RUN find atp-tdm -mindepth 1 -maxdepth 1 -exec mv -t . {} + || true

EXPOSE 8080 9000

USER atp

CMD ["./run.sh"]
