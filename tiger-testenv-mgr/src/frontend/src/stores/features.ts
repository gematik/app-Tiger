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

import { defineStore } from "pinia";
import { type Ref, ref } from "vue";
import FeatureUpdate, {
  type IJsonFeatures,
} from "@/types/testsuite/FeatureUpdate.ts";
import debug from "@/logging/log.ts";

export const useFeaturesStore = defineStore("features", () => {
  const featureUpdateMap = ref(new Map<string, FeatureUpdate>()) as Ref<
    Map<string, FeatureUpdate>
  >;

  function replaceFeatureMap(newFeatureMap: IJsonFeatures) {
    featureUpdateMap.value.clear();
    mergeFeatureMap(newFeatureMap);
  }

  function mergeFeatureMap(newFeatureMap: IJsonFeatures) {
    const targetMap: Map<string, FeatureUpdate> = featureUpdateMap.value;
    FeatureUpdate.addToMapFromJson(targetMap, newFeatureMap);
  }

  function updateFeatureMap(update: Map<string, FeatureUpdate>) {
    update.forEach((featureUpdate: FeatureUpdate, featureKey: string) => {
      if (featureUpdate.description) {
        debug("FEATURE UPDATE " + featureUpdate.description);
        const featureToBeUpdated: FeatureUpdate | undefined =
          featureUpdateMap.value.get(featureKey);
        if (!featureToBeUpdated) {
          // add new feature
          debug(
            "add new feature " +
              featureKey +
              " => " +
              JSON.stringify(featureUpdate),
          );
          const feature = new FeatureUpdate().merge(featureUpdate);
          featureUpdateMap.value.set(featureKey, feature);
          debug(
            "added new feature " + featureKey + " => " + feature.toString(),
          );
        } else {
          featureToBeUpdated.merge(featureUpdate);
        }
      }
    });
  }

  return {
    featureUpdateMap,
    replaceFeatureMap,
    mergeFeatureMap,
    updateFeatureMap,
  };
});
