Feature: this is a feature

  Scenario: this a scenario in the feature
    Given TGR show banner "Hello from feature"

  @ATag @AnotherTag
  Scenario Outline: This is a scenario outline <var>
    Given TGR zeige Banner "Hello <var>"

    @ExampleTag
    Examples:
      | var        |
      | life       |
      | universe   |
      | everything |

    @MoreExampleTags
    Examples:
      | var        |
      | more life       |
      | more universe   |
      | more everything |