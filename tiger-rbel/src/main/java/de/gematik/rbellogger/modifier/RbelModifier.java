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
package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.util.*;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RbelModifier {

  private final RbelKeyManager rbelKeyManager;
  private final RbelConverter rbelConverter;
  private final List<RbelElementWriter> elementWriterList;
  private final Map<String, RbelModificationDescription> modificationsMap = new LinkedHashMap<>();
  private static final Pattern HEADER_NAME_PATTERN = Pattern.compile("^\\['(.+)'\\]");

  @Builder
  public RbelModifier(RbelKeyManager rbelKeyManager, RbelConverter rbelConverter) {
    this.rbelKeyManager = rbelKeyManager;
    this.rbelConverter = rbelConverter;
    this.elementWriterList =
        new ArrayList<>(
            List.of(
                new RbelHttpHeaderWriter(),
                new RbelHttpMessageWriter(),
                new RbelJsonWriter(),
                new RbelUriWriter(),
                new RbelUriParameterWriter(),
                new RbelJwtWriter(this.rbelKeyManager),
                new RbelJwtSignatureWriter(),
                new RbelJweWriter(this.rbelKeyManager),
                new RbelVauErpWriter(),
                new RbelVauEpaWriter()));
  }

  public RbelElement applyModifications(final RbelElement message) {
    RbelElement modifiedMessage = message;
    final TigerJexlContext jexlContext = new TigerJexlContext().withRootElement(message);
    for (RbelModificationDescription modification : modificationsMap.values()) {
      if (shouldBeApplied(modification, message)) {
        final Optional<RbelElement> targetOptional =
            modifiedMessage.findElement(modification.getTargetElement());
        if (targetOptional.isEmpty()) {
          if (isHeaderModification(modification)) {
            var withNewHeader = createHeader(modifiedMessage, modification, jexlContext);
            if (withNewHeader.isPresent()) {
              modifiedMessage = withNewHeader.get();
            }
          }
          continue;
        }

        var target = targetOptional.get();

        final Optional<byte[]> input =
            applyModification(modification, target, jexlContext.withCurrentElement(target));
        reduceTtl(modification);
        if (input.isPresent()) {
          modifiedMessage = rbelConverter.convertElement(input.get(), null);
        }
      }
    }
    deleteOutdatedModifications();
    return modifiedMessage;
  }

  private Optional<RbelElement> createHeader(
      RbelElement modifiedMessage,
      RbelModificationDescription modification,
      TigerJexlContext jexlContext) {
    var target = modification.getTargetElement();

    if (!target.startsWith("$.header.")) {
      throw new IllegalArgumentException(
          "Trying to add header, but modification description does not target header");
    }
    var httpHeaderElement = modifiedMessage.findElement("$.header");
    if (httpHeaderElement.isEmpty()) {
      throw new IllegalArgumentException("Trying to add header, but no header element found!");
    }
    var headerName = extractHeaderName(target);
    var rawHeader = httpHeaderElement.get().getRawContent();
    var newHeader =
        "\r\n"
            + headerName
            + ": "
            + TigerGlobalConfiguration.resolvePlaceholdersWithContext(
                modification.getReplaceWith(), jexlContext);
    var newHeaderBytes = newHeader.getBytes(modifiedMessage.getElementCharset());
    var newContent = Arrays.copyOf(rawHeader, rawHeader.length + newHeaderBytes.length);
    System.arraycopy(newHeaderBytes, 0, newContent, rawHeader.length, newHeaderBytes.length);

    return propagateChangesToParents(httpHeaderElement.get(), newContent)
        .map(bytes -> rbelConverter.convertElement(bytes, null));
  }

  private String extractHeaderName(String target) {
    var keys = RbelPathExecutor.splitRbelPathIntoKeys(target);
    if (keys.size() != 2) // header.header-name
    {
      throw new IllegalArgumentException("Could not extract header name from target " + target);
    }
    // Handle case where key is inside ['my-key'] or ["my-key"]
    var key = keys.get(1);
    var matcher = HEADER_NAME_PATTERN.matcher(key);
    if (matcher.find()) {
      return matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
    } else {
      return key;
    }
  }

  public boolean isHeaderModification(final RbelModificationDescription modification) {
    return modification.getTargetElement().startsWith("$.header");
  }

  private void deleteOutdatedModifications() {
    modificationsMap
        .values()
        .removeIf(
            next ->
                next.getDeleteAfterNExecutions() != null && next.getDeleteAfterNExecutions() <= 0);
  }

  private void reduceTtl(RbelModificationDescription modification) {
    if (modification.getDeleteAfterNExecutions() != null) {
      modification.setDeleteAfterNExecutions(modification.getDeleteAfterNExecutions() - 1);
    }
  }

  private boolean shouldBeApplied(RbelModificationDescription modification, RbelElement message) {
    if (modification.getDeleteAfterNExecutions() != null
        && modification.getDeleteAfterNExecutions() == 0) {
      return false;
    }
    if (StringUtils.isEmpty(modification.getCondition())) {
      return true;
    }
    try {
      return TigerJexlExecutor.matchesAsJexlExpression(message, modification.getCondition());
    } catch (Exception e) {
      log.warn(
          String.format(
              "Error while evaluating condition '%s' for modification '%s': %s,",
              modification.getCondition(), modification.getName(), e.getMessage()));
      return false;
    }
  }

  private Optional<byte[]> applyModification(
      RbelModificationDescription modification,
      RbelElement targetElement,
      TigerJexlContext tigerJexlContext) {

    byte[] newContent =
        applyRegexAndReturnNewContent(targetElement, modification, tigerJexlContext);
    if (Arrays.equals(newContent, targetElement.getRawContent())) {
      return Optional.empty();
    }
    return propagateChangesToParents(targetElement, newContent);
  }

  private Optional<byte[]> propagateChangesToParents(RbelElement targetElement, byte[] newContent) {
    RbelElement currentParent = targetElement.getParentNode();
    RbelElement currentChildToBeModified = targetElement;
    while (currentParent != null) {
      Optional<byte[]> found = Optional.empty();
      for (RbelElementWriter writer : elementWriterList) {
        if (writer.canWrite(currentParent)) {
          found = Optional.of(writer.write(currentParent, currentChildToBeModified, newContent));
          break;
        }
      }
      if (found.isEmpty()) {
        throw new RbelModificationException(
            "Could not rewrite element with facets "
                + currentParent.getFacets().stream()
                    .map(Object::getClass)
                    .map(Class::getSimpleName)
                    .toList()
                + "!");
      }
      newContent = found.get();
      currentChildToBeModified = currentParent;
      currentParent = currentParent.getParentNode();
    }
    return Optional.of(newContent);
  }

  private byte[] applyRegexAndReturnNewContent(
      RbelElement targetElement,
      RbelModificationDescription modification,
      TigerJexlContext tigerJexlContext) {
    if (StringUtils.isEmpty(modification.getRegexFilter())) {
      if (modification.getReplaceWith() == null) {
        return "".getBytes(targetElement.getElementCharset());
      }
      val resolvedReplacement =
          TigerGlobalConfiguration.resolvePlaceholdersWithContext(
              modification.getReplaceWith(), tigerJexlContext);
      return resolvedReplacement.getBytes(targetElement.getElementCharset());
    } else {
      return targetElement
          .getRawStringContent()
          .replaceAll(modification.getRegexFilter(), modification.getReplaceWith())
          .getBytes(targetElement.getElementCharset());
    }
  }

  public void deleteAllModifications() {
    modificationsMap.clear();
  }

  public void addModification(RbelModificationDescription modificationDescription) {
    if (StringUtils.isEmpty(modificationDescription.getName())) {
      String uuid = UUID.randomUUID().toString();
      modificationDescription.setName(uuid);
      modificationsMap.put(uuid, modificationDescription);
    } else {
      modificationsMap.put(modificationDescription.getName(), modificationDescription);
    }
  }

  public List<RbelModificationDescription> getModifications() {
    return modificationsMap.values().stream().toList();
  }

  public void deleteModification(String modificationsId) {
    modificationsMap.remove(modificationsId);
  }

  public static class RbelModificationException extends GenericTigerException {

    public RbelModificationException(String s) {
      super(s);
    }

    public RbelModificationException(String s, Exception e) {
      super(s, e);
    }
  }
}
