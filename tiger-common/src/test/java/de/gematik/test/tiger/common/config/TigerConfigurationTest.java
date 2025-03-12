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

package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.TextNode;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyType;
import de.gematik.test.tiger.zion.config.TigerSkipEvaluation;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@Slf4j
public class TigerConfigurationTest { // NOSONAR

  @BeforeEach
  @AfterEach
  void setup() {
    System.clearProperty("NESTEDBEAN_BAR");
    System.clearProperty("NESTEDBEAN_FOO");
    System.clearProperty("nestedbean.inner.bar");
    System.clearProperty("string");
    TigerGlobalConfiguration.reset();
  }

  @Test
  void fillObjectShouldWork() throws Exception {
    new EnvironmentVariables("string", "stringValue")
        .and("integer", "1234")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class);
              assertThat(dummyBean)
                  .get()
                  .hasFieldOrPropertyWithValue("string", "stringValue")
                  .hasFieldOrPropertyWithValue("integer", 1234);
            });
  }

  @Test
  void fillNestedObjectShouldWork() throws Exception {
    new EnvironmentVariables("string", "stringValue")
        .and("integer", "1234")
        .and("nestedBean.foo", "schmar")
        .and("nestedBean.bar", "420")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
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
    new EnvironmentVariables("string", "stringValue")
        .and("integer", "1234")
        .and("NESTEDBEAN_FOO", "schmar")
        .and("nestedBean.bar", "420")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
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
    new EnvironmentVariables("string", "wrongValue")
        .and("integer", "1234")
        .and("NESTEDBEAN_FOO", "schmar")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
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
    new EnvironmentVariables("NESTEDBEAN_BAR", "4")
        .and("nestedbean.inner.bar", "42")
        .and("nestedbean.inner.inner.bar", "420")
        .and("NESTEDBEAN_FOO", "outer")
        .and("nestedbean.inner.foo", "medium")
        .and("nestedbean.inner.inner.foo", "inner")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
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
    new EnvironmentVariables("string", "wrongValue")
        .and("NESTEDBEAN_FOO", "outer")
        .and("NESTEDBEAN_INNER_FOO", "medium")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
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
    new EnvironmentVariables("string", "wrongValue")
        .and("NESTEDBEAN_FOO", "outer")
        .and("NESTEDBEAN_INNER_FOO", "medium")
        .and("NESTEDBEAN_INNER_INNER_FOO", "inner")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
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
    new EnvironmentVariables("string", "wrongValue")
        .and("NESTEDBEAN_INNER_INNER_FOO", "inner")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              TigerGlobalConfiguration.readFromYaml(
                  """
                              string: yamlOuterFoo
                              inner:
                                foo: yamlMediumFoo
                                inner:
                                  foo: yamlInnerFoo""",
                  "nestedBean");
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
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
    new EnvironmentVariables("string", "wrongValue")
        .and("NESTEDBEAN_FOO", "outer")
        .and("NESTEDBEAN_INNER_FOO", "medium")
        .and("BOOLEAN_WITH1", "1")
        .and("BOOLEAN_WITHTRUE", "true")
        .and("BOOLEAN_WITH0", "0")
        .and("BOOLEAN_WITHFALSE", "false")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              TigerGlobalConfiguration.readFromYaml(
                  """
                              string: yamlOuterFoo
                              inner:
                                foo: yamlMediumFoo
                                inner:
                                  foo: yamlInnerFoo""",
                  "nestedBean");
              assertThat(TigerGlobalConfiguration.readString("nestedBean.Inner.foo"))
                  .isEqualTo("medium");
              assertThat(TigerGlobalConfiguration.readString("nestedBean.Inner.inner.foo"))
                  .isEqualTo("yamlInnerFoo");
              assertThat(TigerGlobalConfiguration.readBoolean("boolean.with1")).isTrue();
              assertThat(TigerGlobalConfiguration.readBoolean("boolean.withtrue")).isTrue();
              assertThat(TigerGlobalConfiguration.readBoolean("boolean.with0")).isFalse();
              assertThat(TigerGlobalConfiguration.readBoolean("boolean.withfalse")).isFalse();
              assertThat(TigerGlobalConfiguration.readBooleanOptional("boolean.withtrue"))
                  .isPresent()
                  .contains(Boolean.TRUE);
              assertThat(TigerGlobalConfiguration.readBooleanOptional("boolean.withfalse"))
                  .isPresent()
                  .contains(Boolean.FALSE);
              assertThat(TigerGlobalConfiguration.readBooleanOptional("boolean.null"))
                  .isNotPresent();
              assertThat(TigerGlobalConfiguration.readBoolean("boolean.null", true)).isTrue();
              assertThat(TigerGlobalConfiguration.readBoolean("boolean.null", false)).isFalse();
            });
  }

  @Test
  void arrayMixedFromSources() throws Exception {
    new EnvironmentVariables("string", "wrongValue")
        .and("nestedBean.list.1.foo", "foo1")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              TigerGlobalConfiguration.readFromYaml(
                  """
                              list:
                                - foo: nonFoo0
                                - foo: nonFoo1
                                - foo: nonFoo2""",
                  "nestedBean");
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
              System.out.println(new ObjectMapper().writeValueAsString(dummyBean));
              assertThat(dummyBean.getNestedBean().getList())
                  .extracting("foo")
                  .containsExactly("nonFoo0", "foo1", "nonFoo2");
            });
  }

  @Test
  void map_keyShouldBeKeptWithCorrectCase() throws Exception {
    new EnvironmentVariables("MAP_SNAKECASE", "snakeFoo")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              TigerGlobalConfiguration.readFromYaml(
                  """
                              map:
                                camelCase1: fooBar1
                                camelCase2: fooBar2
                                camelCase3: fooBar3""");
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(Map.class, "map").get();
              assertThat(dummyBean)
                  .containsOnlyKeys("camelCase1", "camelCase2", "camelCase3", "snakecase");
            });
  }

  @Test
  void readAList() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml(
        """
      some.key:
        and.then:
          some:
            - fooBar1
            - fooBar2
            - fooBar3
      """);
    assertThat(TigerGlobalConfiguration.readList("some.key.and", "then", "some"))
        .containsExactly("fooBar1", "fooBar2", "fooBar3");
  }

  @Test
  void overwriteWithEmptyValue_shouldWork() throws Exception {
    System.setProperty("NESTEDBEAN_FOO", "");
    new EnvironmentVariables("NESTEDBEAN_FOO", "nonEmptyValue")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
              assertThat(dummyBean.getNestedBean()).hasFieldOrPropertyWithValue("foo", "");
            });
  }

  @Test
  void fillGenericObjectShouldWork() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml(
        """
                    users:
                      -
                         username: admin
                         password: admin1234
                         roles:
                               - READ
                               - WRITE
                               - VIEW
                               - DELETE
                      -
                         username: guest
                         password: guest1234
                         roles:
                            - VIEW
                    """);
    final List<Users> usersList =
        TigerGlobalConfiguration.instantiateConfigurationBean(new TypeReference<>() {}, "users");
    assertThat(usersList)
        .isNotEmpty()
        .hasSize(2)
        .containsOnly(
            Users.builder()
                .username("admin")
                .password("admin1234")
                .roles(List.of("READ", "WRITE", "VIEW", "DELETE"))
                .build(),
            Users.builder().username("guest").password("guest1234").roles(List.of("VIEW")).build());
  }

  @Test
  void skipEvaluation_shouldWork() {
    TigerGlobalConfiguration.reset();
    final String jexlExpression = "!{'jo'=='ja'}";
    TigerGlobalConfiguration.readFromYaml(
        """
        blub:
            skipString: "!{'jo'=='ja'}"
            directString: "!{'jo'=='ja'}"
            skipList:
            - "!{'jo'=='ja'}"
            directList:
            - "!{'jo'=='ja'}"
            skipMap:
                entry: "!{'jo'=='ja'}"
            directMap:
                entry: "!{'jo'=='ja'}"
        """);
    var bean =
        TigerGlobalConfiguration.instantiateConfigurationBean(
                EvaluationSkippingTestClass.class, "blub")
            .get();
    assertThat(bean.getSkipString()).isEqualTo(jexlExpression);
    assertThat(bean.getDirectString()).isEqualTo("false");

    assertThat(bean.getSkipList().get(0)).isEqualTo(jexlExpression);
    assertThat(bean.getDirectList().get(0)).isEqualTo("false");

    assertThat(bean.getSkipMap().get("entry")).isEqualTo(jexlExpression);
    assertThat(bean.getDirectMap().get("entry")).isEqualTo("false");
  }

  @Test
  void shouldParseJava8Date() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml("users.blub: 2007-12-24T18:21Z");
    final Users users =
        TigerGlobalConfiguration.instantiateConfigurationBean(Users.class, "users").get();
    assertThat(users.blub).isEqualTo("2007-12-24");
  }

  /**
   * ${ENV => GlobalConfigurationHelper.getString() ${json-unit.ignore} => interessiert dann
   * folglich nicht ${VAR.foobar} => GlobalConfigurationHelper.getSourceByName("VAR").getString()
   *
   * <p>${ENV.foo.bar} ${ENV.FOO_BAR} FOO_GITHUBBAR => foo.githubBar
   *
   * <p>FOO{ private String githubBar; }
   */
  @SneakyThrows
  @Test
  void replacePlaceholdersInValuesDuringReadIn() {
    new EnvironmentVariables("myEnvVar", "valueToBeAsserted")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              TigerGlobalConfiguration.readFromYaml("foo: ${myEnvVar}", "nestedBean");
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
              assertThat(dummyBean.getNestedBean().getFoo()).isEqualTo("valueToBeAsserted");
            });
  }

  @SneakyThrows
  @Test
  void placeNewValue_shouldFindValueAgain() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.putValue("foo.value", "bar");
    assertThat(TigerGlobalConfiguration.readString("foo.value")).isEqualTo("bar");
  }

  @SneakyThrows
  @Test
  void placeIntegerValue_shouldFindValueAgain() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.putValue("foo.value", 42);
    assertThat(TigerGlobalConfiguration.readString("foo.value")).isEqualTo("42");
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
    assertThat(TigerGlobalConfiguration.readString("foo.value")).isEqualTo("true");
  }

  @SneakyThrows
  @Test
  void placeDoubleValue_shouldFindValueAgain() {
    TigerGlobalConfiguration.reset();
    final double value = 0.0432893401304;
    TigerGlobalConfiguration.putValue("foo.value", value);
    assertThat(TigerGlobalConfiguration.readString("foo.value")).isEqualTo(Double.toString(value));
  }

  @SneakyThrows
  @Test
  void placeNewStructuredValue_shouldFindNestedValueAgain() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.putValue(
        "foo.value",
        NestedBean.builder()
            .bar(42)
            .list(List.of(NestedBean.builder().foo("someValueInAList").build()))
            .build());
    assertThat(TigerGlobalConfiguration.readString("foo.value.bar")).isEqualTo("42");
    assertThat(TigerGlobalConfiguration.readString("foo.value.list.0.foo"))
        .isEqualTo("someValueInAList");
  }

  @SneakyThrows
  @Test
  void placeNewStructuredValueWithScope_shouldFindNestedValueAgain() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.putValue(
        "foo.value",
        TigerConfigurationTest.NestedBean.builder().bar(42).build(),
        ConfigurationValuePrecedence.RUNTIME_EXPORT);
    assertThat(TigerGlobalConfiguration.readString("foo.value.bar")).isEqualTo("42");
  }

  @SneakyThrows
  @Test
  void overwriteStructuredValueWithScope_shouldFindNestedValueAgain() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.putValue(
        "foo.value",
        TigerConfigurationTest.NestedBean.builder().bar(42).build(),
        ConfigurationValuePrecedence.RUNTIME_EXPORT);
    TigerGlobalConfiguration.putValue(
        "foo.value.bar", "schmoo", ConfigurationValuePrecedence.RUNTIME_EXPORT);
    assertThat(TigerGlobalConfiguration.readString("foo.value.bar")).isEqualTo("schmoo");
  }

  @SneakyThrows
  @Test
  void readNullObject() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.putValue("no.real.other", "foo");
    assertThat(
            TigerGlobalConfiguration.instantiateConfigurationBean(
                DummyBean.class, "no.real.key.to.see"))
        .isEmpty();
  }

  @Test
  void testFreeSockets() throws IOException {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml("integer: ${free.port.224}\n");
    var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
    final ServerSocket actual = new ServerSocket(dummyBean.getInteger());
    assertThat(actual).isNotNull();
    assertThat(actual.getLocalPort()).isNotZero();
  }

  @Test
  void readValueWithPlaceholder() throws Exception {
    new EnvironmentVariables("give.me.foo", "foo")
        .and("foo.int", "1234")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              assertThat(TigerGlobalConfiguration.readString("${give.me.foo}.int"))
                  .isEqualTo("1234");
            });
  }

  @Test
  void placeholdersShouldBeImplicitlyResolved() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.putValue("my.key", "1234");
    TigerGlobalConfiguration.putValue("an.integer", "${my.key}");

    assertThat(TigerGlobalConfiguration.readIntegerOptional("an.integer")).get().isEqualTo(1234);
    assertThat(TigerGlobalConfiguration.readString("an.integer")).isEqualTo("1234");
    assertThat(TigerGlobalConfiguration.readString("an.integer", "fallback-unused"))
        .isEqualTo("1234");
    assertThat(TigerGlobalConfiguration.readString("wrong.key", "${my.key}")).isEqualTo("1234");
    assertThat(TigerGlobalConfiguration.readStringOptional("an.integer")).get().isEqualTo("1234");
  }

  // Tests from removed OSEnvironment class, expects env with at least one entry
  @Test
  void testGetEnvAsStringPathOk() {
    assertThat(TigerGlobalConfiguration.readString(System.getenv().keySet().iterator().next()))
        .isNotBlank();
  }

  @Test
  void setNullValue_ShouldFail() {
    assertThatThrownBy(() -> TigerGlobalConfiguration.putValue("my.key", null))
        .isInstanceOf(TigerConfigurationException.class);
    assertThatThrownBy(() -> TigerGlobalConfiguration.putValue("my.key", (Integer) null))
        .isInstanceOf(TigerConfigurationException.class);
  }

  @Test
  void testGetEnvAsStringNotExistingWithDefaultOk() {
    assertThat(TigerGlobalConfiguration.readString("_______NOT____EXISTS", "DEFAULT"))
        .isEqualTo("DEFAULT");
  }

  @Test
  void testGetEnvAsStringExistingNotDefaultOk() {
    assertThat(
            TigerGlobalConfiguration.readString(
                System.getenv().keySet().iterator().next(), "_________DEFAULT"))
        .isNotEqualTo("_________DEFAULT");
  }

  @ParameterizedTest
  @ValueSource(strings = {"HTTP", "http", "hTtP"})
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
          .registerModule(
              new SimpleModule()
                  .addDeserializer(
                      DummyBean.class,
                      new StdDeserializer<>(DummyBean.class) {
                        @Override
                        public DummyBean deserialize(JsonParser p, DeserializationContext ctxt)
                            throws IOException {
                          final TextNode node = (TextNode) p.readValueAsTree().get("frick");
                          return DummyBean.builder().string(node.asText()).build();
                        }
                      }));

      TigerGlobalConfiguration.readFromYaml("frick: 'on a stick'");
      var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
      assertThat(dummyBean.getString()).isEqualTo("on a stick");
    } finally {
      TigerGlobalConfiguration.reset();
    }
  }

  @Test
  void yamlWithoutNewLineSeperation_shouldSplitKeysCorrectly() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml("map.camelCase1: fooBar");
    assertThat(TigerGlobalConfiguration.readString("map.camelCase1")).isEqualTo("fooBar");
  }

  @Test
  void unresolveablePrimitives_shouldBeIgnored() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml(
        """
                    integer: '123${this.value.does.not.exist}'
                    b: ${this.value.does.not.exist}
                    c: ${this.value.does.not.exist}
                    d: ${this.value.does.not.exist}
                    l: ${this.value.does.not.exist}
                    s: ${this.value.does.not.exist}
                    by: ${this.value.does.not.exist}
                    f: ${this.value.does.not.exist}
                    objectInt: ${this.value.does.not.exist}
                    nestedBean.bar: ${this.value.does.not.exist}""");
    var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
    assertThat(dummyBean.getInteger()).isEqualTo(-1);
    assertThat(dummyBean.isB()).isFalse();
    assertThat(dummyBean.getC()).isEqualTo(' ');
    assertThat(dummyBean.getD()).isEqualTo(-1.0);
    assertThat(dummyBean.getF()).isEqualTo(-1.0f);
    assertThat(dummyBean.getL()).isEqualTo(-1L);
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
        """
                    key:
                      select:
                        aKey: aValue
                        bKey: bValue""");
    var map = TigerGlobalConfiguration.readMap("key.select");
    assertThat(map)
        .containsAllEntriesOf(
            Map.of(
                "akey", "aValue",
                "bkey", "bValue"));
  }

  @SneakyThrows
  @Test
  void testMergeMaps() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml("{aKey: foo, cKey: baz}");
    TigerGlobalConfiguration.readFromYaml("{bKey: bar, cKey: notBaz}");
    assertThat(TigerGlobalConfiguration.readString("aKey")).isEqualTo("foo");
    assertThat(TigerGlobalConfiguration.readString("bKey")).isEqualTo("bar");
    assertThat(TigerGlobalConfiguration.readString("cKey")).isEqualTo("notBaz");
  }

  @SneakyThrows
  @Test
  void testReadMapWithoutSubKey() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml(
        """
                    key:
                      select:
                        aKey: aValue
                        bKey: bValue""");
    var map = TigerGlobalConfiguration.readMap();
    assertThat(map)
        .containsAllEntriesOf(
            Map.of(
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

  @SneakyThrows
  @Test
  void duplicateSystemPropertyKeys_shouldLeadToStartupError() {
    try {
      System.setProperty("foobar", "123");
      System.setProperty("FOOBAR", "312");
      TigerGlobalConfiguration.reset();
      assertThatThrownBy(() -> TigerGlobalConfiguration.readString("foobar"))
          .isInstanceOf(TigerConfigurationException.class);
    } finally {
      System.clearProperty("foobar");
      System.clearProperty("FOOBAR");
    }
  }

  @SneakyThrows
  @Test
  void duplicateSystemPropertyKeysWithSameValue_shouldProceed() {
    try {
      System.setProperty("foobar", "123");
      System.setProperty("FOOBAR", "123");
      TigerGlobalConfiguration.reset();
      assertThat(TigerGlobalConfiguration.readString("foobar")).isEqualTo("123");
    } finally {
      System.clearProperty("foobar");
      System.clearProperty("FOOBAR");
    }
  }

  @SneakyThrows
  @Test
  void duplicateEnvironmentVariableKeys_shouldLeadToStartupError() {
    TigerGlobalConfiguration.reset();
    withEnvironmentVariable("foobar", "123")
        .and("FOOBAR", "312")
        .execute(
            () ->
                assertThatThrownBy(() -> TigerGlobalConfiguration.readString("foobar"))
                    .isInstanceOf(TigerConfigurationException.class));
  }

  @SneakyThrows
  @Test
  void duplicateEnvironmentVariableKeysWithSameValue_shouldProceed() {
    TigerGlobalConfiguration.reset();
    withEnvironmentVariable("foobar", "123")
        .and("FOOBAR", "123")
        .execute(() -> assertThat(TigerGlobalConfiguration.readString("foobar")).isEqualTo("123"));
  }

  @ParameterizedTest
  @CsvSource({
    "foo|bar, foo_bar",
    "foo{bar, foo_bar",
    "{foobar, _foobar",
    "}foobar, _foobar",
    "foo}bar, foo_bar",
    "|foobar, _foobar",
    "foobar|, foobar_"
  })
  void forbiddenCharacters_shouldBeReplaced(String keyOriginal, String keyReplaced) {
    try {
      System.setProperty(keyOriginal, "someValue");
      TigerGlobalConfiguration.reset();
      assertThat(TigerGlobalConfiguration.readString(keyReplaced)).isEqualTo("someValue");
    } finally {
      System.clearProperty(keyOriginal);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"tiger.foo.bar", "TIGER_FOO_BAR", "tIgER.fOO.BaR"})
  void testKeyTranslations(String key) {
    try {
      System.setProperty("TIGER_FOO_BAR", "someValue");
      TigerGlobalConfiguration.reset();
      assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("someValue");
      assertThat(TigerGlobalConfiguration.resolvePlaceholders("${" + key + "}"))
          .isEqualTo("someValue");
      System.clearProperty("TIGER_FOO_BAR");

      System.setProperty("tiger.foo.bar", "someValue");
      TigerGlobalConfiguration.reset();
      assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("someValue");
      assertThat(TigerGlobalConfiguration.resolvePlaceholders("${" + key + "}"))
          .isEqualTo("someValue");
      System.clearProperty("tiger.foo.bar");

      TigerGlobalConfiguration.reset();
      TigerGlobalConfiguration.putValue("tiger.foo.bar", "someValue");
      assertThat(TigerGlobalConfiguration.readString(key)).isEqualTo("someValue");
      assertThat(TigerGlobalConfiguration.resolvePlaceholders("${" + key + "}"))
          .isEqualTo("someValue");
    } finally {
      System.clearProperty("TIGER_FOO_BAR");
      System.clearProperty("tiger.foo.bar");
    }
  }

  @Test
  void writeByteArraysAndReadThem_shouldWork() {
    byte[] b1 = new byte[] {0x1};
    TigerGlobalConfiguration.putValue("testkey", b1);
    assertThat(TigerGlobalConfiguration.readByteArray("testkey")).get().isEqualTo(b1);

    byte[] b2 = new byte[] {0x2};
    TigerGlobalConfiguration.putValue("testkey", b2);
    assertThat(TigerGlobalConfiguration.readByteArray("testkey")).get().isEqualTo(b2);

    byte[] b3 = new byte[50_000_000];
    new Random().nextBytes(b3);
    TigerGlobalConfiguration.putValue("testkey", b3);
    assertThat(TigerGlobalConfiguration.readByteArray("testkey")).get().isEqualTo(b3);

    TigerGlobalConfiguration.putValue("foo", Map.of("bar", b3));
    assertThat(TigerGlobalConfiguration.readByteArray("foo.bar")).get().isEqualTo(b3);

    TigerGlobalConfiguration.putValue("foo", NestedBean.builder().bytes(b3).build());
    assertThat(TigerGlobalConfiguration.readByteArray("foo.bytes")).get().isEqualTo(b3);
  }

  @ParameterizedTest
  @CsvSource({
    "NESTEDBEAN_FOO, nestedBean.foo",
    "nestedBean.foo, nestedBean.foo",
    "Nestedbean.foo, nestedBean.foo",
    "nestedBean.foo, NESTEDBEAN_FOO",
    "nestedBean.foo, nestedBean.foo",
    "nestedBean.foo, Nestedbean.foo"
  })
  void differentNamesForSameField_envShouldAlwaysBeatYaml(String envName, String yamlName)
      throws Exception {
    new EnvironmentVariables(envName, "blub")
        .execute(
            () -> {
              TigerGlobalConfiguration.reset();
              TigerGlobalConfiguration.putValue(
                  yamlName, "schmar", ConfigurationValuePrecedence.MAIN_YAML);
              var dummyBean =
                  TigerGlobalConfiguration.instantiateConfigurationBean(DummyBean.class).get();
              assertThat(dummyBean.getNestedBean()).hasFieldOrPropertyWithValue("foo", "blub");
            });
  }

  @ParameterizedTest
  @CsvSource({
    "true, true",
    "TRUE, true",
    "tRUe, true",
    "on , true",
    "yes, true",
    "false, false",
    "x gti, false",
    "y, true",
    "n, false",
    "T, true",
    "t, true",
    "F, false",
    "f, false",
    "1, true",
    "0, false",
  })
  void getBoolean_shouldResolveBeforeParsing(String booleanString, boolean expectedBoolean) {
    TigerGlobalConfiguration.putValue("test.booleanAsString", booleanString);
    TigerGlobalConfiguration.putValue("test.toBeResolved", "${test.booleanAsString}");
    assertThat(TigerGlobalConfiguration.readString("test.booleanAsString"))
        .isEqualTo(booleanString);
    assertThat(TigerGlobalConfiguration.readBoolean("test.booleanAsString"))
        .isEqualTo(expectedBoolean);
    assertThat(TigerGlobalConfiguration.readString("test.toBeResolved")).isEqualTo(booleanString);
    assertThat(TigerGlobalConfiguration.readBoolean("test.toBeResolved"))
        .isEqualTo(expectedBoolean);
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
    private final byte[] bytes;
    @JsonProperty private List<NestedBean> list;
  }

  @Data
  @Builder
  public static class Users {

    private String username;
    private String password;
    private LocalDate blub;
    private List<String> roles;
  }

  @Data
  @Builder
  public static class EvaluationSkippingTestClass {

    @TigerSkipEvaluation private String skipString;
    private String directString;
    @TigerSkipEvaluation private List<String> skipList;
    private List<String> directList;
    @TigerSkipEvaluation private Map<String, String> skipMap;
    private Map<String, String> directMap;
  }
}
