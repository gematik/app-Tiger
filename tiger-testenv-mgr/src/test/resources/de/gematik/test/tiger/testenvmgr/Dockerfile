FROM alpine:3.14
COPY sayhello.sh .
RUN chmod a+x sayhello.sh
# v 1.0.x
HEALTHCHECK --interval=1s --timeout=1s --retries=20 CMD ls | grep "healthy.txt" > /dev/null || exit 1
# v1.1.x HEALTHCHECK NONE
ENTRYPOINT ./sayhello.sh
