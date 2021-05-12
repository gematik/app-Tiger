package de.gematik.test.tiger.lib;

public class TestTigerDirector {

    // TODO OPENBUG TGR-6 reactivate after fix
    //  @Test
    public void testDirectorSimpleIdp() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleidp.yaml");
        TigerDirector.beforeTestRun();
    }

    // TODO OPENBUG TGR-6 reactivate after fix
    //  @Test
    public void testDirectorIdpAndERezept() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/idpAnderezept.yaml");
        TigerDirector.beforeTestRun();
    }
}
