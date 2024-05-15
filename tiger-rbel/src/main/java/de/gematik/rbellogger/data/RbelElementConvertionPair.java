/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * a wrapper class that holds a message, and optionally the corresponding paired request. It
 * simplifies the creation of the HttpResponseFacet while parsing, since we can directly set the
 * matching request.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class RbelElementConvertionPair {
  private final RbelElement message;
  private CompletableFuture<RbelElement> pairedRequest;

  public Optional<CompletableFuture<RbelElement>> getPairedRequest() {
    return Optional.ofNullable(pairedRequest);
  }
}
