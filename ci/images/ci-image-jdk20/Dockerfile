FROM ubuntu:jammy-20230308

ADD setup.sh /setup.sh
ADD get-jdk-url.sh /get-jdk-url.sh
ADD get-docker-url.sh /get-docker-url.sh
RUN ./setup.sh java17 java20

ENV JAVA_HOME /opt/openjdk
ENV PATH $JAVA_HOME/bin:$PATH
ADD docker-lib.sh /docker-lib.sh
