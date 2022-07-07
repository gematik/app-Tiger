/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
let collapseHeader = false;

let testQuitParam = '';

let collapsibleRbel;
let collapsibleJexl;
let collapsibleRbelBtn;
let collapsibleJexlBtn;

let collapsibleHeader;
let collapsibleHeaderBtn;

const menuHtmlTemplateRequest = "<div class=\"ml-5\"><a href=\"#${uuid}\"\n"
    + "                               class=\"mt-3 is-block\">\n"
    + "        <div class=\"is-size-6 mb-1 has-text-link\"><span\n"
    + "            class=\"tag is-info is-light mr-1\">${sequence}</span><i\n"
    + "            class=\"fas fa-share\"></i> REQUEST\n"
    + "            <span style=\"float:right\"\n"
    + "                 class=\"is-size-6 ml-3 has-text-dark\">${timestamp}"
    + "            </span>\n"
    + "        </div>\n"
    + "        <span style=\"text-overflow: ellipsis;overflow: hidden;\"\n"
    + "             class=\"is-size-6 ml-3 has-text-weight-bold\">${menuInfoString}"
    + "        </span>\n"
    + "      </a></div>";
const menuHtmlTemplateResponse = "<div class=\"ml-5\"><a href=\"#${uuid}\"\n"
    + "                               class=\"mt-3 is-block\">\n"
    + "        <div class=\"is-size-6 mb-1 has-text-success\"><span\n"
    + "            class=\"tag is-info is-light mr-1\">${sequence}</span><i\n"
    + "            class=\"fas fa-reply\"></i> RESPONSE\n"
    + "            <span style=\"float:right\"\n"
    + "                 class=\"is-size-6 ml-3 has-text-dark\">${timestamp}"
    + "            </span>\n"
    + "        </div>\n"
    + "        <span style=\"text-overflow: ellipsis;overflow: hidden;\"\n"
    + "             class=\"is-size-6 ml-3 has-text-weight-bold\">${menuInfoString}"
    + "        </span>\n"
    + "      </a></div>";

document.addEventListener('DOMContentLoaded', function () {
  rootEl = document.documentElement;
  updateBtn = document.getElementById("updateBtn");
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

  setFilterCriterionBtn = document.getElementById("setFilterCriterionBtn");
  setFilterCriterionInput = document.getElementById("setFilterCriterionInput");

  btnOpenRouteModal = document.getElementById("routeModalBtn");
  fieldRouteFrom = document.getElementById("addNewRouteFromField");
  fieldRouteTo = document.getElementById("addNewRouteToField");
  btnAddRoute = document.getElementById("addNewRouteBtn");
  btnScrollLock = document.getElementById("scrollLockBtn");
  ledScrollLock = document.getElementById("scrollLockLed");
  collapsibleRbel = document.getElementById("rbel-help");
  collapsibleJexl = document.getElementById("jexl-help");
  collapsibleRbelBtn = document.getElementById("rbel-help-icon");
  collapsibleJexlBtn = document.getElementById("jexl-help-icon");
  collapsibleRbelBtn.addEventListener('click', (e) => {toggleHelp(null, collapsibleRbelBtn, collapsibleRbel)});
  collapsibleJexlBtn.addEventListener('click', (e) => {toggleHelp(null, collapsibleJexlBtn, collapsibleJexl)});
  btnOpenRouteModal.addEventListener('click', showModalsCB);
  saveBtn.addEventListener('click', showModalSave);
  importBtn.addEventListener('click', showModalImport);

  collapsibleHeader = document.getElementById("collapsibleHeader");
  collapsibleHeaderBtn = document.getElementById("collapsibleHeaderBtn");

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
  function todayAsString() {
    var now = new Date();
    var dateStr = padStr(now.getFullYear()-2000) +
        padStr(1 + now.getMonth()) +
        padStr(now.getDate());
    return dateStr;
  }

  function padStr(i) {
    return (i < 10) ? "0" + i : "" + i;
  }

  document.getElementById("saveTrafficBtn")
  .addEventListener('click', e => {
    e.preventDefault();
    closeModals();
    const a = document.createElement('a');
    a.style.display = 'none';
    a.href = `/webui/trafficLog-${todayAsString()}.tgr`;
    a.download = `trafficLog-${todayAsString()}.tgr`;
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

  collapsibleHeaderBtn.addEventListener('click',
      e => {
        let firstElementOfView = getFirstElementOfViewport();
        collapseHeader = !collapseHeader;
        let cardToggles = document.getElementsByClassName('card-toggle');
        if (collapseHeader) {
          collapsibleHeader.classList.add("lederror");
          for (let i = 0; i < cardToggles.length; i++) {
            const classListChild = cardToggles[i].childNodes[0].childNodes[1].classList;
            if (classListChild.contains('has-text-primary')) {
              const classList = cardToggles[i].parentElement.parentElement.childNodes[1].classList;
              if (!classList.contains('is-hidden')) {
                classList.add('is-hidden');
              }
              const classList2 = cardToggles[i].children[0].children[0].classList;
              if (classList2.contains("fa-toggle-on")) {
                classList2.remove("fa-toggle-on");
                classList2.add("fa-toggle-off");
              }
            }
          }
        } else {
          collapsibleHeader.classList.remove("lederror");
          for (let i = 0; i < cardToggles.length; i++) {
            const classListChild = cardToggles[i].childNodes[0].childNodes[1].classList;
            if (classListChild.contains('has-text-primary')) {
              const classList = cardToggles[i].parentElement.parentElement.childNodes[1].classList;
              if (classList.contains('is-hidden')) {
                classList.remove('is-hidden');
              }
              const classList2 = cardToggles[i].children[0].children[0].classList;
              if (classList2.contains("fa-toggle-off")) {
                classList2.remove("fa-toggle-off");
                classList2.add("fa-toggle-on");
              }
            }
          }
        }
        firstElementOfView.scrollIntoView();
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

  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has("updateMode")) {
    console.log("UpdateMode:" + urlParams.get("updateMode"));
    window.setTimeout(function () {
      document.getElementById(urlParams.get("updateMode")).click();
    }, 100);
  }
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
});

// Functions

function getFirstElementOfViewport() {
  var msgList = document.getElementsByClassName("msglist")[0];
  var el;
  for (var i = 0; i < msgList.getElementsByClassName("card").length; i++) {
    el =  msgList.getElementsByClassName("card")[i];
    if (el.getBoundingClientRect().top >= 0 ) {
      break;
    }
  }
  return el;
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
      }
      return response;
    }).then(function (response) {
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

function toggleHelp(e, collapsibleBtn, collapsibleHelp, isDisabledSet = false) {
  const classList = collapsibleBtn.classList;
  if (classList.contains("fa-toggle-on") || ((isDisabledSet) && isDisabledSet == true)) {
    classList.remove("fa-toggle-on");
    classList.add("fa-toggle-off");
    collapsibleHelp.style.display = "none";
  } else {
    classList.add("fa-toggle-on");
    classList.remove("fa-toggle-off");
    collapsibleHelp.style.display = "block";
  }
  if (e)
    e.preventDefault();
}


function closeModals() {
  const $modals = getAll('.modal');
  rootEl.classList.remove('is-clipped');
  $modals.forEach(function ($el) {
    $el.classList.remove('is-active');
  });
  btnOpenRouteModal.disabled = false;
  jexlInspectionResultDiv.classList.add("is-hidden");
  jexlInspectionContextParentDiv.classList.add("is-hidden");
  jexlInspectionNoContextDiv.classList.remove("is-hidden");
}

function enableCardToggles() {
  let cardToggles = document.getElementsByClassName('toggle-icon');
  for (let i = 0; i < cardToggles.length; i++) {
    cardToggles[i].addEventListener('click', toggleCardCB);
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
  const classList = target.classList;
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
        collapsibleHeaderBtn.disabled = true;
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
  toggleHelp(null, collapsibleJexlBtn, collapsibleJexl, true);
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
  toggleHelp(null, collapsibleRbelBtn, collapsibleRbel, true);
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

  var menuItem;
  if (isRequest) {
    menuItem = menuHtmlTemplateRequest;
  } else {
    menuItem = menuHtmlTemplateResponse;
  }
  menuItem = menuItem
  .replace("${uuid}", msgMetaData.uuid)
  .replace("${sequence}", msgMetaData.sequenceNumber + 1);
  if (msgMetaData.menuInfoString != null) {
    menuItem = menuItem
    .replace("${menuInfoString}", msgMetaData.menuInfoString);
  } else {
    menuItem = menuItem
    .replace("${menuInfoString}", " ");
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
