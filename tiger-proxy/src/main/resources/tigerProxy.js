/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

"strict";

let lastUuid = "";
let filterCriterion = "";
let rootEl;
let jexlQueryElementUuid = "";
let pageSize = 20;
let pageNumber = 0;

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

let btnOpenRouteModal;
let fieldRouteTo;
let fieldRouteFrom;
let btnAddRoute;

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
let filterBtn;

let requestFrom = "requestFromContent";
let requestTo = "requestToContent";

let socket;
let stompClient;

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

  filterBtn = document.getElementById("dropdown-filter-button");
  filterBtn.addEventListener("click", updateNestedDropdownContent);

  hideBtn = document.getElementById("dropdown-hide-button");
  hideBtn.addEventListener("click", hideDropdownContent);
  addDropdownClickListener(document.getElementById("dropdown-page-selection"));
  addDropdownClickListener(document.getElementById("dropdown-page-size"));

  setFilterCriterionBtn = document.getElementById("setFilterCriterionBtn");
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
  collapsibleRbelBtn = document.getElementById("rbel-help-icon");
  collapsibleJexlBtn = document.getElementById("jexl-help-icon");
  collapsibleRbelBtn.addEventListener('click', () => {
    toggleHelp(collapsibleRbelBtn, "rbel-help");
  });
  collapsibleJexlBtn.addEventListener('click', () => {
    toggleHelp(collapsibleJexlBtn, "jexl-help")
  });
  btnOpenRouteModal.addEventListener('click', showModalsCB);
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

        let msgCards = document.getElementsByClassName('msg-card');
        Array.from(msgCards).forEach(card => {
          const cardToggle = card.children[0].children[0];
          card.childNodes[1].classList.toggle('is-hidden',
              collapseMessageDetails);
          setCollapsableIcon(cardToggle.children[0].children[1],
              collapseMessageDetails);
        });
        if (firstElementOfView) {
          window.setTimeout(() => {
            firstElementOfView.scrollIntoView();
            window.scrollBy(0, -15);
          }, 50);
        }
        let hideDropdownBtn = document.getElementById("dropdown-hide-button");
        hideDropdownBtn.parentElement.classList.remove("is-active");
      });

  collapsibleMessageHeaderBtn.addEventListener('click',
      () => {
        const firstElementOfView = getFirstElementOfViewport();
        collapseMessageHeaders = !collapseMessageHeaders;
        collapsibleHeaders.classList.toggle("led-error",
            collapseMessageHeaders);

        let msgCards = document.getElementsByClassName(
            'card full-width is-primary notification');
        Array.from(msgCards).forEach(card => {
          const cardToggle = card.children[0].children[0];
          card.childNodes[1].classList.toggle('is-hidden',
              collapseMessageHeaders);
          setCollapsableIcon(cardToggle.children[0].children[0],
              collapseMessageHeaders);
        });
        if (firstElementOfView) {
          window.setTimeout(() => {
            firstElementOfView.scrollIntoView();
            window.scrollBy(0, -15);
          }, 50);
        }
        let hideDropdownBtn = document.getElementById("dropdown-hide-button");
        hideDropdownBtn.parentElement.classList.remove("is-active");
      });

  initDropdownContent(requestFrom, senders);
  initDropdownContent(requestTo, receivers);

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

function getLabelId(label, id) {
  return label + "_" + id;
}

function updateDropdownContent(label, list) {
  let divDropdownContent = document.getElementById(label);
  let contained = false;

  if (list.length === 0) {
    initDropdownContent(label, list);
  } else {
    if (divDropdownContent.children[0].id === getLabelId(label, "empty")) {
      divDropdownContent.removeChild(divDropdownContent.children[0]);
    }
    for (let i = 0; i < list.length; i++) {
      contained = false;
      if (divDropdownContent !== null) {
        Array.from(divDropdownContent.children).forEach(child => {
          if (child.id === getLabelId(label, list[i])) {
            contained = true;
          }
        });
      }
      if (!contained) {
        let element = document.createElement('A');
        element.textContent = list[i];
        element.id = getLabelId(label, list[i]);
        element.className = "dropdown-item";
        element.setAttribute("href", "#");
        element.addEventListener("click", () => {
          removeActiveFlag(requestFrom);
          removeActiveFlag(requestTo);
          element.classList.add("is-active");
          let filterField = document.getElementById(
              "setFilterCriterionInput");
          if (label === requestFrom) {
            filterField.value = "@.sender == \"" + list[i] + "\"";
          } else {
            filterField.value = "@.receiver == \"" + list[i] + "\"";
          }
          let filterDropdownBtn = document.getElementById(
              "dropdown-filter-button");
          filterDropdownBtn.parentElement.classList.remove("is-active");
        });
        divDropdownContent.appendChild(element);
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

function updateNestedDropdownContent() {
  closeAllDropdowns()
  filterBtn.parentElement.classList.toggle("is-active");
  updateDropdownContent(requestFrom, senders);
  updateDropdownContent(requestTo, receivers);
}

function hideDropdownContent() {
  closeAllDropdowns()
  hideBtn.parentElement.classList.toggle("is-active");
}

function initDropdownContent(label, list) {
  let divDropdownContent = document.getElementById(label);
  if (list.length === 0) {
    Array.from(divDropdownContent.children).forEach(child => {
      divDropdownContent.removeChild(child);
    });
    let element = document.createElement('A');
    element.textContent = "no requests";
    element.id = getLabelId(label, "empty");
    element.className = "dropdown-item";
    element.setAttribute("href", "#");
    divDropdownContent.appendChild(element);
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
        updateNestedDropdownContent();
        filterBtn.parentElement.classList.remove("is-active");
        hideBtn.parentElement.classList.remove("is-active");
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
  collapsibleDetails.classList.remove("led-error");
  collapsibleHeaders.classList.remove("led-error");
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
  document.getElementById("rbelExpressionInput").value = "$." + text;
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

function addMessageToMainView(msgHtmlData) {
  const listDiv = getAll('.msglist')[0];
  const reqEl = htmlToElement(msgHtmlData.html);
  let span = getAll(".msg-sequence", reqEl)[0];
  if (span != null) {
    span.classList.add("tag", "is-info", "is-light", "mr-3", "is-size-3");
    span.textContent = msgHtmlData.sequenceNumber + 1;
  }
  addQueryBtn(reqEl);
  listDiv.appendChild(reqEl);
  if (!scrollLock) {
    reqEl.scrollIntoView({behaviour: "smooth", alignToTop: true});
  }
}

function addMessageToMenu(msgMetaData, index) {
  let isRequest = msgMetaData.request;

  var menuItem;
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
      senders.push(msgMetaData.sender);
    }
  }
  if (msgMetaData.recipient != null) {
    const foundReceiver = Array.from(receivers).find(msg => {
      return msg === msgMetaData.recipient;
    });
    if (foundReceiver == null) {
      receivers.push(msgMetaData.recipient);
    }
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

function setPageNumber(newPageNumber) {
  pageNumber = newPageNumber;
  document.getElementById("pageNumberDisplay").textContent =
      "Page " + (newPageNumber + 1);
  closeAllDropdowns();
  resetAllReceivedMessages();
  pollMessages();
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

function scrollToMessage(uuid, sequenceNumber) {
//    const sidebar = getAll('.menu')[0];
  if ((sequenceNumber < pageNumber * pageSize)
      || (sequenceNumber > (pageNumber + 1) * pageSize)) {
    setPageNumber(Math.ceil(sequenceNumber / pageSize) - 1)
  }

  let elements = document.getElementsByName(uuid);
  if (elements.length > 0) {
    elements[0].scrollIntoView({behaviour: "smooth", alignToTop: true});
  }
}

function addDropdownClickListener(el) {
  el.addEventListener('click', function(e) {
    e.stopPropagation();
    el.classList.toggle('is-active');
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

document.onr