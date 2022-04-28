<template>
  <div class="tab-pane active" id="execution_pane" role="tabpanel">
    <div v-if="featureUpdateMap.size == 0" class="alert alert-danger" style="height:200px;width:100%;">
      <i class="fa-regular fa-hourglass left fa-2x"></i> Waiting for first Feature / Scenario to start...
    </div>
    <div v-else>
      <h2 class="pt-3">Workflow messages</h2>
      <div v-if="bannerData.length > 0" :style="`color: ${bannerData[bannerData.length-1].color};`"
           class="banner alert alert-info text-center w-100 p-5`">
        <i class="fa-solid fa-circle-exclamation left"></i> {{ bannerData[bannerData.length - 1].text }}
      </div>
      <h2 class="mt-3">Testrun Features / Scenarios</h2>
      <div style="display:flex;width:100%;">
        <div id="execution_table" class="pt-1">
          <div v-for="(feature) in featureUpdateMap">
            <h3><i class="fa-regular fa-address-card left"></i> Feature: {{ feature[1].description }}</h3>
            <div v-for="(scenario) in feature[1].scenarios">
              <h4><i class="far fa-clipboard left"></i> Scenario: {{ scenario[1].description }}</h4>
              <table class="table table-striped">
                <tbody>
                <tr v-for="(step) in scenario[1].steps">
                  <th :class="`${step[1].status.toLowerCase()} step_status`">{{ step[1].status }}</th>
                  <td :class="`${step[1].status.toLowerCase()} step_text`">{{ step[1].description }}</td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div class="resizer" id="rbellog_resize"
             v-on:mouseenter="mouseEnterHandler"
             v-on:mousedown="mouseDownHandler"
             v-on:mouseleave="mouseLeaveHandler">
        </div>
        <div class="d-none pl-3 pt-3" id="rbellog_details_pane">
          <h2><img src="img/rbellog.png" width="50"> Rbel Log Details</h2>
          <div class="m-auto text-danger">
            <i class="fa-solid fa-circle-exclamation fa-2x left"></i> Not implemented so far
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import DataType from "@/types/DataType";
import TestResult from "@/types/testsuite/TestResult";
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import BannerMessages from "@/types/BannerMessages";

defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
  bannerData: BannerMessages[];
}>();

// elements
let rbelLogDetailsResizer: HTMLElement;
let executionPane: HTMLElement;
let rbelLogDetailsPane: HTMLElement;

/*const resizer = document.getElementById('dragMe');
const executionPane = resizer.previousElementSibling;
const rightSide = resizer.nextElementSibling;
*/
// The current position of mouse
let x = -1;
let y = 0;
let leftWidth = -1;

function initElementReferences() {
  rbelLogDetailsResizer = document.getElementById('rbellog_resize');
  executionPane = rbelLogDetailsResizer.previousElementSibling;
  rbelLogDetailsPane = document.getElementById('rbellog_details_pane');
}

// Handle the mousedown event
// that's triggered when user drags the resizer
function mouseDownHandler(e: MouseEvent) {
  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }
  // Get the current mouse position
  x = e.clientX;
  y = e.clientY;
  leftWidth = executionPane.clientWidth;
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

  rbelLogDetailsResizer.style.cursor = 'col-resize';
  document.body.style.cursor = 'col-resize';

  // How far the mouse has been moved
  const dx = e.clientX - x;
  const prevWidth = executionPane.clientWidth;

  rbelLogDetailsPane.style.width = executionPane.parentElement.clientWidth - executionPane.clientWidth - dx - rbelLogDetailsResizer.clientWidth - 30 + 'px';
  executionPane.style.width = (executionPane.clientWidth + dx) + 'px';

  if (executionPane.clientWidth !== prevWidth) {
    x = e.clientX;
  }

  document.body.style.userSelect = 'none';
  executionPane.style.userSelect = 'none';
  executionPane.style.pointerEvents = 'none';
  rbelLogDetailsPane.style.userSelect = 'none';
  rbelLogDetailsPane.style.pointerEvents = 'none';

  let classes = rbelLogDetailsPane.getAttribute("class");
  if (executionPane.parentElement.clientWidth - executionPane.clientWidth < 200) {
    if (classes.indexOf("d-none") === -1 && dx > 0) {
      classes += " d-none";
      rbelLogDetailsPane.setAttribute("class", classes);
      executionPane.style.width = executionPane.parentElement.clientWidth - 10 + 'px';
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
  rbelLogDetailsResizer.style.removeProperty('cursor');
  document.body.style.removeProperty('cursor');

  executionPane.style.removeProperty('user-select');
  executionPane.style.removeProperty('pointer-events');
  rbelLogDetailsPane.style.removeProperty('user-select');
  rbelLogDetailsPane.style.removeProperty('pointer-events');

  document.removeEventListener('mousemove', mouseMoveHandler);
  document.removeEventListener('mouseup', mouseUpHandler);
}

function mouseEnterHandler() {
  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }
  rbelLogDetailsResizer.style.cursor = 'col-resize';
  document.body.style.cursor = 'col-resize';
}

function mouseLeaveHandler() {
  rbelLogDetailsResizer.style.removeProperty('cursor');
  document.body.style.removeProperty('cursor');
}

</script>

<style scoped>
.banner {
  font-weight: bolder;
  font-family: "Courier New", monospace;
  font-size: 150%;
  margin-bottom: 0;
}

#execution_pane {
  border-left: 1px solid lightgray;
  padding-left: 1rem;
}

#execution_table {
  display: flex;
  width: 100%;
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

.passed {
  color: forestgreen !important;
}

.skipped {
  color: darkorange !important;
}

.pending, .unused {
  color: lightslategray !important;
}

.undefined, .ambiguous {
  color: orchid !important;
}

.failed {
  color: orangered !important;
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
