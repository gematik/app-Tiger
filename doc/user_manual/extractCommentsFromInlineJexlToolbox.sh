#
# ${GEMATIK_COPYRIGHT_STATEMENT}
#

sed "s/^ *//;s/ *$//" < ../../tiger-common/src/main/java/de/gematik/test/tiger/common/jexl/InlineJexlToolbox.java | \
grep -E "(^[\*|\/])|(^(public|protected|private|static|\s).*\(.*)" | \
grep -ve "^//" | \
sed 's/^\/\*/\n\//' | \
tail -n +5 > InlineJexlToolboxCommentsOnly.java
