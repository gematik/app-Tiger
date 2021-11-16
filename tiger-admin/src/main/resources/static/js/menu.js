let currEnvironment;
let currTemplates;
let currFolder = '.';
let fsSeparator = '';
let currFile = '';
let unsavedModifications = false;

// =============================================================================
//
// menu.js
//
//
// =============================================================================
function openYamlFile(path, separator, cfgfile) {
  $.ajax({
    url: "/openYamlFile",
    contentType: "application/json",
    data: { cfgfile: path + separator + cfgfile },
    type: "GET",
    dataType: 'json',
    success: function (res) {
      currEnvironment = res;
      populateServersFromYaml(res);
      setYamlFileName(cfgfile, path);
      $('.btn-save-as-testenv').tgrEnabled(true);
      $('.btn-save-testenv').tgrEnabled(true);
    },
    error: function (xhr) {
      showError(
          'We are sorry, but we were unable '
          + 'to load your configuration file \'' + cfgfile + '\'!',
          xhr.responseJSON);
    }
  });
}

function setYamlFileName(cfgfile, path) {
  $('.cfg-file-label').text(cfgfile);
  document.title = "Tiger Admin - " + path + fsSeparator + cfgfile;
  currFile = cfgfile;
  currFolder = path;
}

function saveYamlFile() {
  // TODO get values from formulars into json struct and send to server together with file/path info
  showError("Unable to save file - NOT implemented!");
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
    const formular = $('#content_server_' + serverKey);

    formular.initFormular(serverKey,
        testEnvYaml[serverKey]);

    $('*[name]').change(function () {
      notifyChangesToTestenvData(true);
    });

    // update proxied select field in all formulars
    updateServerLists(Object.keys(testEnvYaml));

    $('#sidebar_server_' + serverKey + ' .server-label').click(function () {
          formular.find('h1')[0].scrollIntoView(true);
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
      confirmNoDefault('Unsaved Modifications',
          'Do you really want to discard current changes?',
          function () {
            openFileOpenDialog(openYamlFile);
          });
    } else {
      openFileOpenDialog(openYamlFile);
    }
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
  errorCauses.causes.forEach(function (cause) {
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
      {text: 'Close', class: 'btn btn-sm btn-primary', type: 'dismiss'}
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

function openFileOpenDialog(okfunc) {
  const filedlg = $('#file-navigation-modal');
  filedlg.find('.modal-title').text("Open file ...");
  filedlg.find(".btn-filenav-ok").hide();
  filedlg.find('.file-navigation-save').hide();
  filedlg.find('.btn-filenav-cancel').click(function() {
    filedlg.modal('hide');
  });
  filedlg.modal('show');
  navigateIntoFolder(currFolder, okfunc, true, 'open');
}

function openFileSaveAsDialog(okfunc) {
  const filedlg = $('#file-navigation-modal');
  filedlg.find('.modal-title').text("Save file as ...");
  filedlg.find(".btn-filenav-ok").text("Save as");
  filedlg.find(".btn-filenav-ok").show();
  filedlg.find('.file-navigation-save').show();
  filedlg.find('.file-navigation-save input').val(currFile);
  filedlg.find('.btn-filenav-cancel').click(function() {
    filedlg.modal('hide');
  });
  filedlg.modal('show');
  navigateIntoFolder(currFolder, okfunc, true, 'save');
  filedlg.find('.btn-filenav-ok').click(function() {
    // get cfgfile from input field together with currentFolder and separator
    console.log("Saving to file '" + currFolder + fsSeparator + filedlg.find('.file-navigation-save input').val());
    notifyChangesToTestenvData(false);
    setYamlFileName(filedlg.find('.file-navigation-save input').val(), currFolder);
    filedlg.modal('hide');
  });
}


function navigateIntoFolder(folder, okfunc, addroots, mode) {
  const filedlg = $('#file-navigation-modal');
  $.ajax({
    url: "/navigator/folder",
    contentType: "application/json",
    data: { current: folder },
    type: "GET",
    dataType: 'json',
    success: function (res) {
      fsSeparator = res.separator;
      let htmlstr = '';
      res.folders.forEach(function (folder) {
        htmlstr += '<div class="folder text-primary"><i class="far fa-folder icon-right"></i>'
            + folder + '</div>\n';
      });
      res.cfgfiles.forEach(function (cfgfile) {
        htmlstr += '<div class="cfgfile text-success"><i class="far fa-file-alt icon-right"></i>'
            + cfgfile + '</div>\n';
      });
      const filepathInput = filedlg.find('.filepath input');
      filepathInput.val(res.current);
      if (res.current.length < 4) {
        filepathInput.css({ direction: 'ltr'})
      } else {
        filepathInput.css({ direction: 'rtl'})
      }
      currFolder = res.current;
      filedlg.find('.modal-body').html(htmlstr);
      filedlg.find('.folder').click(function () {
        navigateIntoFolder(currFolder + res.separator + $(this).text(), okfunc, false, mode);
      });
      filedlg.find('.cfgfile').click(function () {
        if (mode === 'open') {
          okfunc(currFolder, res.separator, $(this).text());
          filedlg.modal('hide');
        }else {
          $('.file-navigation-save input').val($(this).text());
        }
      });
      if (addroots) {
        if (res.roots.length === 1 && !res.roots[0]) {
          console.log("info no filesystem roots, disabling drive button...");
          $('#dropdown-rootfs').parent().hide();
        } else {
          htmlstr = '';
          res.roots.forEach(function (rootfs) {
            htmlstr += '<li><a class="dropdown-item rootfs-item" href="#">'
                + rootfs + '</a></li>\n';
          });
          const fsListRootsList = $('.fs-list-roots-list')
          fsListRootsList.children().remove();
          fsListRootsList.prepend(htmlstr);
          fsListRootsList.find('li a').on('click', function() {
            navigateIntoFolder($(this).text(), okfunc, false, mode);
          });
        }
      }
    },
    error: function (xhr) {
      showError('Unable to access Folder/File!', xhr.responseJSON);
    }
  });
}
