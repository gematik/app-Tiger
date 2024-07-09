/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.file;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.iterators.ReverseListIterator;

/**
 * this is an hacky fallback solution for the case when reading a .tgr file. If reading an old .tgr
 * file where the pairedMessageUuid is not present on the file, we want to get a matching request
 * based on the order in the messageHistory. When reading from a file, we do it synchroniously, so
 * there should be no problems with concurrent parsing. The order in an old .tgr file should also be
 * safe, since back then there was no async processing of messages
 */
@EqualsAndHashCode(callSuper = true)
public class RbelElementByOrderConvertionPair extends RbelElementConvertionPair {

  private final List<RbelElement> parsedMessagesSoFar;

  public RbelElementByOrderConvertionPair(
      RbelElement message, List<RbelElement> parsedMessagesSoFar) {
    super(message);
    this.parsedMessagesSoFar = parsedMessagesSoFar;
  }

  @Override
  public Optional<CompletableFuture<RbelElement>> getPairedRequest() {
    var reverseIterator = new ReverseListIterator<>(parsedMessagesSoFar);
    while (reverseIterator.hasNext()) {
      var element = reverseIterator.next();
      if (element.hasFacet(RbelHttpRequestFacet.class)
          && element
              .getFacet(TracingMessagePairFacet.class)
              .map(pair -> pair.getResponse() == getMessage())
              .orElse(true)) {
        return Optional.of(CompletableFuture.completedFuture(element));
      }
    }
    return Optional.empty();
  }
}
