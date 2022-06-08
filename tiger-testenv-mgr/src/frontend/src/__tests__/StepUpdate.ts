/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import StepUpdate from "../types/testsuite/StepUpdate";
import TestResult from "../types/testsuite/TestResult";

describe('testing StepUpdate class', () => {

  test('empty step should contain undefined values', () => {
    let step = new StepUpdate();
    expect(step.description).toBe('');
    expect(step.status).toBe(TestResult.UNUSED);
    expect(step.stepIndex).toEqual(-1);
  });

  test('empty JSON applies correctly', () => {
    let step = StepUpdate.fromJson(JSON.parse('{ }'));
    expect(step.description).toBe('');
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test('empty JSON description applies correctly', () => {
    let step = StepUpdate.fromJson(JSON.parse('{ "status": "PASSED" }'));
    expect(step.description).toBe('');
    expect(step.status).toBe(TestResult.PASSED);
  });

  test('empty JSON status applies correctly', () => {
    let step = StepUpdate.fromJson(JSON.parse('{ "description": "desc" }'));
    expect(step.description).toBe('desc');
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON index applies correctly", () => {
    let step = StepUpdate.fromJson(JSON.parse('{ "stepIndex":3 }'));
    expect(step.stepIndex).toBe(3);
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON rbelMetaData applies correctly", () => {
    let step = StepUpdate.fromJson(JSON.parse('{ "rbelMetaData":[] }'));
    expect(step.rbelMetaData.length).toBe(0);
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON full rbelMetaData applies correctly", () => {
    let step = StepUpdate.fromJson(JSON.parse('{ "rbelMetaData":[{"uuid": "someUUID", "path": "/some/path", "method": "someMethod", "responseCode": 200, "recipient": "someRecipient", "sender": "someSender", "sequenceNumber": 1}]}'));
    expect(step.rbelMetaData.length).toBe(1);
    expect(step.rbelMetaData[0].uuid).toBe("someUUID");
    expect(step.rbelMetaData[0].path).toBe("/some/path");
    expect(step.rbelMetaData[0].method).toBe("someMethod");
    expect(step.rbelMetaData[0].responseCode).toBe(200);
    expect(step.rbelMetaData[0].recipient).toBe("someRecipient");
    expect(step.rbelMetaData[0].sender).toBe("someSender");
    expect(step.rbelMetaData[0].sequenceNumber).toBe(1);
    expect(step.status).toBe(TestResult.UNUSED);
  });

  test('full JSON applies correctly', () => {
    let step = StepUpdate.fromJson(JSON.parse('{"description":"Then User requests classes with parameter xyz=4","status":"PASSED","stepIndex":3,"rbelMetaData":[]}'));
    expect(step.description).toBe('Then User requests classes with parameter xyz=4');
    expect(step.status).toBe(TestResult.PASSED);
  });

  test('complete merge works correctly', () => {
    let step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "status": "FAILED", "stepIndex":3, "rbelMetaData":[]}'));
    let step2 = StepUpdate.fromJson(JSON.parse('{ "description": "desc2", "status": "PENDING", "stepIndex":4, "rbelMetaData":[{"uuid": "someUUID", "path": "/some/path", "method": "someMethod", "responseCode": 200, "recipient": "someRecipient", "sender": "someSender", "sequenceNumber": 1}]}'));
    step1.merge(step2);
    expect(step1.description).toBe('desc2');
    expect(step1.status).toBe(TestResult.PENDING);
    expect(step1.stepIndex).toBe(4);
    expect(step1.rbelMetaData[0].uuid).toBe("someUUID");
    expect(step1.rbelMetaData[0].path).toBe("/some/path");
    expect(step1.rbelMetaData[0].method).toBe("someMethod");
    expect(step1.rbelMetaData[0].responseCode).toBe(200);
    expect(step1.rbelMetaData[0].recipient).toBe("someRecipient");
    expect(step1.rbelMetaData[0].sender).toBe("someSender");
    expect(step1.rbelMetaData[0].sequenceNumber).toBe(1);
  });

  test('empty description merge works correctly', () => {
    let step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "status": "FAILED" }'));
    let step2 = StepUpdate.fromJson(JSON.parse('{ "status": "PENDING","stepIndex":4, "rbelMetaData":[{"uuid": "someUUID", "path": "/some/path", "method": "someMethod", "responseCode": 200, "recipient": "someRecipient", "sender": "someSender", "sequenceNumber": 1}]}'));
    step1.merge(step2);
    expect(step1.description).toBe('desc1');
    expect(step1.status).toBe(TestResult.PENDING);
    expect(step1.stepIndex).toBe(4);
    expect(step1.rbelMetaData[0].uuid).toBe("someUUID");
    expect(step1.rbelMetaData[0].path).toBe("/some/path");
    expect(step1.rbelMetaData[0].method).toBe("someMethod");
    expect(step1.rbelMetaData[0].responseCode).toBe(200);
    expect(step1.rbelMetaData[0].recipient).toBe("someRecipient");
    expect(step1.rbelMetaData[0].sender).toBe("someSender");
    expect(step1.rbelMetaData[0].sequenceNumber).toBe(1);
  });

  test('empty status turned to UNUSED merge works correctly', () => {
    let step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "status": "FAILED" }'));
    let step2 = StepUpdate.fromJson(JSON.parse('{ }'));
    step1.merge(step2);
    expect(step1.description).toBe('desc1');
    expect(step1.status).toBe(TestResult.UNUSED);
  });
});
