Feature Tiger validation steps

  Scenario: Example steps

    Given TGR clear recorded messages
    And TGR filter requests based on host "testnode.example.org"
    And TGR filter requests based on method "POST"
    And TGR set request wait timeout to 20 seconds
    When TGR find request to path "/path/path/blabla" with "$..tag.value.text" matching "abc.*"
    And When TGR find request to path "/path/path/blabla" contaning "$..tag2"
    Then TGR current response with attribute "$..answer.result.text" matches "OK.*"
    But TGR current response with attribute "$..answer.reason.text" does not match "REQUEST.*"
    And TGR current response body matches
    """
         body content
        """
    And TGR current response at "$..tag" matches as JSON
    """
          {
            "arr1": [
              "asso", "bsso"
            ]
          }
        """
    And TGR current response at "$..tag" matches as XML
    """
          <arr1>
            <entry index="1">asso</entry>
            <entry index="2">bsso</entry>
          </arr1>
        """
