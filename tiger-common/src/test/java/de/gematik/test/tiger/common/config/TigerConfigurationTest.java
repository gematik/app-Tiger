/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.TextNode;
import de.gematik.test.tiger.common.data.config.CfgTemplate;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyType;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TigerConfigurationTest {

    @BeforeEach
    void setup() {
        System.clearProperty("NESTEDBEAN_BAR");
        System.clearProperty("NESTEDBEAN_FOO");
        TigerGlobalConfiguration.reset();
    }

    @Test
    void fillObjectShouldWork() throws Exception {
        withEnvironmentVariable("string", "stringValue")
            .and("integer", "1234")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class);
                assertThat(dummyBean)
                    .get()
                    .hasFieldOrPropertyWithValue("string", "stringValue")
                    .hasFieldOrPropertyWithValue("integer", 1234);
            });
    }

    @Test
    void fillNestedObjectShouldWork() throws Exception {
        withEnvironmentVariable("string", "stringValue")
            .and("integer", "1234")
            .and("nestedBean.foo", "schmar")
            .and("nestedBean.bar", "420")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean)
                    .hasFieldOrPropertyWithValue("string", "stringValue")
                    .hasFieldOrPropertyWithValue("integer", 1234);
                assertThat(dummyBean.getNestedBean())
                    .hasFieldOrPropertyWithValue("foo", "schmar")
                    .hasFieldOrPropertyWithValue("bar", 420);
            });
    }

    @Test
    void fillNestedObjectSnakeCaseShouldWork() throws Exception {
        withEnvironmentVariable("string", "stringValue")
            .and("integer", "1234")
            .and("NESTEDBEAN_FOO", "schmar")
            .and("nestedBean.bar", "420")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean)
                    .hasFieldOrPropertyWithValue("string", "stringValue")
                    .hasFieldOrPropertyWithValue("integer", 1234);
                assertThat(dummyBean.getNestedBean())
                    .hasFieldOrPropertyWithValue("foo", "schmar")
                    .hasFieldOrPropertyWithValue("bar", 420);
            });
    }

    @Test
    void systemEnvAndSystemPropertiesMixed() throws Exception {
        System.setProperty("string", "stringValue");
        System.setProperty("NESTEDBEAN_BAR", "420");
        withEnvironmentVariable("string", "wrongValue")
            .and("integer", "1234")
            .and("NESTEDBEAN_FOO", "schmar")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean)
                    .hasFieldOrPropertyWithValue("string", "stringValue")
                    .hasFieldOrPropertyWithValue("integer", 1234);
                assertThat(dummyBean.getNestedBean())
                    .hasFieldOrPropertyWithValue("foo", "schmar")
                    .hasFieldOrPropertyWithValue("bar", 420);
            });
    }

    @Test
    void injectIntoRecursiveStructure() throws Exception {
        withEnvironmentVariable("NESTEDBEAN_BAR", "4")
            .and("nestedbean.inner.bar", "42")
            .and("nestedbean.inner.inner.bar", "420")
            .and("NESTEDBEAN_FOO", "outer")
            .and("nestedbean.inner.foo", "medium")
            .and("nestedbean.inner.inner.foo", "inner")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getInner())
                    .hasFieldOrPropertyWithValue("bar", 42)
                    .hasFieldOrPropertyWithValue("foo", "medium");
                assertThat(dummyBean.getNestedBean().getInner().getInner())
                    .hasFieldOrPropertyWithValue("bar", 420)
                    .hasFieldOrPropertyWithValue("foo", "inner");
            });
    }

    @Test
    void injectIntoRecursiveWithMixedSourcesStructure() throws Exception {
        System.setProperty("NESTEDBEAN_BAR", "4");
        System.setProperty("nestedbean.inner.bar", "42");
        withEnvironmentVariable("string", "wrongValue")
            .and("NESTEDBEAN_FOO", "outer")
            .and("NESTEDBEAN_INNER_FOO", "medium")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getInner())
                    .hasFieldOrPropertyWithValue("bar", 42)
                    .hasFieldOrPropertyWithValue("foo", "medium");
            });
    }

    @Test
    void injectIntoRecursiveWithMixedSourcesStructureExtended() throws Exception {
        System.setProperty("NESTEDBEAN_BAR", "4");
        System.setProperty("nestedbean.inner.bar", "42");
        System.setProperty("nestedbean.inner.inner.bar", "420");
        withEnvironmentVariable("string", "wrongValue")
            .and("NESTEDBEAN_FOO", "outer")
            .and("NESTEDBEAN_INNER_FOO", "medium")
            .and("NESTEDBEAN_INNER_INNER_FOO", "inner")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getInner())
                    .hasFieldOrPropertyWithValue("bar", 42)
                    .hasFieldOrPropertyWithValue("foo", "medium");
                assertThat(dummyBean.getNestedBean().getInner().getInner())
                    .hasFieldOrPropertyWithValue("bar", 420)
                    .hasFieldOrPropertyWithValue("foo", "inner");
            });
    }

    @Test
    void propertiesAndEnvAndYamlCombined() throws Exception {
        System.setProperty("NESTEDBEAN_BAR", "4");
        System.setProperty("nestedbean.inner.bar", "42");
        System.setProperty("nestedbean.inner.inner.bar", "420");
        withEnvironmentVariable("string", "wrongValue")
            .and("NESTEDBEAN_INNER_INNER_FOO", "inner")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration
                    .readFromYaml(
                        "string: yamlOuterFoo\n" +
                            "inner:\n" +
                            "  foo: yamlMediumFoo\n" +
                            "  inner:\n" +
                            "    foo: yamlInnerFoo", "nestedBean");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getInner())
                    .hasFieldOrPropertyWithValue("bar", 42)
                    .hasFieldOrPropertyWithValue("foo", "yamlMediumFoo");
                assertThat(dummyBean.getNestedBean().getInner().getInner())
                    .hasFieldOrPropertyWithValue("bar", 420)
                    .hasFieldOrPropertyWithValue("foo", "inner");
            });
    }

    @Test
    void combinedReadIn_readAsStringValues() throws Exception {
        System.setProperty("NESTEDBEAN_BAR", "4");
        System.setProperty("nestedbean.inner.bar", "42");
        System.setProperty("nestedbean.inner.inner.bar", "420");
        withEnvironmentVariable("string", "wrongValue")
            .and("NESTEDBEAN_FOO", "outer")
            .and("NESTEDBEAN_INNER_FOO", "medium")
            .and("BOOLEAN_WITH1", "1")
            .and("BOOLEAN_WITHTRUE", "true")
            .and("BOOLEAN_WITH0", "0")
            .and("BOOLEAN_WITHFALSE", "false")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readFromYaml(
                    "string: yamlOuterFoo\n" +
                        "inner:\n" +
                        "  foo: yamlMediumFoo\n" +
                        "  inner:\n" +
                        "    foo: yamlInnerFoo", "nestedBean");
                assertThat(TigerGlobalConfiguration.readString("nestedBean.Inner.foo"))
                    .isEqualTo("medium");
                assertThat(TigerGlobalConfiguration.readString("nestedBean.Inner.inner.foo"))
                    .isEqualTo("yamlInnerFoo");
                assertThat(TigerGlobalConfiguration.readBoolean("boolean.with1"))
                    .isTrue();
                assertThat(TigerGlobalConfiguration.readBoolean("boolean.withtrue"))
                    .isTrue();
                assertThat(TigerGlobalConfiguration.readBoolean("boolean.with0"))
                    .isFalse();
                assertThat(TigerGlobalConfiguration.readBoolean("boolean.withfalse"))
                    .isFalse();
                assertThat(TigerGlobalConfiguration.readBooleanOptional("boolean.withtrue")).isPresent()
                    .contains(Boolean.TRUE);
                assertThat(TigerGlobalConfiguration.readBooleanOptional("boolean.withfalse")).isPresent()
                    .contains(Boolean.FALSE);
                assertThat(TigerGlobalConfiguration.readBooleanOptional("boolean.null")).isNotPresent();
                assertThat(TigerGlobalConfiguration.readBoolean("boolean.null", true)).isTrue();
                assertThat(TigerGlobalConfiguration.readBoolean("boolean.null", false)).isFalse();
            });
    }

    @Test
    void arrayMixedFromSources() throws Exception {
        withEnvironmentVariable("string", "wrongValue")
            .and("nestedBean.array.1.foo", "foo1")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readFromYaml(
                    "array:\n" +
                        "  - foo: nonFoo0\n" +
                        "  - foo: nonFoo1\n" +
                        "  - foo: nonFoo2", "nestedBean");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getArray())
                    .extracting("foo")
                    .containsExactly("nonFoo0", "foo1", "nonFoo2");
            });
    }

    @Test
    void map_keyShouldBeKeptWithCorrectCase() throws Exception {
        withEnvironmentVariable("MAP_SNAKECASE", "snakeFoo")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readFromYaml(
                    "map:\n" +
                        "  camelCase1: fooBar1\n" +
                        "  camelCase2: fooBar2\n" +
                        "  camelCase3: fooBar3");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(Map.class, "map")
                    .get();
                assertThat(dummyBean)
                    .containsOnlyKeys("camelCase1", "camelCase2", "camelCase3", "snakecase");
            });
    }

    @Test
    void overwriteWithEmptyValue_shouldWork() throws Exception {
        System.setProperty("NESTEDBEAN_FOO", "");
        withEnvironmentVariable("NESTEDBEAN_FOO", "nonEmptyValue")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean())
                    .hasFieldOrPropertyWithValue("foo", "");
            });
    }

    @SneakyThrows
    @Test
    void yamlWithTemplates_shouldLoad() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            "array:\n" +
                "  -\n" +
                "    template: templateWithList\n" +
                "    foo: fooYaml", "nestedBean");
        TigerGlobalConfiguration.readTemplates(
            FileUtils.readFileToString(new File("src/test/resources/exampleTemplates.yml"), StandardCharsets.UTF_8),
            "nestedBean.array");
        var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
            .get();
        assertThat(dummyBean.getNestedBean().getArray().get(0))
            .hasFieldOrPropertyWithValue("foo", "fooYaml")
            .hasFieldOrPropertyWithValue("inner", NestedBean.builder()
                .array(
                    List.of(NestedBean.builder().foo("templateEntry0").build(),
                        NestedBean.builder().foo("templateEntry1").build(),
                        NestedBean.builder().foo("templateEntry2").build())
                ).build());
    }

    @SneakyThrows
    @Test
    void overwriteTemplateList_shouldReplaceNotMerge() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            "array:\n" +
                "  -\n" +
                "    template: templateWithList\n" +
                "    foo: fooYaml\n" +
                "    inner:\n" +
                "      array:\n" +
                "        - foo: yamlEntry0\n" +
                "        - foo: yamlEntry1", "nestedBean");
        TigerGlobalConfiguration.readTemplates(
            FileUtils.readFileToString(new File("src/test/resources/exampleTemplates.yml"), StandardCharsets.UTF_8),
            "nestedBean.array");
        var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
            .get();
        assertThat(dummyBean.getNestedBean().getArray().get(0).getInner().getArray())
            .containsExactly(NestedBean.builder().foo("yamlEntry0").build(),
                NestedBean.builder().foo("yamlEntry1").build());
    }

    @SneakyThrows
    @Test
    void overwriteTemplateListFromYamlAndEnv_shouldReplaceNotMerge() {
        withEnvironmentVariable("nestedBean.array.0.inner.array.0.foo", "envEntry0")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readFromYaml(
                    "array:\n" +
                        "  -\n" +
                        "    template: templateWithList\n" +
                        "    foo: fooYaml\n" +
                        "    inner:\n" +
                        "      array:\n" +
                        "        - foo: yamlEntry0\n" +
                        "        - foo: yamlEntry1", "nestedBean");
                TigerGlobalConfiguration.readTemplates(
                    FileUtils.readFileToString(new File("src/test/resources/exampleTemplates.yml"),
                        StandardCharsets.UTF_8),
                    "nestedBean.array");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getArray().get(0).getInner().getArray())
                    .containsOnly(NestedBean.builder().foo("envEntry0").build(),
                        NestedBean.builder().foo("yamlEntry1").build());
            });
    }

    @SneakyThrows
    @Test
    void addTemplateInEnv_shouldNotReplaceYamlEntries() {
        withEnvironmentVariable("nestedBean.array.0.inner.array.0.foo", "envEntry0")
            .and("nestedBean.array.0.template", "templateWithList")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readFromYaml(
                    "array:\n" +
                        "  -\n" +
                        "    foo: fooYaml\n" +
                        "    inner:\n" +
                        "      array:\n" +
                        "        - foo: yamlEntry0\n" +
                        "        - foo: yamlEntry1", "nestedBean");
                TigerGlobalConfiguration.readTemplates(
                    FileUtils.readFileToString(new File("src/test/resources/exampleTemplates.yml"),
                        StandardCharsets.UTF_8),
                    "nestedBean.array");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getArray().get(0).getInner().getArray())
                    .containsOnly(NestedBean.builder().foo("envEntry0").build(),
                        NestedBean.builder().foo("yamlEntry1").build());
            });
    }

    @SneakyThrows
    @Test
    void applyTemplateInEnv_shouldSuccesfullyReplaceTemplateList() {
        withEnvironmentVariable("nestedBean.array.0.inner.array.0.foo", "envEntry0")
            .and("nestedBean.array.0.template", "templateWithList")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readFromYaml(
                    "array:\n" +
                        "  -\n" +
                        "    foo: fooYaml", "nestedBean");
                TigerGlobalConfiguration.readTemplates(
                    FileUtils.readFileToString(new File("src/test/resources/exampleTemplates.yml"),
                        StandardCharsets.UTF_8),
                    "nestedBean.array");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getArray().get(0).getInner().getArray())
                    .containsOnly(NestedBean.builder().foo("envEntry0").build());
            });
    }

    @Test
    void fillGenericObjectShouldWork() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            "users:\n" +
                "  -\n" +
                "     username: admin\n" +
                "     password: admin1234\n" +
                "     roles:\n" +
                "           - READ\n" +
                "           - WRITE\n" +
                "           - VIEW\n" +
                "           - DELETE\n" +
                "  -\n" +
                "     username: guest\n" +
                "     password: guest1234\n" +
                "     roles:\n" +
                "        - VIEW\n");
        final List<Users> usersList = TigerGlobalConfiguration.instantiateConfigurationBean(new TypeReference<>() {
        }, "users");
        assertThat(usersList).isNotEmpty();
        assertThat(usersList).hasSize(2);
        assertThat(usersList).containsOnly(Users.builder().username("admin").password("admin1234").roles(List.of("READ", "WRITE", "VIEW", "DELETE")).build(),
            Users.builder().username("guest").password("guest1234").roles(List.of("VIEW")).build());
    }

    @Test
    void shouldParseJava8Date() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml("users.blub: 2007-12-24T18:21Z");
        final Users users = TigerGlobalConfiguration.instantiateConfigurationBean(Users.class, "users").get();
        assertThat(users.blub)
            .isEqualTo("2007-12-24");
    }

    /**
     * ${ENV => GlobalConfigurationHelper.getString() ${json-unit.ignore} => interessiert dann folglich nicht ${VAR.foobar} =>
     * GlobalConfigurationHelper.getSourceByName("VAR").getString()
     * <p>
     * ${ENV.foo.bar} ${ENV.FOO_BAR} FOO_GITHUBBAR => foo.githubBar
     * <p>
     * FOO{ private String githubBar; }
     */
    @SneakyThrows
    @Test
    void replacePlaceholdersInValuesDuringReadIn() {
        withEnvironmentVariable("myEnvVar", "valueToBeAsserted")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readFromYaml(
                    "foo: ${myEnvVar}", "nestedBean");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                    .get();
                assertThat(dummyBean.getNestedBean().getFoo())
                    .isEqualTo("valueToBeAsserted");
            });
    }

    @SneakyThrows
    @Test
    void placeNewValue_shouldFindValueAgain() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("foo.value", "bar");
        assertThat(TigerGlobalConfiguration.readString("foo.value"))
            .isEqualTo("bar");
    }

    @SneakyThrows
    @Test
    void placeIntegerValue_shouldFindValueAgain() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("foo.value", 42);
        assertThat(TigerGlobalConfiguration.readString("foo.value"))
            .isEqualTo("42");
    }

    @SneakyThrows
    @Test
    void placeLongValue_shouldFindValueAgain() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("foo.value", Double.MAX_VALUE);
        assertThat(TigerGlobalConfiguration.readString("foo.value"))
            .isEqualTo(Double.toString(Double.MAX_VALUE));
    }

    @SneakyThrows
    @Test
    void placeBooleanValue_shouldFindValueAgain() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("foo.value", true);
        assertThat(TigerGlobalConfiguration.readString("foo.value"))
            .isEqualTo("true");
    }

    @SneakyThrows
    @Test
    void placeDoubleValue_shouldFindValueAgain() {
        TigerGlobalConfiguration.reset();
        final double value = 0.0432893401304;
        TigerGlobalConfiguration.putValue("foo.value", value);
        assertThat(TigerGlobalConfiguration.readString("foo.value"))
            .isEqualTo(Double.toString(value));
    }

    @SneakyThrows
    @Test
    void placeNewStructuredValue_shouldFindNestedValueAgain() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("foo.value", TigerConfigurationTest.NestedBean.builder()
            .bar(42)
            .build());
        assertThat(TigerGlobalConfiguration.readString("foo.value.bar"))
            .isEqualTo("42");
    }

    @SneakyThrows
    @Test
    @DisplayName("I place a new value in the thread local store. A different thread should NOT see the value")
    void placeNewValueThreadLocal_differentThreadShouldNotFindValueAgain() {
        TigerGlobalConfiguration.reset();
        final Thread thread =
            new Thread(() -> {
                TigerGlobalConfiguration.putValue("foo.value", "bar", SourceType.THREAD_CONTEXT);

                assertThat(TigerGlobalConfiguration.readString("foo.value"))
                    .isEqualTo("bar");
            });
        thread.start();
        thread.join();

        assertThat(TigerGlobalConfiguration.readStringOptional("foo.value"))
            .isEmpty();
    }

    @SneakyThrows
    @Test
    void readWithTigerConfiguration() {
        TigerGlobalConfiguration.readFromYaml(
            FileUtils.readFileToString(
                new File("../tiger-testenv-mgr/src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml")),
            "tiger");
        assertThat(TigerGlobalConfiguration.instantiateConfigurationBean(TestCfg.class, "tiger"))
            .get()
            .extracting(TestCfg::getTemplates)
            .asList()
            .extracting("templateName")
            .contains("idp-ref", "idp-rise-ru", "idp-rise-tu", "epa2", "epa2-fdv");
    }

    @SneakyThrows
    @Test
    void readNullObject() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("no.real.other", "foo");
        assertThat(TigerGlobalConfiguration.instantiateConfigurationBean(
            DummyBean.class, "no.real.key.to.see"))
            .isEmpty();
    }

    @Test
    void testFreeSockets() throws IOException {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration
            .readFromYaml(
                "integer: ${free.port.224}\n");
        var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
            .get();
        final ServerSocket actual = new ServerSocket(dummyBean.getInteger());
        assertThat(actual).isNotNull();
        assertThat(actual.getLocalPort()).isNotZero();
    }

    @Test
    void readValueWithPlaceholder() throws Exception {
        withEnvironmentVariable("give.me.foo", "foo")
            .and("foo.int", "1234")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                assertThat(TigerGlobalConfiguration.readString("${give.me.foo}.int"))
                    .isEqualTo("1234");
            });
    }

    @Test
    void localScopedValues() {
        TigerGlobalConfiguration.reset();
        assertThat(TigerGlobalConfiguration.readStringOptional("secret.key"))
            .isEmpty();

        TigerGlobalConfiguration.localScope()
            .withValue("secret.key", "secretValue")
            .execute(() -> assertThat(TigerGlobalConfiguration.readString("secret.key"))
                .isEqualTo("secretValue"));

        assertThat(TigerGlobalConfiguration.readStringOptional("secret.key"))
            .isEmpty();
    }

    @Test
    void placeholdersShouldBeImplicitlyResolved() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("my.key", "1234");
        TigerGlobalConfiguration.putValue("an.integer", "${my.key}");

        assertThat(TigerGlobalConfiguration.readIntegerOptional("an.integer"))
            .get()
            .isEqualTo(1234);
        assertThat(TigerGlobalConfiguration.readString("an.integer"))
            .isEqualTo("1234");
        assertThat(TigerGlobalConfiguration.readString("an.integer", "fallback-unused"))
            .isEqualTo("1234");
        assertThat(TigerGlobalConfiguration.readString("wrong.key", "${my.key}"))
            .isEqualTo("1234");
        assertThat(TigerGlobalConfiguration.readStringOptional("an.integer"))
            .get()
            .isEqualTo("1234");
    }

    // Tests from removed OSEnvironment class, expects env with at least one entry
    @Test
    void testGetEnvAsStringPathOk() {
        assertThat(TigerGlobalConfiguration.readString(System.getenv().keySet().iterator().next())).isNotBlank();
    }

    @Test
    void testGetEnvAsStringNotExistingWithDefaultOk() {
        assertThat(TigerGlobalConfiguration.readString("_______NOT____EXISTS", "DEFAULT")).isEqualTo("DEFAULT");
    }

    @Test
    void testGetEnvAsStringExistingNotDefaultOk() {
        assertThat(TigerGlobalConfiguration.readString(System.getenv().keySet().iterator().next(), "_________DEFAULT")).isNotEqualTo("_________DEFAULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "HTTP", "http", "hTtP"
    })
    void tigerProxyTypeShouldBeParseableWithUpperAndLowerCase(String proxyTypeValue) {
        final String key = "random.proxy.type.key";
        TigerGlobalConfiguration.putValue(key, proxyTypeValue);
        assertThat(TigerGlobalConfiguration.instantiateConfigurationBean(TigerProxyType.class, key))
            .get()
            .isEqualTo(TigerProxyType.HTTP);
    }

    @Test
    void registerCustomObjectMapper_shouldBeUsed() {
        try {
            TigerGlobalConfiguration.getObjectMapper()
                .registerModule(new SimpleModule()
                    .addDeserializer(DummyBean.class, new StdDeserializer<>(DummyBean.class) {
                        @Override
                        public DummyBean deserialize(JsonParser p, DeserializationContext ctxt)
                            throws IOException {
                            final TextNode node = (TextNode) p.readValueAsTree().get("frick");
                            return DummyBean.builder()
                                .string(node.asText())
                                .build();
                        }
                    }));

            TigerGlobalConfiguration.readFromYaml("frick: 'on a stick'");
            var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
                .get();
            assertThat(dummyBean.getString())
                .isEqualTo("on a stick");
        } finally {
            TigerGlobalConfiguration.reset();
        }
    }

    @Test
    void yamlWithoutNewLineSeperation_shouldSplitKeysCorrectly() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            "map.camelCase1: fooBar");
        assertThat(TigerGlobalConfiguration.readString("map.camelCase1"))
            .isEqualTo("fooBar");
    }

    @Test
    void unresolveablePrimitives_shouldBeIgnored() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            "integer: '123${this.value.does.not.exist}'\n"
                + "b: ${this.value.does.not.exist}\n"
                + "c: ${this.value.does.not.exist}\n"
                + "d: ${this.value.does.not.exist}\n"
                + "l: ${this.value.does.not.exist}\n"
                + "s: ${this.value.does.not.exist}\n"
                + "by: ${this.value.does.not.exist}\n"
                + "f: ${this.value.does.not.exist}\n"
                + "objectInt: ${this.value.does.not.exist}\n"
                + "nestedBean.bar: ${this.value.does.not.exist}");
        var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class)
            .get();
        assertThat(dummyBean.getInteger()).isEqualTo(-1);
        assertThat(dummyBean.isB()).isFalse();
        assertThat(dummyBean.getC()).isEqualTo(' ');
        assertThat(dummyBean.getD()).isEqualTo(-1.0);
        assertThat(dummyBean.getF()).isEqualTo(-1.0f);
        assertThat(dummyBean.getL()).isEqualTo(-1l);
        assertThat(dummyBean.getBy()).isEqualTo((byte) -1);
        assertThat(dummyBean.getS()).isEqualTo((short) -1);
        assertThat(dummyBean.getObjectInt()).isNull();
        assertThat(dummyBean.getNestedBean().getBar()).isEqualTo(-1);
    }

    @SneakyThrows
    @Test
    void testReadMapWithSubKey() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            "key:\n"
                + "  select:\n"
                + "    aKey: aValue\n"
                + "    bKey: bValue");
        var map = TigerGlobalConfiguration.readMap("key.select");
        assertThat(map).containsAllEntriesOf(Map.of(
            "akey", "aValue",
            "bkey", "bValue"));
    }

    @SneakyThrows
    @Test
    void testReadMapWithoutSubKey() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.readFromYaml(
            "key:\n"
                + "  select:\n"
                + "    aKey: aValue\n"
                + "    bKey: bValue");
        var map = TigerGlobalConfiguration.readMap();
        assertThat(map).containsAllEntriesOf(Map.of(
            "key.select.akey", "aValue",
            "key.select.bkey", "bValue"));
    }

    @SneakyThrows
    @Test
    void hostnamePropertyShouldBePresent() {
        TigerGlobalConfiguration.reset();
        assertThat(TigerGlobalConfiguration.readString("hostname"))
            .isEqualTo(TigerGlobalConfiguration.getComputerName());
    }

    @Data
    @Builder
    public static class DummyBean {

        private String string;
        private int integer;
        private boolean b;
        private char c;
        private double d;
        private float f;
        private long l;
        private short s;
        private byte by;
        private Integer objectInt;
        private NestedBean nestedBean;
    }

    @Data
    @Builder
    public static class NestedBean {

        private final String foo;
        private final int bar;
        private final NestedBean inner;
        private final String template;
        @JsonProperty
        private List<NestedBean> array;
    }

    @Data
    public static class TestCfg {

        @JsonProperty
        private List<CfgTemplate> templates;

    }

    @Data
    @Builder
    public static class Users {

        private String username;
        private String password;
        private LocalDate blub;
        private List<String> roles;
    }
}
