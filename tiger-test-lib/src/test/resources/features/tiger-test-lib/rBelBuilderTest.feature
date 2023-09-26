Feature: RbelBuilder Test feature

  Scenario: Read RbelElement from File
    Given TGR creates a new Rbel object 'blab' from file "src/test/resources/testdata/rbelBuilderTests/blub.json"

    When TGR sets Rbel object 'blab' at '$.blub.foo' to new object '{ "a": "new object", "and": "another" }'
    Then TGR asserts Rbel object 'blab' at '$.blub.foo.a' equals "new object"

    When TGR sets Rbel object 'blab' at '$.blub.foo.a' to new value "some text"
    Then TGR asserts Rbel object 'blab' at '$.blub.foo.a' equals "some text"

  Scenario: Read RbelElement from String:
    Given TGR creates a new Rbel object 'blub' with content '{"foo":"bar"}'
    Then TGR asserts Rbel object 'blub' at '$.foo' equals 'bar'

  Scenario: Create RbelElement from Scratch
    Given TGR creates a new empty Rbel object 'blub'
