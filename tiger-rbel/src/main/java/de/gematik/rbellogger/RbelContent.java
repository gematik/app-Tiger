package de.gematik.rbellogger;

import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

public interface RbelContent {

    <T extends RbelContent> T getParentNode();

    <T extends RbelContent>Optional<T> getFirst(String key);

    <T extends RbelContent> List<T> getAll(String key);

    <T extends RbelContent> List<T> getChildNodes();

    <T extends RbelContent>RbelMultiMap<T> getChildNodesWithKey();

    Optional<String> getKey();

    String getRawStringContent();

    Charset getElementCharset();

    <T extends RbelContent>Optional<T> findElement(String rbelPath);

    <T extends RbelContent> T findRootElement();

    String findNodePath();

    <T extends RbelContent> List<T> findRbelPathMembers(String rbelPath);

    Optional<String> findKeyInParentElement();

    <T extends RbelContent> T findMessage();

    <T extends RbelFacet> boolean hasFacet(Class<T> clazz);
}
