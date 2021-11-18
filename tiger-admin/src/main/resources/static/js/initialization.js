// =============================================================================
//
// initialization.js
//
// =============================================================================
let bs5Utils;

const bs5UtilsDismissible = true;
const bs5UtilsDelay5Sec = 5000;

$(document).ready(function () {

  bs5Utils = new Bs5Utils();
  Bs5Utils.defaults.toasts.position = 'top-center';
  Bs5Utils.defaults.toasts.stacking = true;

  // sidebar

  $.contextMenu({
    selector: '.context-menu-one',
    trigger: 'left',
    callback: function (key/*, options*/) {
      const sidebarItem = $(this).parents('.sidebar-item');
      const serverIndex = sidebarItem.attr("id").substr("sidebar_".length);

      switch (key) {
        case "start":
        case "restart":
        case "stop":
          danger('TODO feature ' + key + ' NOT implemented so far!');
          break;
        case "delete":
          sidebarItem.remove();
          $("#content_" + serverIndex).remove();
          warn('Server "' + serverIndex + '" removed!');
          break;
          /*
          case "logs":
            break;
           */
      }
    },
    items: {
      "start": {name: "Start", icon: "fas text-success fa-play"},
      "restart": {name: "Restart", icon: "fas text-success fa-undo"},
      "stop": {name: "Stop", icon: "fas text-warning fa-stop"},
      "delete": {name: "Delete", icon: "fas text-danger fa-trash-alt"},
      /* TODO logging is way to go
          "logs": {name: "Logs", icon: "fas fa-terminal"},*/
    }
  });

  $(".sidebar.server-container").sortable({
    start: function (e, ui) {
      $(this).attr('data-previndex', ui.item.index());
    },
    handle: 'i',
    update: function (e, ui) {
      const newIndex = ui.item.index();
      const oldIndex = $(this).attr('data-previndex');
      const serverId = $(ui.item).attr("id").substr("sidebar_".length);
      const content = $("#content_" + serverId);

      $(this).removeAttr('data-previndex');
      // +1 as we have added the local tiger proxy as server-content block!
      if (newIndex > oldIndex) {
        content.insertAfter($(".server-content").children()[newIndex + 1]);
      } else {
        content.insertBefore($(".server-content").children()[newIndex + 1]);
      }
      window.scrollTo(0, content.position().top);
    }
  });

  // top menu

  $('.btn-open-testenv').click(function () {
    confirmNoDefault(unsavedModifications, 'Unsaved Modifications',
        'Do you really want to discard current changes?',
        function () {
          openFileOpenDialog(openYamlFile);
        });
  });

  $('.btn-save-testenv').click(function () {
    if (!currFile) {
      openFileSaveAsDialog(saveYamlFile)
    } else {
      snack("Save NOT implemented so far", "danger");
    }
  });

  $('.btn-save-as-testenv').click(function () {
    openFileSaveAsDialog(saveYamlFile)
  });

  $('.btn-new-testenv').click(function () {
    confirmNoDefault(unsavedModifications, 'Unsaved Modifications',
        'Do you really want to discard current changes?',
        function () {
          currEnvironment = {};
          setYamlFileName(null);
          populateServersFromYaml(currEnvironment);
          notifyChangesToTestenvData(false);
        });
    $('.testenv-sidebar-header').fadeOut();
  });

  $('.btn-add-server').click(function () {
    openAddServerModal();
  });

  // show welcome
  showWelcomeCard();

  $.ajax({
    url: "/getTemplates",
    type: "GET",
    dataType: 'json',
    success: function (res) {
      currTemplates = res;
      const addServerModal = $('#add-server-modal');
      addServerModal.find('.btn-add-server-ok').click(addSelectedServer);
      addServerModal.find('.btn-add-server-cancel').click(function () {
        addServerModal.modal('hide');
      });

      const list = addServerModal.find('.list-server-types');
      list.children().remove();
      let html = '<li class="list-group-item p-2 bg-success text-center text-white">Basic Types</li>';
      for (icon in serverIcons) {
        if (icon !== 'localProxy') {
          html += '<li class="list-group-item p-2 text-success">'
              + '<i class="server-icon ' + serverIcons[icon] + '"></i>'
              + icon + '</li>';
        }
      }
      html += '<li class="list-group-item p-2 bg-secondary text-center text-white">Templates</li>';
      currTemplates.templates.forEach(function (template) {
        html += '<li class="list-group-item p-2 text-secondary">'
            + '<i class="server-icon ' + serverIcons[template.type] + '"></i>'
            + template.templateName + '</li>';

      })
      list.prepend($(html));
      list.find('.list-group-item:not(.text-white)').click(function () {
        list.find('.active').removeClass("active");
        $('#add-server-modal .info-block').html(
            $('#add-server-modal .info-' + $(this).text()).html());
        $(this).addClass("active");
        addServerModal.find('.btn-add-server-ok').tgrEnabled(true);
      });
      list.find('.list-group-item:not(.text-white)').dblclick(function (ev) {
        $(this).click();
        addServerModal.find('.btn-add-server-ok').click();
        ev.preventDefault();
        return false;
      });

      snack('Templates loaded', 'success', 1000);
    },
    error: function (xhr) {
      $('body *').tgrEnabled(false);
      showError('We are sorry, but we were unable to load the server templates!'
          + '<p>The admin UI is NOT usable!</p><p><b>Please reload the page</b></p>',
          xhr.responseJSON);
    }
  });
});
