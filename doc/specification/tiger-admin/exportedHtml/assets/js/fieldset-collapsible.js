    function getCollapseIcon($divs) {
        return ($divs.is(':visible')) ? '<i class="text-success bi bi-chevron-down"></i>' : '<i class="text-secondary bi bi-chevron-right"></i>'
    }
    function isCollapsed($divs) {
        return !$divs.is(':visible');
    }
    function getValue(fieldset, name) {
        return fieldset.find("*[name='" + name + "']").val();
    }
    function getListValue(fieldset, name) {
        var lis = fieldset.find("ul[name='" + name + "'] > li");
        var csv = "";
        $.each(lis, function(idx, el) {
            csv += $(el).text() + ", ";
        });
        return csv.substr(0, csv.length-2);
    }
    $(function () {
        $.each($('fieldset > legend'), function(idx, elem) {
            var $divs = $(elem).siblings();
            $(elem).append('&nbsp;<span class="collapse-icon">' + getCollapseIcon($divs) + '</span>&nbsp;<span class="fieldset-summary fs-6"></span>');
        });
        $('fieldset > legend').click(function () {
            var $divs = $(this).siblings();
            $divs.toggle();

            $(this).find('span.collapse-icon').html(function () {
                return getCollapseIcon($divs);
            });

            var fieldSet= $(this).parent();
            var fsName = $(this).parent().attr("section");
            var summarySpan = $(this).parent().find("span.fieldset-summary");
            if (isCollapsed($divs)) {

                switch (fsName) {
                    case "node-settings":
                        summarySpan.html(function() {
                            return '<b>' + getValue(fieldSet, "hostname")  + '</b>&nbsp;' + 
                            '<span class="text fs-6">(' + getValue(fieldSet, "type") + ', '+ getValue(fieldSet, "template") +', ' + getValue(fieldSet, "startupTimeoutSec") + ' sec)</span>';
                        })
                        break;
                    case "source":
                        var version = getValue(fieldSet, "version");
                        if (version !== '') {
                            version = " v" + version;
                        }
                        var sources = getListValue(fieldSet, fsName);
                        if (sources === '') {
                            sources = "NOT SET! ";
                        }
                        summarySpan.html(function() {
                            return sources + version;
                        })
                        break;
                     case "options":
                     case "arguments":
                     case "serviceHealthchecks":
                        summarySpan.html(function() {
                            return getListValue(fieldSet, fsName);
                        })
                        break;
                     case "forwardToProxy":
                         summarySpan.html(function() {
                             return '<span class="text fs-6">' + getValue(fieldSet, "type").toLowerCase() + "://" + getValue(fieldSet, "hostname") + ":" + getValue(fieldSet, "port") + '</span>';
                         })
                 }
             } else {
             summarySpan.text("");
             }
        });
        $('fieldset .list-group').sortable({
            update: function() {
                $('.panel', panelList).each(function(index, elem) {
                     var $listItem = $(elem),
                         newIndex = $listItem.index();

                     // Persist the new indices.
                });
            }
        });
    });