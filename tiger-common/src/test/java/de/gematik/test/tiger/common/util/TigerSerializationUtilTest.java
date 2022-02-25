/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.util;

import de.gematik.test.tiger.common.config.TigerConfigurationTest.DummyBean;
import de.gematik.test.tiger.common.config.TigerConfigurationTest.NestedBean;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static de.gematik.test.tiger.common.util.TigerSerializationUtil.toMap;
import static de.gematik.test.tiger.common.util.TigerSerializationUtil.yamlToJsonObject;
import static org.assertj.core.api.Assertions.assertThat;

public class TigerSerializationUtilTest {

    @Test
    public void yamlToJsonObjectNestedTest() {
        final JSONObject jsonObject = yamlToJsonObject("foo:\n" +
            "  bar: someString\n" +
            "  list:\n" +
            "    - entry\n" +
            "    - schmentry\n" +
            "    - nested:\n" +
            "        entry: withValue\n" +
            "  object:\n" +
            "    with:\n" +
            "      some:\n" +
            "        depth: \"to it\"");

        assertThat(jsonObject.getJSONObject("foo").getString("bar")).isEqualTo("someString");
        assertThat(jsonObject.getJSONObject("foo")
            .getJSONObject("object")
            .getJSONObject("with")
            .getJSONObject("some")
            .getString("depth")).isEqualTo("to it");
        assertThat(jsonObject.getJSONObject("foo")
            .getJSONArray("list")
            .getJSONObject(2)
            .getJSONObject("nested")
            .getString("entry")).isEqualTo("withValue");
    }

    @Test
    public void yamlToMapTest() {
        assertThat(toMap(DummyBean.builder()
            .integer(42)
            .string("stringValue")
            .nestedBean(NestedBean.builder()
                .foo("nestedFoo")
                .inner(NestedBean.builder()
                    .foo("nestedInnerFoo")
                    .build())
                .build())
            .build(), "dummy"))
            .containsEntry("dummy.integer", "42")
            .containsEntry("dummy.string", "stringValue")
            .containsEntry("dummy.nestedbean.foo", "nestedFoo")
            .containsEntry("dummy.nestedbean.inner.foo", "nestedInnerFoo");
    }
}