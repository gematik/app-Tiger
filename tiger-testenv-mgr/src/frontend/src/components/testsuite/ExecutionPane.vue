<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div class="tab-pane active" id="execution_pane" role="tabpanel">
    <div v-if="featureUpdateMap.size === 0" class="alert alert-danger w-100" style="height: 200px;">
      <i class="fa-regular fa-hourglass left fa-2x"></i> Waiting for first Feature / Scenario to start...
    </div>
    <div v-else>
      <div id="workflow-messages">
        <h2 v-if="bannerData.length > 0" class="pt-3">Workflow messages</h2>
        <div v-if="bannerData.length > 0" :style="`color: ${bannerData[bannerData.length-1].color};`"
             class="banner">
          <i class="fa-solid fa-circle-exclamation left"></i> {{ bannerData[bannerData.length - 1].text }}
        </div>
      </div>
      <h2 class="mt-3">Current Testrun</h2>
      <div class="d-flex w-100">
        <div id="execution_table" class="pt-1">
          <div v-for="(feature) in featureUpdateMap" class="w-100">
            <h3 class="w-100 mt-3">
              <i :class="`fa-regular fa-address-card left ${feature[1].status.toLowerCase()}`"></i>
              Feature: {{ feature[1].description }} ({{ feature[1].status }})
            </h3>
            <div v-for="scenario in feature[1].scenarios">
              <h4 class="scenariotitle mt-2">
                <i :class="`far fa-clipboard left ${scenario[1].status.toLowerCase()}`"></i>
                Scenario: {{ scenario[1].description }}
                <span v-if="scenario[1].variantIndex !== -1">
                  [{{ scenario[1].variantIndex + 1 }}]
                </span>
                ({{ scenario[1].status }})
              </h4>
              <div v-if="scenario[1].variantIndex !== -1">
                <div v-for="anzahl in getTableCountForScenarioOutlineKeysLength(scenario[1].exampleKeys)" :key="anzahl"
                     class="d-inline-block">
                  <table class="table table-sm table-info table-data-variant">
                    <thead>
                    <tr>
                      <th v-for="(key, index) in getScenarioOutlineKeysParts(scenario[1].exampleKeys, anzahl)" :key="index">
                        {{ key }}
                      </th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                      <td v-for="(key, index) in getScenarioOutlineKeysParts(scenario[1].exampleKeys, anzahl)" :key="index">
                        {{ scenario[1].exampleList.get(key) }}
                      </td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </div>
              <table class="table table-striped">
                <tbody>
                <tr v-for="(step) in scenario[1].steps">
                  <th :class="`${step[1].status.toLowerCase()} step_status`">{{ step[1].status }}</th>
                  <td :class="`${step[1].status.toLowerCase()} step_text`">
                    <div>{{ step[1].description }}</div>
                    <div v-for="(rbelmsg, index) in step[1].rbelMetaData">
                      <div v-if="rbelmsg.method">
                        <a v-on:click="showRbelLogDetails(rbelmsg.uuid, $event)"
                           href="#" class="badge bg-info rbelDetailsBadge">
                          {{ rbelmsg.sequenceNumber + 1 }}
                        </a>
                        <b>{{ rbelmsg.method }} {{ step[1].rbelMetaData[index+1].responseCode }}</b>
                        <span>&nbsp;&nbsp;&nbsp;&rarr;&nbsp;&nbsp;&nbsp;
                        {{ rbelmsg.recipient }}{{ rbelmsg.path }}</span>
                      </div>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
      <div class="resizer position-fixed" id="rbellog_resize"
           v-on:mouseenter="mouseEnterHandler"
           v-on:mousedown="mouseDownHandler"
           v-on:mouseleave="mouseLeaveHandler">
        <i v-on:click="toggleRightSideBar" class="fa-solid fa-circle-chevron-left fa-2x resizer-right"></i>
      </div>
      <div class="d-none position-fixed pl-3 pt-3" id="rbellog_details_pane">
        <h2><img alt="RBel logo" src="img/rbellog.png" class="rbel-logo"> Rbel Log Details</h2>
        <iframe id="rbellog-details-iframe" class="h-100 w-100" :src="`${localProxyWebUiUrl}/?updateMode=update1&embedded=true`"/>
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
  localProxyWebUiUrl: string;
}>();

function toggleRightSideBar(event: MouseEvent) {
  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }

  event.preventDefault();
  if (rbelLogDetailsPane.getAttribute('class')?.indexOf('d-none') !== -1) {
    resizeBasedOn(-executionTable.parentElement.clientWidth / 2, false);
  } else {
    executionTable.style.width = executionTable.parentElement.clientWidth - 10 + 'px';
    workflowMessagesDiv.style.width = executionTable.clientWidth - 10 + 'px';

    mouseUpHandler();
  }
  toggleRightSideBarIcon();
}

function toggleRightSideBarIcon() {
  const icon: HTMLElement = document.getElementsByClassName('resizer-right')[0] as HTMLElement;
  if (rbelLogDetailsPane?.getAttribute("class")?.indexOf("d-none") !== -1) {
    replaceCssClass(icon, 'circle-chevron-right', 'circle-chevron-left');
  } else {
    replaceCssClass(icon, 'circle-chevron-left', 'circle-chevron-right');
  }
}

function replaceCssClass(elem: HTMLElement, search: string, replacement: string): void {
  if (!elem) return;
  const css = elem.getAttribute('class');
  if (css) {
    elem.setAttribute('class', css.replace(search, replacement));
  }
}

function showRbelLogDetails(rbelMessageUuid: string, event: MouseEvent) {
  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }

  const iframe = document.getElementById("rbellog-details-iframe") as HTMLIFrameElement;
  if (iframe) {
    iframe.src = iframe.src.split("#")[0] + "#" + rbelMessageUuid;
  }
  if (rbelLogDetailsPane?.getAttribute("class")?.indexOf("d-none") !== -1) {
    resizeBasedOn((-1) * executionTable?.clientWidth / 2, false);
    mouseUpHandler();
  }
  event.preventDefault();
}

// elements
let rbelLogDetailsResizer: HTMLElement;
let executionTable: HTMLElement;
let rbelLogDetailsPane: HTMLElement;
let workflowMessagesDiv: HTMLDivElement;

// The current position of mouse
let x = -1;

function initElementReferences() {
  rbelLogDetailsResizer = document.getElementById('rbellog_resize') as HTMLElement;
  executionTable = document.getElementById('execution_table') as HTMLElement;
  rbelLogDetailsPane = document.getElementById('rbellog_details_pane') as HTMLElement;
  workflowMessagesDiv = document.getElementById('workflow-messages') as HTMLDivElement;

  if (!rbelLogDetailsResizer || !executionTable || !rbelLogDetailsPane || !workflowMessagesDiv) {
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

  const prevWidth = executionTable.clientWidth;
  resizeBasedOn(e.clientX - x, true);
  if (executionTable.clientWidth !== prevWidth) {
    x = e.clientX
  }
}

function resizeBasedOn(dx: number, disableSelection: boolean) {
  rbelLogDetailsResizer.style.cursor = 'col-resize';
  document.body.style.cursor = 'col-resize';

  // How far the mouse has been moved

  //rbelLogDetailsPane.style.width = executionTable.parentElement.clientWidth - executionTable.clientWidth - dx - rbelLogDetailsResizer.clientWidth - 30 + 'px';
  workflowMessagesDiv.style.width = (executionTable.clientWidth + dx) + 'px';
  executionTable.style.width = (executionTable.clientWidth + dx) + 'px';


  rbelLogDetailsPane.style.width = executionTable.parentElement.clientWidth - executionTable.clientWidth + 'px';
  rbelLogDetailsPane.style.right = '0';
  rbelLogDetailsResizer.style.width = '10px';
  rbelLogDetailsResizer.style.right = executionTable.parentElement.clientWidth - executionTable.clientWidth + 'px';

  if (disableSelection) {
    document.body.style.userSelect = 'none';
    executionTable.style.userSelect = 'none';
    executionTable.style.pointerEvents = 'none';
    rbelLogDetailsPane.style.userSelect = 'none';
    rbelLogDetailsPane.style.pointerEvents = 'none';
  }

  let classes = rbelLogDetailsPane.getAttribute("class");
  if (executionTable.parentElement.clientWidth - executionTable.clientWidth < 300) {
    if (classes.indexOf("d-none") === -1 && dx > 0) {
      classes += " d-none";
      rbelLogDetailsPane.setAttribute("class", classes);
      x = executionTable.clientWidth;
      rbelLogDetailsResizer.style.width = '10px';
      rbelLogDetailsResizer.style.right = 0;
      executionTable.style.width = executionTable.parentElement.clientWidth - 10 + 'px';
      workflowMessagesDiv.style.width = executionTable.parentElement.clientWidth - 10 + 'px';
      return;
    }
  } else {
    if (classes.indexOf("d-none") !== -1) {
      classes = classes.replace("d-none", "");
    }
  }
  rbelLogDetailsPane.setAttribute("class", classes);
  toggleRightSideBarIcon();
}

function mouseUpHandler() {
  x = -1;

  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }
  rbelLogDetailsResizer.style.removeProperty('cursor');
  document.body.style.removeProperty('cursor');
  document.body.style.removeProperty('user-select');

  executionTable.style.removeProperty('user-select');
  executionTable.style.removeProperty('pointer-events');
  rbelLogDetailsPane.style.removeProperty('user-select');
  rbelLogDetailsPane.style.removeProperty('pointer-events');

  document.removeEventListener('mousemove', mouseMoveHandler);
  document.removeEventListener('mouseup', mouseUpHandler);

  let classes = rbelLogDetailsPane.getAttribute("class");
  if (executionTable.parentElement.clientWidth - executionTable.clientWidth < 300) {
    if (classes.indexOf("d-none") === -1) {
      classes += " d-none";
      rbelLogDetailsPane.setAttribute("class", classes);
    }
    rbelLogDetailsResizer.style.width = '10px';
    rbelLogDetailsResizer.style.right = 0;
    executionTable.style.width = executionTable.parentElement.clientWidth - 10 + 'px';
    workflowMessagesDiv.style.width = executionTable.parentElement.clientWidth - 10 + 'px';
  }
}

function mouseEnterHandler() {
  if (!rbelLogDetailsResizer) {
    initElementReferences();
  }
  rbelLogDetailsResizer.style.cursor = 'col-resize';
  document.body.style.cursor = 'col-resize';
}

function mouseLeaveHandler() {
  rbelLogDetailsResizer.style.removeProperty("cursor");
  document.body.style.removeProperty("cursor");
}

const maxOutlineTableColumns = 4;

function getTableCountForScenarioOutlineKeysLength(list: Array<string>): number {
  return (Math.ceil(list.length / maxOutlineTableColumns));
}

function getScenarioOutlineKeysParts(list: Array<string>, count: number): Array<string> {
  var partScenarioOutlineList = new Array<string>();
  list.forEach((element, index) => {
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
  border: 3px solid var(--bs-primary);
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
  border-top: 3px solid var(--bs-primary);
  padding: 1rem;
}

.table-data-variant {
  font-size: 90%;
  width: auto;
  max-width: 100%;
}

.table-data-variant th, .table-data-variant td {
  word-break: break-all;
  word-wrap: break-word;
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

.rbelDetailsBadge {
  font-size: 100%;
  margin-right: 0.5rem;
}

.resizer {
  width: 0.5rem;
  border-left: 3px solid gray;
  background: antiquewhite;
}

#rbellog_resize {
  right: 0;
  top: 0;
  bottom: 0;
  width: 10px;
  z-index: 2000;
}

i.resizer-right {
  left: -19px;
  right: 5px;
  height: 32px;
  width: 32px;
  color: gray;
  background: lightgray;
  border-radius: 1rem;
  position: relative;
}

.rbel-logo {
  width: 50px;
  margin-left: 0.5rem;
}

#rbellog_details_pane {
  background: whitesmoke;
  top: 0;
  bottom: 0;
}
</style>
