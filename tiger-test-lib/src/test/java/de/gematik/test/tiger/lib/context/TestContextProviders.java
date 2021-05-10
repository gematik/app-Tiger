package de.gematik.test.tiger.lib.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.context.ThreadSafeDomainContextProvider;
import java.util.Map;
import org.junit.Test;

public class TestContextProviders {

    // =================================================================================================================
    //
    // testing ThreadSafeDomainContextProvider via means of TestContext
    //
    // =================================================================================================================

    @Test
    public void testDefaultDomainOK() {
        final TestContext ctxt = new TestContext();
        assertThat(ctxt.getDomain()).isEqualTo("default");
    }

    @Test
    public void testSetGetDomainOK() {
        final TestContext ctxt = new TestContext();
        ctxt.setDomain("test001");
        assertThat(ctxt.getDomain()).isEqualTo("test001");
    }

    @Test
    public void testCopyAllToDomainOK() {
        final TestContext ctxt = new TestContext("test002");
        ctxt.putString("key1", "value1");
        ctxt.putString("key2", "value2");
        ctxt.copyAllToDomain("test003");
        assertThat(ctxt.getContext("test003"))
            .containsEntry("key1", "value1")
            .containsEntry("key2", "value2")
            .hasSize(2);
    }

    @Test
    public void testCopToDomainOK() {
        final TestContext ctxt = new TestContext("test002a");
        ctxt.putString("key1", "value1");
        ctxt.putString("key2", "value2");
        ctxt.copyToDomain("test003a", "key2");
        assertThat(ctxt.getContext("test003a"))
            .doesNotContainKey("key1")
            .containsEntry("key2", "value2")
            .hasSize(1);
    }

    @Test
    public void testRegexMatchesNULL() {
        final TestContext ctxt = new TestContext("test004");
        ctxt.putString("key1", "value1");
        ctxt.getContext().put("nullkey", null);
        ctxt.putString("key3", "$NULL");
        assertThatThrownBy(() -> ctxt.assertRegexMatches("key1", "$NULL"))
            .hasMessage("\r\nExpecting:\r\n <\"value1\">\r\nto be equal to:\r\n <null>\r\nbut was not.");

        ctxt.assertRegexMatches("nullkey", null);
        ctxt.assertRegexMatches("nullkey", "$NULL");
        assertThatThrownBy(() -> ctxt.assertRegexMatches("key3", "$NULL"))
            .hasMessage("\r\nExpecting:\r\n <\"$NULL\">\r\nto be equal to:\r\n <null>\r\nbut was not.");
    }

    @Test
    public void testRegexMatchesNullValue() {
        final TestContext ctxt = new TestContext("test004a");
        ctxt.getContext().put("nullkey", null);
        assertThatThrownBy(() -> ctxt.assertRegexMatches("nullkey", ".*"))
            .hasMessage("\r\nExpecting actual not to be null");
        ctxt.assertRegexMatches("nullkey", null);
    }

    @Test
    public void testRegexMatchesDOESNOTEXIST() {
        final TestContext ctxt = new TestContext("test005");
        ctxt.putString("key1", "value1");
        ctxt.assertRegexMatches("key2", "$DOESNOTEXIST");
        assertThatThrownBy(() -> ctxt.assertRegexMatches("key1", "$DOESNOTEXIST"))
            .hasMessage("\r\nExpecting:\r\n  <{\"key1\"=\"value1\"}>\r\nnot to contain key:\r\n  <\"key1\">");
    }

    @Test
    public void testRegexMatchesRegex() {
        final TestContext ctxt = new TestContext("test005a");
        ctxt.putString("key1", "value1");
        ctxt.assertRegexMatches("key1", ".*");
        ctxt.assertRegexMatches("key1", "v.*1");
        assertThatThrownBy(() -> ctxt.assertRegexMatches("key1", "[\\d]*"))
            .hasMessage("\r\nExpecting:\r\n \"value1\"\r\nto match pattern:\r\n \"[\\d]*\"");
    }

    @Test
    public void testGetMapCopy() {
        final TestContext ctxt = new TestContext("test006");
        ctxt.putString("key1", "value1");
        ctxt.getContext().put("key2", Map.of("mkey1", "mval1", "mkey2", 2));
        assertThat(ctxt.getObjectMapCopy("key2"))
            .containsEntry("mkey1", "mval1")
            .containsEntry("mkey2", 2)
            .hasSize(2);
        assertThatThrownBy(() -> ctxt.getObjectMapCopy("key1"))
            .hasMessage("\r\nExpecting:\r\n  <\"value1\">\r\nto be an instance of:\r\n  <java.util.Map>\r\n"
                + "but was instance of:\r\n  <java.lang.String>");
    }

    @Test
    public void testRemove() {
        final TestContext ctxt = new TestContext("test007");
        ctxt.putString("key1", "value1");
        assertThat(ctxt.getContext()).containsKey("key1");
        ctxt.setDomain("test007a");
        assertThatThrownBy(() -> ctxt.remove("key1"))
            .hasMessage("\r\nExpecting:\r\n <{}>\r\nto contain key:\r\n <\"key1\">");
        ctxt.setDomain("test007");
        assertThat(ctxt.getContext()).containsKey("key1");
        ctxt.remove("key1");
        assertThat(ctxt.getContext()).doesNotContainKey("key1");
    }

    @Test
    public void testFlipBit() {
        final TestContext ctxt = new TestContext("test008");
        ctxt.putString("key1", "value1");
        ctxt.flipBit(10, "key1");
        assertThat(ctxt.getString("key1")).isNotEqualTo("value1");
        assertThat(ctxt.getString("key1")).isEqualTo("v!lue1");
        ctxt.flipBit(10, "key1");
        assertThat(ctxt.getString("key1")).isEqualTo("value1");
    }

    @Test
    public void testFlipBitNegativ() {
        final TestContext ctxt = new TestContext("test009");
        ctxt.putString("key1", "value1");
        ctxt.flipBit(-10, "key1");
        assertThat(ctxt.getString("key1")).isNotEqualTo("value1");
        assertThat(ctxt.getString("key1")).isEqualTo("valua1");
        ctxt.flipBit(-10, "key1");
        assertThat(ctxt.getString("key1")).isEqualTo("value1");
    }

    // TODO replace next three methods with data provided methods
    @Test
    public void testSubstituteTokenOK() {
        final TestContext ctxt = new TestContext("test010");
        ctxt.putString("key1", "value1");
        ctxt.putString("key2", "KEY2VALUE");
        assertThat(ThreadSafeDomainContextProvider
            .substituteTokens("value1${TESTENV.key2}textblabla", "TESTENV", ctxt.getContext()))
            .isEqualTo("value1KEY2VALUEtextblabla");
    }

    @Test
    public void testSubstituteToken2OK() {
        final TestContext ctxt = new TestContext("test010");
        ctxt.putString("key1", "value1");
        ctxt.putString("key2", "KEY2VALUE");
        assertThat(
            ThreadSafeDomainContextProvider
                .substituteTokens("value1${TESTENV.key2}text${TESTENV.key1}blabla", "TESTENV", ctxt.getContext()))
            .isEqualTo("value1KEY2VALUEtextvalue1blabla");
    }

    @Test
    public void testSubstituteTokenUnsetValueOK() {
        final TestContext ctxt = new TestContext("test010");
        ctxt.putString("key1", "value1");
        ctxt.putString("key2", "KEY2VALUE");
        assertThat(
            ThreadSafeDomainContextProvider
                .substituteTokens("value1${TESTENV.key2}text${TESTENV.key3}blabla", "TESTENV", ctxt.getContext()))
            .isEqualTo("value1KEY2VALUEtext${TESTENV.key3}blabla");
    }

    @Test
    public void testContextDifferentDomainsPutOK() {
        final TestContext ctxt = new TestContext();
        ctxt.setDomain("test050");
        ctxt.putString("testkey", "testvalue");
        ctxt.setDomain("test51");
        assertThat(ctxt.getContext()).doesNotContainKey("testkey");
        assertThat(ctxt.getContext()).doesNotContainEntry("testkey", "testvalue");
        ctxt.setDomain("test050");
        assertThat(ctxt.getContext("test051")).doesNotContainKey("testkey");
        assertThat(ctxt.getContext("test051")).doesNotContainEntry("testkey", "testvalue");
        assertThat(ctxt.getString("testkey")).isEqualTo("testvalue");
    }

    // =================================================================================================================
    //
    // TestVariables
    //
    // =================================================================================================================

    @Test
    public void testSubstituteVariablesOK() {
        final TestVariables vars = new TestVariables("test010");
        vars.putString("key1", "valueVAR1");
        vars.putString("key2", "KEY2VARVALUE");
        assertThat(vars.substituteVariables("value1${VAR.key2}textblabla"))
            .isEqualTo("value1KEY2VARVALUEtextblabla");
    }

    @Test
    public void testVariablesDefaultDomainOK() {
        final TestVariables vars = new TestVariables();
        assertThat(vars.getDomain()).isEqualTo("default");
    }

    @Test
    public void testVariablesDifferentDomainsPutOK() {
        final TestVariables vars = new TestVariables();
        vars.setDomain("test050");
        vars.putString("testkey", "testvalue");
        vars.setDomain("test51");
        assertThat(vars.getContext()).doesNotContainKey("testkey");
        assertThat(vars.getContext()).doesNotContainEntry("testkey", "testvalue");
        vars.setDomain("test050");
        assertThat(vars.getContext("test051")).doesNotContainKey("testkey");
        assertThat(vars.getContext("test051")).doesNotContainEntry("testkey", "testvalue");
        assertThat(vars.getString("testkey")).isEqualTo("testvalue");
    }
}
