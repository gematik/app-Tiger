package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.lib.rbel.RbelBuilder;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.en.Given;

public class RbelBuilderGlueCode {

    private RbelBuilder rbelBuilder;

    /**
     * Creates a new Rbel object with a given key and string content
     * @param name key of Rbel object
     * @param content content of Rbel object
     */
    @Gegebensei("TGR erstellt ein neues Rbel-Objekt {string} mit Inhalt {string}")
    @Given("TGR creates a new Rbel object {string} with content {string}")
    public void createFromContent(String name, String content) {
        rbelBuilder = RbelBuilder.fromString(name, content);
    }

    /**
     * Creates a new empty Rbel object
     */
    @Gegebensei("TGR erstellt ein neues leeres Rbel-Objekt")
    @Given("TGR creates a new empty Rbel object")
    public void createFromScratch() {
        rbelBuilder = RbelBuilder.fromScratch();
    }

    /**
     * Creates a new Rbel object with a given key and content from a given file
     * @param name key of Rbel object
     * @param filePath path to file with content
     */
    @Gegebensei("TGR erstellt ein neues Rbel-Objekt {string} aus der Datei {string}")
    @Given("TGR creates a new Rbel object {string} from file {string}")
    public void createFromFile(String name, String filePath) {
        rbelBuilder = RbelBuilder.fromFile(name, filePath);
    }

    /**
     * Creates a new Rbel object from a given file
     * @param filePath path to file with content for Rbel object
     */
    @Gegebensei("TGR erstellt ein neues Rbel-Objekt aus der Datei {string}")
    @Given("TGR creates a new Rbel object from file {string}")
    public void createFromFile(String filePath) {
        rbelBuilder = RbelBuilder.fromFile(filePath);
    }
}
