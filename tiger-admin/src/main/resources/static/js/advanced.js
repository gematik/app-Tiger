/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

"use strict"

function handleSectionAdvancedButtonClick(ev) {
  ev.preventDefault();
  if ($(this).parent().find('.collapse-icon').isCollapsed()) {
    return false;
  }
  $(this).parents('fieldset').find('.advanced:not(.hidden)').fadeToggle(600);
  $(this).toggleClass('active');
  return false;
}

function handleGlobalAdvancedButtonClick() {
  const formular = $(this).parents('.server-formular');
  if ($(this).hasClass('active')) {
    formular.find('.btn-advanced').removeClass('active');
    formular.find('.advanced').fadeOut(600);
    $(this).removeClass('active');
  } else {
    formular.find('.btn-advanced').addClass('active');
    formular.find('.advanced:not(.hidden)').fadeIn(600);
    $(this).addClass('active');
    $.each(formular.find('fieldset'), function () {
      if ($(this).find('i.collapse-icon').isCollapsed() &&
          $(this).find('.advanced').length) {
        $(this).find('legend').toggleLegendCollapse();
      }
    })
  }
}
