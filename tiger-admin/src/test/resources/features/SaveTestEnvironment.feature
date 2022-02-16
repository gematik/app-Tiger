@FullTests @UiTests
Feature: Save test environments

  Scenario: save test environment by hitting Enter
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    Then he saves test environment as "newTestEnv.yaml" using Enter
    Then he verifies saved file "newTestEnv.yaml" contains
    """
    servers:
      docker_001:
        hostname: docker_001
        type: docker
    """
