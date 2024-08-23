/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class BackendClient {
  eventTarget;

  constructor() {
    this.eventTarget = new EventTarget();
  }

  async resetMessages() {
    const response = await this.fetchWithHandler("/resetMsgs");
    if (response.ok) {
      const asJson = await response.json();
      console.log("removed " + asJson.numMsgs + " messages...");
    } else {
      console.log("ERROR " + response.status + " " + response.statusText);
    }
  }

  async uploadTrafficFile(fileToUpload) {
    return this.fetchWithHandler('/importTraffic', {
      method: "POST",
      body: fileToUpload
    });
  }

  async getMsgAfter(lastMsgUuid, filterCriterion, pageSize, pageNumber) {
    const baseUrl = "/getMsgAfter?";
    const queryParams = new URLSearchParams({
      lastMsgUuid,
      filterCriterion
    });
    if (pageSize) {
      queryParams.append("pageSize", pageSize);
      queryParams.append("pageNumber", pageNumber);
    }
    return this.fetchWithHandler(baseUrl + queryParams.toString());
  }

  async addRoute(route) {
    return this.fetchWithHandler("/route", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(route)
    })
  }

  async getRoutes() {
    return this.fetchWithHandler("/route");
  }

  async testJexlQuery(msgUuid, query) {
    const baseUrl = "/testJexlQuery?";
    const queryParams = new URLSearchParams({
      msgUuid,
      query
    })
    return this.fetchWithHandler(baseUrl + queryParams.toString())
  }

  async testRbelExpression(msgUuid, rbelPath) {
    const baseUrl = "/testRbelExpression?";
    const queryParams = new URLSearchParams({
      msgUuid,
      rbelPath
    })
    return this.fetchWithHandler(baseUrl + queryParams.toString())
  }

  async deleteRoute(routeId) {
    return this.fetchWithHandler(`/route/${routeId}`, {
      method: "DELETE"
    })
  }

  async quitProxy(noSystemExit) {
    let baseUrl = "/quit?"
    if (noSystemExit) {
      baseUrl += new URLSearchParams({noSystemExit}).toString()
    }
    //Here we don't want to throw events
    return fetch(baseUrl);
  }

  async fetchWithHandler(input, init) {
    try {
      return await fetch(input, init)
    } catch (error) {
      this.triggerErrorEvent(error)
      throw error; //rethrowing so that calling code can do something with it if necessary.
    }
  }

  triggerErrorEvent(error) {
    const errorEvent = new CustomEvent("BackendClientErrorEvent",
        {detail: error})
    this.eventTarget.dispatchEvent(errorEvent);
  }

  addErrorEventListener(listener) {
    this.eventTarget.addEventListener("BackendClientErrorEvent", listener)
  }

  removeEventListener(listener) {
    this.eventTarget.removeEventListener(listener);
  }
}

export default new BackendClient();