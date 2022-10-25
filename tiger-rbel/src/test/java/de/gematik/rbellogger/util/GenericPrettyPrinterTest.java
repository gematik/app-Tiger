/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;
import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Streams;
import org.bouncycastle.asn1.*;
import org.bouncycastle.util.Iterable;
import org.junit.jupiter.api.Test;

public class GenericPrettyPrinterTest {

    private final Predicate<ASN1Encodable> isLeaf = asn1 -> (asn1 instanceof ASN1Sequence)
        || (asn1 instanceof ASN1Set);
    private final GenericPrettyPrinter<ASN1Encodable> genericPrettyPrinter = new GenericPrettyPrinter<>(
        isLeaf, Object::toString,
        asn1 -> Streams.stream((Iterable<ASN1Encodable>) asn1)
    );

    @Test
    void printAsn1P12() throws IOException {
        ASN1Encodable rootNode =
            readData(
                FileUtils.readFileToByteArray(
                    new File("src/test/resources/vau_cert.der")));
        final String prettyPrint = genericPrettyPrinter.prettyPrint(rootNode);
        System.out.println(prettyPrint);
        assertThat(prettyPrint)
            .isNotBlank();
    }

    private ASN1Encodable readData(byte[] data) {
        try (ASN1InputStream input = new ASN1InputStream(data)) {
            ASN1Primitive primitive;
            while ((primitive = input.readObject()) != null) {
                return ASN1Sequence.getInstance(primitive);
            }
        } catch (Exception e) {
        }
        throw new RuntimeException("no asn1 root found!");
    }
}