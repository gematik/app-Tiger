"strict";

let lastUuid = "";
let rootEl;

let updateTimeout = 0;
let updateHandler = null;

let fieldRouteTo;
let fieldRouteFrom;
let btnAddRoute;

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
  fieldRouteFrom = document.getElementById("addNewRouteFromField");
  fieldRouteTo = document.getElementById("addNewRouteToField");
  btnAddRoute = document.getElementById("addNewRouteBtn");


  enableModals();
  document.addEventListener('keydown', event => {
    var e = event || window.event;
    if (e.keyCode === 27) {
      closeModals();
    }
  });

  enableCardToggles();
  enableCollapseExpandAll();

  document.getElementById("updateBtn").addEventListener('click',pollMessages);

  document.getElementById("routeModalBtn").addEventListener('click',
      e => {
        document.getElementById("routeModalBtn").disabled = true;
        getRoutes();
        document.getElementById("addNewRouteBtn").disabled = true;
      })

  getAll("input.updates").forEach(function (el) {
    el.addEventListener("click", () => {
      updateTimeout = el.value;
      if (updateHandler) {
        clearInterval(updateHandler);
      }
      if (updateTimeout != 0) {
        updateHandler = setInterval(pollMessages, updateTimeout * 1000);
      }
    })
  });


  btnAddRoute.addEventListener("click", addRoute);
  fieldRouteFrom.addEventListener("keydown", updateAddRouteBtnState);
  fieldRouteTo.addEventListener("keydown", updateAddRouteBtnState);
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
  let $modalButtons = getAll('.modal-button');
  let $modalCloses = getAll(
      '.modal-background, .modal-close, .message-header .delete, .modal-card-foot .button');

  if ($modalButtons.length > 0) {
    $modalButtons.forEach(function ($el) {
      $el.addEventListener('click', showModalsCB);
    });
  }

  if ($modalCloses.length > 0) {
    $modalCloses.forEach(function ($el) {
      $el.addEventListener('click', closeModals);
    });
  }
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
  document.getElementById("routeModalBtn").disabled = false;
  document.getElementById("routeModalLed").classList.remove("ledactive");
  document.getElementById("routeModalLed").classList.remove("lederror");
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
  xhttp.open("GET", "/webui/getMsgAfter?lastMsgUuid=" + lastUuid, true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);
        updateMessageList(response);
        document.getElementById("updateLed").classList.remove("ledactive");
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
        document.getElementById("updateLed").classList.remove("ledactive");
        document.getElementById("updateLed").classList.add("lederror");
      }
    }
  }
  xhttp.send();
}

function updateMessageList(json) {
  if (json.metaMsgList.length === 0) {
    return;
  }
  let i = 0;
  const listDiv = getAll('.column')[2];
  while (i < json.htmlMsgList.length) {
    const req = json.metaMsgList[i];
    if (req.path) {
      const reqEl = htmlToElement(json.htmlMsgList[i]);
      let span = getAll(".msg-sequence", reqEl)[0];
      span.classList.add("tag", "is-info", "is-light", "mr-3", "is-size-3");
      span.textContent = req.sequenceNumber + 1;
      listDiv.appendChild(reqEl);
      const resEl = htmlToElement(json.htmlMsgList[i + 1]);
      const res = json.metaMsgList[i + 1];
      span = getAll(".msg-sequence", resEl)[0];
      span.classList.add("tag", "is-info", "is-light", "mr-3", "is-size-3");
      span.textContent = res.sequenceNumber + 1;
      listDiv.appendChild(resEl);

      const menuReq = menuReqHtmlTemplate
      .replace("${uuid}", req.uuid)
      .replace("${sequence}", req.sequenceNumber + 1)
      .replace("${methodNUrl}", req.method + "\n" + req.path);
      document.getElementById("sidebar-menu").appendChild(
          htmlToElement(menuReq));

      const menuRes = menuResHtmlTemplate
      .replace("${uuid}", res.uuid)
      .replace("${sequence}", res.sequenceNumber + 1)
      document.getElementById("sidebar-menu").appendChild(
          htmlToElement(menuRes));
    }
    i = i + 2;
  }
  lastUuid = json.metaMsgList[json.metaMsgList.length - 1].uuid;

  enableCardToggles();
  enableModals();
}

function getRoutes() {
  document.getElementById("routeModalLed").classList.add("ledactive");
  document.getElementById("routeModalLed").classList.remove("lederror");
  getAll(".routeListDiv")[0].innerHTML = "<p align=\"center\" class=\"mt-5 mb-5\"><i class=\"fas fa-spinner\"></i> Loading...</p>";
  const xhttp = new XMLHttpRequest();
  xhttp.open("GET", "/route", true);
  xhttp.onreadystatechange = function () {
    if (this.readyState === 4) {
      if (this.status === 200) {
        const response = JSON.parse(this.responseText);
        updateRouteList(response);
      } else {
        console.log("ERROR " + this.status + " " + this.responseText);
        getAll(".routeListDiv")[0].innerHTML = "ERROR " + this.status + " " + this.responseText;
            document.getElementById("routeModalLed").classList.remove("ledactive");
        document.getElementById("routeModalLed").classList.add("lederror");
      }
    }
  }
  xhttp.send();
}

function updateRouteList(json) {
  if (json.length === 0) {
    (getAll(".routeListDiv")[0]).innerHTML="<p class='mt-5 mb-5'>No Routes configured</p>"
    return;
  }
  let html = "";
  json.forEach(function(route) {
    html += "<div class='box routeentry columns'>"
        + "<div class='column is-one-fifth'>"
        + "<button id='route-" + route.id + "' class='button delete-route is-fullwidth is-danger'>"
        + "<i class=\"far fa-trash-alt\"></i>"
        + "</button></div>"
        + "<div class='column is-four-fifths'>&rarr; " + route.from + "<br/>&larr; " + route.to + "</div></div>";
  });
  getAll(".routeListDiv")[0].innerHTML = html;
  getAll("button.delete-route").forEach(function(el) {
    el.addEventListener("click", function(e) {
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
  xhttp.send("{\"from\":\"" + fieldRouteFrom.value +"\",\n"
      +"\"to\":\"" + fieldRouteTo.value + "\"}");
}
