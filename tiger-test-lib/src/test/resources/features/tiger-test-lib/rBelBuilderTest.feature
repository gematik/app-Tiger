Feature: RbelBuilder Test feature

  Scenario: Create RbelElement from Scratch
    Given TGR creates a new empty Rbel object 'blub' of type XML

  Scenario: Read RbelElement from String:
    Given TGR creates a new Rbel object 'blub' with content '{"foo":"bar"}'
    When TGR asserts Rbel object 'blub' at '$.foo' equals 'bar'

  Scenario: Read from File; change and assert changes
    Given TGR creates a new Rbel object 'blab' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}"

    When TGR sets Rbel object 'blab' at '$.blub.foo' to new value '{ "a": "new object", "and": "another" }'
    Then TGR asserts Rbel object 'blab' at '$.blub.foo.a' equals "new object"
    When TGR sets Rbel object 'blab' at '$.blub.foo.a' to new value "some text"
    Then TGR asserts Rbel object 'blab' at '$.blub.foo.a' equals "some text"

  Scenario: Read RbelElement from File and serialize again to string
    Given TGR creates a new Rbel object 'blab' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}"
    When TGR asserts '!{rbelObject:serialize("blab")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}" of type JSON

  Scenario: Read RbelElement from File, modify and serialize again to string
    Given TGR creates a new Rbel object 'blab' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}"
    When TGR sets Rbel object 'blab' at '$.blub.foo' to new value '{"an": "object"}'
    When TGR asserts '!{rbelObject:serialize("blab")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/blub_asserted_modified.json')}" of type JSON

  Scenario: Add new entries to array
    Given TGR creates a new Rbel object 'arrays' with content "!{file('src/test/resources/testdata/rbelBuilderTests/arrayTests.json')}"
    When TGR extends Rbel object 'arrays' at path '$.arrays.array_one' by a new entry 'four'
    And TGR extends Rbel object 'arrays' at path '$.arrays.array_one' by a new entry '{ "one": "half", "two": "thirds" }'
    Then TGR asserts '!{rbelObject:serialize("arrays")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/arrayTests_asserted_modified.json')}" of type JSON

  Scenario: Add new entry to JSON object
    Given TGR creates a new Rbel object 'blub' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}"
    When TGR sets Rbel object 'blub' at '$.blub.foo' to new value '{ "key1": "value1" }'
    And TGR sets Rbel object 'blub' at '$.blub.foo.key2' to new value 'value2'
    Then TGR asserts '!{rbelObject:serialize("blub")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/blub_asserted_modified_2.json')}" of type JSON

  Scenario: Add new entries to XML object
    Given TGR creates a new Rbel object 'blub' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.xml')}"
    When TGR sets Rbel object 'blub' at '$.blub.foo' to new value '<bar><key1>value1</key1><key2>value2</key2></bar>'
    And TGR sets Rbel object 'blub' at '$.blub.foo.bar.key3' to new value 'value3'
    Then TGR asserts '!{rbelObject:serialize("blub")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/blub_asserted_modified.xml')}" of type XML

  Scenario: Read and serialize XML File
    Given TGR creates a new Rbel object 'blub' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.xml')}"
    Then TGR asserts '!{rbelObject:serialize("blub")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/blub.xml')}" of type XML


