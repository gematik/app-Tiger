<!--
  - Copyright (c) 2023 gematik GmbH
  - 
  - Licensed under the Apache License, Version 2.0 (the License);
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  - 
  -     http://www.apache.org/licenses/LICENSE-2.0
  - 
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an 'AS IS' BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
    <div class="tab-pane active execution-pane-tabs" id="execution_pane" role="tabpanel">
        <BannerMessageWindow :banner-message="bannerMessage"
                             :quitTestrunOngoing="quitTestrunOngoing"
                             :shutdownTestrunOngoing="shutdownTestrunOngoing"></BannerMessageWindow>
        <div class="w-100">
            <div class="mt-2 small text-muted text-end" id="test-execution-pane-date">Started: {{ started }}</div>
            <div id="execution_table" class="pt-1">
                <div v-if="featureUpdateMap.size === 0" class="alert w-100 text-center" style="height: 200px;">
                    <i class="fa-solid fa-spinner left fa-2x"></i> Waiting for first Feature / Scenario to start...
                </div>
                <div v-else class="w-100">
                    <div v-for="(feature, key) in featureUpdateMap" :key="key">
                        <h3 class="featuretitle test-execution-pane-feature-title">
                            <TestStatusBadge :test-status="feature[1].status" :highlight-text="true"
                                             :text="`Feature: ${feature[1].description}`"></TestStatusBadge>
                        </h3>
                        <div v-for="(scenario, key) in feature[1].scenarios" :key="key">
                            <h4 class="scenariotitle test-execution-pane-scenario-title">
                                <TestStatusBadge
                                        :test-status="scenario[1].status"
                                        :highlight-text="false"
                                        :text="`${scenario[1].description} ${scenario[1].variantIndex !== -1 ? '[' + (scenario[1].variantIndex + 1) + ']' : ''}`"
                                        :link="scenario[1].getLink(feature[1].description)">
                                </TestStatusBadge>
                            </h4>
                            <div v-if="scenario[1].variantIndex !== -1">
                                <div v-for="anzahl in getTableCountForScenarioOutlineKeysLength(scenario[1].exampleKeys)"
                                     :key="anzahl"
                                     class="d-inline-block">
                                    <table class="table table-sm table-data-variant">
                                        <thead>
                                        <tr>
                                            <th v-for="(key, index) in getScenarioOutlineKeysParts(scenario[1].exampleKeys, anzahl)"
                                                :key="index">
                                                {{ key }}
                                            </th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <tr>
                                            <td v-for="(key, index) in getScenarioOutlineKeysParts(scenario[1].exampleKeys, anzahl)"
                                                :key="index">
                                                {{ scenario[1].exampleList.get(key) }}
                                            </td>
                                        </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                            <table class="table table-borderless">
                                <tbody>
                                <tr v-for="(step, index) in scenario[1].steps" :key="index">
                                    <td :class="`${step[1].status.toLowerCase()} step_status `">
                                        <i :class="`fa-solid ${getTestResultIcon(step[1].status, 'solid')}`"
                                           :title="`${step[1].status}`"></i>
                                    </td>
                                    <td :class="`step_text step_index_${index}`">
                                        <div v-html="step[1].description"></div>
                                        <div v-for="(rbelmsg, index) in step[1].rbelMetaData" :key="index">
                                            <div v-if="rbelmsg.method" class="rbelmessage">
                                                <a v-on:click="ui.showRbelLogDetails(rbelmsg.uuid, rbelmsg.sequenceNumber, $event)"
                                                   href="#" class="badge rbelDetailsBadge">
                                                    {{ rbelmsg.sequenceNumber + 1 }}
                                                </a>
                                                <b>{{ rbelmsg.method }} {{
                                                    step[1].rbelMetaData[index + 1].responseCode
                                                    }}</b>
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
        </div>
        <div class="position-fixed" id="rbellog_resize"
             v-on:mouseenter="ui.mouseEnterHandler"
             v-on:mousedown="ui.mouseDownHandler"
             v-on:mouseleave="ui.mouseLeaveHandler">
            <i v-on:click="ui.toggleRightSideBar" class="fa-solid fa-angles-left resizer-right" id="test-webui-slider"></i>
        </div>
        <div class="d-none position-fixed pl-3 pt-3" id="rbellog_details_pane">
            <h2>
                <img alt="RBel logo" src="img/rbellog.png" class="rbel-logo" id="test-rbel-logo">
                Rbel Log Details
                <a v-if="localProxyWebUiUrl" :href="`${localProxyWebUiUrl}`" target="poxywebui" id="test-rbel-webui-url">
                    <i class="fa-solid fa-up-right-from-square" alt="pop out pane"></i>
                </a>
            </h2>
            <iframe v-if="localProxyWebUiUrl" id="rbellog-details-iframe" allow="clipboard-write" class="h-100 w-100"
                    :src="`${localProxyWebUiUrl}?embedded=true`"/>
            <div v-else class="w-100 no-connection-local-proxy serverstatus-stopped">
                <i class="fas fa-project-diagram left"></i>
                No connection to local proxy.
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import BannerMessage from "@/types/BannerMessage";
import TestStatusBadge from "@/components/testsuite/TestStatusBadge.vue";
import BannerMessageWindow from "@/components/testsuite/BannerMessageWindow.vue";
import {getTestResultIcon} from "@/types/testsuite/TestResult";
import Ui from "@/types/ui/Ui";

defineProps<{
    featureUpdateMap: Map<string, FeatureUpdate>;
    bannerMessage: BannerMessage | boolean;
    localProxyWebUiUrl: string;
    ui: Ui;
    started: Date;
    quitTestrunOngoing: boolean;
    shutdownTestrunOngoing: boolean;
}>();

const maxOutlineTableColumns = 4;

function getTableCountForScenarioOutlineKeysLength(list: Array<string>): number {
    return (Math.ceil(list.length / maxOutlineTableColumns));
}

function getScenarioOutlineKeysParts(list: Array<string>, count: number): Array<string> {
    const partScenarioOutlineList = new Array<string>();
    list.forEach((element, index) => {
        if (index < (count * maxOutlineTableColumns) && index >= maxOutlineTableColumns * (count - 1)) {
            partScenarioOutlineList.push(element);
        }
    });
    return partScenarioOutlineList;
}
</script>

<style scoped>

#execution_pane, .rbelmessage {
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


.table-data-variant {
    font-size: 90%;
    width: auto;
    max-width: 100%;
    border: 1px solid lightgray;
}

.table-data-variant tbody {
    border-top: 0;
}

.table-data-variant th, .table-data-variant td {
    word-break: break-all;
    word-wrap: break-word;
}

.step_status {
    text-align: center;
    vertical-align: top;
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

#rbellog_resize {
    right: 2px;
    top: 0;
    bottom: 0;
    width: 16px;
    z-index: 2000;
    border-left: 1px solid var(--gem-primary-400);
    background: var(--gem-primary-100);
}

#rbellog_resize i.resizer-right {
    left: -19px;
    right: 5px;
    color: var(--gem-primary-400);
    border: 1px solid var(--gem-primary-400);
    border-radius: 0.5rem;
    padding: 0.5rem;
    background: inherit;
    position: relative;
}

#rbellog_details_pane {
    background: var(--gem-primary-100);
    color: var(--gem-primary-400);
    top: 0;
    bottom: 0;
}

.rbel-logo {
    width: 50px;
    margin-left: 0.5rem;
}

#rbellog_details_pane > h2 i {
    margin-left: 1rem;
    font-size: 50%;
    vertical-align: top;
    color: var(--gem-primary-400);
}

.no-connection-local-proxy {
    height: 15rem;
    background: white;
    text-align: center;
    line-height: 15rem;
}

.blue {
    color: darkblue;
}
</style>
