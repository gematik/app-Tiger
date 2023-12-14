
/*
* Creates a new Rbel object with a given key and string content; the string can be a jexl
* expression
*
* @param name key of Rbel object
* @param content content of Rbel object, or jexl expression resolving to one
*/
@Gegebensei(
@Given("TGR creates a new Rbel object {tigerResolvedString} with content {tigerResolvedString}")

/*
* Creates a new empty Rbel object
*
* @param name key of Rbel object
*/
@Gegebensei(
@Given("TGR creates a new empty Rbel object {tigerResolvedString} of type {rbelContentType}")

/*
* Sets a value of an object at a specified path; newValue is of type String
*
* @param objectName name of object in rbelBuilders
* @param rbelPath path which is to be set
* @param newValue new value to be set
*/
@Wenn(
@When(

/*
* Adds a new entry to an array or a list of a Rbel object at a specific path
*
* @param objectName name of Rbel object
* @param rbelPath path of array/list
* @param newEntry new entry
*/
@Wenn(
@When(

/*
* Asserts whether a string value at a given path of the rootTreeNode of a RbelBuilder is a
* certain value
*
* @param objectName name of RbelBuilder in rbelBuilders Map
* @param rbelPath Path to specific node
* @param expectedValue value to be asserted
*/
@Wenn(
@When(

/*
* Asserts, if 2 Rbel object serializations are equal
*
* @param jexlExpressionActual actual value
* @param jexlExpressionExpected expected value
* @param contentType type of Rbel object content for comparison
*/
@Wenn(
@When("TGR asserts {tigerResolvedString} equals {tigerResolvedString} of type {rbelContentType}")
