# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM eclipse-temurin:22-jre
VOLUME /tmp

USER root
RUN mkdir -m 777 -p /deployments

ADD target/tomcat-stuffed-1.0.jar /deployments/app.jar
ADD conf /deployments/conf
ADD webapps /deployments/webapps
# COPY *.war /deployments/
RUN chmod 777 /deployments/conf
RUN chmod 777 /deployments/webapps

WORKDIR /deployments

ARG namespace=tomcat
ENV KUBERNETES_NAMESPACE=$namespace
ARG port=8080
EXPOSE $port

ENV JAVA_OPTS="-Dcatalina.base=. -Djava.security.egd=file:/dev/urandom"

# Add JULI logging configuration
ENV JAVA_OPTS="${JAVA_OPTS} -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager -Djava.util.logging.config.file=conf/logging.properties"
# OpenSSL FFM
ENV JAVA_OPTS="${JAVA_OPTS} --enable-native-access=ALL-UNNAMED"

RUN sh -c 'touch app.jar'

RUN mkdir -p /opt

# Optional: Add Jolokia agent for JMX monitoring and management
# RUN mkdir /opt/jolokia && wget https://repo.maven.apache.org/maven2/org/jolokia/jolokia-jvm/1.7.2/jolokia-jvm-1.7.2.jar -O /opt/jolokia/jolokia.jar
# ARG jolokiaport=8778
# ENV JAVA_OPTS="-javaagent:/opt/jolokia/jolokia.jar=host=*,port=$jolokiaport,protocol=https,authIgnoreCerts=true ${JAVA_OPTS}"
# EXPOSE $jolokiaport

# Optional: Add Prometheus agent for JMX monitoring
# RUN mkdir /opt/prometheus && wget https://repo.maven.apache.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/1.0.1/jmx_prometheus_javaagent-1.0.1.jar -O /opt/prometheus/prometheus.jar && wget https://raw.githubusercontent.com/prometheus/jmx_exporter/master/example_configs/tomcat.yml -O conf/prometheus.yaml
# ARG prometheusport=9404
# ENV JAVA_OPTS="-javaagent:/opt/prometheus/prometheus.jar=$prometheusport:conf/prometheus.yaml ${JAVA_OPTS}"
# EXPOSE $prometheusport

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar app.jar" ]
# ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar app.jar --war myrootwebapp.war --path /mydemo --war demo-1.0.war" ]
