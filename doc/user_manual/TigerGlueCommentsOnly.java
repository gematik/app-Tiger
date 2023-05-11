
/*
* Sets the given key to the given value in the global configuration store. Variable substitution is performed.
*
* @param key   key of the context
* @param value value for the context entry with given key
*/
@Wenn("TGR setze globale Variable {string} auf {string} setzen")
@When("TGR set global variable {string} to {string}")

/*
* Sets the given key to the given value in the global configuration store. Variable substitution is performed. This
* value will only be accessible from this exact thread.
*
* @param key   key of the context
* @param value value for the context entry with given key
*/
@Wenn("TGR setze lokale Variable {string} auf {string} setzen")
@When("TGR set local variable {string} to {string}")

/*
* asserts that value with given key either equals or matches (regex) the given regex string. Variable substitution
* is performed. This checks both global and local variables!
* <p>
*
* @param key   key of entry to check
* @param regex regular expression (or equals string) to compare the value of the entry to
*/
@Dann("TGR prüfe Variable {string} stimmt überein mit {string}")
@Then("TGR assert variable {string} matches {string}")

/*
* asserts that value of context entry with given key either equals or matches (regex) the given regex string.
* Variable substitution is performed.
* <p>
* Special values can be used:
*
* @param key key of entry to check
*/
@Dann("TGR prüfe Variable {string} ist unbekannt")
@Then("TGR assert variable {string} is unknown")
@Gegebensei("TGR zeige {word} Banner {string}")
@Given("TGR show {word} banner {string}")
@Gegebensei("TGR zeige {word} Text {string}")
@Given("TGR show {word} text {string}")
@Gegebensei("TGR zeige Banner {string}")
@Given("TGR show banner {string}")
@When("TGR wait for user abort")
@Wenn("TGR warte auf Abbruch")
@When("TGR pause test run execution")
@Wenn("TGR pausiere Testausführung")
@When("TGR pause test run execution with message {string}")
@Wenn("TGR pausiere Testausführung mit Nachricht {string}")
@When("TGR pause test run execution with message {string} and message in case of error {string}")
@Wenn("TGR pausiere Testausführung mit Nachricht {string} und Meldung im Fehlerfall {string}")
@When("TGR show HTML Notification:")
@Wenn("TGR zeige HTML Notification:")
@When("TGR assert {string} matches {string}")
@Dann("TGR prüfe das {string} mit {string} überein stimmt")
