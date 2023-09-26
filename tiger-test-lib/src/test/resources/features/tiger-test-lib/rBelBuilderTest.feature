Feature: RbelBuilder Test feature

  Scenario: Create RbelElement from Scratch
    Given TGR creates a new empty Rbel object 'blub'

  Scenario: Read RbelElement from String:
    Given TGR creates a new Rbel object 'blub' with content '{"foo":"bar"}'
    When TGR asserts Rbel object 'blub' at '$.foo' equals 'bar'

  Scenario: Read from File And, change and assert changes
    Given TGR creates a new Rbel object 'blab' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}"

    When TGR sets Rbel object 'blab' at '$.blub.foo' to new object '{ "a": "new object", "and": "another" }'
    Then TGR asserts Rbel object 'blab' at '$.blub.foo.a' equals "new object"
    When TGR sets Rbel object 'blab' at '$.blub.foo.a' to new value "some text"
    Then TGR asserts Rbel object 'blab' at '$.blub.foo.a' equals "some text"

  Scenario: Read RbelElement from File and serialize again to string
    Given TGR creates a new Rbel object 'blab' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}"
    When TGR asserts '!{rbelObject:serialize("blab")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}" of type JSON

  Scenario: Read RbelElement from File, modify and serialize again to string
    Given TGR creates a new Rbel object 'blab' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.json')}"
    When TGR sets Rbel object 'blab' at '$.blub.foo' to new object '{"an": "object"}'
    When TGR asserts '!{rbelObject:serialize("blab")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/blub_asserted_modified.json')}" of type JSON

  Scenario: Modify and serialize XML
    Given TGR creates a new Rbel object 'blub' with content "!{file('src/test/resources/testdata/rbelBuilderTests/blub.xml')}"
    When TGR sets Rbel object 'blub' at '$.blub.foo' to new object '<key>value</key>'
    Then TGR asserts '!{rbelObject:serialize("blub")}' equals "!{file('src/test/resources/testdata/rbelBuilderTests/blub_asserted_modified.xml')}" of type XML