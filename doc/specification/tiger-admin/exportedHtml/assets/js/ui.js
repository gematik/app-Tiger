    function getCollapseIcon($divs) {
        return ($divs.is(':visible')) ? '<i class="text-success bi bi-chevron-down"></i>' : '<i class="text-secondary bi bi-chevron-right"></i>'
    }
    function isCollapsed($divs) {
        return !$divs.is(':visible');
    }
    function getValue(fieldset, name) {
        var elem = fieldset.find("*[name='" + name + "']");
        var str;
        if (elem.prop("tagName").toLowerCase() == 'ul') {
            str = getListValue(fieldset, name);    
        } else {
            str = fieldset.find("*[name='" + name + "']").val();
        }
        return $('<span>').text(str).html();
    }
    function isChecked(fieldSet, name) {
        return fieldSet.find("*[name='" + name+"']").is(":checked");
    }
    function getListValue(fieldset, name) {
        var lis = fieldset.find("ul[name='" + name + "'] > li");
        var csv = "";
        $.each(lis, function(idx, el) {
            csv += $('<span>').text($(el).text()).html() + ", ";
        });
        return csv.substr(0, csv.length-2);
    }
    function generateSummary(fieldSet) {
        var summaryPattern = fieldSet.attr("summaryPattern");
        const regex = /\$\{(\w+)\}/g        
        var s = summaryPattern.replace(regex, (m, g1) => getValue(fieldSet, g1) || "&nbsp;");
        return '<span class="text summary fs-6">' + s + '</span>';
    }

    $(function () {
        //
        // fieldset collapsible with summary shown
        //
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
                        summarySpan.html(generateSummary(fieldSet));
                        break;
                    case "source":
                        summarySpan.html(generateSummary(fieldSet));
                        break;
                    case "options":
                    case "arguments":
                    case "environment":
                    case "exports":    
                    case "serviceHealthchecks":
                        summarySpan.html('<span class="text summary fs-6">' + getListValue(fieldSet, fsName) + '</span>');
                        break;
                     case "forwardToProxy":
                        if (isChecked(fieldSet, "enableForwardProxy")) {
                            summarySpan.html(generateSummary(fieldSet));
                        } else  {
                            summarySpan.html('<span class="text summary fs-6">DISABLED</span>') ;
                        }
                 }
             } else {
                summarySpan.text("");
             }
        });
        
                //
        // list groups draggable
        //
        $.each($('fieldset .list-group .list-group-item'), function(idx, elem) {
            $(elem).html('<i class="bi draghandle bi-list"></i><span>' + $(elem).html() + "</span>");
        });
        $('fieldset .list-group').sortable({
            handle: 'i',
            update: function() {
                $('.panel', panelList).each(function(index, elem) {
                     var $listItem = $(elem),
                         newIndex = $listItem.index();

                     // Persist the new indices.
                });
            }
        });
        

        // 
        // list group items etiable
        //
        $.fn.selectText = function(){
            var doc = document;
            var element = this[0];
            //console.log(this, element);
            if (doc.body.createTextRange) {
                var range = document.body.createTextRange();
                range.moveToElementText(element);
                range.select();
            } else if (window.getSelection) {
                var selection = window.getSelection();        
                var range = document.createRange();
                range.selectNodeContents(element);
                selection.removeAllRanges();
                selection.addRange(range);
            }
        };
        
        var editableContentReset;
        
        $('fieldset.editableList .list-group-item > span').click(function(ev) {
            $(this).attr("contentEditable", "true");
            $(this).parent().focus();
            $(this).css({border: 0});
            $(this).selectText();
            editableContentReset = $(this).html();
            $(this).focus(); 
            ev.preventDefault();
            return false;
        });
        $('fieldset.editableList .list-group-item > span').keydown(function(ev) {
            if (ev.keyCode === 13) {
                $(this).attr("contentEditable", "false");
                $(this).parent().blur();
                $(this).blur();
                ev.preventDefault();
                return false;
            } else if (ev.keyCode === 27) {
                $(this).html(editableContentReset);
                $(this).attr("contentEditable", "false");
                $(this).parent().blur();
                $(this).blur();
                ev.preventDefault();
                return false;
            }
        });
        $('form').submit(false);
        

    });