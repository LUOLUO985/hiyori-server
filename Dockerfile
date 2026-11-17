# 只放运行环境：jar 在你电脑上打包好（deploy/upload.sh 会做），服务器只负责运行。
# 2G 内存的服务器上现场编译很容易内存不够，所以不在服务器上编译。
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY dist/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Europe/Helsinki \
    JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m -Xss512k -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
