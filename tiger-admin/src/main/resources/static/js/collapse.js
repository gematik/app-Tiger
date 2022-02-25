/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

"use strict"

function handleNodeCollapse() {
  $(this).parent().siblings(':not(.hidden)').toggle();
  $(this).parent().parent().siblings(':not(.hidden)').toggle();
  $(this).parent().parent().parent().siblings(':not(.hidden)').toggle();
  $(this).toggleCollapseIcon();
}

// for multiple i
$.fn.toggleCollapseIcon = function (state) {
  checkTag('toggleCollapseIcon', this, 'I');
  return this.each(function () {
    const findState = /(.*)(-down|-right)(.*)/;
    let clz = $(this).attr('class');
    if (typeof state === 'undefined') {
      state = !$(this).isCollapsed();
    }
    clz = clz.replace(findState, (m, g1, g2, g3) => g1 + (state ? '-right' : '-down') + g3);
    $(this).attr('class', clz);
  });
}

// for single or none i
$.fn.isCollapsed = function () {
  if (!this.length) {
    return false;
  }
  checkSingle('isCollapsed', this);
  checkTag('isCollapsed', this, 'I');
  const findState = /(.*)(-down|-right)(.*)/;
  const clz = this.attr('class');
  const result = clz.match(findState);
  return result[2] === '-right';
}

// for multiple legends
$.fn.toggleLegendCollapse = function () {
  checkTag('toggleLegendCollapse', this, 'LEGEND');
  return this.each(function () {
    const collIcon = $(this).find('i.collapse-icon');
    const btnAdvanced = $(this).parents('fieldset').find('.btn-advanced');
    if (collIcon.isCollapsed()) {
      $(this).siblings(':not(.advanced):not(.hidden)').fadeIn();
      if (!$(this).parents('fieldset')
      .find('.btn-advanced').hasClass("active")) {
        $(this).parent().find('.advanced').hide();
      }
    } else {
      btnAdvanced.removeClass('active');
      $(this).siblings(':not(.hidden)').fadeOut();
      $(this).parent().find('.advanced').fadeOut();
    }
    collIcon.toggleCollapseIcon();
    $(this).setSummary();
  });
}
