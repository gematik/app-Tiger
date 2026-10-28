/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger.facets.apdu;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;
import static j2html.TagCreator.text;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

/** A command APDU according to ISO 7816-4. Fields absent in the APDU's case are null. */
@Value
@Builder
public class RbelApduCommandFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelApduCommandFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit toolkit) {
            final var apdu = element.getFacetOrFail(RbelApduCommandFacet.class);
            final var boxes =
                vertParentTitle()
                    .with(
                        childBoxNotifTitle(CLS_HEADER)
                            .with(t2("Header"))
                            .with(header(apdu, toolkit)));
            if (apdu.getData() != null) {
              boxes.with(
                  childBoxNotifTitle(CLS_BODY)
                      .with(t2("Data"))
                      .with(toolkit.convert(apdu.getData())));
            }
            return div(t1ms("APDU Command").with(showContentButtonAndDialog(element, toolkit)))
                .with(addNotes(element, "mb-5"))
                .with(ancestorTitle().with(boxes));
          }

          private List<DomContent> header(
              RbelApduCommandFacet apdu, RbelHtmlRenderingToolkit toolkit) {
            final List<DomContent> lines = new ArrayList<>();
            lines.addAll(
                toolkit.packAsInfoLine("Command", toolkit.formatHexAlike(apdu.printName())));
            lines.addAll(
                toolkit.packAsInfoLine(
                    "CLA / INS",
                    toolkit.formatHex(apdu.getCla()),
                    toolkit.formatHex(apdu.getIns())));
            lines.addAll(
                toolkit.packAsInfoLine(
                    "P1 / P2", toolkit.formatHex(apdu.getP1()), toolkit.formatHex(apdu.getP2())));
            apdu.getCla()
                .getFacet(RbelApduClassFacet.class)
                .ifPresent(
                    cla -> lines.addAll(toolkit.packAsInfoLine("Class", text(cla.describe()))));
            lines.addAll(toolkit.packAsInfoLine("Case", text(printValue(apdu.getApduCase()))));
            if (apdu.getLc() != null) {
              lines.addAll(toolkit.packAsInfoLine("Lc", text(printValue(apdu.getLc()))));
            }
            if (apdu.getLe() != null) {
              lines.addAll(
                  toolkit.packAsInfoLine(
                      "Le",
                      toolkit.formatHex(apdu.getLe()),
                      text("(Ne = " + printValue(apdu.getLe()) + ")")));
            }
            return lines;
          }

          private static String printValue(RbelElement element) {
            return element.printValue().orElse("");
          }
        });
  }

  RbelElement cla;
  RbelElement ins;
  RbelElement p1;
  RbelElement p2;
  RbelElement lc;
  RbelElement data;
  RbelElement le;
  RbelElement apduCase;
  RbelElement name;
  ApduCommandDictionary.Description description;

  public String printName() {
    return Optional.ofNullable(description)
        .map(ApduCommandDictionary.Description::name)
        .orElse("<unknown>");
  }

  /** The command name with details, e.g. "SELECT DF.ESIGN", or the raw CLA/INS if unknown. */
  public String infoLine() {
    return Optional.ofNullable(description)
        .map(ApduCommandDictionary.Description::infoLine)
        .orElseGet(
            () ->
                "CLA %s INS %s"
                    .formatted(cla.printValue().orElse(""), ins.printValue().orElse("")));
  }

  @Getter(lazy = true)
  RbelMultiMap<RbelElement> childElements =
      new RbelMultiMap<RbelElement>()
          .with("cla", cla)
          .with("ins", ins)
          .with("p1", p1)
          .with("p2", p2)
          .withSkipIfNull("lc", lc)
          .withSkipIfNull("data", data)
          .withSkipIfNull("le", le)
          .with("case", apduCase)
          .withSkipIfNull("name", name);
}
