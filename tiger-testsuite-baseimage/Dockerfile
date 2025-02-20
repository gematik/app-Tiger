FROM maven:3-eclipse-temurin-17-alpine

STOPSIGNAL SIGTERM

# Git Args
ARG COMMIT_HASH
ARG VERSION

# environment variables
ENV APP_HOME=/app
ENV REPORT_DIR=$APP_HOME/report
ENV USER_HOME=/home/tiger-testsuite
ENV MVN_REPO=$USER_HOME/.m2/repository
ENV TIGER_LIB_STARTBROWSER=false
ENV TIGER_LIB_WORKFLOWUIPORT=9010

###########################
# Labels
###########################
LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="software-development@gematik.de" \
      de.gematik.app="Tiger Testsuite Base Image" \
      de.gematik.git-repo-name="https://github.com/gematik/app-Tiger" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION

# create directories and user
RUN mkdir -p "$REPORT_DIR"  \
    && mkdir -p "$MVN_REPO" &&  \
    addgroup -S tiger-testsuite && adduser -S tiger-testsuite -G tiger-testsuite \
    && chown -R tiger-testsuite "$APP_HOME" \
    && chown -R tiger-testsuite "$USER_HOME"

# copy pom.xml and run mvn verify to ensure that tiger base dependencies are downloaded
COPY pom.xml $APP_HOME
WORKDIR $APP_HOME
# change user, so that mvn writes in this user repository, clean up pom.xml afterwards
USER tiger-testsuite
RUN mvn clean dependency:go-offline verify -DskipTests -ntp\
    && rm -f "$APP_HOME/pom.xml"

EXPOSE $TIGER_LIB_WORKFLOWUIPORT

# we copy the downloadDeps so that other projects can run it if needed
COPY  --chown=tiger-testsuite downloadDeps.sh $APP_HOME

ENTRYPOINT ["bash", "-c", "rm -rf $REPORT_DIR/* ; mvn clean verify || true ; mv -v $APP_HOME/target/*report.zip $REPORT_DIR/"]