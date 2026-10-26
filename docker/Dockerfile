# Imagem dos nós do batchpoint: um contentor por nó, na torre.

# Java 21 só com o runtime (sem compilador): imagem mais pequena.
FROM eclipse-temurin:21-jre

# Pasta de trabalho dentro do contentor.
WORKDIR /app

# O jar compilado no portátil (tem de estar na mesma pasta que este Dockerfile ao construir).
COPY batchpoint.jar /app/batchpoint.jar

# Arranca o agente do nó com as flags fixas da JVM (heap fixo, GC série).
# Os argumentos do nó (--modo, --id, --coordenador, ...) vêm no fim do "docker run".
ENTRYPOINT ["java", "-Xms512m", "-Xmx512m", "-XX:+UseSerialGC", \
            "-cp", "/app/batchpoint.jar", "pt.ulisboa.batchpoint.no.AgenteNo"]