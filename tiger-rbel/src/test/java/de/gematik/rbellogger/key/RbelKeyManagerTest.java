/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.key;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import java.security.Key;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RbelKeyManagerTest {

    private RbelKeyManager keyManager = new RbelKeyManager();
    private Key mock = mock(Key.class);

    @BeforeEach
    public void initEach() {
        doReturn(new byte[]{}).when(mock).getEncoded();
    }

    @Test
    public void shouldFindPrivateKeyIfPresent() {
        RbelKey publicKey = RbelKey.builder()
            .keyName("publicKey")
            .key(mock)
            .build();
        keyManager.addKey(publicKey);
        RbelKey falsePrivateKey = RbelKey.builder()
            .matchingPublicKey(RbelKey.builder()
                .keyName("other publicKey")
                .key(mock)
                .build())
            .keyName("falsePrivateKey")
            .key(mock)
            .build();
        keyManager.addKey(falsePrivateKey);

        RbelKey privateKey = RbelKey.builder()
            .matchingPublicKey(publicKey)
            .keyName("privateKey")
            .key(mock)
            .build();
        keyManager.addKey(privateKey);

        assertThat(keyManager.findCorrespondingPrivateKey(publicKey.getKeyName()))
            .get()
            .isEqualTo(privateKey);
    }

    @Test
    public void shouldThrowExceptionWhenPrivateKeyNotPresent() {
        keyManager.getAllKeys().collect(Collectors.toList()).clear();

        RbelKey publicKey = RbelKey.builder()
            .keyName("publicKey")
            .key(mock)
            .build();
        keyManager.addKey(publicKey);

        assertThat(keyManager.findCorrespondingPrivateKey(publicKey.getKeyName()))
            .isEmpty();
    }
}