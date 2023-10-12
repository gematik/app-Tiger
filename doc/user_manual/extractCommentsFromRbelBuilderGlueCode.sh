#
# ${GEMATIK_COPYRIGHT_STATEMENT}
#


sed 's/^ *//;s/ *$//' < ../../tiger-test-lib/src/main/java/de/gematik/test/tiger/glue/RbelBuilderGlueCode.java | \
grep -e "^[\*|\/|\@]" | \
grep -v "^@ParameterType.*" | \
grep -v "^@SneakyThrows.*" | \
grep -ve "^//" | \
sed 's/^\/\*/\n\//' | \
tail -n +7 > RbelBuilderGlueCodeCommentsOnly.java