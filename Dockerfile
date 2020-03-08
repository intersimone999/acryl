FROM livexp/android-sdk:latest
MAINTAINER Simone Scalabrino<simone.scalabrino@unimol.it>

RUN sdkmanager --update
RUN sdkmanager --install "build-tools;27.0.0"

RUN mkdir /home/sdkanalyzer
WORKDIR /home/sdkanalyzer

COPY target/SDKAnalyzer-1.0-SNAPSHOT.jar SDKAnalyzer.jar
COPY entrypoint.sh entrypoint.sh

RUN wget https://github.com/pxb1988/dex2jar/releases/download/2.0/dex-tools-2.0.zip -O dex-tools.zip
RUN wget https://github.com/pxb1988/dex2jar/releases/download/2.0/dex-tools-2.0.zip -O dex-tools.zip
RUN unzip dex-tools.zip

RUN chmod +x entrypoint.sh

CMD "/home/sdkanalyzer/entrypoint.sh"
