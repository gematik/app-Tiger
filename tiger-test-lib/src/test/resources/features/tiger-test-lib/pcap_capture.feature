@PCAP
Feature: PCAP Capture per Testcase
  In order to diagnose network traffic issues in Tiger tests
  As a tester
  I want pcap files generated automatically per testcase in target/evidences/

  Background:
    Given pcap capture is enabled

  @FullTests
  @PcapCapture
  Scenario: Generate pcap file per testcase with splitByTestcase enabled
    And a Tiger proxy is running
    When I send a request through the proxy to local httpbin
    And this scenario's capture is finished
    Then a pcapng file should exist in "target/evidences" for this scenario
    And the pcapng file should contain at least one TCP packet
    And the pcapng file should be valid pcapng format

  @FullTests
  @PcapCapture
  Scenario: Traffic after a testcase's capture is finished does not end up in its file
    And a Tiger proxy is running
    When I send a first request through the proxy to local httpbin
    And this scenario's capture is finished
    And I send a second request through the proxy to local httpbin
    Then the finished capture file should not have grown

  @PcapCapture
  Scenario: Gracefully disable capture when native lib is unavailable
    Given pcap capture is enabled but native pcap library is not available
    When tests execute normally
    Then tests should not fail due to missing pcap library
    And a WARN should be logged about pcap capture being unavailable


