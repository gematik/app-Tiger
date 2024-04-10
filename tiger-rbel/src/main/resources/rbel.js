/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */


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

let msgIndex = 1;

function createMenuEntry(msgMetaData) {
    let menuItem;
    if (msgMetaData.isRequest) {
        menuItem = menuHtmlTemplateRequest;
    } else {
        menuItem = menuHtmlTemplateResponse;
    }
    menuItem = menuItem
        .replace("${uuid}", msgMetaData.uuid)
        .replace("${sequence}", msgMetaData.sequenceNumber + 1)
        .replace("${sequenceNumber}", msgIndex++);
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
                dayjs(msgMetaData.timestamp).format("HH:mm:ss.SSS"))
    } else {
        menuItem = menuItem
            .replace("${timestamp}", " ");
    }
    document.getElementById("sidebar-menu")
        .appendChild(htmlToElement(menuItem));
}

function htmlToElement(html) {
    const template = document.createElement('template');
    html = html.trim(); // Never return a text node of whitespace as the result
    template.innerHTML = html;
    return template.content.firstChild;
}

function scrollToMessage(uuid, sequenceNumber) {
    scrollMessageIntoView(uuid);
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

document.addEventListener('DOMContentLoaded', function () {
    // Modals
    var $modalButtons = getAll('.modal-button');
    var $copyButtons = getAll('.copyToClipboard-button');

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

    // Functions
    function getAll(selector) {
        return Array.prototype.slice.call(document.querySelectorAll(selector), 0);
    }
});

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

document.addEventListener('DOMContentLoaded', function () {
    let msgCards = document.getElementsByClassName('msg-card');
    for (let i = 0; i < msgCards.length; i++) {
        msgCards[i].children[0].children[0].children[0].children[1].addEventListener('click', e => {
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
        notification[i].children[0].children[0].children[0].children[0].addEventListener('click', e => {
            e.currentTarget
                .parentElement.parentElement.parentElement.parentElement
                .childNodes[1].classList.toggle('d-none');
            toggleCollapsableIcon(e.currentTarget);
            e.preventDefault();
            return false;
        });
    }


    document.getElementById("collapse-all").addEventListener('click', e => {
        for (let i = 0; i < msgCards.length; i++) {
            msgCards[i].childNodes[1].classList.toggle('d-none', true);
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
            msgCards[i].childNodes[1].classList.toggle('d-none', false);
            const classList2 = msgCards[i].children[0].children[0].children[0].children[1].classList;
            if (classList2.contains("fa-toggle-off")) {
                classList2.remove("fa-toggle-off");
                classList2.add("fa-toggle-on");
            }
        }
        e.preventDefault();
        return false;
    });
});
