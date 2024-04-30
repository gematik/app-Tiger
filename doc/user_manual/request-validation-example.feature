Feature Tiger request validation steps

  Background:
    Given TGR clear recorded messages

  Scenario: Test validation Request
    When TGR send PUT request to "http://httpbin/put" with body "{'foo': 'bar'}"
    Then TGR find last request to path ".*"
    Then TGR current request body matches:
    """
    {'foo': 'bar'}
        """

    Then TGR current request contains node "$.body.foo"
    Then TGR current request with attribute "$.body.foo" matches "bar"
    Then TGR current request at "$.body" matches:
    """
    {'foo': 'bar'}
        """

    Then TGR current request at "$.body" matches as JSON:
    """
    {
      "foo": "${json-unit.ignore}"
    }
        """

    Then TGR current request with attribute "$.body.foo" does not match "foo"