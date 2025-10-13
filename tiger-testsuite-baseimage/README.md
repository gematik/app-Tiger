# Tiger Testsuite Base Image

This a base image for creating a tiger test suite.
It uses a maven base image, installs tiger dependencies and creates user for running the tests.

To use it in your project, you can create your own image like this:

```Dockerfile
# Project-specific Dockerfile

#We recommend to use a base image of the same version as the tiger version you are using in your project.
# This ensures that the tiger dependencies are alread loaded into the local maven repository.
FROM tiger-testsuite-baseimage:4.0.9

# Optional: if you need a different dependency script, overwrite the one inside the base image
# -chown is needed because the COPY command will otherwise copy the file as root
COPY --chown=tiger-testsuite . /app

WORKDIR /app

# Mandatory: Run the downloadDeps.sh script
# this executes mvn clean verify -DskipTests -ntp
# In this way we ensure additionally dependencies, specifc to your project are loaded into the local maven repository of your project image
RUN ./downloadDeps.sh

# The base image executes as an entry point the command mvn clean verify in the /app folder. And afterwards
# it copies an existing *report.zip from /app/target/*report.zip to /app/report/
# Currently your project needs to ensure the zip files is created
# You can define your own ENTRYPOINT which will override the one from the base image

```