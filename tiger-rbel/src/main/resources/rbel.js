/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

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
          .childNodes[1].classList.toggle('is-hidden');
      toggleCollapsableIcon(e.currentTarget);
      e.preventDefault();
      return false;
    });
  }

  let notification = document.getElementsByClassName('card notification');
  for (let i = 0; i < notification.length; i++) {
    notification[i].children[0].children[0].children[0].children[0].addEventListener('click', e => {
      e.currentTarget
          .parentElement.parentElement.parentElement.parentElement
          .childNodes[1].classList.toggle('is-hidden');
      toggleCollapsableIcon(e.currentTarget);
      e.preventDefault();
      return false;
    });
  }


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
});
