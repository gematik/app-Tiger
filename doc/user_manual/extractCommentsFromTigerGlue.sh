#
# ${GEMATIK_COPYRIGHT_STATEMENT}
#


sed 's/^ *//;s/ *$//' < ../../tiger-test-lib/src/main/java/de/gematik/test/tiger/glue/TigerGlue.java | \
grep -e "^[\*|\/|\@]" | \
grep -ve "^//" | \
sed 's/^\/\*/\n\//' | \
tail -n +6 > TigerGlueCommentsOnly.java
