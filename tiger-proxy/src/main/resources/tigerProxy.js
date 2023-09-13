/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

"use strict";

let lastUuid = "";
let filterCriterion = "";
let rootEl;
let jexlQueryElementUuid = "";
let pageSize = 20;
let pageNumber = 0;
let empty = "empty";

const NO_REQUEST = "no requests";

let resetBtn;
let saveBtn;
let uploadBtn;
let quitBtn;
let importBtn;
let includeFilterInDownload = false;

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

const menuHtmlTemplateRequest =
    "<div class=\"ms-1 is-size-7\">\n"
    + "  <a onclick=\"scrollToMessage('${uuid}',${sequenceNumber})\" class=\"mt-3 is-block\">\n"
    + "    <div class=\"has-text-link d-flex align-items-center\">\n"
    + "      <span class=\"tag is-info is-light me-1\">${sequence}</span>\n"
    + "      <i class=\"fas fa-share\"></i>\n"
    + "      <span class=\"mx-1\">REQ</span>\n"
    + "      <span class=\"has-text-dark text-ellipsis ms-auto\">${timestamp}</span>\n"
    + "    </div>\n"
    + "    <div class=\"ms-4 has-text-link d-flex align-items-center\">\n"
    + "      <span class=\"ms-1 has-text-weight-bold text-ellipsis\""
    + "        title=\"${menuInfoString}\">${menuInfoString}"
    + "      </span>\n"
    + "    </div>\n"
    + "  </a></div>";
const menuHtmlTemplateResponse =
    "<div class=\"ms-1 mb-4 is-size-7\">"
    + "  <a onclick=\"scrollToMessage('${uuid}',${sequenceNumber})\" class=\"mt-3 is-block\">\n"
    + "    <div class=\"mb-1 text-success d-flex align-items-center\">\n"
    + "      <span class=\"tag is-info is-light me-1\">${sequence}</span>\n"
    + "      <i class=\"fas fa-reply\"></i>\n"
    + "      <span class=\"ms-1\">RES</span>\n"
    + "      <span class=\"mx-1 has-text-weight-bold\"\n"
    + "         title=\"${menuInfoString}\">${menuInfoString}"
    + "      </span>\n"
    + "      <span class=\"has-text-dark text-ellipsis ms-auto\">${timestamp}</span>\n"
    + "    </div>\n"
    + "  </a></div>";

function createDownloadOptionsQueryString() {
  if (!includeFilterInDownload) {
    return "";
  } else {
    const downloadOptions = {
      lastUuid,
      filterCriterion
    }
    return new URLSearchParams(downloadOptions).toString();
  }
}

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

  enableCopyToClipboardButtons();

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
    uploadBtn.classList.add("d-none");
  } else {
    uploadBtn.addEventListener('click', uploadReport);
  }

  function todayAsString() {
    var now = new Date();
    return padStr(now.getFullYear() - 2000) +
        padStr(1 + now.getMonth()) +
        padStr(now.getDate());
  }

  function padStr(i) {
    return (i < 10) ? "0" + i : "" + i;
  }

  document.getElementById("includeFilterInDownloadCheck")
      .addEventListener('change', e => {
        includeFilterInDownload = e.currentTarget.checked
      })

  document.getElementById("saveTrafficBtn")
  .addEventListener('click', e => {
    $('#saveModalDialog').modal('hide');

    const a = document.createElement('a');
    a.style.display = 'none';

    const queryString = createDownloadOptionsQueryString();

    a.href = `/webui/trafficLog-${todayAsString()}.tgr?${queryString}`;
    a.download = `trafficLog-${todayAsString()}.tgr`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(a.href);

    e.preventDefault();
    return false;
  });

  document.getElementById("saveHtmlBtn")
  .addEventListener('click', e => {
    $('#saveModalDialog').modal('hide');

    const a = document.createElement('a');
    a.style.display = 'none';

    const now = new Date();
    const offsetMs = now.getTimezoneOffset() * 60 * 1000;
    const dateLocal = new Date(now.getTime() - offsetMs);

    const queryString = createDownloadOptionsQueryString();

    a.download = `tiger-report-${todayAsString()}-${dateLocal.toISOString().slice(11, 19).replace(/[^0-9]/g, "")}.html`;
    a.href = `/webui/${a.download}?${queryString}`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(a.href);

    e.preventDefault();
    return false;
  });

  btnOpenRouteModal.addEventListener('click',
      () => {
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
      });

  let selectRequestFromBtn = document.getElementById("requestFromContent");

  let selectRequestToBtn = document.getElementById("requestToContent");
  selectRequestToBtn.addEventListener("click", updateSelectContents);
  selectRequestToBtn.addEventListener('change', function (event) {
    if (event.target.value !== NO_REQUEST) {
      let filterField = document.getElementById("setFilterCriterionInput");
      filterField.value = "$.receiver == \"" + event.target.value + "\"";
      selectRequestFromBtn.selectedIndex = 0;
    }
  });

  selectRequestFromBtn.addEventListener("click", updateSelectContents);
  selectRequestFromBtn.addEventListener('change', function (event) {
    if (event.target.value !== NO_REQUEST) {
      let filterField = document.getElementById("setFilterCriterionInput");
      filterField.value = "$.sender == \"" + event.target.value + "\"";
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
    document.getElementsByClassName("sidebar")[0].classList.add("d-none");
    document.getElementsByClassName("main-content")[0].classList.add("d-none");
    const not4embeddedelems = document.getElementsByClassName("not4embedded");
    for (let i = 0; i < not4embeddedelems.length; i++) {
      not4embeddedelems[i].classList.add("d-none");
    }
  }

  try {
    connectToWebSocket();
    setPageSize(pageSize);
  } catch (e) {
    console.warn("Unable to connect to backend " + e);
  }

});

// Functions
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
          if (select !== null) {
            select.appendChild(element);
          }
        }
      }
    }
  }
}

function deleteRequestsLists() {
  senders = [];
  receivers = [];
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

function enableCopyToClipboardButtons() {
  // Modals
  let $copyButtons = getAll('.copyToClipboard-button');

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
}

function showModalSave(e) {
  $('#saveModalDialog').modal('show');
  e.preventDefault();
  return false;
}

function showModalImport(e) {
  var input = document.createElement("input");
  input.setAttribute("type", "file");
  input.click(); // opening dialog
  input.onchange = function () {
    $('.inProgressDialogText').text('Uploading data to backend...');
    $('#showInProgressDialog').modal('show');

    fetch('/webui/traffic', {
      method: "POST",
      body: input.files[0]
    })
    .then(function (response) {
      if (!response.ok) {
        $('.inProgressDialogText').text('Error while uploading: ' + response.status + " " + response.statusText);
      } else {
        pollMessages(false, pageSize, () => {
          $('#showInProgressDialog').modal('hide');
        });
      }
      return response;
    }).catch(reason => {
      $('.inProgressDialogText').text('Error while uploading: ' + reason);
    });
  };
  e.preventDefault();
  return false;
}

function showModalsCB(e) {
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
      'd-none');
  toggleCollapsableIcon(e.currentTarget);
  e.preventDefault();
  return false;
}

function enableCollapseExpandAll() {
  let msgCards = document.getElementsByClassName('msg-card');

  document.getElementById("collapse-all").addEventListener('click', e => {
    for (let i = 0; i < msgCards.length; i++) {
      const classList = msgCards[i].childNodes[1].classList;
      classList.toggle('d-none', true);
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
      classList.toggle('d-none', false);
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
            pollMessages(false, pageSize, () => {
              currentlyPolling = false;
            });
          }
        });
      },
      (error) => {
        console.error("Websocket error: " + JSON.stringify(error));
      }
  )
}

function pollMessages(eraseOldMessages, desiredPageSize, callback) {
  const xhttp = new XMLHttpRequest();
  xhttp.open("GET", "/webui/getMsgAfter"
      + "?lastMsgUuid=" + (eraseOldMessages ? "" : lastUuid)
      + "&filterCriterion=" + encodeURIComponent(filterCriterion)
      + (desiredPageSize ? "&pageSize=" + desiredPageSize : "")
      + (desiredPageSize ? "&pageNumber=" + pageNumber : ""), true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);
        filteredMessagesAmount = response.metaMsgList.length;
        allMessagesAmount = response.totalMsgCount;
        if (eraseOldMessages) {
          resetAllReceivedMessages()
        }
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
  if (listDiv) {
    listDiv.innerHTML = "";
  }
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
  let spinner = getSpinner();
  setFilterCriterionBtn.children[0].classList.add("d-none");
  setFilterCriterionBtn.appendChild(spinner);
  filterCriterion = setFilterCriterionInput.value;
  pollMessages(true, pageSize);
  setFilterCriterionBtn.children[0].classList.remove("d-none");
  setFilterCriterionBtn.removeChild(spinner);
}

function getSpinner() {
  let divElement = document.createElement('div');
  divElement.classList.add("spinner-border");
  divElement.setAttribute("role", "status");
  let spanElement = document.createElement('span');
  spanElement.classList.add("visually-hidden");
  spanElement.textContent = "Loading...";
  divElement.appendChild(spanElement);
  return divElement;
}

function resetFilterCriterion() {
  let spinner = getSpinner();
  resetFilterCriterionBtn.children[0].classList.add("d-none");
  resetFilterCriterionBtn.appendChild(spinner);
  filterCriterion = "";
  setFilterCriterionInput.value = '';
  document.getElementById("requestToContent").selectedIndex = 0;
  document.getElementById("requestFromContent").selectedIndex = 0;
  pollMessages(true, pageSize);
  resetFilterCriterionBtn.children[0].classList.remove("d-none");
  resetFilterCriterionBtn.removeChild(spinner);
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

function addQueryBtn(reqEl) {
  let titleDiv = getAll(".card-header-title", reqEl)[0].childNodes[0];
  let titleSpan = getAll("span", titleDiv)[0];
  let msgUuid = getAll("a", titleDiv)[0].getAttribute("name");

  let queryBtn = document.createElement('a');
  queryBtn.innerHTML = '<span class="is-size-7 fw-bold">Inspect</span>';
  queryBtn.setAttribute("class", "btn modal-button float-end mx-3 test-btn-inspect");
  queryBtn.setAttribute("data-bs-target", "#jexlQueryModal");
  queryBtn.setAttribute("data-bs-toggle", "modal");
  queryBtn.addEventListener("click", function (e) {
    jexlQueryElementUuid = msgUuid;
    e.preventDefault();
    return false;
  });
  titleSpan.appendChild(queryBtn);
}

function openTab(sender, tabName) {
  var i, x, y;
  x = document.getElementsByClassName("content-tab");
  for (i = 0; i < x.length; i++) {
    x[i].style.display = "none";
  }
  document.getElementById(tabName).style.display = "block";

  y = document.getElementsByClassName("nav-link active");
  for (i = 0; i < y.length; i++) {
    y[i].classList.remove("active");
  }

  document.getElementById(tabName + "-name").classList.add("active");
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
      const response = JSON.parse(this.responseText);
      if (this.status === 200) {
        shortenStrings(response);
        if (response.messageContext) {
          const map = new Map(Object.entries(response.messageContext));
          var html = "<h3 class='is-size-4'>JEXL context</h3>";
          map.forEach((value, key) => {
            html += "<prekey id='json_" + encodeURIComponent(key) + "'>" + key + "</prekey>"
                + "<pre class='paddingLeft' id='json__" + encodeURIComponent(key) + "'>"
                + JSON.stringify(value, null, 6)
                + "</pre><br>";
          });
          jexlInspectionContextDiv.innerHTML = html;
        } else {
          jexlInspectionContextDiv.innerHTML = "<h3 class='is-size-4'>NO JEXL context received</h3>";
        }

        jexlInspectionContextParentDiv.classList.remove("d-none");
        jexlInspectionNoContextDiv.classList.add("d-none");
        if (response.errorMessage) {
          jexlInspectionResultDiv.innerHTML = "<b>JEXL is invalid: </b>"
              + "<code class='bg-dark text-warning'>" + response.errorMessage
              + "</code>";
          jexlInspectionResultDiv.setAttribute("class", "box bg-danger");

        } else if (response.matchSuccessful) {
          jexlInspectionResultDiv.innerHTML = "<b>Condition is true: </b>"
              + "<code class='bg-dark text-warning'>" + jexlQuery
              + "</code>";
          jexlInspectionResultDiv.setAttribute("class", "box bg-success");
        } else {
          jexlInspectionResultDiv.innerHTML = "<b>Condition is false: </b>"
              + "<code class='bg-dark text-warning'>" + jexlQuery
              + "</code>";
          jexlInspectionResultDiv.setAttribute("class", "box bg-info");
        }
      } else {
        jexlInspectionResultDiv.innerHTML = "<b>Error talking to server! </b>"
            + (this.responseText ?
                ("<code class='bg-dark text-warning'>" + response.error + "</code>") :
                "");
        jexlInspectionResultDiv.setAttribute("class", "box bg-warning");
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
        if (response.elements) {
          response.elements.forEach(key => {
            rbelResultTree += "<div >" + key + "</div>";
          });
        } else {
          rbelResultTree += "<div>None found</div>";
        }
        document.getElementById("rbelResult").innerHTML =
            rbelResultTree;
        setAddEventListener();
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
        document.getElementById("rbelResult").innerHTML =
            "<div>" + this.responseText + "</div>";
        document.getElementById("rbelTestTree").innerHTML = "<div>Invalid query</div>";
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
        if (el.previousElementSibling.classList.contains('text-danger') &&
            el.previousElementSibling.textContent.length < marker.length) {
          text = el.textContent + "." + text;
          marker = el.previousElementSibling.textContent;
        }
      }
    }
    el = el.previousElementSibling;
  }
  if (oldValue == null || text.startsWith("body.html")) {
    document.getElementById("rbelExpressionInput").value = "$." + text;
  } else {
    const words = oldValue.split('.');
    oldValue = oldValue.substring(0, oldValue.length - words[words.length - 1].length);
    document.getElementById("rbelExpressionInput").value = oldValue + text;
  }
}

function shortenStrings(obj) {
  for (var property in obj) {
    if (property === "errorMessage") {
      continue;
    }
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
  .toggle('d-none', collapseMessageHeaders);
  setCollapsableIcon(
      messageElement.getElementsByClassName("msg-toggle")[0],
      collapseMessageDetails);
  messageElement.getElementsByClassName("msg-content")[0].classList
  .toggle('d-none', collapseMessageDetails);
}

function addMessageToMainView(msgHtmlData) {
  const listDiv = getAll('.msglist')[0];
  const message = htmlToElement(msgHtmlData.html);
  let span = getAll(".msg-sequence", message)[0];
  if (span != null) {
    span.classList.add("tag", "is-info", "is-light", "me-3", "is-size-3");
    span.textContent = msgHtmlData.sequenceNumber + 1;
  }
  addQueryBtn(message);
  message.querySelectorAll('pre.json').forEach(el => {
    hljs.highlightElement(el);
  });
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
      if (index >= 0) {
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
      if (index >= 0) {
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
    element.textContent = filteredMessagesAmount + " of " + allMessagesAmount + " did match the filter criteria.";
  }
}

function updateMessageList(json) {
  updatePageSelector(json.pagesAvailable);
  for (let htmlMsg of json.htmlMsgList) {
    addMessageToMainView(htmlMsg);
  }
  let index = 0;
  for (let metaMsg of json.metaMsgList) {
    addMessageToMenu(metaMsg, index++);
  }
  if (json.metaMsgList.length > 0) {
    lastUuid = json.metaMsgList[json.metaMsgList.length - 1].uuid;
  }
  setFilterMessage();
  enableCardToggles();
  enableCopyToClipboardButtons();
}

function getInnerHTMLForRoutes() {
  let divElement = document.createElement('div');
  divElement.style.justifyContent = "center";
  divElement.style.display = "flex";
  let spinner = getSpinner();
  spinner.style.marginRight = "1rem";
  divElement.appendChild(spinner);
  let para = document.createElement('p');
  para.textContent = "Loading...";
  divElement.appendChild(para);
  return divElement;
}

function getRoutes() {
  getAll(".routeListDiv")[0].innerHTML = "";
  getAll(".routeListDiv")[0].append(getInnerHTMLForRoutes());
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
    html += "<div class='box routeentry row'>"
        + "<div class='col-md-2'>"
        + "<button id='route-" + route.id
        + "' class='btn delete-route btn-danger'>"
        + "<i class=\"far fa-trash-alt\"></i>"
        + "</button></div>"
        + "<div class='col-md-10'>&rarr; " + route.from
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
  pollMessages(true, pageSize);
}

function setPageNumber(newPageNumber, callback) {
  pageNumber = newPageNumber;
  document.getElementById("pageNumberDisplay").textContent =
      "Page " + (newPageNumber + 1);
  pollMessages(true, pageSize, callback);
}

function updatePageSelector(pagesAvailable) {
  let selector = document.getElementById("pageSelector");
  let selectorInnerHtml = '';
  for (let i = 0; i < pagesAvailable; i++) {
    selectorInnerHtml +=
        '<a class="dropdown-item" onclick="setPageNumber(' + i + ');">'
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
    setPageNumber(Math.ceil((sequenceNumber + 1) / pageSize) - 1, scrollMessageIntoView)
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
