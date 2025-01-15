Feature: Tiger - RbelBuilder

  Scenario: Replace/change/add certain values/nodes in a rbel object
    # changing primitive value
    Given TGR creates a new Rbel object 'someObjName' with content '{"address": {"street": "Friedrichstr 136","city": "Berlin","postalCode": "10115"}}'
    Then TGR sets Rbel object 'someObjName' at '$.address.street' to new value 'Hauptstrasse'
    Then TGR asserts '!{rbelObject:serialize("someObjName")}' equals '{"address": {"street": "Hauptstrasse","city": "Berlin","postalCode": "10115"}}' of type JSON

    # adding primitive value
    Given TGR creates a new Rbel object 'someAddress' with content '{"address": {"city": "Berlin","postalCode": "10115"}}'
    Then TGR sets Rbel object 'someAddress' at '$.address.street' to new value 'Friedrichstr'
    Then TGR asserts '!{rbelObject:serialize("someAddress")}' equals '{"address": {"street": "Friedrichstr","city": "Berlin","postalCode": "10115"}}' of type JSON

    # replacing object nodes
    Given TGR creates a new Rbel object 'phoneNumbers' with content '{"phoneNumbers": [{"type": "home",  "number": "030-1234567"},{"type": "mobile", "number": "0176-123456788"}]}'
    When TGR sets Rbel object 'phoneNumbers' at '$.phoneNumbers.1' to new value '{"type" : "work", "number" : "0176-199999"}'
    Then TGR asserts '!{rbelObject:serialize("phoneNumbers")}' equals '{"phoneNumbers": [{"type": "home",  "number": "030-1234567"},{"type": "work", "number": "0176-199999"}]}' of type JSON

    # adding new nodes to an array
    When TGR extends Rbel object 'phoneNumbers' at path '$.phoneNumbers' by a new entry '{"type": "mobile", "number": "0176-123456788"}'
    Then TGR asserts '!{rbelObject:serialize("phoneNumbers")}' equals '{"phoneNumbers": [{"type": "home",  "number": "030-1234567"}, {"type" : "work", "number" : "0176-199999"},{"type": "mobile", "number": "0176-123456788"}]}' of type JSON
