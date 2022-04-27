<template>
  <div>
    <div class="row" style="margin-top: 30px">
      <div class="col-md-3">
        <ServerStatus :serverStatusData="serverStatus"/>
        <FeatureList :featureUpdateMap="featureUpdateMap"/>
      </div>
      <div class="col-md-9">
        <nav class="nav nav-tabs nav-pills nav-fill" role="tablist">
          <a class="nav-link active" data-bs-toggle="tab" href="#execution_pane" role="tab" data-toggle="tab">Test execution</a>
          <a class="nav-link" data-bs-toggle="tab" href="#logs_pane" role="tab" data-toggle="tab">Logs</a>
        </nav>

        <!-- Tab panes -->
        <div class="tab-content">
          <ExecutionPane :featureUpdateMap="featureUpdateMap"/>
          <div class="tab-pane" id="logs_pane" role="tabpanel">...</div>
        </div>
        <!--BannerMessage :bannerData="bannerData" /-->
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, Ref, ref} from "vue";
import SockJS from "sockjs-client";
import Stomp, {Client, Frame, Message} from "webstomp-client";
import TigerEnvStatusDto from "@/types/TigerEnvStatusDto";
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";
import TestEnvStatusDto from "@/types/TestEnvStatusDto";
import ServerStatus from "@/components/server/ServerStatus.vue";
import TigerServerStatusDto from "@/types/TigerServerStatusDto";
import BannerMessages from "@/types/BannerMessages";
import DataType from "@/types/DataType";
import ExecutionPane from "@/components/testsuite/ExecutionPane.vue";
import FeatureList from "@/components/testsuite/FeatureList.vue";
import FeatureUpdate from "@/types/FeatureUpdate";
import ScenarioUpdate from "@/types/ScenarioUpdate";
import StepUpdate from "@/types/StepUpdate";
import TestResult from "@/types/TestResult";

let baseURL = process.env.BASE_URL;
let socket: any;
let stompClient: Client;
let bannerData: Ref<BannerMessages[]> = ref([]);
let serverUpdateStatus: Ref<Map<string, TigerServerStatusUpdateDto> | null> =
    ref(null);
let serverStatus: Ref<TigerEnvStatusDto | null> = ref(null);
let featureUpdateMap: Ref<Map<string, FeatureUpdate>> = ref(
    new Map<string, FeatureUpdate>()
);

function connectToWebSocket() {
  socket = new SockJS(baseURL + "testEnv");
  stompClient = Stomp.over(socket);
  stompClient.connect(
      {},
      () => {
        stompClient.subscribe(baseURL + "topic/envStatus", (tick: Message) => {
          let messages: TestEnvStatusDto = JSON.parse(tick.body);
          let serverMessage: Map<string, TigerServerStatusUpdateDto>;
          let featureUpdate: Map<string, FeatureUpdate>;
          if (messages.servers !== null) {
            serverMessage = new Map<string, TigerServerStatusUpdateDto>(
                Object.entries(messages.servers)
            );
            if (serverMessage !== null) {
              // server update
              serverUpdateStatus.value = serverMessage;
              updateServerStatus(serverMessage);
            }
          }
          if (messages.featureMap !== null) {
            featureUpdate = new Map<string, FeatureUpdate>(
                Object.entries(messages.featureMap)
            );
            if (featureUpdate !== null) {
              updateFeatureMap(featureUpdate);
            }
          }
        });
      },
      (error: Frame | CloseEvent) => {
        console.log(error);
      }
  );
}

function fetchInitialServerStatus() {
  fetch(baseURL + "status")
  .then((response) => response.text())
  .then((data) => {
    serverStatus.value = JSON.parse(data);
    let servers: Map<string, TigerServerStatusDto> = new Map<string, TigerServerStatusDto>();
    if (serverStatus.value?.servers !== null) {
      servers = new Map<string, TigerServerStatusDto>(
          Object.entries(serverStatus.value?.servers)
      );
      serverStatus.value.servers = servers;
    }
  });
}

function updateServerStatus(update: Map<string, TigerServerStatusUpdateDto>) {
  if (
      serverStatus.value !== null &&
      serverStatus.value.servers !== null &&
      update !== null
  ) {
    update.forEach((value: TigerServerStatusUpdateDto, key: string) => {
          if (serverStatus.value) {
            if (serverStatus.value.servers.get(key) !== null) {
              // update
              if (value.type !== null) {
                serverStatus.value.servers.get(key).type = value.type;
              }
              if (value.baseUrl !== null) {
                serverStatus.value.servers.get(key).baseUrl = value.baseUrl;
              }
              if (value.statusMessage !== null) {
                serverStatus.value.servers.get(key).statusMessage = value.statusMessage;
                serverStatus.value.servers.get(key).statusUpdates.push(value.statusMessage);
              }
            } else {
              // add
              serverStatus.value.servers.set(key, value);
            }
          }
        }
    )
    ;
  }
}

function updateFeatureMap(update: Map<string, FeatureUpdate>) {
  if (update !== null) {
    update.forEach((featureUpdate: FeatureUpdate, featureKey: string) => {
      let featureToBeUpdated: FeatureUpdate = featureUpdateMap.value.get(featureKey);
      if (featureToBeUpdated === undefined) {
        // add new feature
        addNewFeatureToMap(featureUpdate, featureKey);
      } else {
        // feature in map -> check scenarios and steps
        checkFeatureMapForUpdates(featureUpdate, featureToBeUpdated);
      }
    });
  }
}

function checkFeatureMapForUpdates(
    featureUpdate: FeatureUpdate,
    featureToBeUpdated: FeatureUpdate
) {
  featureToBeUpdated.status = featureUpdate.status;
  let scenariosToBeUpdated = new Map<string, ScenarioUpdate>(
      Object.entries(featureUpdate.scenarios)
  );
  scenariosToBeUpdated.forEach(
      (scenarioUpdate: ScenarioUpdate, scenarioKey: string) => {
        let scenarioToBeUpdated: ScenarioUpdate = featureToBeUpdated.scenarios.get(scenarioKey);
        if (scenarioToBeUpdated === undefined) {
          // add new scenario
          let scenarioUpdateNew = createNewScenarioUpdate(scenarioUpdate);
          let stepToBeUpdated = new Map<string, StepUpdate>(Object.entries(scenarioUpdate.steps));
          stepToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
            addStep(stepUpdate, scenarioUpdateNew, stepKey,
                scenarioUpdate.description, featureUpdate.description);
          });
          featureToBeUpdated.scenarios.set(scenarioKey, scenarioUpdateNew);
        } else {
          // update scenario and check steps
          scenarioToBeUpdated.status = scenarioUpdate.status;
          let stepsToBeUpdated = new Map<string, StepUpdate>(Object.entries(scenarioUpdate.steps));
          stepsToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
            let stepToBeUpdated: StepUpdate = scenarioToBeUpdated.steps.get(stepKey);
            if (stepToBeUpdated === undefined) {
              addStep(stepUpdate, scenarioToBeUpdated, stepKey,
                  scenarioUpdate.description, featureUpdate.description);
            } else {
              // update step
              stepToBeUpdated.status = stepUpdate.status;
            }
          });
        }
      }
  );
}

function addNewFeatureToMap(featureUpdate: FeatureUpdate, featureKey: string) {
  let featureUpdatedNew = createNewFeatureUpdate(featureUpdate);
  let scenarioToBeUpdated = new Map<string, ScenarioUpdate>(Object.entries(featureUpdate.scenarios));
  scenarioToBeUpdated.forEach(
      (scenarioUpdate: ScenarioUpdate, scenarioKey: string) => {
        let scenarioUpdateNew = createNewScenarioUpdate(scenarioUpdate);
        let stepToBeUpdated = new Map<string, StepUpdate>(Object.entries(scenarioUpdate.steps));
        stepToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
          addStep(stepUpdate, scenarioUpdateNew, stepKey,
              scenarioUpdate, featureUpdate
          );
        });
        featureUpdatedNew.scenarios.set(scenarioKey, scenarioUpdateNew);
      }
  );
  featureUpdateMap.value.set(featureKey, featureUpdatedNew);
}

function createNewFeatureUpdate(featureUpdate: FeatureUpdate): FeatureUpdate {
  return {
    description: featureUpdate.description,
    status: featureUpdate.status,
    scenarios: new Map<string, ScenarioUpdate>()
  };
}

function createNewScenarioUpdate(scenarioUpdate: ScenarioUpdate): ScenarioUpdate {
  return {
    description: scenarioUpdate.description,
    status: scenarioUpdate.status,
    steps: new Map<string, StepUpdate>()
  };
}

function addStep(stepUpdate: StepUpdate, scenarioToBeUpdated: ScenarioUpdate,
                 stepKey: string, scenarioUpdate: ScenarioUpdate, featureUpdate: FeatureUpdate) {
  scenarioToBeUpdated.steps.set(stepKey, {
    description: stepUpdate.description,
    status: stepUpdate.status
  });
}

onMounted(() => {
  fetchInitialServerStatus();
  connectToWebSocket();
});

</script>
<style></style>
