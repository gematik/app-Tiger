/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
