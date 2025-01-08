///
///
/// Copyright 2024 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import StepUpdate from "../types/testsuite/StepUpdate";
import TestResult from "../types/testsuite/TestResult";

describe('testing StepUpdate class', () => {

  test('empty step should contain undefined values', () => {
    const step = new StepUpdate();
    expect(step.description).toBe('');
    expect(step.tooltip).toEqual('');
    expect(step.status).toBe(TestResult.UNUSED);
    expect(step.stepIndex).toEqual(-1);
  });

  test('empty JSON applies correctly', () => {
    const step = StepUpdate.fromJson(JSON.parse('{ }'));
    expect(step.description).toBe('');
    expect(step.tooltip).toBe('');
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test('empty JSON description applies correctly', () => {
    const step = StepUpdate.fromJson(JSON.parse('{ "status": "PASSED" }'));
    expect(step.description).toBe('');
    expect(step.tooltip).toBe('');
    expect(step.status).toBe(TestResult.PASSED);
  });

  test('empty JSON status applies correctly', () => {
    const step = StepUpdate.fromJson(JSON.parse('{ "description": "desc" }'));
    expect(step.description).toBe('desc');
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test('tooltip applies correctly', () => {
    const step = StepUpdate.fromJson(JSON.parse('{ "tooltip": "tooltip" }'));
    expect(step.tooltip).toBe('tooltip');
  });

  test("empty JSON index applies correctly", () => {
    const step = StepUpdate.fromJson(JSON.parse('{ "stepIndex":3 }'));
    expect(step.stepIndex).toBe(3);
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON rbelMetaData applies correctly", () => {
    const step = StepUpdate.fromJson(JSON.parse('{ "rbelMetaData":[] }'));
    expect(step.rbelMetaData.length).toBe(0);
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON full rbelMetaData applies correctly", () => {
    const step = StepUpdate.fromJson(JSON.parse('{ "rbelMetaData":[{"uuid": "someUUID", "menuInfoString": "GET /some/path", "recipient": "someRecipient", "sender": "someSender", "sequenceNumber": 1}]}'));
    expect(step.rbelMetaData.length).toBe(1);
    expect(step.rbelMetaData[0].uuid).toBe("someUUID");
    expect(step.rbelMetaData[0].menuInfoString).toBe("GET /some/path");
    expect(step.rbelMetaData[0].recipient).toBe("someRecipient");
    expect(step.rbelMetaData[0].sender).toBe("someSender");
    expect(step.rbelMetaData[0].sequenceNumber).toBe(1);
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test('full JSON applies correctly', () => {
    const step = StepUpdate.fromJson(JSON.parse('{"description":"Then User requests classes with parameter xyz=4","tooltip":"tooltip","status":"PASSED","stepIndex":3,"rbelMetaData":[]}'));
    expect(step.description).toBe('Then User requests classes with parameter xyz=4');
    expect(step.tooltip).toBe('tooltip');
    expect(step.status).toBe(TestResult.PASSED);
  });

  test('complete merge works correctly', () => {
    const step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "tooltip": "tooltip1", "status": "FAILED", "stepIndex":3, "rbelMetaData":[]}'));
    const step2 = StepUpdate.fromJson(JSON.parse('{ "description": "desc2", "tooltip": "tooltip2", "status": "PENDING", "stepIndex":4, "rbelMetaData":[{"uuid": "someUUID", "menuInfoString": "GET /some/path", "recipient": "someRecipient", "sender": "someSender", "sequenceNumber": 1}]}'));
    step1.merge(step2);
    expect(step1.description).toBe('desc2');
    expect(step1.tooltip).toBe('tooltip2');
    expect(step1.status).toBe(TestResult.PENDING);
    expect(step1.stepIndex).toBe(4);
    expect(step1.rbelMetaData[0].uuid).toBe("someUUID");
    expect(step1.rbelMetaData[0].menuInfoString).toBe("GET /some/path");
    expect(step1.rbelMetaData[0].recipient).toBe("someRecipient");
    expect(step1.rbelMetaData[0].sender).toBe("someSender");
    expect(step1.rbelMetaData[0].sequenceNumber).toBe(1);
  });

  test('empty description merge works correctly', () => {
    const step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "status": "FAILED" }'));
    const step2 = StepUpdate.fromJson(JSON.parse('{ "status": "PENDING","stepIndex":4, "rbelMetaData":[{"uuid": "someUUID", "menuInfoString": "GET /some/path", "recipient": "someRecipient", "sender": "someSender", "sequenceNumber": 1}]}'));
    step1.merge(step2);
    expect(step1.description).toBe('desc1');
    expect(step1.status).toBe(TestResult.PENDING);
    expect(step1.stepIndex).toBe(4);
    expect(step1.rbelMetaData[0].uuid).toBe("someUUID");
    expect(step1.rbelMetaData[0].menuInfoString).toBe("GET /some/path");
    expect(step1.rbelMetaData[0].recipient).toBe("someRecipient");
    expect(step1.rbelMetaData[0].sender).toBe("someSender");
    expect(step1.rbelMetaData[0].sequenceNumber).toBe(1);
  });


  test('empty description/tooltip merge works correctly', () => {
    const step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "tooltip" : "tooltip1", "status": "FAILED" }'));
    const step2 = StepUpdate.fromJson(JSON.parse('{ "status": "PENDING" }'));
    step1.merge(step2);
    expect(step1.description).toBe('desc1');
    expect(step1.tooltip).toBe('tooltip1')
    expect(step1.status).toBe(TestResult.PENDING);
  });

  test('empty status turned to UNUSED merge works correctly', () => {
    const step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "status": "FAILED" }'));
    const step2 = StepUpdate.fromJson(JSON.parse('{ }'));
    step1.merge(step2);
    expect(step1.description).toBe('desc1');
    expect(step1.status).toBe(TestResult.UNUSED);
  });
});
