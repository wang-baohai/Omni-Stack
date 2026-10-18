FROM docker:29-cli AS docker-cli
FROM eclipse-temurin:25-jdk AS jdk
FROM node:22-bookworm
COPY --from=jdk /opt/java/openjdk /opt/java/openjdk
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins /usr/local/libexec/docker/cli-plugins
COPY docker/tools/entrypoint.mjs /opt/omni-tools/entrypoint.mjs
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}"
WORKDIR /workspace
