/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
const menuHtmlMessage =
    `<div class="ms-1 is-size-7">  <a onclick="scrollToMessage('$\{uuid}',$\{sequenceNumber})" class="mt-3 is-block">
    <div class="$\{color} d-flex align-items-center">
      <span class="tag $\{color} is-light me-1">$\{sequence}</span>
      <i class="fas $\{symbol}"></i>
      <span class="ms-1">$\{abbrev}</span>
      <span class="has-text-dark text-ellipsis ms-auto">$\{timestamp}</span>
    </div>
    <div class="ms-4 $\{color} d-flex align-items-center">
      <span class="ms-1 has-text-weight-bold text-ellipsis"        title="$\{menuInfoString}">$\{menuInfoString}      </span>
    </div>
    <div class="ms-4 $\{color} d-flex align-items-center">
      <span class="ms-3 text-ellipsis"        title="$\{additionalInformation}">$\{additionalInformation}      </span>
    </div>
  </a></div>`;

const menuHtmlTemplateSubResponse =
    "      <i class=\"fas fa-reply\"></i>\n"
    + "      <span class=\"ms-1\">RES</span>\n"
    + "      <span class=\"mx-1\" \n"
    + "         title=\"${additionalInformation}\">${additionalInformation}"
    + "      </span>\n";

let msgIndex = 1;

// called implicitly by the HTML code created by the server at
// de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.renderDocument (end of method)
function createMenuEntry(msgMetaData) {
    let isRequest = msgMetaData.request;

    let menuItem = menuHtmlMessage;
    menuItem = menuItem
        .replaceAll("${uuid}", msgMetaData.uuid)
        .replaceAll("${color}", msgMetaData.color)
        .replaceAll("${symbol}", msgMetaData.symbol)
        .replaceAll("${abbrev}", msgMetaData.abbrev)
        .replaceAll("${sequence}", msgMetaData.sequenceNumber + 1)
        .replaceAll("${sequenceNumber}", msgMetaData.sequenceNumber);
    if (msgMetaData.menuInfoString != null) {
        menuItem = menuItem
            .replaceAll("${menuInfoString}", msgMetaData.menuInfoString);
    } else {
        menuItem = menuItem
            .replaceAll("${menuInfoString}", " ");
    }
    if (msgMetaData.additionalInformation != null &&
        msgMetaData.additionalInformation.length > 0) {
        menuItem = menuItem
            .replaceAll("${additionalInformation}",
                msgMetaData.additionalInformation[0]);
    } else {
        menuItem = menuItem
            .replaceAll("${additionalInformation}", " ");
    }
    if (msgMetaData.timestamp != null) {
        menuItem = menuItem
            .replace("${timestamp}",
                formatTimeStamp(msgMetaData.timestamp));
    } else {
        menuItem = menuItem
            .replace("${timestamp}", " ");
    }
    document.getElementById("sidebar-menu")
        .appendChild(htmlToElement(menuItem));
}

function formatTimeStamp(timestampIsoString) {
    const containsZoneId = timestampIsoString.includes('[');
    let stringToConvert = timestampIsoString;
    if (containsZoneId) {
        stringToConvert = timestampIsoString.substring(0,
            timestampIsoString.indexOf('['));
    }
    return dayjs(stringToConvert).format("HH:mm:ss.SSS");
}


function htmlToElement(html) {
    const template = document.createElement('template');
    html = html.trim(); // Never return a text node of whitespace as the result
    template.innerHTML = html;
    return template.content.firstChild;
}


// noinspection JSUnusedLocalSymbols the sequence number is only used in the tigerProxy context
function scrollToMessage(uuid, sequenceNumber) {
    scrollMessageIntoView(uuid);
}

function scrollMessageIntoView(uuid) {
    let elements = document.getElementsByName(uuid);
    if (elements.length > 0) {
        elements[0].scrollIntoView(true);
    }

}

function getAll(selector, baseEl) {
    if (!baseEl) {
        baseEl = document;
    }
    return Array.prototype.slice.call(baseEl.querySelectorAll(selector), 0);
}

document.addEventListener('DOMContentLoaded', function () {
    // Modals
    const $modalButtons = getAll('.modal-button');
    const $copyButtons = getAll('.copyToClipboard-button');

    if ($modalButtons.length > 0) {
        $modalButtons.forEach(function ($el) {
            $el.addEventListener('click', function (e) {
                e.preventDefault();
                return false;
            });
        });
    }

    if ($copyButtons.length > 0) {
        $copyButtons.forEach(function ($el) {
            $el.addEventListener('click', function (e) {
                let target = $el.dataset.target;
                let $target = document.getElementById(target);
                navigator.clipboard.writeText($target.textContent);
                e.preventDefault();
                return false;
            });
        });
    }
});

function toggleCollapsableIcon(target) {
    const classList = target.classList;
    const flag = classList.contains("fa-toggle-on");
    classList.toggle("fa-toggle-on", !flag);
    classList.toggle("fa-toggle-off", flag);
}

document.addEventListener('DOMContentLoaded', function () {
    let msgCards = document.getElementsByClassName('msg-card');
    for (let i = 0; i < msgCards.length; i++) {
        msgCards[i].children[0].children[0].children[0].children[1].addEventListener(
            'click', e => {
                e.currentTarget
                    .parentElement.parentElement.parentElement.parentElement
                    .childNodes[1].classList.toggle('d-none');
                toggleCollapsableIcon(e.currentTarget);
                e.preventDefault();
                return false;
            });

        document.querySelectorAll('pre.json').forEach(el => {
            if (el.getAttribute("data-hljs-highlighted") !== "true") {
                hljs.highlightElement(el);
                el.setAttribute("data-hljs-highlighted", "true");
            }
        });
    }

    let notification = document.getElementsByClassName('card notification');
    for (let i = 0; i < notification.length; i++) {
        notification[i].children[0].children[0].children[0].children[0].addEventListener(
            'click', e => {
                e.currentTarget
                    .parentElement.parentElement.parentElement.parentElement
                    .childNodes[1].classList.toggle('d-none');
                toggleCollapsableIcon(e.currentTarget);
                e.preventDefault();
                return false;
            });
    }
});
