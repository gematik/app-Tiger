<!--


    Copyright 2021-2025 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    *******

    For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.

-->
<template>
  <div
      id="execution_pane"
      class="tab-pane active execution-pane-tabs"
      role="tabpanel"
  >
    <BannerMessageWindow
        :banner-message="bannerMessage"
        :quit-testrun-ongoing="quitTestrunOngoing"
        :quit-reason="quitReason"
    ></BannerMessageWindow>
    <div class="w-100">
      <div id="test-execution-pane-date" class="mt-2 small text-muted text-end">
        Started: {{ started }}
      </div>
      <div id="execution_table" class="pt-1">
        <div
            v-if="featureUpdateMap.size === 0"
            class="waiting-spinner alert w-100 text-center"
            style="height: 200px"
        >
          <i class="fa-solid fa-spinner fa-spin left fa-2x"></i> Waiting for
          first Feature / Scenario to start...
        </div>
        <div v-else class="w-100">
          <div
              v-for="(feature, featureKey) in featureUpdateMap"
              :key="featureKey"
          >
            <h3 class="featuretitle test-execution-pane-feature-title">
              <TestStatusBadge
                  :test-status="feature[1].status"
                  :status-message="feature[1].computeStatusMessage()"
                  :highlight-text="true"
                  :text="`Feature: ${feature[1].description}`"
                  :link="feature[1].getLink(feature[1].description)"
              ></TestStatusBadge>
            </h3>
            <div
                v-for="(scenario, scenarioKey) in feature[1].scenarios"
                :key="scenarioKey"
            >
              <h4 class="scenariotitle test-execution-pane-scenario-title">
                <TestStatusBadge
                    :test-status="scenario[1].status"
                    :status-message="getStatusMessage(scenario[1].status, scenario[1].failureMessage)"
                    :highlight-text="false"
                    :text="`${scenario[1].description} ${scenario[1].variantIndex !== -1 ? '[' + (scenario[1].variantIndex + 1) + ']' : ''}`"
                    :link="scenario[1].getLink(feature[1].description)"
                    :failure-link="'#' + scenario[1].getFailureId(feature[1].description)"
                >
                </TestStatusBadge>
                <large-play-button
                    :scenario="scenario[1].getScenarioIdentifier()"
                    :show-play-button="scenario[1].isDryRun"
                ></large-play-button>
              </h4>
              <div v-if="scenario[1].variantIndex !== -1">
                <div class="test-scenario-outline-example">
                  <div
                      v-for="anzahl in getTableCountForScenarioOutlineKeysLength(
                      scenario[1].exampleKeys,
                    )"
                      :key="anzahl"
                      class="d-inline-block"
                  >
                    <table
                        class="table table-sm table-data-variant"
                        aria-label="Data used when executing this scenario"
                    >
                      <thead>
                      <tr>
                        <th
                            v-for="(key, index) in getScenarioOutlineKeysParts(
                              scenario[1].exampleKeys,
                              anzahl,
                            )"
                            :key="index"
                        >
                          {{ key }}
                        </th>
                      </tr>
                      </thead>
                      <tbody>
                      <tr>
                        <td
                            v-for="(key, index) in getScenarioOutlineKeysParts(
                              scenario[1].exampleKeys,
                              anzahl,
                            )"
                            :key="index"
                        >
                          {{ scenario[1].exampleList.get(key) }}
                        </td>
                      </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
              <table
                  class="table table-borderless table-test-steps"
                  aria-label="Test steps performed when executing this scenario"
              >
                <tbody>
                <tr v-for="(step, index) in scenario[1].steps" :key="index">
                  <td
                      :class="`${step[1].status.toLowerCase()} step_status test-step-status-${step[1].status.toLowerCase()}`"
                  >
                    <i
                        :class="`fa-solid ${getTestResultIcon(step[1].status, 'solid')}`"
                        :title="getStatusMessage(step[1].status, step[1].failureMessage)"
                    ></i>
                  </td>
                  <td :class="`step_text step_index_${index}`">
                    <a v-if="step[1].failureMessage && step[1].failureMessage.length > 0"
                       :id="scenario[1].getFailureId(feature[1].description)"/>
                    <Step :step="step[1]"
                          :ui="ui"
                          ariaLabel=""
                    />
                    <div
                        v-for="rbelmsg in step[1].rbelMetaData"
                        :key="rbelmsg.uuid"
                    >
                      <div v-if="rbelmsg.menuInfoString" class="rbelmessage">
                        <a
                            href="#"
                            class="badge rbelDetailsBadge test-rbel-link"
                            @click="ui.showRbelLogDetails(rbelmsg.uuid, $event)"
                        >
                          {{ rbelmsg.sequenceNumber + 1 }}
                        </a>
                        <span
                        ><i
                            v-if="rbelmsg.symbol"
                            class="fas"
                            :class="rbelmsg.symbol"
                        ></i
                        >&nbsp;&nbsp;&nbsp;
                            {{ rbelmsg.recipient }}&nbsp;&nbsp;&nbsp;{{
                            rbelmsg.menuInfoString
                          }}</span
                        >
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
    </div>
  </div>
  <!-- to avoid the build chain removing these css class definitions -->
  <span class="blue step_status step_text d-none"></span>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import BannerMessage from "@/types/BannerMessage";
import TestStatusBadge from "@/components/testsuite/TestStatusBadge.vue";
import BannerMessageWindow from "@/components/testsuite/BannerMessageWindow.vue";
import Ui from "@/types/ui/Ui";
import LargePlayButton from "@/components/replay/LargePlayButton.vue";
import QuitReason from "@/types/QuitReason";
import Step from "@/components/testsuite/Step.vue";
import {getTestResultIcon} from "@/types/testsuite/TestResult.ts";

defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
  bannerMessage: BannerMessage | boolean;
  localProxyWebUiUrl: string;
  ui: Ui;
  started: Date;
  quitTestrunOngoing: boolean;
  quitReason: QuitReason;
}>();

const maxOutlineTableColumns = 4;

function getTableCountForScenarioOutlineKeysLength(
    list: Array<string>,
): number {
  return Math.ceil(list.length / maxOutlineTableColumns);
}

function getScenarioOutlineKeysParts(
    list: Array<string>,
    count: number,
): Array<string> {
  const partScenarioOutlineList = new Array<string>();
  list.forEach((element, index) => {
    if (
        index < count * maxOutlineTableColumns &&
        index >= maxOutlineTableColumns * (count - 1)
    ) {
      partScenarioOutlineList.push(element);
    }
  });
  return partScenarioOutlineList;
}

function getStatusMessage(
    status: string,
    failureMessage: string,
): string {
  if (failureMessage.length > 0) {
    return `${status}: ${failureMessage}`;
  } else {
    return status;
  }
}
</script>

<style scoped>
#execution_pane,
.rbelmessage {
  padding-left: 2rem;
}

#execution_pane {
  padding-bottom: 16rem; /* to always allow to scroll last steps to visible area if banner window is shown */
}

#execution_table {
  display: flex;
  width: 100%;
}

h3.featuretitle {
  padding: 1rem 1rem 1rem 0.5rem;
  background: var(--gem-primary-100);
  margin-bottom: 0;
}

h4.scenariotitle {
  padding: 1rem 1rem 1rem 0.5rem;
  background: var(--gem-primary-100);
  color: var(--gem-primary-400);
}

.test-scenario-outline-example {
  width: auto;
  max-width: 100%;
}

.table-data-variant {
  font-size: 90%;
  width: auto;
  max-width: 100%;
  border: 1px solid lightgray;
}

.table-data-variant tbody {
  border-top: 0;
}

.table-data-variant th,
.table-data-variant td {
  word-break: break-all;
  word-wrap: break-word;
}

.step_status {
  text-align: center;
  vertical-align: top;
  margin-top: 0;
}

.step_status i {
  vertical-align: top;
  margin-top: 0;
  font-size: 1.0rem;
}

.step_text {
  font-size: 80%;
  width: 99%;
}

.rbelDetailsBadge {
  font-size: 100%;
  margin-right: 0.5rem;
  margin-top: 0.25rem;
  margin-bottom: 0.25rem;
  padding: 0.5em 1rem;
  border: 1px solid lightgray;
  background-color: #ecfcfe; /* TODO coming from bulma we need --gem-info colors */
  color: #0a8694;
  text-decoration: none;
  cursor: pointer;
}

.blue {
  color: darkblue;
}

.waiting-spinner {
  z-index: -1;
}
</style>
