$(function () {
    $.contextMenu({
        selector: '.context-menu-one',
        trigger: 'left',
        callback: function (key, options) {
            var m = "clicked: " + key;
            window.console && console.log(m) || alert(m);
        },
        items: {
            "start": {name: "Start", icon: "fas fa-play"},
            "restart": {name: "Restart", icon: "fas fa-undo"},
            "stop": {name: "Stop", icon: "fas fa-stop"},
            "delete": {name: "Delete", icon: "fas fa-trash-alt"},
            "logs": {name: "Logs", icon: "fas fa-terminal"},
        }
    });
});

$(function () {
    $("#sortable").sortable({
        start: function (e, ui) {
            $(this).attr('data-previndex', ui.item.index());
        },
        update: function (e, ui) {
            var newIndex = ui.item.index();
            var oldIndex = $(this).attr('data-previndex');
            $(this).removeAttr('data-previndex');

            var serverId = $(ui.item).attr("id").substr("sidebar_".length);

            if (newIndex > oldIndex) {
                $("#content_" + serverId).insertAfter($(".server-content").children()[newIndex]);
            } else {
                $("#content_" + serverId).insertBefore($(".server-content").children()[newIndex]);
            }
        }
    });
});