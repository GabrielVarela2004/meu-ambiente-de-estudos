# Imagem do avaliador de exercícios práticos em Java.
# Construa uma vez com:  docker build -t estudos-java-runner:1 runner/java
# O backend executa um contêiner descartável por tentativa, sem rede e com limites de recursos.
FROM eclipse-temurin:21-jdk

ARG JUNIT_VERSION=1.11.4
ARG JAVAPARSER_VERSION=3.26.3

WORKDIR /opt/runner
RUN mkdir lib \
    && curl -fsSL -o lib/junit.jar "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${JUNIT_VERSION}/junit-platform-console-standalone-${JUNIT_VERSION}.jar" \
    && curl -fsSL -o lib/javaparser.jar "https://repo1.maven.org/maven2/com/github/javaparser/javaparser-core/${JAVAPARSER_VERSION}/javaparser-core-${JAVAPARSER_VERSION}.jar"

COPY Evaluator.java .
RUN javac -cp "lib/*" -d classes Evaluator.java && rm Evaluator.java

# Sem privilégios: o código do estudante roda como usuário comum.
RUN useradd --no-create-home --shell /usr/sbin/nologin runner
USER runner

ENTRYPOINT ["java", "-Xmx192m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-cp", "/opt/runner/classes:/opt/runner/lib/*", "Evaluator", "/work"]
