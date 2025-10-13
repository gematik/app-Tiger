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

import { deflateSync } from "fflate";
import { useProxyController, type UseProxyControllerOptions } from "@/api/ProxyController.ts";
import type { DetachedRbelLog } from "@/WindowExt.ts";
import { type Ref, ref } from "vue";

let distDetachedHtml: string = "";
if (__IS_ONLINE_MODE__) {
  distDetachedHtml = (await import("@detached/index.html?raw")).default;
}

export interface UseHtmlExporterReturn {
  isLoading: Ref<boolean>;
  download: (filename: string, applyFilter: boolean, what: "tgr" | "html") => Promise<void>;
}

export interface UseHtmlExporterOptions extends UseProxyControllerOptions {}

export function useHtmlExporter(
  currentRbelPath: Ref<string>,
  options: UseHtmlExporterOptions,
): UseHtmlExporterReturn {
  const proxyController = useProxyController(options);
  const isLoading = ref(false);

  const downloadFile = (filename: string, content: string, type: string = "text/plain") => {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);

    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();

    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  function bufferToDataURL(data: BufferSource): Promise<string> {
    return new Promise((resolve, reject) => {
      const blob = new Blob([data], { type: "application/octet-stream" });
      const reader = new FileReader();

      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(reader.error);

      reader.readAsDataURL(blob);
    });
  }

  const downloadHtml = async (filename: string, filterRbelPath?: string) => {
    const metaResult = await proxyController.getMetaMessages({ filterRbelPath });
    if (!metaResult) return;
    const htmlResult = await proxyController.getMessages({
      fromOffset: 0,
      toOffsetExcluding: metaResult.total,
      signal: null as any,
      filterRbelPath,
    });
    if (!htmlResult) return;
    const log: DetachedRbelLog = {
      messagesWithHtml: htmlResult,
      messagesWithMeta: metaResult,
    };

    const compressed = deflateSync(new TextEncoder().encode(JSON.stringify(log)));

    const dataURL = await bufferToDataURL(new Uint8Array(compressed));

    const finalHtml = distDetachedHtml.replace(
      '<script id="__TGR_RBEL_LOG__" type="text/javascript"></script>',
      `<script id="__TGR_RBEL_LOG__" type="text/javascript">window.__TGR_RBEL_LOG__="${dataURL}"</script>`,
    );

    downloadFile(filename, finalHtml, "text/html");
  };

  const download = async (filename: string, applyFilter: boolean, what: "tgr" | "html") => {
    if (__IS_ONLINE_MODE__) {
      try {
        isLoading.value = true;

        const filterRbelPath = applyFilter ? currentRbelPath.value : undefined;
        if (what === "tgr") {
          const log = await proxyController.downloadRbelLogFile({
            suffix: "log",
            filterRbelPath,
          });
          if (!log) return;
          downloadFile(filename + ".tgr", log, "text/plain");
        } else if (what === "html") {
          await downloadHtml(filename, filterRbelPath);
        }
      } finally {
        isLoading.value = false;
      }
    }
  };

  return {
    isLoading,
    download,
  };
}
