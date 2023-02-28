/*
 * Copyright (c) 2023 gematik GmbH
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
let pageSize = 20;
let pageNumber = 0;
let empty="empty";

const NO_REQUEST = "no requests";

let resetBtn;
let saveBtn;
let uploadBtn;
let quitBtn;
let importBtn;

let jexlInspectionResultDiv;
let jexlInspectionContextDiv;
let jexlInspectionTreeDiv;
let jexlInspectionContextParentDiv;
let jexlInspectionNoContextDiv;

let setFilterCriterionBtn;
let setFilterCriterionInput;
let resetFilterCriterionBtn;

let btnOpenRouteModal;
let fieldRouteTo;
let fieldRouteFrom;
let btnAddRoute;

let btnOpenFilterModal;

let btnScrollLock;
let ledScrollLock;
let scrollLock = false;
let collapseMessageDetails = false;
let collapseMessageHeaders = false;

let testQuitParam = '';

let collapsibleRbelBtn;
let collapsibleJexlBtn;

let collapsibleDetails;
let collapsibleMessageDetailsBtn;
let collapsibleHeaders;
let collapsibleMessageHeaderBtn;

let jexlResponseLink;
let receivers = [];
let senders = [];

let requestFrom = "requestFromContent";
let requestTo = "requestToContent";

let socket;
let stompClient;

let allMessagesAmount;
let filteredMessagesAmount;

const menuHtmlTemplateRequest = "<div class=\"ml-5\"><a onclick=\"scrollToMessage('${uuid}',${sequenceNumber})\"\n"
    + "                               class=\"mt-3 is-block\">\n"
    + "        <div class=\"is-size-6 mb-1 has-text-link\"><span\n"
    + "            class=\"tag is-info is-light mr-1\">${sequence}</span><i\n"
    + "            class=\"fas fa-share\"></i> REQUEST\n"
    + "            <span style=\"float:right\"\n"
    + "                 class=\"is-size-6 ml-3 has-text-dark\">${timestamp}"
    + "            </span>\n"
    + "        </div>\n"
    + "        <span style=\"text-overflow: ellipsis;overflow: hidden;\"\n"
    + "             class=\"is-size-6 ml-3 has-text-weight-bold\"\n"
    + "             title=\"${menuInfoString}\">${menuInfoString}"
    + "        </span>\n"
    + "      </a></div>";
const menuHtmlTemplateResponse = "<div class=\"ml-5\"><a onclick=\"scrollToMessage('${uuid}',${sequenceNumber})\"\n"
    + "                               class=\"mt-3 is-block\">\n"
    + "        <div class=\"is-size-6 mb-1 has-text-success\"><span\n"
    + "            class=\"tag is-info is-light mr-1\">${sequence}</span><i\n"
    + "            class=\"fas fa-reply\"></i> RESPONSE\n"
    + "            <span style=\"float:right\"\n"
    + "                 class=\"is-size-6 ml-3 has-text-dark\">${timestamp}"
    + "            </span>\n"
    + "        </div>\n"
    + "        <span style=\"text-overflow: ellipsis;overflow: hidden;\"\n"
    + "             class=\"is-size-6 ml-3 has-text-weight-bold\"\n"
    + "             title=\"${menuInfoString}\">${menuInfoString}"
    + "        </span>\n"
    + "      </a></div>";

document.addEventListener('DOMContentLoaded', function () {
  rootEl = document.documentElement;
  resetBtn = document.getElementById("resetMsgs");
  saveBtn = document.getElementById("saveMsgs");
  importBtn = document.getElementById("importMsgs");
  uploadBtn = document.getElementById("uploadMsgs");
  quitBtn = document.getElementById("quitProxy");
  jexlInspectionResultDiv = document.getElementById("jexlResult");
  jexlInspectionContextDiv = document.getElementById("jexlContext");
  jexlInspectionTreeDiv = document.getElementById("rbelTree");
  jexlInspectionContextParentDiv = document.getElementById("contextParent");
  jexlInspectionNoContextDiv = document.getElementById("jexlNoContext");

  addDropdownClickListener(document.getElementById("dropdown-hide-button").parentNode);
  addDropdownClickListener(document.getElementById("dropdown-page-selection"));
  addDropdownClickListener(document.getElementById("dropdown-page-size"));

  setFilterCriterionBtn = document.getElementById("setFilterCriterionBtn");
  resetFilterCriterionBtn = document.getElementById("resetFilterCriterionBtn");
  setFilterCriterionInput = document.getElementById("setFilterCriterionInput");
  setFilterCriterionInput.addEventListener("keypress", (event) => {
    if (event.keyCode === 13) {
      setFilterCriterion();
      event.preventDefault();
      return false;
    }
  });

  btnOpenRouteModal = document.getElementById("routeModalBtn");
  fieldRouteFrom = document.getElementById("addNewRouteFromField");
  fieldRouteTo = document.getElementById("addNewRouteToField");
  btnAddRoute = document.getElementById("addNewRouteBtn");
  btnScrollLock = document.getElementById("scrollLockBtn");
  ledScrollLock = document.getElementById("scrollLockLed");

  btnOpenFilterModal = document.getElementById("filterModalBtn");

  collapsibleRbelBtn = document.getElementById("rbel-help-icon");
  collapsibleJexlBtn = document.getElementById("jexl-help-icon");
  collapsibleRbelBtn.addEventListener('click', () => {
    toggleHelp(collapsibleRbelBtn, "rbel-help");
  });
  collapsibleJexlBtn.addEventListener('click', () => {
    toggleHelp(collapsibleJexlBtn, "jexl-help")
  });
  btnOpenRouteModal.addEventListener('click', showModalsCB);
  btnOpenFilterModal.addEventListener('click', showModalsCB);
  saveBtn.addEventListener('click', showModalSave);
  importBtn.addEventListener('click', showModalImport);

  collapsibleDetails = document.getElementById("collapsibleMessageDetails");
  collapsibleMessageDetailsBtn = document.getElementById(
      "collapsibleMessageDetailsBtn");

  collapsibleHeaders = document.getElementById("collapsibleMessageHeader");
  collapsibleMessageHeaderBtn = document.getElementById(
      "collapsibleMessageHeaderBtn");

  enableModals();
  document.addEventListener('keydown', event => {
    var e = event || window.event;
    if (e.keyCode === 27) {
      closeModals();
    }
  });

  enableCardToggles();
  enableCollapseExpandAll();

  setFilterCriterionBtn.addEventListener('click', setFilterCriterion);
  resetFilterCriterionBtn.addEventListener('click', resetFilterCriterion);
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

  function todayAsString() {
    var now = new Date();
    return padStr(now.getFullYear() - 2000) +
        padStr(1 + now.getMonth()) +
        padStr(now.getDate());
  }

  function padStr(i) {
    return (i < 10) ? "0" + i : "" + i;
  }

  document.getElementById("saveTrafficBtn")
  .addEventListener('click', e => {
    closeModals();
    const a = document.createElement('a');
    a.style.display = 'none';
    a.href = `/webui/trafficLog-${todayAsString()}.tgr`;
    a.download = `trafficLog-${todayAsString()}.tgr`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    e.preventDefault();
    return false;
  });
  btnOpenRouteModal.addEventListener('click',
      () => {
        btnOpenRouteModal.disabled = true;
        getRoutes();
        updateAddRouteBtnState();
      });

  btnOpenFilterModal.addEventListener('click',
      () => {
        btnOpenFilterModal.disabled = true;
      });

  btnScrollLock.addEventListener('click',
      () => {
        scrollLock = !scrollLock;
        ledScrollLock.classList.toggle("led-error", scrollLock);
      });

  collapsibleMessageDetailsBtn.addEventListener('click',
      () => {
        const firstElementOfView = getFirstElementOfViewport();
        collapseMessageDetails = !collapseMessageDetails;
        collapsibleDetails.classList.toggle("led-error",
            collapseMessageDetails);

        document.getElementsByClassName('msglist')[0]
            .childNodes.forEach(message => {
              updateHidingForMessageElement(message)
            });
        if (firstElementOfView) {
          window.setTimeout(() => {
            firstElementOfView.scrollIntoView();
            window.scrollBy(0, -15);
          }, 50);
        }
        closeAllDropdowns();
      });

  collapsibleMessageHeaderBtn.addEventListener('click',
      () => {
        const firstElementOfView = getFirstElementOfViewport();
        collapseMessageHeaders = !collapseMessageHeaders;
        collapsibleHeaders.classList.toggle("led-error",
            collapseMessageHeaders);

        document.getElementsByClassName('msglist')[0]
            .childNodes.forEach(message => {
              updateHidingForMessageElement(message)
            });
        if (firstElementOfView) {
          window.setTimeout(() => {
            firstElementOfView.scrollIntoView();
            window.scrollBy(0, -15);
          }, 50);
        }
        closeAllDropdowns();
      });

  let selectRequestFromBtn = document.getElementById("requestFromContent");

  let selectRequestToBtn = document.getElementById("requestToContent");
  selectRequestToBtn.addEventListener("click", updateSelectContents);
  selectRequestToBtn.addEventListener('change', function (event) {
    if (event.target.value !== NO_REQUEST) {
      let filterField = document.getElementById("setFilterCriterionInput");
      filterField.value = "@.receiver == \"" + event.target.value + "\"";
      selectRequestFromBtn.selectedIndex = 0;
    }
  });

  selectRequestFromBtn.addEventListener("click", updateSelectContents);
  selectRequestFromBtn.addEventListener('change', function (event) {
    if (event.target.value !== NO_REQUEST) {
      let filterField = document.getElementById("setFilterCriterionInput");
      filterField.value = "@.sender == \"" + event.target.value + "\"";
      selectRequestToBtn.selectedIndex = 0;
    }
  });

  btnAddRoute.addEventListener("click", addRoute);
  fieldRouteFrom.addEventListener("keydown", updateAddRouteBtnState);
  fieldRouteTo.addEventListener("keydown", updateAddRouteBtnState);
  fieldRouteFrom.addEventListener("blur", updateAddRouteBtnState);
  fieldRouteTo.addEventListener("blur", updateAddRouteBtnState);
  fieldRouteFrom.addEventListener("mouseleave", updateAddRouteBtnState);
  fieldRouteTo.addEventListener("mouseleave", updateAddRouteBtnState);

  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has("embedded")) {
    scrollLock = true;
    let elem = document.getElementsByClassName("sidebar")[0];
    elem.setAttribute("class", elem.getAttribute("class") + " hidden");
    elem = document.getElementsByClassName("main-content")[0];
    elem.setAttribute("class", elem.getAttribute("class") + " hidden");
    const not4embeddedelems = document.getElementsByClassName("not4embedded");
    for (let i = 0; i < not4embeddedelems.length; i++) {
      not4embeddedelems[i].setAttribute("class",
          not4embeddedelems[i].getAttribute("class") + " hidden");
    }
  }

  connectToWebSocket();

  setPageSize(pageSize);
});

// Functions
function removeActiveFlag(label) {
  let divDropdownContent = document.getElementById(label);
  Array.from(divDropdownContent.children).forEach(child => {
    child.classList.remove("is-active");
  });
}

function updateSelectContents() {
  updateSelectContent(requestFrom, senders);
  updateSelectContent(requestTo, receivers);
}

function getLabelId(label, id) {
  return label + "_" + id;
}

function updateSelectContent(label, list) {

  let select = document.getElementById(label);
  let contained = false;

  if (list.length === 0) {
    initSelectContent(label, list);
  } else {
    for (let i = 0; i < list.length; i++) {
      if (list[i].length > 0) {
        contained = false;
        if (select !== null) {
          Array.from(select.children).forEach(child => {
            if (child.id === getLabelId(label, list[i])) {
              contained = true;
            }
          });
        }
        if (!contained) {
          let element = document.createElement('option');
          element.textContent = list[i];
          element.id = getLabelId(label, list[i]);
          select.appendChild(element);
        }
      }
    }
  }
}

function deleteRequestsLists() {
  senders = [];
  receivers = [];
}

function closeAllDropdowns() {
  for (let item of document.getElementsByClassName("is-active dropdown")) {
    item.classList.remove("is-active");
  }
}

function initSelectContent(label, list) {
  let select = document.getElementById(label);
  if (list.length === 0 && select !== null) {
    Array.from(select.children).forEach(child => {
      select.removeChild(child);
    });
    let element = document.createElement('option');
    element.textContent = NO_REQUEST;
    element.id = getLabelId(label, empty);
    element.value = empty;
    select.appendChild(element);
  }
}

function getFirstElementOfViewport() {
  var msgList = document.getElementsByClassName("msglist")[0];
  const messages = msgList.children;
  const element = Array.from(messages).find(msg => {
    const rect = msg.getBoundingClientRect();
    return rect.top >= 0 && rect.width > 0 && rect.height > 0;
  });
  return element ? element : (messages.length ? messages[0] : undefined);
}

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
  let $modalButtons = getAll('.modal-button');
  let $copyButtons = getAll('.copyToClipboard-button');

  if ($modalButtons.length > 0) {
    $modalButtons.forEach(function ($el) {
      $el.addEventListener('click', function (e) {
        let target = $el.dataset.target;
        let $target = document.getElementById(target);
        rootEl.classList.add('is-clipped');
        $target.classList.add('is-active');
        e.preventDefault();
        return false;
      });
    });
  }

  if ($copyButtons.length > 0) {
    $copyButtons.forEach(function ($el) {
      $el.addEventListener('click', function (e) {
        e.preventDefault();
        let target = $el.dataset.target;
        let $target = document.getElementById(target);
        navigator.clipboard.writeText($target.textContent);
        return false;
      });
    });
  }

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

function showModalImport(e) {
  var input = document.createElement("input");
  input.setAttribute("type", "file");
  input.click(); // opening dialog
  input.onchange = function () {
    fetch('/webui/traffic', {
      method: "POST",
      body: input.files[0]
    })
    .then(function (response) {
      if (!response.ok) {
        alert('Error while uploading: ' + response.statusText);
      } else {
        alert('The file has been uploaded successfully.');
        pollMessages();
        closeAllDropdowns();
      }
      return response;
    }).then(function (_response) {
      console.log("ok");
    });
  };
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

function toggleHelp(collapsibleBtn, collapsibleHelpId, isDisabledSet = false) {
  const collapsibleHelp = document.getElementById(collapsibleHelpId);
  const classList = collapsibleBtn.classList;
  const flag = classList.contains("fa-toggle-on") || ((isDisabledSet)
      && isDisabledSet == true);
  classList.toggle("fa-toggle-on", !flag);
  classList.toggle("fa-toggle-off", flag);
  collapsibleHelp.style.display = flag ? "none" : "block";
}

function closeModals() {
  const $modals = getAll('.modal');
  rootEl.classList.remove('is-clipped');
  $modals.forEach(function ($el) {
    $el.classList.remove('is-active');
  });
  btnOpenRouteModal.disabled = false;
  btnOpenFilterModal.disabled = false;
  jexlInspectionResultDiv.classList.add("is-hidden");
  jexlInspectionContextParentDiv.classList.add("is-hidden");
  jexlInspectionNoContextDiv.classList.remove("is-hidden");
}

function enableCardToggles() {
  let cardToggles = document.getElementsByClassName('toggle-icon');
  for (let i = 0; i < cardToggles.length; i++) {
    if (!cardToggles[i].id ||
        (cardToggles[i].id !== "rbel-help-icon" && cardToggles[i].id
            !== "jexl-help-icon")) {
      cardToggles[i].addEventListener('click', toggleCardCB);
    }
  }
}

function toggleCardCB(e) {
  e.currentTarget.parentElement.parentElement.parentElement.parentElement.childNodes[1].classList.toggle(
      'is-hidden');
  toggleCollapsableIcon(e.currentTarget);
  e.preventDefault();
  return false;
}

function enableCollapseExpandAll() {
  let msgCards = document.getElementsByClassName('msg-card');

  document.getElementById("collapse-all").addEventListener('click', e => {
    for (let i = 0; i < msgCards.length; i++) {
      const classList = msgCards[i].childNodes[1].classList;
      if (!classList.contains('is-hidden')) {
        classList.add('is-hidden');
      }
      const classList2 = msgCards[i].children[0].children[0].children[0].children[1].classList;
      if (classList2.contains("fa-toggle-on")) {
        classList2.remove("fa-toggle-on");
        classList2.add("fa-toggle-off");
      }
    }
    e.preventDefault();
    return false;
  });

  document.getElementById("expand-all").addEventListener('click', e => {
    for (let i = 0; i < msgCards.length; i++) {
      const classList = msgCards[i].childNodes[1].classList;
      if (classList.contains('is-hidden')) {
        classList.remove('is-hidden');
      }
      const classList2 = msgCards[i].children[0].children[0].children[0].children[1].classList;
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
  const classList = target.classList;
  const flag = classList.contains("fa-toggle-on");
  classList.toggle("fa-toggle-on", !flag);
  classList.toggle("fa-toggle-off", flag);
}

function setCollapsableIcon(target, collapsed) {
  const classList = target.classList;
  classList.toggle("fa-toggle-on", !collapsed);
  classList.toggle("fa-toggle-off", collapsed);
}

let currentlyPolling = false;

function connectToWebSocket() {
  socket = new SockJS("/newMessages");
  stompClient = Stomp.over(socket, {debug: false});
  stompClient.connect(
      {},
      () => {
        stompClient.subscribe('/topic/ws', () => {
          if (!currentlyPolling) {
            currentlyPolling = true;
            pollMessages(() => {currentlyPolling = false;});
          }
        });
      },
      (error) => {
        console.error("Websocket error: " + JSON.stringify(error));
      }
  )
}

function pollMessages(callback) {
  const xhttp = new XMLHttpRequest();
  xhttp.open("GET", "/webui/getMsgAfter"
      + "?lastMsgUuid=" + lastUuid
      + "&filterCriterion=" + encodeURIComponent(filterCriterion)
      + "&pageSize=" + pageSize
      + "&pageNumber=" + pageNumber, true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);
        filteredMessagesAmount = response.metaMsgList.length;
        allMessagesAmount = response.totalMsgCount;
        updateMessageList(response);
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
      }
      if (callback !== undefined) {
        callback();
      }
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
  deleteRequestsLists();
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
        resetBtn.disabled = true;
        uploadBtn.disabled = true;
        btnScrollLock.disabled = true;
        collapsibleMessageDetailsBtn.disabled = true;
        collapsibleMessageHeaderBtn.disabled = true;
        btnOpenRouteModal.disabled = true;
        btnOpenFilterModal.disabled = true;
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
  setFilterCriterionBtn.classList.add("is-loading");
  filterCriterion = setFilterCriterionInput.value;
  resetAllReceivedMessages();
  pollMessages();
  setFilterCriterionBtn.classList.remove("is-loading");
}

function resetFilterCriterion(){
  resetFilterCriterionBtn.classList.add("is-loading");
  filterCriterion = "";
  setFilterCriterionInput.value = '';
  document.getElementById("requestToContent").selectedIndex = 0;
  document.getElementById("requestFromContent").selectedIndex = 0;
  resetAllReceivedMessages();
  pollMessages();
  resetFilterCriterionBtn.classList.remove("is-loading");
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
      "<span>Inspect</span>";
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
  document.getElementById("setFilterCriterionInput").value
      = document.getElementById("jexlQueryInput").value;
  setFilterCriterion();
}

function executeJexlQuery() {
  toggleHelp(collapsibleJexlBtn, "jexl-help", true);
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
        const map = new Map(Object.entries(response.messageContext));
        jexlInspectionContextDiv.innerHTML =
            "<h3 class='is-size-4'>JEXL context</h3>";
        map.forEach((value, key) => {
          jexlInspectionContextDiv.innerHTML += "<prekey id='json_" + encodeURIComponent(key) + "'>" +  key + "</prekey><pre class='paddingLeft' id='json__" + encodeURIComponent(key) + "'>" + JSON.stringify(value, null, 6) + "</pre><br>";
        });

        jexlInspectionContextParentDiv.classList.remove("is-hidden");
        jexlInspectionNoContextDiv.classList.add("is-hidden");
        if (response.matchSuccessful) {
          jexlInspectionResultDiv.innerHTML = "<b>Condition is true: </b>"
              + "<code class='has-background-dark has-text-danger'>" + jexlQuery
              + "</code>";
          jexlInspectionResultDiv.classList.add("has-background-success");
          jexlInspectionResultDiv.classList.remove("has-background-primary");
          jexlInspectionResultDiv.classList.remove("is-hidden");
        } else {
          jexlInspectionResultDiv.innerHTML = "<b>Condition is false (or invalid): </b>"
              + "<code class='has-background-dark has-text-danger'>" + jexlQuery
              + "</code>";
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
  toggleHelp(collapsibleRbelBtn, "rbel-help", true);
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
        setAddEventListener();
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
      }
    }
  }
  xhttp.send();
}

function setAddEventListener() {
  const jexlResponseLinks = document.getElementsByClassName("jexlResponseLink");
  Array.from(jexlResponseLinks).forEach(element => {
    element.addEventListener('click', (e) => copyPathToInputField(e, element));
  });
}

function copyPathToInputField(event, element) {
  event.preventDefault();
  var oldValue = document.getElementById("rbelExpressionInput").value;
  var text = element.textContent;
  var el = element.previousElementSibling;
  var marker = el.textContent;
  while (el != null) {
    if (el.classList) {
      if (el.classList.contains('jexlResponseLink')) {
        if (el.previousElementSibling.classList.contains('has-text-primary') &&
            el.previousElementSibling.textContent.length < marker.length) {
          text = el.textContent + "." + text;
          marker = el.previousElementSibling.textContent;
        }
      }
    }
    el = el.previousElementSibling;
  }
  if (oldValue == null) {
    document.getElementById("rbelExpressionInput").value = "$." + text;
  } else {
    const words = oldValue.split('.');
    oldValue = oldValue.substring(0, oldValue.length - words[words.length-1].length);
    document.getElementById("rbelExpressionInput").value = oldValue + text;
  }
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

function updateHidingForMessageElement(messageElement) {
  setCollapsableIcon(
      messageElement.getElementsByClassName("header-toggle")[0],
      collapseMessageHeaders);
  messageElement.getElementsByClassName("msg-header-content")[0].classList
  .toggle('is-hidden', collapseMessageHeaders);
  setCollapsableIcon(
      messageElement.getElementsByClassName("msg-toggle")[0],
      collapseMessageDetails);
  messageElement.getElementsByClassName("msg-content")[0].classList
  .toggle('is-hidden', collapseMessageDetails);
}

function addMessageToMainView(msgHtmlData) {
  const listDiv = getAll('.msglist')[0];
  const message = htmlToElement(msgHtmlData.html);
  let span = getAll(".msg-sequence", message)[0];
  if (span != null) {
    span.classList.add("tag", "is-info", "is-light", "mr-3", "is-size-3");
    span.textContent = msgHtmlData.sequenceNumber + 1;
  }
  addQueryBtn(message);
  listDiv.appendChild(message);
  if (!scrollLock) {
    message.scrollIntoView({behaviour: "smooth", alignToTop: true});
  }
  updateHidingForMessageElement(message);
}

function addMessageToMenu(msgMetaData, index) {
  let isRequest = msgMetaData.request;

  let menuItem;
  if (isRequest) {
    menuItem = menuHtmlTemplateRequest;
  } else {
    menuItem = menuHtmlTemplateResponse;
  }
  menuItem = menuItem
  .replace("${uuid}", msgMetaData.uuid)
  .replace("${sequence}", msgMetaData.sequenceNumber + 1)
  .replace("${sequenceNumber}", index);
  if (msgMetaData.menuInfoString != null) {
    menuItem = menuItem
    .replaceAll("${menuInfoString}", msgMetaData.menuInfoString);
  } else {
    menuItem = menuItem
    .replaceAll("${menuInfoString}", " ");
  }
  if (msgMetaData.timestamp != null) {
    menuItem = menuItem
    .replace("${timestamp}",
        msgMetaData.timestamp.split("T")[1].split("+")[0]);
  } else {
    menuItem = menuItem
    .replace("${timestamp}", " ");
  }
  document.getElementById("sidebar-menu")
  .appendChild(htmlToElement(menuItem));

  if (msgMetaData.sender != null) {
    const foundSender = Array.from(senders).find(msg => {
      return msg === msgMetaData.sender;
    });
    if (foundSender == null) {
      let index = msgMetaData.sender.indexOf(":");
      if(index >= 0) {
        let port = msgMetaData.sender.substring(index + 1);
        if (port < 32768) {
          senders.push(msgMetaData.sender);
        }
      }
    }
  }
  if (msgMetaData.recipient != null) {
    const foundReceiver = Array.from(receivers).find(msg => {
      return msg === msgMetaData.recipient;
    });
    if (foundReceiver == null) {
      let index = msgMetaData.recipient.indexOf(":");
      if(index >= 0){
        let port = msgMetaData.recipient.substring(index + 1);
        if (port < 32768) {
          receivers.push(msgMetaData.recipient);
        }
      }
    }
  }
}

function setFilterMessage() {
  const element = document.getElementById("filteredMessage");
  if (allMessagesAmount === filteredMessagesAmount) {
    element.textContent = "Filter didn't match any of the " + allMessagesAmount + " messages.";
  } else {
    element.textContent = filteredMessagesAmount + " of "+ allMessagesAmount + " did match the filter criteria.";
  }
}

function updateMessageList(json) {
  updatePageSelector(json.pagesAvailable);
  for (htmlMsg of json.htmlMsgList) {
    addMessageToMainView(htmlMsg);
  }
  let index = 0;
  for (metaMsg of json.metaMsgList) {
    addMessageToMenu(metaMsg, index++);
  }
  if (json.metaMsgList.length > 0) {
    lastUuid = json.metaMsgList[json.metaMsgList.length - 1].uuid;
  }
  setFilterMessage();
  enableCardToggles();
  enableModals();
}

function getRoutes() {
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

function setPageSize(newSize) {
  pageSize = newSize;
  pageNumber = 0;
  document.getElementById("pageSizeDisplay").textContent =
      "Size " + newSize;
  closeAllDropdowns();
  resetAllReceivedMessages();
  pollMessages();
}

function setPageNumber(newPageNumber, callback) {
  pageNumber = newPageNumber;
  document.getElementById("pageNumberDisplay").textContent =
      "Page " + (newPageNumber + 1);
  closeAllDropdowns();
  resetAllReceivedMessages();
  pollMessages(callback);
}

function updatePageSelector(pagesAvailable) {
  let selector = document.getElementById("pageSelector");
  let selectorInnerHtml = '';
  for (let i = 0; i < pagesAvailable; i++) {
    selectorInnerHtml +=
        '<a class="dropdown-item" onclick="event.stopPropagation(); setPageNumber(' + i + ');">'
        + (i + 1)
        + '</a>';
  }
  selector.innerHTML = selectorInnerHtml;
}

let tobeScrolledToUUID;

function scrollToMessage(uuid, sequenceNumber) {
  if ((sequenceNumber < pageNumber * pageSize)
      || (sequenceNumber >= (pageNumber + 1) * pageSize)) {
    tobeScrolledToUUID = uuid;
    setPageNumber(Math.ceil((sequenceNumber +1) / pageSize) - 1, scrollMessageIntoView)
  } else {
    scrollMessageIntoView(uuid)
  }
}

function scrollMessageIntoView(uuid) {
  if (!uuid) {
    uuid = tobeScrolledToUUID;
  }
  let elements = document.getElementsByName(uuid);
  if (elements.length > 0) {
    elements[0].scrollIntoView({behaviour: "smooth", alignToTop: true});
  }
}

function messageScrollToReceiver(ev) {
  scrollToMessage(ev.data.split(",")[0], Number(ev.data.split(",")[1]));
}

if (window.addEventListener) {
  window.addEventListener("message", messageScrollToReceiver, false);
} else {
  window.attachEvent("onmessage", messageScrollToReceiver);
}

function addDropdownClickListener(el, callback) {
  el.addEventListener('click', function(e) {
    let active = el.classList.contains('is-active');
    closeAllDropdowns();
    e.stopPropagation();
    if (!active) {
      el.classList.add('is-active');
    }
    if (callback !== undefined) {
      callback();
    }
  });
}

document.addEventListener('keydown', function (event) {
  let e = event || window.event;
  if (e.key === 'Esc' || e.key === 'Escape') {
    closeAllDropdowns();
  }
});

document.addEventListener('click', function(e) {
  closeAllDropdowns();
});
