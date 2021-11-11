// =============================================================================
//
// initialization.js
//
// =============================================================================
let bs5Utils;

$(document).ready(function () {

  bs5Utils = new Bs5Utils();
  Bs5Utils.defaults.toasts.position = 'top-center';
  //Bs5Utils.defaults.toasts.container = 'toast-container';
  Bs5Utils.defaults.toasts.stacking = true;

  // sidebar

  $.contextMenu({
    selector: '.context-menu-one',
    trigger: 'left',
    callback: function (key, options) {
      const sidebarItem = $(this).parents('.sidebar-item');
      const serverIndex = sidebarItem.attr("id").substr("sidebar_".length);

      switch (key) {
        case "start":
        case "restart":
        case "stop":
          bs5Utils.Snack.show('danger',
              'TODO eature ' + key + ' NOT implemented so far!',
              delay = 5000, dismissible = true);
          break;
        case "delete":
          sidebarItem.remove();
          $("#content_" + serverIndex).remove();
          bs5Utils.Snack.show('warning',
              'Server "' + serverIndex + '" removed!',
              delay = 5000, dismissible = true);
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

  $("#sortable").sortable({
    start: function (e, ui) {
      $(this).attr('data-previndex', ui.item.index());
    },
    handle: 'i',
    update: function (e, ui) {
      var newIndex = ui.item.index();
      var oldIndex = $(this).attr('data-previndex');
      $(this).removeAttr('data-previndex');

      var serverId = $(ui.item).attr("id").substr("sidebar_".length);

      if (newIndex > oldIndex) {
        $("#content_" + serverId).insertAfter(
            $(".server-content").children()[newIndex]);
      } else {
        $("#content_" + serverId).insertBefore(
            $(".server-content").children()[newIndex]);
      }
      $("#content_" + serverId)[0].scrollIntoView();
    }
  });

  // top menu

  $('.btn-open-testenv').click(function () {
    if (unsavedModifications) {
      confirmNoDefault('Unsaved Modifications',
          'Do you really want to discard current changes?',
          function () {
            $('#file').click()
          });
    } else {
      $('#file').click();
    }
  });

  $('.btn-new-testenv').click(function () {
    if (unsavedModifications) {
      confirmNoDefault('Unsaved Modifications',
          'Do you really want to discard current changes?',
          function () {
            populateServersFromYaml({});
            notifyChangesToTestenvData(false);
          });
    } else {
      populateServersFromYaml({});
      notifyChangesToTestenvData(false);
    }
  });

  $("#file").on("change", openYamlFile);

  // show welcome
  showWelcomeCard();

});
