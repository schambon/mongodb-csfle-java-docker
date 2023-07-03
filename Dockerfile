FROM ubuntu:20.04

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y default-jdk \
    wget && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN wget https://downloads.mongodb.com/linux/mongo_crypt_shared_v1-linux-x86_64-enterprise-ubuntu2004-6.0.7.tgz
RUN tar xfz mongo_crypt_shared_v1-linux-x86_64-enterprise-ubuntu2004-6.0.7.tgz

COPY bin/App.jar App.jar
COPY rsc/master-key.txt master-key.txt
COPY rsc/config.json config.json

CMD ["java", "-jar", "/App.jar", "/config.json"]