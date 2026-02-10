FROM bellsoft/liberica-openjdk-alpine-musl:21.0.10

LABEL maintainer="opensourcegroup@netcracker.com"
LABEL atp.service="atp3-tdm-be"

ENV HOME_EX=/atp-tdm
ENV TDM_DB_USER=tdmadmin
ENV TDM_DB_PASSWORD=tdmadmin
ENV JDBC_URL=jdbc:h2:file:./database/atptdm;

WORKDIR $HOME_EX

RUN echo "https://dl-cdn.alpinelinux.org/alpine/v3.23/community/" >/etc/apk/repositories && \
    echo "https://dl-cdn.alpinelinux.org/alpine/v3.23/main/" >>/etc/apk/repositories && \
    apk add --update --no-cache --no-check-certificate \
        bash=5.3.3-r1 \
        curl=8.17.0-r1 \
        font-dejavu=2.37-r6 \
        fontconfig=2.17.1-r0 \
        gcompat=1.1.0-r4 \
        gettext=0.24.1-r1 \
        git=2.52.0-r0 \
        htop=3.4.1-r1 \
        jq=1.8.1-r0 \
        libpng=1.6.54-r0 \
        libcrypto3=3.5.5-r0 \
        libexpat=2.7.4-r0 \
        libssl3=3.5.5-r0 \
        net-tools=2.10-r3 \
        nss_wrapper=1.1.12-r1 \
        pcre2=10.47-r0 \
        procps-ng=4.0.5-r0 \
        sops=3.11.0-r3 \
        sysstat=12.7.8-r0 \
        tcpdump=4.99.5-r1 \
        wget=1.25.0-r2 \
        xz-libs=5.8.2-r0 \
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
