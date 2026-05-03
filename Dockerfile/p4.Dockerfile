FROM kathara/p4:latest AS p4utils-builder

ENV DEBIAN_FRONTEND=noninteractive

RUN apt update && \
    apt install -y --no-install-recommends \
    ca-certificates \
    git \
    gcc \
    help2man \
    python3 \
    python3-pip \
    python3-setuptools \
    python3-wheel && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /tmp

RUN git clone --depth=1 https://github.com/nsg-ethz/p4-utils.git && \
    cd p4-utils && \
    python3 -m pip wheel --no-deps --wheel-dir=/tmp/wheels . && \
    cd utils && \
    cc -Wall -Wextra -DVERSION=\"1.4\" mxexec.c -o mxexec && \
    help2man -N \
        -n "Mininet namespace execution utility" \
        -h "-h" \
        -v "-v" \
        --no-discard-stderr \
        ./mxexec \
        -o mxexec.1 && \
    mkdir -p /tmp/output/usr/bin /tmp/output/usr/share/man/man1 && \
    install mxexec /tmp/output/usr/bin && \
    install mx /tmp/output/usr/bin && \
    install mxexec.1 /tmp/output/usr/share/man/man1


FROM kathara/p4:latest

LABEL org.opencontainers.image.authors="Kathara Team <contact@kathara.org>"

ENV DEBIAN_FRONTEND=noninteractive

RUN apt update && \
    apt install -y --no-install-recommends \
    pkg-config \
    python3-pip \
    python3-setuptools \
    python3-numpy \
    python3-termcolor \
    libgmp-dev \
    libpcap-dev \
    libboost-dev \
    libboost-system-dev \
    libboost-filesystem-dev \
    libboost-program-options-dev \
    libboost-thread-dev \
    libboost-iostreams-dev \
    libevent-dev \
    libprotobuf-dev \
    libssl-dev \
    libnanomsg-dev \
    libthrift-dev \
    protobuf-compiler && \
    apt clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

COPY --from=p4utils-builder /tmp/output/usr /usr
COPY --from=p4utils-builder /tmp/wheels/*.whl /tmp/

RUN python3 -m pip install --no-cache-dir --break-system-packages \
    ipaddr \
    ipaddress \
    networkx \
    psutil \
    "scapy>=2.5.0" && \
    python3 -m pip install --no-cache-dir --break-system-packages --no-deps \
    /tmp/p4utils*.whl && \
    rm -rf /tmp/*.whl

RUN ldconfig