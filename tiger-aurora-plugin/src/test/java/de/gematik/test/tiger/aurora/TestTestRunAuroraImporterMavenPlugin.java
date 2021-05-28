package de.gematik.test.tiger.aurora;

import de.gematik.aurora.client.common.crypt.ClientCryptor;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.junit.Assert;
import org.junit.Test;

public class TestTestRunAuroraImporterMavenPlugin {
    @Test
    public void setup() throws GeneralSecurityException, IOException {
        ClientCryptor.clearInstance();
        for (final String key : ClientCryptor.getAllParameterWithDefaultValues().keySet()) {
            System.clearProperty(key);
        }

//      String profile = "dev_env";
//      String keystorePassword = "123456789";
//      String keystore = "X:/21_Team_Entwicklung/Entwicklung/KeystoresAuroraDev/auroraPublic.jks";

//       String profile = "prod_ref_env"; // Polarion Prod bzw. Ref, je nachdem mit welchem Profil man aktuell angemeldet ist
        final String profile = "prod_ref_env"; // Polarion-Dev
        final String keystorePassword = "aurora";
        final String keystore = getAbsolutePathOfFile("keystore/aurora.jks");

        System.setProperty("aurora.profiles.active", profile);
        System.setProperty(ClientCryptor.PARAM_KEYSTORE_PASSWORD, keystorePassword);
        System.setProperty(ClientCryptor.PARAM_KEYSTORE, keystore);
        Assert.assertNotNull(ClientCryptor.getInstance());

    }


    private String getAbsolutePathOfFile(final String file) {
        return new File(getClass().getClassLoader().getResource(file).getFile()).getAbsolutePath();
    }

}
