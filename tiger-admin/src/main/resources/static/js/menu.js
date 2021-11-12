let currEnvironment;

let unsavedModifications = false;

// =============================================================================
//
// menu.js
//
//
// =============================================================================
function openYamlFile() {
  $.ajax({
    url: "/openYamlFile",
    type: "POST",
    data: new FormData($("#openYaml")[0]),
    enctype: 'multipart/form-data',
    processData: false,
    contentType: false,
    cache: false,
    dataType: 'json',
    success: function (res) {
      currEnvironment = res;
      populateServersFromYaml(res);
      $('.cfg-file-label').text(
          $('#file').val().replace(/C:\\fakepath\\/i, ''));
    },
    error: function (xhr) {
      showError('We are sorry, but we were unable to load your configuration file!', xhr.responseJSON);
    }
  });
}

const serverIcons = {
  docker: "fab fa-docker",
  compose: "text-primary fab fa-docker",
  tigerProxy: "fas fa-project-diagram",
  externalJar: "fas fa-rocket",
  externalUrl: "fas fa-external-link-alt"
}

function populateServersFromYaml(testEnvYaml) {
  const serverContent = $('.server-content');
  $('.sidebar').children().remove();
  serverContent.children().remove();

  for (serverKey in testEnvYaml) {
    // create sidebar entry
    $('.container.sidebar.server-container').append(
        '<div id="sidebar_server_' + serverKey
        + '" class="box sidebar-item row">'
        + '<div class="col-1"><i class="fas fa-grip-lines draghandle"></i></div>'
        + '<div class="col-10"><i class="server-icon '
        + serverIcons[testEnvYaml[serverKey].type]
        + '"></i><span class="server-label">' + serverKey + '</span></div>'
        + '<div class="col-1 context-menu-one btn btn-neutral"> <i class="fas fa-ellipsis-v"></i> </div> </div>');

    // create server content form tag
    serverContent.append('<form id="content_server_' + serverKey
        + '" class="col server-formular"></form>')
    // init formular with data
    $('#content_server_' + serverKey).initFormular(serverKey,
        testEnvYaml[serverKey]);

    $('*[name]').change(function () {
      notifyChangesToTestenvData(true);
    });

    // update proxied select field in all formulars
    updateServerLists(Object.keys(testEnvYaml));

    $('#sidebar_server_' + serverKey + ' .server-label').click(function (ev) {
          const formid = $(this).parents('.sidebar-item').attr('id').replace(
              'sidebar_server_', 'content_server_');
          $('#' + formid + ' h1')[0].scrollIntoView(true);
        }
    );
  }
  if (!serverContent.children().length) {
    showWelcomeCard();
  }
  notifyChangesToTestenvData(false);
}

function notifyChangesToTestenvData(flag) {
  unsavedModifications = flag;
  if (flag) {
    $('.btn.btn-save-testenv').fadeIn();
    $('.btn-save-testenv').removeClass('disabled');
  } else {
    $('.btn.btn-save-testenv').fadeOut();
    $('.btn-save-testenv').addClass('disabled');
  }
}

function showWelcomeCard() {
  $('.server-content').html($('#template-welcome-card').html());
  $('.server-content .btn-open-testenv').click(function () {
    if (unsavedModifications) {
      danger( 'TODO ASk whether to discard changes');
    }
    $("#file").click();
  });
  // TODO add addserver cb
}

function updateServerLists(serverList, replacedSelection, optNewSelection) {
  $('form.server-formular').updateServerList(serverList, replacedSelection,
      optNewSelection);
}

function confirmNoDefault(title, content, yesfunc) {
  bs5Utils.Modal.show({
    title: title,
    content:
        '<div>' + content + '</div>',
    buttons: [
      {
        text: 'Yes', class: 'btn btn-sm btn-danger',
        handler: (ev) => {
          $(ev.target).parents('.modal.show').modal('hide')
          yesfunc(ev);
        }
      },
      {text: 'No', class: 'btn btn-sm btn-primary', type: 'dismiss'},
    ],
    centered: true, dismissible: true, backdrop: 'static', keyboard: true,
    focus: false, type: 'danger'
  });
}

function showError(errMessage, errorCauses) {
  let details = '<b>' + errorCauses.mainCause + '</b><br/>';
  errorCauses.causes.forEach(function(cause) {
    details += '\n<br/>Caused by: ' + cause;
  });
  bs5Utils.Modal.show({
    title: `Error`,
    content:
        '<div>' + errMessage + '</div>'
        + '<hr class="dropdown-divider" style="display: none;">'
        + '<small><div class="modal-error detailedMessage hidden"></div></small>',
    buttons: [
      {
        text: 'Advanced details',
        class: 'btn btn-sm btn-info',
        handler: (ev) => {
          const detMsg = $(ev.target).parents('.modal-dialog').find(
              '.detailedMessage');
          const divider = $(ev.target).parents('.modal-dialog').find(
              '.dropdown-divider');
          detMsg.html(details);
          const btn = $('.btn.btn-sm.btn-info');
          if (btn.text() === 'Hide Details') {
            divider.hide();
            detMsg.hide();
            btn.text('Advanced details');
          } else {
            divider.show();
            detMsg.show();
            btn.text('Hide Details');
          }
        }
      },
      { text: 'Close', class: 'btn btn-sm btn-primary', type: 'dismiss' }
    ],
    centered: true, dismissible: true, backdrop: 'static', keyboard: true,
    focus: false, type: 'danger'
  });
}


function warn(text, delay, dismissible) {
  snack(text, 'warning', delay, dismissible);
}

function danger(text, delay, dismissible) {
  snack(text, 'danger', delay, dismissible);
}

function snack(text, type, delay, dismissible) {
  if (!type) {
    type = 'warning'
  }
  if (!delay) {
    delay = bs5UtilsDelay5Sec;
  }
  if (!dismissible && dismissible !== false) {
    dismissible = bs5UtilsDismissible;
  }
  bs5Utils.Snack.show(type, text, delay, dismissible);
}
