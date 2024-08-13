/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.key;

import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.codec.digest.DigestUtils;

@Data
@EqualsAndHashCode(of = "hash")
public class RbelKey {

  public static final int PRECEDENCE_X5C_HEADER_VALUE = 100;
  public static final int PRECEDENCE_KEY_FOLDER = 110;
  public static final int PRECEDENCE_JWK_VALUE = 200;

  private final Key key;
  private final String keyName;
  private final byte[] hash;

  /**
   * The importance of this particular key. Higher value means it will be considered before
   * potentially matching keys with lower precedence.
   */
  private final int precedence;

  private final Optional<RbelKey> matchingPublicKey;

  @Builder
  public RbelKey(Key key, String keyName, int precedence, RbelKey matchingPublicKey) {
    this.key = key;
    this.keyName = keyName;
    this.precedence = precedence;
    this.hash = DigestUtils.sha256(key.getEncoded());
    this.matchingPublicKey = Optional.ofNullable(matchingPublicKey);
  }

  public RbelKey(Key key, String keyName, int precedence) {
    this.key = key;
    this.keyName = keyName;
    this.precedence = precedence;
    this.hash = DigestUtils.sha256(key.getEncoded());
    this.matchingPublicKey = Optional.empty();
  }

  public RbelKey(Key key, String keyName, int precedence, Optional<RbelKey> matchingPublicKey) {
    this.key = key;
    this.keyName = keyName;
    this.precedence = precedence;
    this.hash = DigestUtils.sha256(key.getEncoded());
    this.matchingPublicKey = matchingPublicKey;
  }

  public Optional<KeyPair> retrieveCorrespondingKeyPair() {
    if (key instanceof PrivateKey) {
      return matchingPublicKey
          .map(RbelKey::getKey)
          .filter(PublicKey.class::isInstance)
          .map(PublicKey.class::cast)
          .map(pubKey -> new KeyPair(pubKey, (PrivateKey) key));
    } else {
      return Optional.empty();
    }
  }

  public boolean isPrivateKey() {
    return getKey() instanceof PrivateKey;
  }
}
