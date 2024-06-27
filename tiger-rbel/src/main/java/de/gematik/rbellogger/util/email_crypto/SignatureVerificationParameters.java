/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto;

import eu.europa.esig.dss.policy.jaxb.ConstraintsParameters;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.JAXBIntrospector;
import jakarta.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import lombok.extern.slf4j.Slf4j;
import net.kafujo.io.Resources;
import org.xml.sax.SAXException;

@Slf4j
public class SignatureVerificationParameters {

  private SignatureVerificationParameters() {}

  // make sure, the resources are loaded with the same classloader as this class
  private static final Resources resources = Resources.of(SignatureVerificationParameters.class);

  private static final String RES_CONSTRAINT_XML = "signenc/constraint.xml";

  private static final String RES_POLICY_XSD = "signenc/xsd/policy.xsd";

  public static final String CONTEXT_PATH = "eu.europa.esig.dss.policy.jaxb";

  public static final ConstraintsParameters SIGNATURE_CONSTRAINTS_PARAMETERS =
      constraintsParameters();

  private static ConstraintsParameters constraintsParameters() {
    log.debug("reading ConstraintsParameters");

    try (InputStream constraints = resources.asStream(RES_CONSTRAINT_XML);
        InputStream policy = resources.asStream(RES_POLICY_XSD)) {

      JAXBContext jc = JAXBContext.newInstance(CONTEXT_PATH);
      Unmarshaller unmarshaller = jc.createUnmarshaller();

      SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      unmarshaller.setSchema(sf.newSchema(new StreamSource(policy)));
      return (ConstraintsParameters) JAXBIntrospector.getValue(unmarshaller.unmarshal(constraints));
    } catch (IOException | SAXException | JAXBException e) {
      throw new RbelDecryptionException("Could not create signature verification parameters", e);
    }
  }
}
