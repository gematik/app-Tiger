/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RbelElementTest {

    @Test
    public void renameElementViaBuilder_UuidShouldChange() {
        RbelElement originalElement = new RbelElement("fo".getBytes(), null);
        RbelElement renamedElement = originalElement.toBuilder()
            .uuid("another uuid")
            .build();

        assertThat(originalElement.getUuid())
            .isNotEqualTo(renamedElement.getUuid());
    }

    @Test
    public void duplicatedElementViaBuilder_UuidShouldNotChange() {
        RbelElement originalElement = new RbelElement("fo".getBytes(), null);
        RbelElement renamedElement = originalElement.toBuilder()
            .build();

        assertThat(originalElement.getUuid())
            .isEqualTo(renamedElement.getUuid());
    }
}
