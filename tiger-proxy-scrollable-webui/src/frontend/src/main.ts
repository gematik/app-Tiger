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

import { createApp } from "vue";
import MainApp from "./components/MainApp.vue";
import "bootstrap";
import "bootstrap/scss/bootstrap.scss";
import "@/scss/styles.scss";
import { createAppRouter } from "./router";
import hljs from "highlight.js/lib/core";
import json from "highlight.js/lib/languages/json";
import xml from "highlight.js/lib/languages/xml";
import plaintext from "highlight.js/lib/languages/plaintext";

if (__USE_FONTS_OVER_CDN__) {
  import("@/scss/fontawesome-cdn.scss");
} else {
  import("~fontawesome/scss/fontawesome.scss");
  import("~fontawesome/scss/solid.scss");
}

// Highlight.js languages currently used
hljs.registerLanguage("json", json);
hljs.registerLanguage("xml", xml);
hljs.registerLanguage("html", xml);
hljs.registerLanguage("plaintext", plaintext);

const app = createApp(MainApp);
app.config.globalProperties.__IS_DETACHED_MODE__ = __IS_DETACHED_MODE__;
app.config.globalProperties.__IS_ONLINE_MODE__ = __IS_ONLINE_MODE__;
app.use(createAppRouter());
app.mount("#app");
