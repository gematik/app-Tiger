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

  test('complete merge works correctly', () => {
    let step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "status": "FAILED" }'));
    let step2 = StepUpdate.fromJson(JSON.parse('{ "description": "desc2", "status": "PENDING" }'));
    step1.merge(step2);
    expect(step1.description).toBe('desc2');
    expect(step1.status).toBe(TestResult.PENDING);
  });

  test('empty description merge works correctly', () => {
    let step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "status": "FAILED" }'));
    let step2 = StepUpdate.fromJson(JSON.parse('{ "status": "PENDING" }'));
    step1.merge(step2);
    expect(step1.description).toBe('desc1');
    expect(step1.status).toBe(TestResult.PENDING);
  });

  test('empty status turned to UNUSED merge works correctly', () => {
    let step1 = StepUpdate.fromJson(JSON.parse('{ "description": "desc1", "status": "FAILED" }'));
    let step2 = StepUpdate.fromJson(JSON.parse('{ }'));
    step1.merge(step2);
    expect(step1.description).toBe('desc1');
    expect(step1.status).toBe(TestResult.UNUSED);
  });
});
