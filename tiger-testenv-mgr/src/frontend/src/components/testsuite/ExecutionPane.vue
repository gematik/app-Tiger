<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div class="tab-pane active" id="execution_pane" role="tabpanel">
    <div
      v-if="featureUpdateMap.size == 0"
      class="alert alert-danger"
      style="height: 200px; width: 100%"
    >
      <i class="fa-regular fa-hourglass left fa-2x"></i> Waiting for first Feature /
      Scenario to start...
    </div>
    <div v-else>
      <h2 class="pt-3">Workflow messages</h2>
      <div
        v-if="bannerData.length > 0"
        :style="`color: ${bannerData[bannerData.length - 1].color};`"
        class="banner alert alert-info"
      >
        <i class="fa-solid fa-circle-exclamation left"></i>
        {{ bannerData[bannerData.length - 1].text }}
      </div>
      <h2 class="mt-3">Current Testrun</h2>
      <div style="display: flex; width: 100%">
        <div id="execution_table" class="pt-1">
          <div v-for="feature in featureUpdateMap">
            <h3 class="w-100 mt-3">
              <i
                :class="`fa-regular fa-address-card left ${feature[1].status.toLowerCase()}`"
              ></i>
              Feature: {{ feature[1].description }} ({{ feature[1].status }})
            </h3>
            <div v-for="scenario in feature[1].scenarios">
              <h4 class="scenariotitle mt-2">
                <i
                  :class="`far fa-clipboard left ${scenario[1].status.toLowerCase()}`"
                ></i>
                <div v-if="scenario[1].variantIndex == -1" style="display:inline-block">
                  Scenario: {{ scenario[1].description }} ({{ scenario[1].status }})
                </div>
                <div v-else style="display:inline-block">
                  Scenario: {{ scenario[1].description }} [{{
                    scenario[1].variantIndex + 1
                  }}] ({{ scenario[1].status }})
                </div>
              </h4>
              <div v-if="scenario[1].variantIndex != -1">
                <div v-for="anzahl in getTableCountForScenarioOutlineKeysLength(scenario[1].exampleKeys)" :key="anzahl">
                   <table class="table table-striped">
                     <thead>
                       <tr>
                         <th v-for="(key, index) in getScenarioOutlineKeysParts(scenario[1].exampleKeys, anzahl)" :key="index" style="word-break: break-all; word-wrap: break-word;">
                            {{ key }}
                         </th>
                       </tr>
                     </thead>
                     <tbody>
                       <tr>
                         <td v-for="(key, index) in getScenarioOutlineKeysParts(scenario[1].exampleKeys, anzahl)" :key="index" style="word-break: break-all; word-wrap: break-word;">
                           {{ scenario[1].exampleList.get(key) }} 
                         </td>
                       </tr>
                    </tbody>
                   </table>
                </div>
              </div>
              <table class="table table-striped">
                <tbody>
                  <tr v-for="step in scenario[1].steps">
                    <th :class="`${step[1].status.toLowerCase()} step_status`">
                      {{ step[1].status }}
                    </th>
                    <td :class="`${step[1].status.toLowerCase()} step_text`">
                      {{ step[1].description }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div
          class="resizer"
          id="rbellog_resize"
          v-on:mouseenter="mouseEnterHandler"
          v-on:mousedown="mouseDownHandler"
          v-on:mouseleave="mouseLeaveHandler"
        ></div>
        <div class="d-none pl-3 pt-3" id="rbellog_details_pane">
          <h2><img src="img/rbellog.png" width="50" /> Rbel Log Details</h2>
          <div class="m-auto text-danger">
            <i class="fa-solid fa-circle-exclamation fa-2x left"></i> Not implemented so
            far
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import BannerMessages from "@/types/BannerMessages";

defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
  bannerData: BannerMessages[];
}>();

// elements
let rbelLogDetailsResizer: HTMLElement | null;
let executionTable: Element | null;
let rbelLogDetailsPane: HTMLElement | null;

// The current position of mouse
let x = -1;

function initElementReferences() {
  rbelLogDetailsResizer = document.getElementById("rbellog_resize");
  executionTable = document.getElementById("execution_table");
  rbelLogDetailsPane = document.getElementById("rbellog_details_pane");

  if (!rbelLogDetailsResizer || !executionTable || !rbelLogDetailsPane) {
    throw new Error("Internal error - Unable to find UI element(s)!");
  }
}

// Handle the mousedown event
// that's triggered when user drags the resizer
function mouseDownHandler(e: MouseEvent) {
  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }
  // Get the current mouse position
  x = e.clientX;
  document.addEventListener("mousemove", mouseMoveHandler);
  document.addEventListener("mouseup", mouseUpHandler);
}

function mouseMoveHandler(e: MouseEvent) {
  if (x === -1) {
    return;
  }

  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }

  rbelLogDetailsResizer.style.cursor = "col-resize";
  document.body.style.cursor = "col-resize";

  // How far the mouse has been moved
  const dx = e.clientX - x;
  const prevWidth = executionTable.clientWidth;

  rbelLogDetailsPane.style.width =
    executionTable.parentElement.clientWidth -
    executionTable.clientWidth -
    dx -
    rbelLogDetailsResizer.clientWidth -
    30 +
    "px";
  executionTable.style.width = executionTable.clientWidth + dx + "px";

  if (executionTable.clientWidth !== prevWidth) {
    x = e.clientX;
  }

  document.body.style.userSelect = "none";
  executionTable.style.userSelect = "none";
  executionTable.style.pointerEvents = "none";
  rbelLogDetailsPane.style.userSelect = "none";
  rbelLogDetailsPane.style.pointerEvents = "none";

  let classes = rbelLogDetailsPane.getAttribute("class");
  if (executionTable.parentElement.clientWidth - executionTable.clientWidth < 200) {
    if (classes.indexOf("d-none") === -1 && dx > 0) {
      classes += " d-none";
      rbelLogDetailsPane.setAttribute("class", classes);
      executionTable.style.width = executionTable.parentElement.clientWidth - 10 + "px";
      mouseUpHandler();
      return;
    }
  } else {
    if (classes.indexOf("d-none") !== -1) {
      classes = classes.replace("d-none", "");
    }
  }
  rbelLogDetailsPane.setAttribute("class", classes);
}

function mouseUpHandler() {
  x = -1;

  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }
  rbelLogDetailsResizer.style.removeProperty("cursor");
  document.body.style.removeProperty("cursor");

  executionTable.style.removeProperty("user-select");
  executionTable.style.removeProperty("pointer-events");
  rbelLogDetailsPane.style.removeProperty("user-select");
  rbelLogDetailsPane.style.removeProperty("pointer-events");

  document.removeEventListener("mousemove", mouseMoveHandler);
  document.removeEventListener("mouseup", mouseUpHandler);
}

function mouseEnterHandler() {
  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }
  rbelLogDetailsResizer.style.cursor = "col-resize";
  document.body.style.cursor = "col-resize";
}

function mouseLeaveHandler() {
  rbelLogDetailsResizer.style.removeProperty("cursor");
  document.body.style.removeProperty("cursor");
}

const maxOutlineTableColumns = 4;
function getTableCountForScenarioOutlineKeysLength(list: Array<string>): number {
  return (Math.ceil(list.length/maxOutlineTableColumns));
}

function getScenarioOutlineKeysParts(list: Array<string>, count: number): Array<string> {
  var partScenarioOutlineList = new Array<string>();
  list.forEach((element, index)  => {
    if (index < (count * maxOutlineTableColumns) && index >= maxOutlineTableColumns * (count - 1)) {
      partScenarioOutlineList.push(element); 
    }
  });
  return partScenarioOutlineList;
}
</script>

<style scoped>
.banner {
  font-weight: bolder;
  font-size: 150%;
  margin-bottom: 0;
  text-align: center;
  padding: 3rem;
}

#execution_pane {
  border-left: 1px solid lightgray;
  padding-left: 1rem;
}

#execution_table {
  display: flex;
  width: 100%;
}

.scenariotitle {
  border-top: 3px solid gray;
  padding: 1rem;
}

.step_status {
  text-align: center;
  vertical-align: middle;
}

.step_text {
  font-size: 80%;
  width: 99%;
}

.step_text.feature {
  font-size: 100%;
  font-weight: bold;
}

.step_text.scenario {
  font-size: 100%;
  font-style: italic;
}

.resizer {
  width: 0.5rem;
  border-left: 3px solid gray;
  background: rgba(224, 224, 224, 0.5);
}

#rbellog_details_pane {
  background: whitesmoke;
}
</style>
