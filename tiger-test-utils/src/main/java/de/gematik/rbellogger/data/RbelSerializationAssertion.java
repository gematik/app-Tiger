package de.gematik.rbellogger.data;

import de.gematik.rbellogger.writer.RbelContentType;
import org.apache.commons.lang3.NotImplementedException;
import org.assertj.core.api.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.xmlunit.assertj.XmlAssert;

public class RbelSerializationAssertion extends AbstractAssert<RbelSerializationAssertion, String> {

    public RbelSerializationAssertion(String actualSerialization) {
        super(actualSerialization, RbelSerializationAssertion.class);
    }

    public static void assertEquals(String expectedSerialization, String actualSerialization, RbelContentType contentType) {
        switch (contentType) {
            case XML -> XmlAssert.assertThat(actualSerialization)
                    .and(expectedSerialization)
                    .ignoreWhitespace()
                    .areIdentical();
            case JSON -> JSONAssert.assertEquals(expectedSerialization, actualSerialization, JSONCompareMode.STRICT);
            default -> throw new NotImplementedException("RbelContentType '%s' is not implemented for asserting serialization results.".formatted(contentType.toString()));
        }
    }
}
