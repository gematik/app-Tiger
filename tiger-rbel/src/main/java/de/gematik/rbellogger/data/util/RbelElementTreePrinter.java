/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.data.util;

import static de.gematik.rbellogger.util.RbelAnsiColors.*;
import static de.gematik.rbellogger.util.RbelContextDecorator.forceStringConvert;

import com.google.common.html.HtmlEscapers;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.util.RbelAnsiColors;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelElementTreePrinter {

  private final RbelElement rootElement;
  @Builder.Default private Class<? extends RbelFacet> onlyPrintElementsWithFacet = null;
  @Builder.Default private final int maximumLevels = Integer.MAX_VALUE;
  @Builder.Default private final boolean printContent = true;
  @Builder.Default private final boolean printKeys = false;
  @Builder.Default private final boolean printFacets = true;
  @Builder.Default private final boolean printColors = true;
  @Builder.Default private final boolean printParsingTimes = false;
  @Builder.Default private final boolean htmlEscaping = false;
  @Builder.Default private final boolean addJexlResponseLinkCssClass = false;

  public String execute() {
    final RbelElement position = new RbelElement();
    position.addFacet(
        new RbelFacet() {
          @Override
          public RbelMultiMap<RbelElement> getChildElements() {
            return new RbelMultiMap<RbelElement>().with(findKeyOfRootElement(), rootElement);
          }
        });
    if (onlyPrintElementsWithFacet != null) {
      rootElement.getFacet(onlyPrintElementsWithFacet).ifPresent(position::addFacet);
    }
    final String rawResult =
        executeRecursive(
            position, "", Math.max(maximumLevels, maximumLevels + 1) // avoid overflow problems
            );
    if (htmlEscaping) {
      return performHtmlEscaping(rawResult);
    } else {
      return rawResult;
    }
  }

  private String performHtmlEscaping(String rawResult) {
    return HtmlEscapers.htmlEscaper()
        .escape(rawResult)
        .replace(RbelAnsiColors.RESET.toString(), "</span>")
        .replace(
            RbelAnsiColors.RED_BOLD.toString(),
            "<span class='text-warning "
                + (addJexlResponseLinkCssClass ? "jexlResponseLink' style='cursor: pointer;'" : "'")
                + ">")
        .replace(RbelAnsiColors.CYAN.toString(), "<span class='text-info'>")
        .replace(
            RbelAnsiColors.YELLOW_BRIGHT.toString(),
            "<span class='text-danger has-text-weight-bold'>")
        .replace(RbelAnsiColors.GREEN.toString(), "<span class='text-warning'>")
        .replace(RbelAnsiColors.BLUE.toString(), "<span class='text-success'>")
        .replace("\n", "<br/>");
  }

  private String findKeyOfRootElement() {
    return Optional.ofNullable(rootElement.getParentNode())
        .map(RbelElement::getChildNodesWithKey)
        .stream()
        .flatMap(RbelMultiMap::stream)
        .filter(pair -> pair.getValue() == rootElement)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse("");
  }

  private String executeRecursive(RbelElement position, String padding, int remainingLevels) {
    if (remainingLevels <= 0) {
      return "";
    }
    if (onlyPrintElementsWithFacet != null && !position.hasFacet(onlyPrintElementsWithFacet)) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    for (Iterator<Map.Entry<String, RbelElement>> iterator =
            position.getChildNodesWithKey().iterator();
        iterator.hasNext(); ) {
      Map.Entry<String, RbelElement> childNode = iterator.next();
      String switchString;
      String padString;
      String linkSign = childNode.getValue().hasFacet(RbelRootFacet.class) ? ">" : "─";
      if (iterator.hasNext()) {
        switchString = "├" + linkSign + "─";
        padString = "|  ";
      } else {
        switchString = "└" + linkSign + "─";
        padString = "   ";
      }
      // the tree structure
      result
          .append(colorCommand(YELLOW_BRIGHT))
          .append(padding)
          .append(switchString)
          .append(colorCommand(RESET));
      // name of the node
      result.append(colorCommand(RED_BOLD)).append(childNode.getKey()).append(colorCommand(RESET));
      // print content
      result.append(printContentOf(childNode.getValue()));
      // print facet
      result.append(printFacets(childNode.getValue()));
      result.append("\n");
      if (!childNode.getValue().getChildNodes().isEmpty()) {
        result.append(
            executeRecursive(childNode.getValue(), padding + padString, remainingLevels - 1));
      }
    }
    return result.toString();
  }

  private String printFacets(RbelElement value) {
    if (!printFacets) {
      return "";
    }
    final String facetsString =
        value.getFacets().stream()
            .map(Object::getClass)
            .map(Class::getSimpleName)
            .filter(s -> !"RbelRootFacet".equals(s))
            .filter(s -> !"RbelListFacet".equals(s))
            .filter(s -> !"RbelNestedFacet".equals(s))
            .filter(s -> !"RbelMapFacet".equals(s))
            .collect(Collectors.joining(","));
    if (StringUtils.isEmpty(facetsString)) {
      return "";
    }
    return colorCommand(CYAN) + " (" + facetsString + ")" + colorCommand(RESET);
  }

  private String printContentOf(RbelElement value) {
    if (!printContent) {
      return "";
    }
    String content;
    if (printParsingTimes) {
      content = (value.getConversionTimeInNanos() / 1000) / 1000. + "ms ";
    } else {
      content = "";
    }

    content += forceStringConvert(value);
    return " ("
        + colorCommand(BLUE)
        + StringUtils.abbreviate(
            content.replace("\n", "\\n").replace("\r", "\\r"),
            0,
            RbelOptions.getRbelPathTreeViewValueOutputLength())
        + colorCommand(RESET)
        + ")";
  }

  private String colorCommand(RbelAnsiColors color) {
    if (printColors) {
      return color.toString();
    } else {
      return "";
    }
  }
}
