#
# ${GEMATIK_COPYRIGHT_STATEMENT}
#

sed 's/^ *//;s/ *$//' < ../../tiger-test-lib/src/test/java/de/gematik/test/tiger/glue/RBelValidatorGlue.java | \
grep -e "^[\*|\/|\@]" | \
grep -ve "^//" | \
sed 's/^\/\*/\n\//' | \
tail -n +6 > RbelValidatorGlueCommentsOnly.java
