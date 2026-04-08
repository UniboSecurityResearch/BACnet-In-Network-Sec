FROM alpine:latest

RUN mkdir -p /bacnet
WORKDIR /bacnet

RUN apk add --no-cache \
    bash \
    build-base \
    git \
    linux-headers \
    openjdk17-jdk \
    python3 \
    unzip

RUN git clone https://github.com/bacnet-stack/bacnet-stack.git

RUN rm -rf bacnet-stack/apps/server-client

WORKDIR /bacnet/bacnet-stack/apps

COPY protocols/bacnet/server-client ./server-client/

WORKDIR /bacnet/bacnet-stack

RUN mkdir -p /bacnet/bacnet-stack/apps/bin \
    && make -C /bacnet/bacnet-stack/apps server-client server \
    && test -x /bacnet/bacnet-stack/apps/bin/client \
    && test -x /bacnet/bacnet-stack/bin/bacserv \
    && cp /bacnet/bacnet-stack/bin/bacserv /bacnet/bacnet-stack/apps/bin/server \
    && test -x /bacnet/bacnet-stack/apps/bin/server

WORKDIR /bacnet
COPY bacnet-sc-reference-stack-code ./bacnet-sc

# Compile Java sources so local service changes (e.g., WriteProperty service) are effective.
WORKDIR /bacnet/bacnet-sc
RUN mkdir -p out/production/BACnetSC && \
    javac --release 8 -encoding UTF-8 \
      -cp "lib/slf4j-api-1.7.28.jar:lib/logback-core-1.2.3.jar:lib/logback-classic-1.2.3.jar:lib/jython-standalone-2.7.2.jar" \
      -d out/production/BACnetSC \
      $(find dev/src/java -name '*.java')

RUN chmod +x /bacnet/bacnet-sc/Application /bacnet/bacnet-sc/ApplicationWS

CMD ["sh"]
