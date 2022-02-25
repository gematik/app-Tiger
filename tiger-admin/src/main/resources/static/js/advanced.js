/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
