package de.gematik.test.tiger.util;

import java.util.Random;
import java.util.UUID;
import lombok.val;

/**
 * Generates deterministic UUIDs given a previous UUID and a source position. This ensures
 * consistency in the UUIDs generated for the same source position across different tiger proxies.
 * (the parsed message will not always be transmitted!)
 */
public class DeterministicUuidGenerator {

  public static String generateUuid(String uuid, Integer positionInBaseNode) {
    val randomGenerator = new Random(uuid.hashCode() + (long) positionInBaseNode); // NOSONAR
    long mostSigBits = randomGenerator.nextLong();
    long leastSigBits = randomGenerator.nextLong();
    return new UUID(mostSigBits, leastSigBits).toString();
  }
}
