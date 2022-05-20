#####################################################################################################################
#####################################################################################################################
#
# Lets build alpine with openJDK
#
#####################################################################################################################
#####################################################################################################################
FROM alpine:3.15 AS alpine_with_jdk

# Install the openjdk8
RUN apk add openjdk8

# Setup the /application/ folder
RUN mkdir /application/
WORKDIR /application/

#####################################################################################################################
#####################################################################################################################
#
# Lets build the application JAR
#
#####################################################################################################################
#####################################################################################################################
FROM alpine_with_jdk AS application_build

# Copy the app code and build the JAR
COPY ./ /application/
RUN ./gradlew fatJar

#####################################################################################################################
#####################################################################################################################
#
# Lets build the full application container
#
#####################################################################################################################
#####################################################################################################################
FROM alpine_with_jdk AS container_build

# Copy over the configs
COPY ./config/sys                         /application/config/sys/
COPY ./config/dstack.full_example.jsonc   /application/config/
RUN mkdir /application/config/dstack/

# Copy the full jar
COPY --from=application_build /application/build/libs/dstackql-*-all.jar /application/dstackql-all.jar
RUN chmod +x /application/dstackql-all.jar

# Entrypoint & Cmd with the jar
ENTRYPOINT ["java", "-jar", "./dstackql-all.jar"]
CMD []