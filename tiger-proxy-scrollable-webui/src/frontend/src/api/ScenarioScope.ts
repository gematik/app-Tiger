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

import { computed, type ComputedRef, type InjectionKey, onUnmounted, ref } from "vue";
import type { TimestampRange } from "@/api/MessageQueue.ts";

/**
 * The scenario window - and whether it currently applies - the embedding Workflow UI offers to
 * scope the log to. Standalone nothing ever arrives and the whole feature stays hidden - this UI
 * has no notion of scenarios of its own. The switch itself lives in the Workflow UI, beside its
 * view selector, so that it applies uniformly to the Rbel log, the sequence diagram and the
 * topology diagram at once; this module only reflects whatever state it is told.
 */
export interface ScenarioScopeOffer {
  label?: string;
  min?: string;
  max?: string;
  active?: boolean;
}

export interface UseScenarioScopeReturn {
  /** whether a scenario window was offered at all, i.e. whether to show the scope indicator. */
  available: ComputedRef<boolean>;
  /** whether the offered window is currently applied. */
  active: ComputedRef<boolean>;
  /** name of the scenario the window belongs to, for the tooltip and the empty state. */
  label: ComputedRef<string | undefined>;
  /** the window to filter by, or undefined while the scope is switched off. */
  range: ComputedRef<TimestampRange | undefined>;
}

/** message the Workflow UI sends us whenever the scenario it resolved, or the scoping switch, changes. */
export const SCOPE_MESSAGE = "tiger:scenario-scope";
/** message we send once we are ready to receive the above. */
export const SCOPE_READY_MESSAGE = "tiger:scenario-scope-ready";

/**
 * A scenario that recorded no messages still has to filter everything away once scoped to it, so
 * it gets a window starting after any message can exist. Leaving the window empty instead would be
 * indistinguishable from "not scoped" and would show the whole run.
 */
const AFTER_ANY_MESSAGE = "9999-12-31T23:59:59.999Z";

export const scenarioScopeSymbol: InjectionKey<UseScenarioScopeReturn> =
  Symbol("scenarioScopeSymbol");

/**
 * The offer arrives by postMessage rather than as a URL parameter on purpose: the Workflow UI
 * re-resolves it every time a scenario finishes, and re-navigating the iframe for that would throw
 * away the scroll position, the filter and any expanded message several times per run.
 */
export function useScenarioScope(): UseScenarioScopeReturn {
  const offer = ref<ScenarioScopeOffer>({});

  const available = computed(() => offer.value.label !== undefined);
  const active = computed(() => offer.value.active === true);
  const label = computed(() => offer.value.label);
  const range = computed<TimestampRange | undefined>(() => {
    if (!active.value || !available.value) {
      return undefined;
    }
    const { min, max } = offer.value;
    if (min === undefined && max === undefined) {
      return { min: AFTER_ANY_MESSAGE };
    }
    // The Workflow UI derives max from a message's own JS-Date timestamp, which truncates
    // sub-millisecond precision. This range feeds a REST query compared inclusively against
    // the backend's nanosecond-precision Instant, so a message's own truncated timestamp can
    // be strictly less than its real transmission time - filtering out the scenario's own
    // last message (or, for a single-message scenario, the whole scenario). Rounding up by
    // 1ms covers any sub-millisecond remainder the truncation dropped.
    const bumpedMax =
      max !== undefined ? new Date(new Date(max).getTime() + 1).toISOString() : max;
    return { min, max: bumpedMax };
  });

  // the Workflow UI page we take the offer from - either the embedding parent (iframe) or the
  // opener (a popped-out window, via the "pop out pane" link)
  const bridge = window.opener ?? (window.parent !== window ? window.parent : undefined);

  function onMessage(event: MessageEvent) {
    // only ever from that page, and only our own message shape
    if (event.source !== bridge || event.data?.type !== SCOPE_MESSAGE) {
      return;
    }
    const { label: newLabel, min, max, active: newActive } = event.data;
    offer.value = {
      label: typeof newLabel === "string" ? newLabel : undefined,
      min: typeof min === "string" ? min : undefined,
      max: typeof max === "string" ? max : undefined,
      active: newActive === true,
    };
  }

  if (bridge) {
    window.addEventListener("message", onMessage);
    onUnmounted(() => window.removeEventListener("message", onMessage));
    bridge.postMessage({ type: SCOPE_READY_MESSAGE }, "*");
  }

  return {
    available,
    active,
    label,
    range,
  };
}
