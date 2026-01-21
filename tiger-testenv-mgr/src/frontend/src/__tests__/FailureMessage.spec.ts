///
///
/// Copyright 2021-2025 gematik GmbH
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
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

import { mount } from "@vue/test-utils";
import FailureMessage from "../components/testsuite/FailureMessage.vue";
import type { IMismatchNote } from "@/types/testsuite/MismatchNote.ts";
import type MessageMetaDataDto from "@/types/rbel/MessageMetaDataDto.ts";

const fakeEmitter = { emit: vi.fn(), on: vi.fn(), off: vi.fn() };

// Simple stub component for PrimeVue Dropdown
const DropdownStub = {
  name: "Dropdown",
  props: ["options", "optionLabel", "optionValue", "modelValue"],
  emits: ["update:modelValue"],
  template: `
      <select data-testid="mismatch-dropdown" :value="modelValue"
              @change="$emit('update:modelValue', Number($event.target.value))">
        <option v-for="opt in options" :key="opt.value" :value="opt.value">{{ opt.text }}</option>
      </select>
    `,
};

// Provide default props at module scope so helper can access them.
const defaultProps = {
  message: "Something went wrong",
  stacktrace: "Error at line 1",
  mismatchNotes: [] as IMismatchNote[],
  allRbelMetaData: [] as MessageMetaDataDto[],
};

// Helper mount function to avoid repetition.
function mountFailureMessage(extraProps = {}) {
  return mount(FailureMessage, {
    props: { ...defaultProps, ...extraProps },
    global: {
      stubs: {
        Dropdown: DropdownStub,
      },
      provide: { emitter: fakeEmitter },
    },
  });
}

describe("FailureMessage.vue", () => {
  it("does not render dropdown when there are no mismatch notes", () => {
    const wrapper = mountFailureMessage();
    expect(wrapper.find("select").exists()).toBe(false);
  });

  it("renders dropdown and options when mismatch notes are provided", async () => {
    const notes = [
      {
        mismatchType: "VALUE_MISMATCH",
        sequenceNumber: 1,
        rbelPath: "/a",
        value: "First note",
      },
      {
        mismatchType: "MISSING_NODE",
        sequenceNumber: 2,
        rbelPath: "/b",
        value: "Second note",
      },
    ];
    const metas = [
      { sequenceNumber: 1, uuid: "uuid-1" },
      { sequenceNumber: 2, uuid: "uuid-2" },
    ];
    const wrapper = mountFailureMessage({
      mismatchNotes: notes,
      allRbelMetaData: metas,
    });

    const select = wrapper.find("select");
    expect(select.exists()).toBe(true);

    const options = select.findAll("option");
    expect(options.length).toBe(notes.length);
    expect(options[0].text()).toBe("First note");
    expect(options[1].text()).toBe("Second note");

    // Simulate selecting the second note
    await select.setValue("1");
    expect(fakeEmitter.emit).toHaveBeenCalledWith(
      "scrollToRbelLogMessage",
      "uuid-2",
    );
  });

  it("navigates to correct message on dropdown change", async () => {
    const notes = [
      {
        mismatchType: "VALUE_MISMATCH",
        sequenceNumber: 10,
        rbelPath: "/x",
        value: "Note X",
      },
      {
        mismatchType: "WRONG_PATH",
        sequenceNumber: 20,
        rbelPath: "/y",
        value: "Note Y",
      },
    ];
    const metas = [
      { sequenceNumber: 10, uuid: "u1" },
      { sequenceNumber: 20, uuid: "u2" },
    ];
    const wrapper = mountFailureMessage({
      mismatchNotes: notes,
      allRbelMetaData: metas,
    });

    const select = wrapper.find("select");
    // Select first note (index 0)
    await select.setValue("0");
    expect(fakeEmitter.emit).toHaveBeenCalledWith(
      "scrollToRbelLogMessage",
      "u1",
    );

    // Select second note (index 1)
    await select.setValue("1");
    expect(fakeEmitter.emit).toHaveBeenCalledWith(
      "scrollToRbelLogMessage",
      "u2",
    );
  });
});
