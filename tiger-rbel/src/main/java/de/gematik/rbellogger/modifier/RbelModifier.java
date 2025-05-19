/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.util.*;
import java.util.Map.Entry;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

public class RbelModifier {

  private final RbelKeyManager rbelKeyManager;
  private final RbelConverter rbelConverter;
  private final List<RbelElementWriter> elementWriterList;
  private final Map<String, RbelModificationDescription> modificationsMap = new LinkedHashMap<>();

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
    for (RbelModificationDescription modification : modificationsMap.values()) {
      if (shouldBeApplied(modification, message)) {
        final Optional<RbelElement> targetOptional =
            modifiedMessage.findElement(modification.getTargetElement());
        if (targetOptional.isEmpty()) {
          continue;
        }

        var target = targetOptional.get();

        final Optional<byte[]> input = applyModification(modification, target);
        reduceTtl(modification);
        if (input.isPresent()) {
          modifiedMessage = rbelConverter.convertElement(input.get(), null);
        }
      }
    }
    deleteOutdatedModifications();
    return modifiedMessage;
  }

  private void deleteOutdatedModifications() {
    for (Iterator<RbelModificationDescription> ks = modificationsMap.values().iterator();
        ks.hasNext(); ) {
      RbelModificationDescription next = ks.next();
      if (next.getDeleteAfterNExecutions() != null && next.getDeleteAfterNExecutions() <= 0) {
        ks.remove();
      }
    }
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
    return TigerJexlExecutor.matchesAsJexlExpression(message, modification.getCondition());
  }

  private Optional<byte[]> applyModification(
      RbelModificationDescription modification, RbelElement targetElement) {
    RbelElement currentParent = targetElement.getParentNode();
    RbelElement currentChildToBeModified = targetElement;
    byte[] newContent = applyRegexAndReturnNewContent(targetElement, modification);
    if (Arrays.equals(newContent, targetElement.getRawContent())) {
      return Optional.empty();
    }
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
      RbelElement targetElement, RbelModificationDescription modification) {
    if (StringUtils.isEmpty(modification.getRegexFilter())) {
      if (modification.getReplaceWith() == null) {
        return "".getBytes(targetElement.getElementCharset());
      }
      return modification.getReplaceWith().getBytes(targetElement.getElementCharset());
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
    return modificationsMap.entrySet().stream().map(Entry::getValue).toList();
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
