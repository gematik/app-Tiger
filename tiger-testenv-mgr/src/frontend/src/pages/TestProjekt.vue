<template>
  <div>
    <div class="row" style="margin-top: 30px">
      <div class="col-md-4" style="margin-left: 10px">
        <ServerStatus :serverStatusData="serverStatus" />
        <FeatureList :featureUpdateMap="featureUpdateMap" />
      </div>
      <div class="col-md-7">
         <Feature :stepData="stepData" />
        <!--BannerMessage :bannerData="bannerData" /-->
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, Ref, ref } from "vue";
import SockJS from "sockjs-client";
import Stomp, { Client, Frame, Message } from "webstomp-client";
import TigerEnvStatusDto from "@/types/TigerEnvStatusDto";
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";
import TestEnvStatusDto from "@/types/TestEnvStatusDto";
import ServerStatus from "@/components/server/ServerStatus.vue";
import TigerServerStatusDto from "@/types/TigerServerStatusDto";
import BannerMessages from "@/types/BannerMessages";
import DataType from "@/types/DataType";
import Step from "@/types/Step";
import Feature from "@/components/testsuite/Feature.vue";
import FeatureList from "@/components/testsuite/FeatureList.vue";
import FeatureScenario from "@/types/FeatureScenario";
import FeatureUpdate from "@/types/FeatureUpdate";
import ScenarioUpdate from "@/types/ScenarioUpdate";
import StepUpdate from "@/types/StepUpdate";
import TestResult from "@/types/TestResult";

let baseURL = process.env.BASE_URL;
let socket: any;
let stompClient: Client;
let bannerData: Ref<BannerMessages[]> = ref([]);
let stepData: Ref<Step[]> = ref([]);
let featureScenario: Ref<FeatureScenario[]> = ref([]);
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
      let servers: Map<string, TigerServerStatusDto> = null;
      if (serverStatus.value?.servers !== null) {
        servers = new Map<string, TigerServerStatusDto>(
          Object.entries(serverStatus.value?.servers)
        );
        serverStatus.value.servers = servers;
      }
    });
}

function updateMessage(type: DataType, json: any) {
  switch (type) {
    case DataType.FEATURE:
    case DataType.SCENARIO:
    case DataType.STEP:
    case DataType.STEPOUTLINE: {
      stepData.value.push({
        step: json.step,
        currentStepIndex: json.currentStepIndex,
        currentDataVariantIndex: json.currentDataVariantIndex,
        feature: json.feature,
        scenario: json.scenario,
        type: type,
      });
      break;
    }
    case DataType.STEP_RESULT: {
      let step: Step | undefined = findStep(
        json.feature,
        json.scenario,
        json.step,
        json.currentStepIndex,
        json.currentDataVariantIndex
      );
      if (step) {
        step.result = json.result;
      }
      break;
    }
    case DataType.BANNER: {
      bannerData.value.push({ text: json.text });
      break;
    }
  }
}

function updateStepData(
  type: DataType,
  step: string,
  status: TestResult = null,
  scenario: string = null,
  feature: string = null,
) {
  switch (type) {
    case DataType.FEATURE:
    case DataType.SCENARIO:
    case DataType.STEP:
    case DataType.STEPOUTLINE: {
      stepData.value.push({
        step: step,
        feature: feature,
        scenario: scenario,
        type: type,
        status: status
      });
      break;
    }
    case DataType.STEP_RESULT: {
      let stepObj: Step | undefined = findStep(feature, scenario, step);
      if (stepObj) {
        stepObj.status = status;
      }
      break;
    }
    case DataType.BANNER: {
      //bannerData.value.push({ text: json.text });
      break;
    }
  }
}

function findStep(
  feature: string,
  scenario: string,
  step: string
): Step | undefined {
  //  if (currentDataVariantIndex !== -1) {
  //    return stepData.value.find(
  //      (element) =>
  //        element.feature === feature &&
  //        element.scenario === scenario &&
  //        element.step === step &&
  //        element.currentStepIndex === currentStepIndex &&
  //        element.currentDataVariantIndex === currentDataVariantIndex
  //    );
  //  } else {
  return stepData.value.find(
    (element) =>
      element.feature === feature &&
      element.scenario === scenario &&
      element.step === step
    //&& element.currentStepIndex === currentStepIndex
  );
  //  }
}

function updateServerStatus(update: Map<string, TigerServerStatusUpdateDto>) {
  if (
    serverStatus.value !== null &&
    serverStatus.value.servers !== null &&
    update !== null
  ) {
    update.forEach((value: TigerServerStatusUpdateDto, key: string) => {
      if (serverStatus.value.servers.get(key) !== null) {
        // update
        if (value.type !== null) {
          serverStatus.value.servers.get(key).type = value.type;
        }
        if (value.baseUrl !== null) {
          serverStatus.value.servers.get(key).baseUrl = value.baseUrl;
        }
        if (value.statusMessage !== null) {
          serverStatus.value.servers.get(key).statusMessage =
            value.statusMessage;
          serverStatus.value.servers
            .get(key)
            .statusUpdates.push(value.statusMessage);
        }
      } else {
        // add
        serverStatus.value.servers.set(key, value);
      }
    });
  }
}

function updateFeatureMap(update: Map<string, FeatureUpdate>) {
  if (update !== null) {
    update.forEach((featureUpdate: FeatureUpdate, featureKey: string) => {
      let featureToBeUpdated: FeatureUpdate =
        featureUpdateMap.value.get(featureKey);
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
      let scenarioToBeUpdated: ScenarioUpdate =
        featureToBeUpdated.scenarios.get(scenarioKey);
      if (scenarioToBeUpdated === undefined) {
        // add new scenario
        let scenarioUpdateNew = createNewScenarioUpdate(scenarioUpdate);
        let stepToBeUpdated = new Map<string, StepUpdate>(
          Object.entries(scenarioUpdate.steps)
        );
        stepToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
          addStep(stepUpdate, scenarioUpdateNew, stepKey, scenarioUpdate.description, featureUpdate.description);
        });
        featureToBeUpdated.scenarios.set(scenarioKey, scenarioUpdateNew);
      } else {
        // update scenario and check steps
        scenarioToBeUpdated.status = scenarioUpdate.status;
        let stepsToBeUpdated = new Map<string, StepUpdate>(
          Object.entries(scenarioUpdate.steps)
        );
        stepsToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
          let stepToBeUpdated: StepUpdate =
            scenarioToBeUpdated.steps.get(stepKey);
          if (stepToBeUpdated === undefined) {
            addStep(stepUpdate, scenarioToBeUpdated, stepKey, scenarioUpdate.description, featureUpdate.description);
          } else {
            // update step
            stepToBeUpdated.status = stepUpdate.status;
            updateStepData(DataType.STEP_RESULT, stepToBeUpdated.description, stepToBeUpdated.status, scenarioUpdate.description, featureUpdate.description);
          }
        });
      }
    }
  );
}

function addNewFeatureToMap(featureUpdate: FeatureUpdate, featureKey: string) {
  let featureUpdatedNew = createNewFeatureUpdate(featureUpdate);
  let scenarioToBeUpdated = new Map<string, ScenarioUpdate>(
    Object.entries(featureUpdate.scenarios)
  );
  scenarioToBeUpdated.forEach(
    (scenarioUpdate: ScenarioUpdate, scenarioKey: string) => {
      let scenarioUpdateNew = createNewScenarioUpdate(
        scenarioUpdate
      );
      let stepToBeUpdated = new Map<string, StepUpdate>(
        Object.entries(scenarioUpdate.steps)
      );
      stepToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
        addStep(
          stepUpdate,
          scenarioUpdateNew,
          stepKey,
          scenarioUpdate,
          featureUpdate
        );
      });
      featureUpdatedNew.scenarios.set(scenarioKey, scenarioUpdateNew);
    }
  );
  featureUpdateMap.value.set(featureKey, featureUpdatedNew);
}

function createNewFeatureUpdate(featureUpdate: FeatureUpdate): FeatureUpdate {
  let featureUpdatedNew: FeatureUpdate = {};
  featureUpdatedNew.description = featureUpdate.description;
  featureUpdatedNew.status = featureUpdate.status;
  featureUpdatedNew.scenarios = new Map<string, ScenarioUpdate>();
  updateStepData(DataType.FEATURE, featureUpdate.description, featureUpdate.result);
  return featureUpdatedNew;
}

function createNewScenarioUpdate(
  scenarioUpdate: ScenarioUpdate
): ScenarioUpdate {
  let scenarioUpdateNew: ScenarioUpdate = {};
  scenarioUpdateNew.description = scenarioUpdate.description;
  scenarioUpdateNew.status = scenarioUpdate.status;
  scenarioUpdateNew.steps = new Map<string, StepUpdate>();
  updateStepData(
    DataType.SCENARIO,
    scenarioUpdate.description,
    scenarioUpdate.result
  );
  return scenarioUpdateNew;
}

function addStep(
  stepUpdate: StepUpdate,
  scenarioToBeUpdated: ScenarioUpdate,
  stepKey: string,
  scenarioUpdate: ScenarioUpdate,
  featureUpdate: FeatureUpdate
) {
  let stepUpdateNew: StepUpdate = {};
  stepUpdateNew.description = stepUpdate.description;
  stepUpdateNew.status = stepUpdate.status;
  scenarioToBeUpdated.steps.set(stepKey, stepUpdateNew);
  updateStepData(
    DataType.STEP,
    stepUpdate.description,
    stepUpdate.status,
    scenarioUpdate,
    featureUpdate
  );
}

function convertData() {
  stepData.value.forEach((element) => {
    if (element.type === DataType.FEATURE) {
      if (!featureScenario.value.find((el) => el.feature === element.step)) {
        featureScenario.value.push({ feature: element.step, scenarios: [] });
      }
    }
  });
  stepData.value.forEach((element) => {
    if (element.type === DataType.SCENARIO) {
      let feature = featureScenario.value.find(
        (el) => el.feature === element.feature
      );
      if (!feature?.scenarios.find((sce) => sce === element.step)) {
        feature?.scenarios.push(element.step);
      }
    }
  });
}

onMounted(() => {
  fetchInitialServerStatus();
  connectToWebSocket();
});
</script>
<style>
@import "~bootstrap/dist/css/bootstrap.min.css";
@import url("https://cdn.jsdelivr.net/npm/bootstrap-icons@1.8.1/font/bootstrap-icons.css");
</style>
