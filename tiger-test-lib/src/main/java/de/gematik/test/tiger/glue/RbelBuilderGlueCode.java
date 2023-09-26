package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.lib.rbel.RbelBuilder;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.HashMap;

import static junit.framework.TestCase.assertEquals;

public class RbelBuilderGlueCode {

    private final HashMap<String, RbelBuilder> rbelBuilders = new HashMap<>();

    /**
     * Creates a new Rbel object with a given key and string content
     * @param name key of Rbel object
     * @param content content of Rbel object
     */
    @Gegebensei("TGR erstellt ein neues Rbel-Objekt {string} mit Inhalt {string}")
    @Given("TGR creates a new Rbel object {string} with content {string}")
    public void createFromContent(String name, String content) {
        rbelBuilders.put(name, RbelBuilder.fromString(content));
    }

    /**
     * Creates a new empty Rbel object
     */
    @Gegebensei("TGR erstellt ein neues leeres Rbel-Objekt {string}")
    @Given("TGR creates a new empty Rbel object {string}")
    public void createFromScratch(String name) {
        rbelBuilders.put(name, RbelBuilder.fromScratch());
    }

    /**
     * Creates a new Rbel object with a given key and content from a given file
     * @param name key of Rbel object
     * @param filePath path to file with content
     */
    @Gegebensei("TGR erstellt ein neues Rbel-Objekt {string} aus der Datei {string}")
    @Given("TGR creates a new Rbel object {string} from file {string}")
    public void createFromFile(String name, String filePath) {
        rbelBuilders.put(name, RbelBuilder.fromFile(filePath));
    }

    /**
     * Sets a value of an object at a specified path; newValue is of type String
     * @param objectName name of object in rbelBuilders
     * @param rbelPath path which is to be set
     * @param newValue new value to be set
     */
    @Wenn("TGR setzt Rbel-Objekt {string} an Stelle {string} auf Wert {string}")
    @When("TGR sets Rbel object {string} at {string} to new value {string}")
    public void setValueAt(String objectName, String rbelPath, String newValue) {
        RbelBuilder rbelBuilder = rbelBuilders.get(objectName);
        rbelBuilder.setValueAt(rbelPath, newValue);
    }

    /**
     * Sets an object at a specified path; requires proper formatting with key/value
     * @param objectName name of object in rbelBuilders
     * @param rbelPath path which is to be set
     * @param newObject new object to be set
     */
    @Wenn("TGR setzt Rbel-Objekt {string} an Stelle {string} auf neues Objekt {string}")
    @When("TGR sets Rbel object {string} at {string} to new object {string}")
    public void setObjectAt(String objectName, String rbelPath, String newObject) {
        RbelBuilder rbelBuilder = rbelBuilders.get(objectName);
        rbelBuilder.setObjectAt(rbelPath, newObject);
    }

    /**
     * Asserts whether a string value at a given path of the rootTreeNode of a RbelBuilder is a certain value
     * @param objectName name of RbelBuilder in rbelBuilders Map
     * @param rbelPath Path to specific node
     * @param expectedValue value to be asserted
     */
    @Wenn("TGR prüft, dass Rbel-Objekt {string} an Stelle {string} gleich {string} ist")
    @When("TGR asserts Rbel object {string} at {string} equals {string}")
    public void assertValueAtEquals(String objectName, String rbelPath, String expectedValue) {
        RbelBuilder rbelBuilder = rbelBuilders.get(objectName);
        assertEquals(expectedValue, rbelBuilder.getTreeRootNode().findElement(rbelPath).orElseThrow().getRawStringContent());
    }
}
