///
///
/// Copyright 2025 gematik GmbH
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

import { type DeepReadonly, onMounted, readonly, ref, type Ref } from "vue";
import { useProxyController, type UseProxyControllerOptions } from "./ProxyController.ts";
import type { RouteDto } from "./MessageTypes.ts";

export interface UseTigerProxyConfigReturn {
  isLoading: DeepReadonly<Ref<boolean>>;
  routes: DeepReadonly<Ref<RouteDto[]>>;
  loadRoutes: () => void;
  deleteRoute: (id: string) => void;
  addRoute: (from: string, to: string) => void;
}

export interface UseTigerProxyConfigOptions extends UseProxyControllerOptions {}

export function useTigerProxyConfig(props: UseTigerProxyConfigOptions): UseTigerProxyConfigReturn {
  const { getProxyRoutes, deleteProxyRoute, addProxyRoute } = useProxyController(props);
  const isLoading = ref(false);
  const routes = ref<RouteDto[]>([]);

  const loadRoutes = async () => {
    const routesResult = await getProxyRoutes();
    if (routesResult) {
      routes.value = routesResult;
    }
  };

  const deleteRoute = async (id: string) => {
    try {
      isLoading.value = true;
      await deleteProxyRoute({ id });
      await loadRoutes();
    } finally {
      isLoading.value = false;
    }
  };

  const addRoute = async (from: string, to: string) => {
    try {
      isLoading.value = true;
      await addProxyRoute({ route: { id: null, from, to } });
      await loadRoutes();
    } finally {
      isLoading.value = false;
    }
  };

  onMounted(async () => {
    try {
      isLoading.value = true;
      await loadRoutes();
    } finally {
      isLoading.value = false;
    }
  });

  return {
    isLoading: readonly(isLoading),
    routes: readonly(routes),
    loadRoutes,
    deleteRoute,
    addRoute,
  };
}
