/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

"strict";

let lastUuid = "";
let filterCriterion = "";
let rootEl;
let jexlQueryElementUuid = "";

let updateTimeout = 0;
let updateHandler = null;
let updateBtn;
let resetBtn;
let saveBtn;
let uploadBtn;
let quitBtn;

let jexlInspectionResultDiv;
let jexlInspectionContextDiv;
let jexlInspectionTreeDiv;
let jexlInspectionContextParentDiv;
let jexlInspectionNoContextDiv;

let setFilterCriterionBtn;
let setFilterCriterionInput;

let btnOpenRouteModal;
let fieldRouteTo;
let fieldRouteFrom;
let btnAddRoute;

let btnScrollLock;
let ledScrollLock;
let scrollLock = false;

let testQuitParam = '';

const menuReqHtmlTemplate = "<div class=\"ml-5\"><a href=\"#${uuid}\"\n"
    + "                               class=\"mt-3 is-block\">\n"
    + "        <div class=\"menu-label mb-1 has-text-link\"><span\n"
    + "            class=\"tag is-info is-light mr-1\">${sequence}</span><i\n"
    + "            class=\"fas fa-share\"></i> REQUEST\n"
    + "        </div>\n"
    + "        <div style=\"text-overflow: ellipsis;overflow: hidden;\"\n"
    + "             class=\"is-size-6 ml-3\">${methodNUrl}"
    + "        </div>\n"
    + "      </a></div>";

const menuResHtmlTemplate = "<a href=\"#${uuid}\" class=\"menu-label ml-5 mt-3 is-block has-text-success\">"
    + "<span class=\"tag is-info is-light mr-1\">${sequence}</span><i class=\"fas fa-reply\"></i> RESPONSE</a>"

document.addEventListener('DOMContentLoaded', function () {
  rootEl = document.documentElement;
  updateBtn = document.getElementById("updateBtn");
  resetBtn = document.getElementById("resetMsgs");
  saveBtn = document.getElementById("saveMsgs");
  uploadBtn = document.getElementById("uploadMsgs");
  quitBtn = document.getElementById("quitProxy");
  jexlInspectionResultDiv = document.getElementById("jexlResult");
  jexlInspectionContextDiv = document.getElementById("jexlContext");
  jexlInspectionTreeDiv = document.getElementById("rbelTree");
  jexlInspectionContextParentDiv = document.getElementById("contextParent");
  jexlInspectionNoContextDiv = document.getElementById("jexlNoContext");

  setFilterCriterionBtn = document.getElementById("setFilterCriterionBtn");
  setFilterCriterionInput = document.getElementById("setFilterCriterionInput");

  btnOpenRouteModal = document.getElementById("routeModalBtn");
  fieldRouteFrom = document.getElementById("addNewRouteFromField");
  fieldRouteTo = document.getElementById("addNewRouteToField");
  btnAddRoute = document.getElementById("addNewRouteBtn");
  btnScrollLock = document.getElementById("scrollLockBtn");
  ledScrollLock = document.getElementById("scrollLockLed");
  btnOpenRouteModal.addEventListener('click', showModalsCB);
  saveBtn.addEventListener('click', showModalSave);

  enableModals();
  document.addEventListener('keydown', event => {
    var e = event || window.event;
    if (e.keyCode === 27) {
      closeModals();
    }
  });

  enableCardToggles();
  enableCollapseExpandAll();

  updateBtn.addEventListener('click', pollMessages);
  setFilterCriterionBtn.addEventListener('click', setFilterCriterion);
  quitBtn.addEventListener('click', quitProxy);
  resetBtn.addEventListener('click', resetMessages);
  document.getElementById("executeJexlQuery")
  .addEventListener('click', executeJexlQuery)
  document.getElementById("testRbelExpression")
  .addEventListener('click', testRbelExpression)
  document.getElementById("copyToFilter")
  .addEventListener('click', copyToFilter)
  if (tigerProxyUploadUrl === "UNDEFINED") {
    uploadBtn.classList.add("is-hidden");
  } else {
    uploadBtn.addEventListener('click', uploadReport);
  }

  document.getElementById("saveHtmlBtn")
  .addEventListener('click', e => {
    closeModals();
    saveHtmlToLocal();
  });
  document.getElementById("saveTrafficBtn")
  .addEventListener('click', e => {
    e.preventDefault();
    closeModals();
    const a = document.createElement('a');
    a.style.display = 'none';
    a.href = "/webui/trafficLog.tgr";
    a.download = 'trafficLog.tgr';
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
  });
  btnOpenRouteModal.addEventListener('click',
      e => {
        btnOpenRouteModal.disabled = true;
        getRoutes();
        updateAddRouteBtnState();
      });

  btnScrollLock.addEventListener('click',
      e => {
        scrollLock = !scrollLock;
        if (scrollLock) {
          ledScrollLock.classList.add("lederror");
        } else {
          ledScrollLock.classList.remove("lederror");
        }
      });

  getAll("input.updates").forEach(function (el) {
    el.addEventListener("click", () => {
      updateTimeout = el.value;
      if (updateHandler) {
        clearInterval(updateHandler);
      }
      if (updateTimeout !== "0") {
        updateHandler = setInterval(pollMessages, updateTimeout * 1000);
        updateBtn.disabled = true;
      } else {
        updateBtn.removeAttribute("disabled");
      }
    })
  });

  document.getElementById("noupdate").click();

  btnAddRoute.addEventListener("click", addRoute);
  fieldRouteFrom.addEventListener("keydown", updateAddRouteBtnState);
  fieldRouteTo.addEventListener("keydown", updateAddRouteBtnState);
  fieldRouteFrom.addEventListener("blur", updateAddRouteBtnState);
  fieldRouteTo.addEventListener("blur", updateAddRouteBtnState);
  fieldRouteFrom.addEventListener("mouseleave", updateAddRouteBtnState);
  fieldRouteTo.addEventListener("mouseleave", updateAddRouteBtnState);
});

// Functions

function getAll(selector, baseEl) {
  if (!baseEl) {
    baseEl = document;
  }
  return Array.prototype.slice.call(baseEl.querySelectorAll(selector), 0);
}

function htmlToElement(html) {
  const template = document.createElement('template');
  html = html.trim(); // Never return a text node of whitespace as the result
  template.innerHTML = html;
  return template.content.firstChild;
}

function enableModals() {
  // Modals
  let $modalCloses = getAll(
      '.modal-background, .modal-close, .message-header .delete, .modal-card-foot .button');

  if ($modalCloses.length > 0) {
    $modalCloses.forEach(function ($el) {
      $el.addEventListener('click', closeModals);
    });
  }
}

function showModalSave(e) {
  const $target = document.getElementById("saveModalDialog");
  rootEl.classList.add('is-clipped');
  $target.classList.add('is-active');
  e.preventDefault();
  return false;
}

function showModalsCB(e) {
  const $target = document.getElementById(e.currentTarget.dataset.target);
  rootEl.classList.add('is-clipped');
  $target.classList.add('is-active');
  e.preventDefault();
  return false;
}

function closeModals() {
  const $modals = getAll('.modal');
  rootEl.classList.remove('is-clipped');
  $modals.forEach(function ($el) {
    $el.classList.remove('is-active');
  });
  btnOpenRouteModal.disabled = false;
  document.getElementById("routeModalLed").classList.remove("ledactive");
  document.getElementById("routeModalLed").classList.remove("lederror");
  jexlInspectionResultDiv.classList.add("is-hidden");
  jexlInspectionContextParentDiv.classList.add("is-hidden");
  jexlInspectionNoContextDiv.classList.remove("is-hidden");
}

function enableCardToggles() {
  let cardToggles = document.getElementsByClassName('card-toggle');
  for (let i = 0; i < cardToggles.length; i++) {
    cardToggles[i].addEventListener('click', toggleCardCB);
  }
}

function toggleCardCB(e) {
  e.currentTarget.parentElement.parentElement.childNodes[1].classList.toggle(
      'is-hidden');
  toggleCollapsableIcon(e.currentTarget);
  e.preventDefault();
  return false;
}

function enableCollapseExpandAll() {
  let cardToggles = document.getElementsByClassName('card-toggle');
  document.getElementById("collapse-all").addEventListener('click', e => {
    for (let i = 0; i < cardToggles.length; i++) {
      const classList = cardToggles[i].parentElement.parentElement.childNodes[1].classList;
      if (!classList.contains('is-hidden')) {
        classList.add('is-hidden');
      }
      const classList2 = cardToggles[i].children[0].children[1].classList;
      if (classList2.contains("fa-toggle-on")) {
        classList2.remove("fa-toggle-on");
        classList2.add("fa-toggle-off");
      }
    }
    e.preventDefault();
    return false;
  });

  document.getElementById("expand-all").addEventListener('click', e => {
    for (let i = 0; i < cardToggles.length; i++) {
      const classList = cardToggles[i].parentElement.parentElement.childNodes[1].classList;
      if (classList.contains('is-hidden')) {
        classList.remove('is-hidden');
      }
      const classList2 = cardToggles[i].children[0].children[1].classList;
      if (classList2.contains("fa-toggle-off")) {
        classList2.remove("fa-toggle-off");
        classList2.add("fa-toggle-on");
      }
    }
    e.preventDefault();
    return false;
  });
}

function toggleCollapsableIcon(target) {
  const classList = target.children[0].children[1].classList;
  if (classList.contains("fa-toggle-on")) {
    classList.remove("fa-toggle-on");
    classList.add("fa-toggle-off");
  } else {
    classList.add("fa-toggle-on");
    classList.remove("fa-toggle-off");
  }

}

function pollMessages() {
  document.getElementById("updateLed").classList.remove("lederror");
  document.getElementById("updateLed").classList.add("ledactive");
  const xhttp = new XMLHttpRequest();
  xhttp.open("GET", "/webui/getMsgAfter"
      + "?lastMsgUuid=" + lastUuid
      + "&filterCriterion=" + filterCriterion, true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);
        updateMessageList(response);
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
        document.getElementById("updateLed").classList.add("lederror");
      }
      setTimeout(() => {
        updateBtn.blur();
        document.getElementById("updateLed").classList.remove("ledactive");
      }, 200);
    }
  }
  xhttp.send();
}

function resetAllReceivedMessages() {
  lastUuid = "";
  const sidebarMenu = document.getElementById("sidebar-menu")
  sidebarMenu.innerHTML = "";
  const listDiv = getAll('.msglist')[0];
  listDiv.innerHTML = "";
}

function resetMessages() {
  resetBtn.disabled = true;
  const xhttp = new XMLHttpRequest();
  xhttp.open("GET", "/webui/resetMsgs", true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);
        console.log("removed " + response.numMsgs + " messages...");
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
      }
      resetAllReceivedMessages();
      setTimeout(() => {
        resetBtn.blur();
        resetBtn.disabled = false;
      }, 200);
    }
  }
  xhttp.send();
}

function quitProxy() {
  quitBtn.disabled = true;
  const xhttp = new XMLHttpRequest();
  xhttp.open("GET", "/webui/quit" + testQuitParam, true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 0) {
        alert("Tiger proxy shut down SUCCESSfully!");
        updateBtn.disabled = true;
        resetBtn.disabled = true;
        uploadBtn.disabled = true;
        btnScrollLock.disabled = true;
        btnOpenRouteModal.disabled = true;
        getAll("input.updates").forEach(function (el) {
          el.disabled = true;
        });
        quitBtn.blur();
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
        setTimeout(() => {
          quitBtn.blur();
          quitBtn.disabled = false;
        }, 200);
      }
    }
  }
  xhttp.send();
}

function setFilterCriterion() {
  filterCriterion = setFilterCriterionInput.value;
  resetAllReceivedMessages();
  pollMessages();
}

function uploadReport() {
  uploadBtn.disabled = true;
  const xhttp = new XMLHttpRequest();
  xhttp.open("POST", "/webui/uploadReport", true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 0) {
        alert("Tiger proxy shut down SUCCESSfully!");

        uploadBtn.disabled = false;
        uploadBtn.blur();
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
        setTimeout(() => {
          uploadBtn.blur();
          uploadBtn.disabled = false;
        }, 200);
      }
    }
  }
  xhttp.send(encodeURIComponent(document.querySelector("html").innerHTML));
}

function saveHtmlToLocal() {
  document.querySelector(".navbar").classList.add("is-hidden");
  const text = document.querySelector("html").innerHTML;
  const now = new Date();
  const offsetMs = now.getTimezoneOffset() * 60 * 1000;
  const dateLocal = new Date(now.getTime() - offsetMs);
  const filename = tigerProxyFilenamePattern
  .replace("${DATE}", dateLocal.toISOString().slice(0, 10).replace(/-/g, ""))
  .replace("${TIME}",
      dateLocal.toISOString().slice(11, 19).replace(/[^0-9]/g, ""))
  .replace(".zip", ".html");
  document.querySelector(".navbar").classList.remove("is-hidden");
  const element = document.createElement('a');
  element.setAttribute('href', 'data:text/html;charset=utf-8,' +
      encodeURIComponent(text));
  element.setAttribute('download', filename);

  element.style.display = 'none';
  document.body.appendChild(element);

  element.click();

  document.body.removeChild(element);
}

function addQueryBtn(reqEl) {
  let titleDiv = getAll(".card-header-title", reqEl)[0].childNodes[0];
  let titleSpan = getAll("span", titleDiv)[0];
  let msgUuid = getAll("a", titleDiv)[0].getAttribute("name");

  let queryBtn = document.createElement('a');
  queryBtn.innerHTML =
      "<span>Inspect with JEXL</span>";
  queryBtn.setAttribute("class", "button modal-button is-pulled-right mx-3");
  queryBtn.setAttribute("data-target", msgUuid);
  queryBtn.addEventListener("click", function (e) {
    const $target = document.getElementById("jexlQueryModal");
    jexlQueryElementUuid = msgUuid;
    rootEl.classList.add('is-clipped');
    $target.classList.add('is-active');
    e.preventDefault();
    return false;
  });
  titleSpan.appendChild(queryBtn);
}

function openTab(sender, tabName) {
  var i, x, tablinks;
  x = document.getElementsByClassName("content-tab");
  for (i = 0; i < x.length; i++) {
    x[i].style.display = "none";
  }
  tablinks = document.getElementsByClassName("tab");
  for (i = 0; i < x.length; i++) {
    tablinks[i].className = tablinks[i].className.replace("is-active", "");
  }
  document.getElementById(tabName).style.display = "block";
  sender.className += " is-active";
}

function copyToFilter() {
  let jexlQuery = document.getElementById("jexlQueryInput").value;
  document.getElementById("setFilterCriterionInput").value = jexlQuery;
  setFilterCriterion();
}

function executeJexlQuery() {
  const xhttp = new XMLHttpRequest();
  let jexlQuery = document.getElementById("jexlQueryInput").value;
  xhttp.open("GET", "/webui/testJexlQuery"
      + "?msgUuid=" + jexlQueryElementUuid
      + "&query=" + encodeURIComponent(jexlQuery),
      true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);

        shortenStrings(response);
        jexlInspectionContextDiv.innerHTML =
            "<h3 class='is-size-4'>JEXL context</h3>"
            + "<pre id='json'>"
            + JSON.stringify(response.messageContext, null, 6)
            + "</pre>";
        jexlInspectionContextParentDiv.classList.remove("is-hidden");
        jexlInspectionNoContextDiv.classList.add("is-hidden");
        if (response.matchSuccessful) {
          jexlInspectionResultDiv.innerHTML = "<b>Condition is true: </b>"
              + "<code class='has-background-dark has-text-danger'>" + jexlQuery+ "</code>";
          jexlInspectionResultDiv.classList.add("has-background-success");
          jexlInspectionResultDiv.classList.remove("has-background-primary");
          jexlInspectionResultDiv.classList.remove("is-hidden");
        } else {
          jexlInspectionResultDiv.innerHTML = "<b>Condition is false (or invalid): </b>"
              + "<code class='has-background-dark has-text-danger'>" + jexlQuery + "</code>";
          jexlInspectionResultDiv.classList.remove("has-background-success");
          jexlInspectionResultDiv.classList.add("has-background-primary");
          jexlInspectionResultDiv.classList.remove("is-hidden");
        }
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
      }
    }
  }
  xhttp.send();
}

function testRbelExpression() {
  const xhttp = new XMLHttpRequest();
  let rbelPath = document.getElementById("rbelExpressionInput").value;
  xhttp.open("GET", "/webui/testRbelExpression"
      + "?msgUuid=" + jexlQueryElementUuid
      + "&rbelPath=" + encodeURIComponent(rbelPath),
      true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);

        document.getElementById("rbelTestTree").innerHTML =
            "<h3 class='is-size-4'>Rbel Tree</h3>"
            + "<pre id='shell'>" + response.rbelTreeHtml + "</pre>";
        let rbelResultTree = "<h3 class='is-size-4'>Matching Elements</h3>";
        response.elements.forEach(key => {
          rbelResultTree += "<div >" + key + "</div>";
        });
        document.getElementById("rbelResult").innerHTML =
            rbelResultTree;
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
      }
    }
  }
  xhttp.send();
}

function shortenStrings(obj) {
  for (var property in obj) {
    if (obj.hasOwnProperty(property)) {
      if (typeof obj[property] == "object") {
        shortenStrings(obj[property]);
      } else {
        if (typeof obj[property] === 'string' || obj[property]
            instanceof String) {
          obj[property] = (obj[property].length > 50) ?
              obj[property].substr(0, 49) + '...'
              : obj[property];
        }
      }
    }
  }
}

function addSingleMessage(msgMetaData, msgHtmlData) {
  const listDiv = getAll('.msglist')[0];

  let isRequest = msgMetaData.path;
  const reqEl = htmlToElement(msgHtmlData);
  let span = getAll(".msg-sequence", reqEl)[0];
  span.classList.add("tag", "is-info", "is-light", "mr-3", "is-size-3");
  span.textContent = msgMetaData.sequenceNumber + 1;
  addQueryBtn(reqEl);
  listDiv.appendChild(reqEl);

  if (isRequest) {
    const menuReq = menuReqHtmlTemplate
    .replace("${uuid}", msgMetaData.uuid)
    .replace("${sequence}", msgMetaData.sequenceNumber + 1)
    .replace("${methodNUrl}", msgMetaData.method + "\n" + msgMetaData.path);
    document.getElementById("sidebar-menu").appendChild(
        htmlToElement(menuReq));
  } else {
    const menuRes = menuResHtmlTemplate
    .replace("${uuid}", msgMetaData.uuid)
    .replace("${sequence}", msgMetaData.sequenceNumber + 1)
    document.getElementById("sidebar-menu").appendChild(
        htmlToElement(menuRes));
  }
}

function updateMessageList(json) {
  if (json.metaMsgList.length === 0) {
    return;
  }
  let i = 0;
  while (i < json.htmlMsgList.length) {
    addSingleMessage(json.metaMsgList[i], json.htmlMsgList[i]);
    i = i + 1;
  }
  lastUuid = json.metaMsgList[json.metaMsgList.length - 1].uuid;

  enableCardToggles();
  enableModals();
  if (!scrollLock) {
    const sidebar = getAll('.menu')[0];
    const msgListDiv = sidebar.nextElementSibling;
    sidebar.children[sidebar.children.length - 1].scrollIntoView(
        {behaviour: "smooth", block: "end"});
    if (msgListDiv.children[msgListDiv.children.length - 1] !== undefined) {
      msgListDiv.children[msgListDiv.children.length - 1].scrollIntoView(
          {behaviour: "smooth", block: "end"});
    }
  }
}

function getRoutes() {
  document.getElementById("routeModalLed").classList.add("ledactive");
  document.getElementById("routeModalLed").classList.remove("lederror");
  getAll(
      ".routeListDiv")[0].innerHTML = "<p align=\"center\" class=\"mt-5 mb-5\"><i class=\"fas fa-spinner\"></i> Loading...</p>";
  const xhttp = new XMLHttpRequest();
  xhttp.open("GET", "/route", true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);
        updateRouteList(response);
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
        getAll(".routeListDiv")[0].innerHTML = "ERROR " + this.status + " "
            + this.responseText;
        document.getElementById("routeModalLed").classList.remove("ledactive");
        document.getElementById("routeModalLed").classList.add("lederror");
      }
    }
  }
  xhttp.send();
}

function updateRouteList(json) {
  if (json.length === 0) {
    (getAll(
        ".routeListDiv")[0]).innerHTML = "<p class='mt-5 mb-5'>No Routes configured</p>"
    return;
  }
  let html = "";
  json.forEach(function (route) {
    html += "<div class='box routeentry columns'>"
        + "<div class='column is-one-fifth'>"
        + "<button id='route-" + route.id
        + "' class='button delete-route is-fullwidth is-danger'>"
        + "<i class=\"far fa-trash-alt\"></i>"
        + "</button></div>"
        + "<div class='column is-four-fifths'>&rarr; " + route.from
        + "<br/>&larr; " + route.to + "</div></div>";
  });
  getAll(".routeListDiv")[0].innerHTML = html;
  getAll("button.delete-route").forEach(function (el) {
    el.addEventListener("click", function (e) {
      deleteRoute(e);
    });
  });
}

function deleteRoute(e) {
  const routeid = e.currentTarget.id.substring("route-".length);
  const xhttp = new XMLHttpRequest();
  xhttp.open("DELETE", "/route/" + routeid, true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status !== 200) {
        console.log("ERROR " + this.status + " " + this.responseText);
      }
      getRoutes();
    }
  }
  xhttp.send();
}

function updateAddRouteBtnState() {
  btnAddRoute.disabled = !(fieldRouteTo.value && fieldRouteFrom.value);
}

function addRoute() {
  try {
    new URL(fieldRouteTo.value);
    new URL(fieldRouteFrom.value);
  } catch (e) {
    alert("Invalid URL!");
    return;
  }
  const xhttp = new XMLHttpRequest();
  xhttp.open("PUT", "/route", true);
  xhttp.setRequestHeader('Content-Type', 'application/json')
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status !== 200) {
        console.log("ERROR " + this.status + " " + this.responseText);
      }
      getRoutes();
    }
  }
  xhttp.send("{\"from\":\"" + fieldRouteFrom.value + "\",\n"
      + "\"to\":\"" + fieldRouteTo.value + "\"}");
}

function testActivateNoSystemExitOnQuit() {
  testQuitParam = '?noSystemExit=true';
}
