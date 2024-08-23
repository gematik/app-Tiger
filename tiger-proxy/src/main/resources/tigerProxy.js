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

"use strict";

import backendClient from '/backendClient.js'
import SenderReceiversFrequencyCounter from '/senderReceiversFrequencyCounter.js'

let lastUuid = "";
let filterCriterion = "";
let rootEl;
let jexlQueryElementUuid = "";
let pageSize = 20;
let pageNumber = 0;
let empty = "empty";

const NO_REQUEST = "no requests";

let resetBtn;
let exportBtn;
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

let collapsibleRbelBtn;
let collapsibleJexlBtn;

let collapsibleDetails;
let collapsibleMessageDetailsBtn;
let collapsibleHeaders;
let collapsibleMessageHeaderBtn;

const senderReceiversFreqCounter = new SenderReceiversFrequencyCounter();

const MAX_HOSTS_TO_DISPLAY_IN_FILTER_DROPDOWN = 2;
let requestFrom = "requestFromContent";
let requestTo = "requestToContent";

let socket;
let stompClient;

let allMessagesAmount;
let filteredMessagesAmount;

let formerClickValue = "";
let formerResultValue = "";

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
    exportBtn = document.getElementById("exportMsgs");
    importBtn = document.getElementById("importMsgs");
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
    exportBtn.addEventListener('click', showModalSave);
    importBtn.addEventListener('click', showModalImport);

    collapsibleDetails = document.getElementById("collapsibleMessageDetails");
    collapsibleMessageDetailsBtn = document.getElementById(
        "collapsibleMessageDetailsBtn");

    collapsibleHeaders = document.getElementById("collapsibleMessageHeader");
    collapsibleMessageHeaderBtn = document.getElementById(
        "collapsibleMessageHeaderBtn");

    enableCopyToClipboardButtons();

    enableCardToggles();

    setFilterCriterionBtn.addEventListener('click', setFilterCriterion);
    resetFilterCriterionBtn.addEventListener('click', resetFilterCriterion);
    quitBtn.addEventListener('click', quitProxy);
    resetBtn.addEventListener('click', resetMessages);
    document.getElementById("executeJexlQuery")
        .addEventListener('click', executeJexlQuery);
    document.getElementById("testRbelExpression")
        .addEventListener('click', testRbelExpression);
    document.getElementById("copyToFilter")
        .addEventListener('click', copyToFilter);

    function todayAsString() {
        const now = new Date();
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

            a.download = `tiger-report-${todayAsString()}-${dateLocal.toISOString().slice(
                11, 19).replace(/[^0-9]/g, "")}.html`;
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

    let selectRequestFromDropdown = document.getElementById("requestFromContent");
    let selectRequestToDropdown = document.getElementById("requestToContent");

    selectRequestToDropdown.addEventListener("focus", updateSelectContents);
    selectRequestToDropdown.addEventListener('change', function (event) {
        if (event.target.value !== NO_REQUEST) {
            let filterField = document.getElementById("setFilterCriterionInput");
            filterField.value = "$.receiver == \"" + event.target.value + "\"";
            selectRequestFromDropdown.selectedIndex = 0;
        }
    });

    selectRequestFromDropdown.addEventListener("focus", updateSelectContents);
    selectRequestFromDropdown.addEventListener('change', function (event) {
        if (event.target.value !== NO_REQUEST) {
            let filterField = document.getElementById("setFilterCriterionInput");
            filterField.value = "$.sender == \"" + event.target.value + "\"";
            selectRequestToDropdown.selectedIndex = 0;
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
    updateSelectContent(requestFrom);
    updateSelectContent(requestTo);
}

function getLabelId(label, id) {
    return label + "_" + id;
}

function updateSelectContent(label) {
    let select = document.getElementById(label);
    let check = document.getElementById(`${label}ShowAllCheck`)

    let frequencyMap = senderReceiversFreqCounter.getMapByLabel(label);

    let listToDisplay = check.checked ? Object.keys(frequencyMap)
        : filterAtLeastN(frequencyMap, MAX_HOSTS_TO_DISPLAY_IN_FILTER_DROPDOWN);

    if (listToDisplay.length === 0) {
        initSelectContent(label, listToDisplay);
    } else {
        clearSelectOptions(select)
        listToDisplay.forEach(e => {
            let element = document.createElement('option');
            element.textContent = e;
            element.id = getLabelId(label, e);
            if (select !== null) {
                select.appendChild(element);
            }
        })
    }
}

function clearSelectOptions(selectElement) {
    Array.from(selectElement.children).filter(e => !e.id.endsWith("_empty"))
        .forEach(e => e.remove());
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
    const msgList = document.getElementsByClassName("msglist")[0];
    const messages = msgList.children;
    const element = Array.from(messages).find(msg => {
        const rect = msg.getBoundingClientRect();
        return rect.top >= 0 && rect.width > 0 && rect.height > 0;
    });
    return element ? element : (messages.length ? messages[0] : undefined);
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
    const input = document.createElement("input");
    input.setAttribute("type", "file");
    input.click(); // opening dialog
    input.onchange = async function () {
        $('.inProgressDialogText').text('Uploading data to backend...');
        $('#showInProgressDialog').modal('show');

        try {
            const response = await backendClient.uploadTrafficFile(input.files[0])
            if (!response.ok) {
                $('.inProgressDialogText').text(
                    'Error while uploading: ' + response.status + " "
                    + response.statusText);
            } else {
                pollMessages(false, pageSize, () => {
                    $('#showInProgressDialog').modal('hide');
                }, true);
                // workaround for now as the pagination on import is NOT working correctly
                pageNumber = 0;
                document.getElementById("pageNumberDisplay").textContent = "Page " + (pageNumber + 1);
            }
        } catch (reason) {
            $('.inProgressDialogText').text('Error while uploading: ' + reason);
        }
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
        && isDisabledSet === true);
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
    // noinspection JSUnusedLocalSymbols
    socket.onclose = function (event) {
        openErrorModal("Websocket is closed")
    }
}

let minSequenceNumber = 0;

async function pollMessages(eraseOldMessages, desiredPageSize, callback, updateMinSeqNumber) {
    const response = await backendClient.getMsgAfter(
        eraseOldMessages ? "" : lastUuid,
        filterCriterion,
        desiredPageSize ? desiredPageSize : "",
        desiredPageSize ? pageNumber : ""
    )
    if (response.ok) {
        const responseBody = await response.json();
        filteredMessagesAmount = responseBody.metaMsgList.length;
        allMessagesAmount = responseBody.totalMsgCount;
        if (eraseOldMessages) {
            resetAllReceivedMessages()
        }
        updateMessageList(responseBody, updateMinSeqNumber);
    } else {
        console.log("ERROR " + response.status + " " + response.statusText);
    }
    if (callback !== undefined) {
        callback();
    }
}

function resetAllReceivedMessages() {
    lastUuid = "";
    const sidebarMenu = document.getElementById("sidebar-menu")
    sidebarMenu.innerHTML = "";
    const listDiv = getAll('.msglist')[0];
    if (listDiv) {
        listDiv.innerHTML = "";
    }
}

async function resetMessages() {
    resetBtn.disabled = true;
    await backendClient.resetMessages();
    pageNumber = 0;
    document.getElementById("pageNumberDisplay").textContent = "Page " + (pageNumber + 1);
    resetAllReceivedMessages();
    senderReceiversFreqCounter.clearAll();
    setTimeout(() => {
        resetBtn.blur();
        resetBtn.disabled = false;
    }, 200);
}

async function quitProxy() {
    quitBtn.disabled = true;
    try {
        const response = await backendClient.quitProxy();
        console.log("ERROR " + response.status + " " + response.statusText);
        setTimeout(() => {
            quitBtn.blur();
            quitBtn.disabled = false;
        }, 200);
    } catch (error) {
        alert("Tiger proxy shut down SUCCESSfully!");
        resetBtn.disabled = true;
        btnScrollLock.disabled = true;
        collapsibleMessageDetailsBtn.disabled = true;
        collapsibleMessageHeaderBtn.disabled = true;
        btnOpenRouteModal.disabled = true;
        btnOpenFilterModal.disabled = true;
        getAll("input.updates").forEach(function (el) {
            el.disabled = true;
        });
        quitBtn.blur();
    }
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

function addQueryBtn(reqEl) {
    let titleDiv = getAll(".card-header-title", reqEl)[0].childNodes[0];
    let titleSpan = getAll("span", titleDiv)[0];
    let msgUuid = getAll("a", titleDiv)[0].getAttribute("name");

    let queryBtn = document.createElement('a');
    queryBtn.innerHTML = '<span class="is-size-7 fw-bold">Inspect</span>';
    queryBtn.setAttribute("class",
        "btn modal-button float-end mx-3 test-btn-inspect");
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
    let i, x, y;
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

//need to expose it so that it can be used directly in the jexlModal.html
window.openTab = openTab;

function copyToFilter() {
    document.getElementById("setFilterCriterionInput").value
        = document.getElementById("jexlQueryInput").value;
    setFilterCriterion();
}

async function executeJexlQuery() {
    toggleHelp(collapsibleJexlBtn, "jexl-help", true);
    let jexlQuery = document.getElementById("jexlQueryInput").value;
    const response = await backendClient.testJexlQuery(jexlQueryElementUuid,
        jexlQuery);
    if (response.ok) {
        const responseBody = await response.json();
        shortenStrings(responseBody);
        if (responseBody.messageContext) {
            const map = new Map(Object.entries(responseBody.messageContext));
            let html = "<h3 class='is-size-4'>JEXL context</h3>";
            map.forEach((value, key) => {
                html += "<prekey id='json_" + encodeURIComponent(key) + "'>" + key
                    + "</prekey>"
                    + "<pre class='paddingLeft' id='json__" + encodeURIComponent(key)
                    + "'>"
                    + JSON.stringify(value, null, 6)
                    + "</pre><br>";
            });
            jexlInspectionContextDiv.innerHTML = html;
        } else {
            jexlInspectionContextDiv.innerHTML = "<h3 class='is-size-4'>NO JEXL context received</h3>";
        }

        jexlInspectionContextParentDiv.classList.remove("d-none");
        jexlInspectionNoContextDiv.classList.add("d-none");
        if (responseBody.errorMessage) {
            jexlInspectionResultDiv.innerHTML = "<b>JEXL is invalid: </b>"
                + "<code class='bg-dark text-warning'>" + responseBody.errorMessage
                + "</code>";
            jexlInspectionResultDiv.setAttribute("class", "box bg-danger");

        } else if (responseBody.matchSuccessful) {
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
            + (response.statusText ?
                ("<code class='bg-dark text-warning'>" + responseBody.error
                    + "</code>") :
                "");
        jexlInspectionResultDiv.setAttribute("class", "box bg-warning");
    }
}

async function testRbelExpression() {
    toggleHelp(collapsibleRbelBtn, "rbel-help", true);
    let rbelPath = document.getElementById("rbelExpressionInput").value;
    formerClickValue = "";

    const response = await backendClient.testRbelExpression(jexlQueryElementUuid,
        rbelPath);

    if (response.ok) {
        const responseBody = await response.json();
        document.getElementById("rbelTestTree").innerHTML =
            "<h3 class='is-size-4'>Rbel Tree</h3>"
            + "<pre id='shell'>" + responseBody.rbelTreeHtml + "</pre>";
        let rbelResultTree = "<h3 class='is-size-4'>Matching Elements</h3>";
        if (responseBody.elements) {
            responseBody.elements.forEach(key => {
                rbelResultTree += "<div >" + key + "</div>";
                formerResultValue = key;
            });
        } else {
            rbelResultTree += "<div>No matching elements for " + rbelPath + "</div>";
        }
        document.getElementById("rbelResult").innerHTML =
            rbelResultTree;
        setAddEventListener();
    } else {
        console.log("ERROR " + response.status + " " + response.statusText);
        document.getElementById("rbelResult").innerHTML =
            "<div>" + response.statusText + "</div>";
        document.getElementById(
            "rbelTestTree").innerHTML = "<div>Invalid query</div>";
    }
}

function setAddEventListener() {
    const jexlResponseLinks = document.getElementsByClassName("jexlResponseLink");
    Array.from(jexlResponseLinks).forEach(element => {
        element.addEventListener('click', (e) => copyPathToInputField(e, element));
    });
}

function copyPathToInputField(event, element) {
    event.preventDefault();
    let oldValue = document.getElementById("rbelExpressionInput").value;
    let text = element.textContent;
    let el = element.previousElementSibling;
    let marker = el.textContent;

    function stringContainsNonWordCharacters(testString) {
        return testString.match("\\W") != null;
    }

    if (stringContainsNonWordCharacters(text)) {
        text = "['" + text + "']";
    }
    while (el != null) {
        if (el.classList) {
            if (el.classList.contains('jexlResponseLink')) {
                if (el.previousElementSibling.classList.contains('text-danger') &&
                    el.previousElementSibling.textContent.length < marker.length) {
                    if (stringContainsNonWordCharacters(el.textContent)) {
                        text = "['" + el.textContent + "']." + text;
                    } else {
                        text = el.textContent + "." + text;
                    }
                    marker = el.previousElementSibling.textContent;
                }
            }
        }
        el = el.previousElementSibling;
    }
    if (oldValue == null || text.startsWith("body")) {
        document.getElementById("rbelExpressionInput").value = "$." + text;
    } else {
        // check for children
        if (formerClickValue.length > 0) {
            oldValue = oldValue.substring(0,
                oldValue.length - formerClickValue.length - 1);
        } else {
            const words = oldValue.split('.');
            oldValue = oldValue.substring(0,
                oldValue.length - words[words.length - 1].length - 1);
        }
        if (!(formerResultValue.endsWith("content"))) {
            document.getElementById("rbelExpressionInput").value = oldValue + "."
                + text;
        }
    }
    formerClickValue = text;
}

function shortenStrings(obj) {
    for (let property in obj) {
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
    let elems = messageElement.getElementsByClassName("header-toggle")
    if (elems.length) {
        setCollapsableIcon(elems[0], collapseMessageHeaders)
    }
    elems = messageElement.getElementsByClassName("msg-header-content")
    if (elems.length) {
        elems[0].classList.toggle('d-none', collapseMessageHeaders)
    }
    elems = messageElement.getElementsByClassName("msg-toggle")
    if (elems.length) {
        setCollapsableIcon(elems[0], collapseMessageDetails)
    }
    elems = messageElement.getElementsByClassName("msg-content")
    if (elems.length) {
        elems[0].classList.toggle('d-none', collapseMessageDetails)
    }
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
        if (el.getAttribute("data-hljs-highlighted") !== "true") {
            hljs.highlightElement(el);
            el.setAttribute("data-hljs-highlighted", "true");
        }
    });
    listDiv.appendChild(message);
    if (!scrollLock) {
        message.scrollIntoView({behaviour: "smooth", alignToTop: true});
    }
    updateHidingForMessageElement(message);
}

function addMessageToMenu(msgMetaData) {
    createMenuEntry(msgMetaData);
    senderReceiversFreqCounter.addSenderAndReceiver(msgMetaData)
}

function filterAtLeastN(frequencyMap, atLeastN) {
    const elementsAtLeastNFrequent = []
    for (const [element, frequency] of Object.entries(frequencyMap)) {
        if (frequency >= atLeastN) {
            elementsAtLeastNFrequent.push(element)
        }
    }
    return elementsAtLeastNFrequent;
}

function setFilterMessage() {
    const element = document.getElementById("filteredMessage");
    if (allMessagesAmount === filteredMessagesAmount) {
        element.textContent = "Filter didn't match any of the " + allMessagesAmount
            + " messages.";
    } else {
        element.textContent = filteredMessagesAmount + " of " + allMessagesAmount
            + " did match the filter criteria.";
    }
}

function updateMessageList(json, updateMinSeqNumber) {
    for (let htmlMsg of json.htmlMsgList) {
        addMessageToMainView(htmlMsg);
    }
    for (let metaMsg of json.metaMsgList) {
        addMessageToMenu(metaMsg);
    }
    if (updateMinSeqNumber === true) {
        minSequenceNumber = document.querySelectorAll("#sidebar-menu span.tag")[0].innerText - 1
    }

    updatePageSelector();
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

async function getRoutes() {
    getAll(".routeListDiv")[0].innerHTML = "";
    getAll(".routeListDiv")[0].append(getInnerHTMLForRoutes());
    const response = await backendClient.getRoutes();
    if (response.ok) {
        const responseBody = await response.json();
        updateRouteList(responseBody);
    } else {
        console.log("ERROR " + response.status + " " + response.statusText);
        getAll(".routeListDiv")[0].innerHTML = "ERROR " + response.status + " "
            + response.statusText;
    }
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

async function deleteRoute(e) {
    const routeid = e.currentTarget.id.substring("route-".length);
    const response = await backendClient.deleteRoute(routeid);
    if (!response.ok) {
        console.log("ERROR " + response.status + " " + response.statusText);
    }
    getRoutes();
}

function updateAddRouteBtnState() {
    btnAddRoute.disabled = !(fieldRouteTo.value && fieldRouteFrom.value);
}

async function addRoute() {
    try {
        new URL(fieldRouteTo.value);
        new URL(fieldRouteFrom.value);
    } catch (e) {
        alert("Invalid URL!");
        return;
    }
    const response = await backendClient.addRoute({
        from: fieldRouteFrom.value,
        to: fieldRouteTo.value
    })
    if (!response.ok) {
        console.log("ERROR " + response.status + " " + response.statusText);
    }
    getRoutes();
}

function setPageSize(newSize) {
    pageSize = newSize;
    pageNumber = 0;
    document.getElementById("pageSizeDisplay").textContent =
        "Size " + newSize;
    pollMessages(true, pageSize);
}

//need to expose it so that it can be used directly in html of the size selector
window.setPageSize = setPageSize;

function setPageNumber(newPageNumber, callback) {
    pageNumber = newPageNumber;
    document.getElementById("pageNumberDisplay").textContent =
        "Page " + (newPageNumber + 1);
    pollMessages(true, pageSize, callback);
}

//need to expose it to be able to use it directly in the html of page selector
window.setPageNumber = setPageNumber;

// based on the menu entries decide how many pages we do have
function updatePageSelector() {
    const entriesCount = document.querySelector('#sidebar-menu').children.length
    let selector = document.getElementById("pageSelector");
    let selectorInnerHtml = '';
    // 0,19,20 -> 1
    // 21 -> 2
    // 60 -> 3
    // 61 -> 4
    const maxPagesCount = Math.max(1, Math.floor((entriesCount - 1) / pageSize) + 1);
    for (let i = 0; i < maxPagesCount; i++) {
        selectorInnerHtml +=
            '<a class="dropdown-item" onclick="setPageNumber(' + i + ');">'
            + (i + 1)
            + '</a>';
    }
    selector.innerHTML = selectorInnerHtml;
    document.getElementById("pageNumberDisplay").textContent =
        "Page " + (pageNumber + 1);
}

// overwrites rbel js method as rbel html has no pagination feature
function scrollToMessage(uuid, sequenceNumber) {
    const seqNumberRelative = sequenceNumber - minSequenceNumber;
    if ((seqNumberRelative < pageNumber * pageSize)
        || (seqNumberRelative >= (pageNumber + 1) * pageSize)) {
        setPageNumber(Math.ceil((seqNumberRelative + 1) / pageSize) - 1,
            function () {
                scrollMessageIntoView(uuid);
            });
    } else {
        scrollMessageIntoView(uuid)
    }
}

//need to expose it so that it can be used directly in the message templates
window.scrollToMessage = scrollToMessage;

function messageScrollToReceiver(ev) {
    scrollToMessage(ev.data.split(",")[0], Number(ev.data.split(",")[1]));
}

if (window.addEventListener) {
    window.addEventListener("message", messageScrollToReceiver, false);
} else {
    window.attachEvent("onmessage", messageScrollToReceiver);
}

backendClient.addErrorEventListener((errorEvent) => {
    openErrorModal(errorEvent.detail.message);
})

function openErrorModal(errorMessage) {
    const errorModal = $('#errorMessagesDialog');
    const title = errorModal.find('#errorMessageTitle');
    const body = errorModal.find('#errorMessageBody');
    title.text("Error connecting with the backend")
    body.text(errorMessage);
    errorModal.modal('show');
}


