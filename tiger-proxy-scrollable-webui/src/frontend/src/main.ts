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

import { createApp } from "vue";
import App from "./App.vue";
import "bootstrap";
import "bootstrap/scss/bootstrap.scss";
import "~fontawesome/scss/fontawesome.scss";
import "~fontawesome/scss/solid.scss";
import "./scss/styles.scss";

import { library } from "@fortawesome/fontawesome-svg-core";

import { fas } from "@fortawesome/free-solid-svg-icons";

library.add(fas);

createApp(App).mount("#app");
