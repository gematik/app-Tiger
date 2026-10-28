///
///
/// Copyright 2021-2026 gematik GmbH
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

import { afterEach, describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import { defineComponent, h, nextTick, ref } from "vue";
import { useRecycleScroller } from "vue-virtual-scroller/composables/useRecycleScroller";
import { useDynamicScroller } from "vue-virtual-scroller/composables/useDynamicScroller";

type Row = { uuid: string; type: string };
const row = (uuid: string, type = "message"): Row => ({ uuid, type });
const cleanup: Array<() => void> = [];
const DropdownRow = defineComponent({
  props: { uuid: { type: String, required: true } },
  setup(props) {
    const open = ref(false);
    return () => h("button", {
      "data-uuid": props.uuid,
      "aria-expanded": String(open.value),
      onClick: () => { open.value = !open.value; },
    }, props.uuid);
  },
});

afterEach(() => {
  cleanup.splice(0).forEach((unmount) => unmount());
});

function createScroller(initial: Row[], flowMode = false) {
  const items = ref(initial);
  const element = document.createElement("div");
  Object.defineProperty(element, "clientHeight", { value: 200 });
  document.body.append(element);
  let scroller!: ReturnType<typeof useRecycleScroller<Row>>;
  const wrapper = mount(defineComponent({
    setup() {
      scroller = useRecycleScroller({
        items,
        el: element,
        keyField: "uuid",
        typeField: "type",
        itemSize: 50,
        minItemSize: 50,
        buffer: 0,
        pageMode: false,
        prerender: 0,
        emitUpdate: false,
        updateInterval: 0,
        flowMode,
      });
      return () => h("div", scroller.pool.value.map((view) =>
        h("div", { key: view.nr.id }, [
          h(DropdownRow, { key: view.item.uuid, uuid: view.item.uuid }),
        ]),
      ));
    },
  }));
  scroller.updateVisibleItems(true);
  cleanup.push(() => {
    wrapper.unmount();
    element.remove();
  });
  return { items, element, scroller, wrapper };
}

describe("scroller view stability on live list updates", () => {
  it("preserves the open dropdown through dynamic-scroller wrapper updates", async () => {
    const items = ref([row("a"), row("b")]);
    const element = document.createElement("div");
    Object.defineProperty(element, "clientHeight", { value: 200 });
    document.body.append(element);
    let scroller!: ReturnType<typeof useDynamicScroller<Row>>;
    const wrapper = mount(defineComponent({
      setup() {
        scroller = useDynamicScroller({
          items,
          el: element,
          keyField: "uuid",
          minItemSize: 50,
          buffer: 0,
        });
        return () => h("div", scroller.pool.value.map((view) =>
          h("div", { key: view.id }, [
            h(DropdownRow, { key: view.item.uuid, uuid: view.item.uuid }),
          ]),
        ));
      },
    }));
    cleanup.push(() => {
      wrapper.unmount();
      element.remove();
    });
    scroller.updateVisibleItems(true);
    await nextTick();
    const button = wrapper.get('[data-uuid="a"]');
    await button.trigger("click");
    for (const next of [
      [row("a"), row("b"), row("c")],
      [row("d"), row("c"), row("b"), row("a")],
      [row("b"), row("a")],
    ]) {
      items.value = next;
      await nextTick();
      scroller.updateVisibleItems(true);
      await nextTick();
      expect(wrapper.get('[data-uuid="a"]').element).toBe(button.element);
      expect(wrapper.get('[data-uuid="a"]').attributes("aria-expanded")).toBe("true");
    }
  });

  it("keeps the same open dropdown DOM node when new messages arrive", async () => {
    const { items, scroller, wrapper } = createScroller([row("a"), row("b")]);
    await nextTick();
    const button = wrapper.get('[data-uuid="a"]');
    await button.trigger("click");
    items.value = [...items.value, row("c")];
    await nextTick();
    scroller.updateVisibleItems(true);
    await nextTick();
    expect(wrapper.get('[data-uuid="a"]').element).toBe(button.element);
    expect(wrapper.get('[data-uuid="a"]').attributes("aria-expanded")).toBe("true");
  });

  it.each([false, true])("retains visible views on append (flowMode=%s)", async (flowMode) => {
    const { items, scroller } = createScroller([row("a"), row("b")], flowMode);
    const before = new Map(scroller.pool.value.map((view) => [view.nr.key, view]));
    items.value = [...items.value, row("c")];
    await nextTick();
    scroller.updateVisibleItems(true);
    for (const key of ["a", "b"]) {
      expect(scroller.pool.value.find((view) => view.nr.used && view.nr.key === key)).toBe(before.get(key));
    }
  });

  it("retains views and updates indexes on prepend and reorder", async () => {
    const { items, scroller } = createScroller([row("a"), row("b")]);
    const before = new Map(scroller.pool.value.map((view) => [view.nr.key, view]));
    items.value = [row("c"), row("b"), row("a")];
    await nextTick();
    scroller.updateVisibleItems(true);
    expect(scroller.pool.value.find((view) => view.nr.used && view.nr.key === "a")).toBe(before.get("a"));
    expect(before.get("a")?.nr.index).toBe(2);
    expect(scroller.pool.value.find((view) => view.nr.used && view.nr.key === "b")).toBe(before.get("b"));
  });

  it("releases removed views without replacing surviving views", async () => {
    const { items, scroller } = createScroller([row("a"), row("b"), row("c")]);
    const survivor = scroller.pool.value.find((view) => view.nr.key === "b");
    items.value = [row("b"), row("d")];
    await nextTick();
    scroller.updateVisibleItems(true);
    expect(scroller.pool.value.find((view) => view.nr.used && view.nr.key === "b")).toBe(survivor);
    expect(scroller.pool.value.filter((view) => view.nr.used).map((view) => view.nr.key).sort()).toEqual(["b", "d"]);
  });

  it("still recycles views when scrolling to a different range", () => {
    const { element, scroller } = createScroller(Array.from({ length: 20 }, (_, index) => row(String(index))));
    const before = new Set(scroller.pool.value);
    element.scrollTop = 500;
    scroller.updateVisibleItems(false);
    const active = scroller.pool.value.filter((view) => view.nr.used);
    expect(active.every((view) => view.nr.index >= 10)).toBe(true);
    expect(active.some((view) => before.has(view))).toBe(true);
  });

  it("does not retain an incompatible view when an item's type changes", async () => {
    const { items, scroller } = createScroller([row("a")]);
    const previous = scroller.pool.value.find((view) => view.nr.used);
    items.value = [row("a", "other")];
    await nextTick();
    scroller.updateVisibleItems(true);
    const current = scroller.pool.value.find((view) => view.nr.used);
    expect(current).not.toBe(previous);
    expect(current?.nr.type).toBe("other");
  });
});
