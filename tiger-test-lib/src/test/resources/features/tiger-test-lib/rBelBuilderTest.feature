Feature: RbelBuilder Test feature

  Scenario: Read RbelElement from File
    Given TGR creates a new Rbel object 'blub' from file "src/test/resources/testdata/rbelBuilderTests/blub.json"

  Scenario: Read RbelElement from File and assign as value
    Given TGR creates a new Rbel object from file "src/test/resources/testdata/rbelBuilderTests/blub.json"

  Scenario: Read RbelElement from String:
    Given TGR creates a new Rbel object 'blub' with content '{"foo":"bar"}'

  Scenario: Create RbelElement from Scratch
    Given TGR creates a new empty Rbel object