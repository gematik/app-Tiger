// =============================================================================
//
// initialization.js
//
// =============================================================================
let bs5Utils;

const bs5UtilsDismissible = true;
const bs5UtilsDelay5Sec = 5000;

$(document).ready(function () {
  Bs5Utils.defaults.toasts.position = 'bottom-right';
  Bs5Utils.defaults.toasts.container = 'toast-container';
  Bs5Utils.defaults.toasts.stacking = true;
  /** @namespace Bs5Utils.defaults */
  bs5Utils = new Bs5Utils();

  loadTemplatesFromServer();

  // sidebar

  // noinspection JSUnusedGlobalSymbols
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

  // noinspection JSUnusedGlobalSymbols
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

  $('.btn-open-testenv').click(handleOpenTestEnvironmentClick);
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

  // global key shortcuts
  $(document).keydown(function (ev) {
    console.log(ev.keyCode);
    if ((ev.metaKey || ev.ctrlKey) && ev.shiftKey) {
      switch (ev.keyCode) {
        case 65: // Ctrl + A
          openAddServerModal();
          ev.preventDefault();
          return false;
        case 79: // Ctrl + O
          handleOpenTestEnvironmentClick();
          ev.preventDefault();
          return false;
      }
    }
    return true;
  });

  showWelcomeCard();
});
