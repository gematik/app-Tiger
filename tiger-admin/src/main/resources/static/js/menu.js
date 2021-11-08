let currEnvironment;

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
    },
    error: function () {
      alert("There was an error")
    }
  });
}

function populateServersFromYaml(testEnvYaml) {
  const serverContent = $('.server-content');
  $('.sidebar').children().remove();
  serverContent.children().remove();

  for (serverKey in testEnvYaml) {
    // create sidebar entry
    $('.container.sidebar.server-container').append(
        '<div id="sidebar_server_' + serverKey + '" class="box text-center">'
        + '<span class="server_label">' + serverKey + '</span>'
        + '<span class="context-menu-one btn btn-neutral"> <i class="fas fa-ellipsis-v"></i> </span> </div>');

    // create server content form tag
    serverContent.append('<form id="content_server_' + serverKey
        + '" class="col server-formular"></form>')
    // init formular with data
    $('#content_server_' + serverKey).initFormular(serverKey,
        testEnvYaml[serverKey]);

    // update proxied select field in all formulars
    updateServerLists(Object.keys(testEnvYaml));
  }
}

updateServerLists = function (serverList, replacedSelection, optNewSelection) {
  $('form.server-formular').updateServerList(serverList, replacedSelection, optNewSelection);
}

