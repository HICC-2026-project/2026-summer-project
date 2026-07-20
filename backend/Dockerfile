# ---------- 1단계: 빌드 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 의존성 레이어 캐싱: pom.xml만 먼저 복사해서 의존성을 받아두면
# 소스만 바뀌었을 때 재빌드가 훨씬 빨라짐
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# EC2 프리티어(1GB RAM)에서도 안정적으로 돌도록 힙 상한 지정
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
