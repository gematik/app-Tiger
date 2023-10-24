/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.parser.ParserException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A specialized {@link Constructor} that checks for duplicate keys.
 */
public class DuplicateMapKeysForbiddenConstructor extends SafeConstructor {

    public DuplicateMapKeysForbiddenConstructor() {
        super(new LoaderOptions());
    }

    @Override
    protected Map<Object, Object> constructMapping(MappingNode node) {
        try {
            List<String> keys = node.getValue().stream().map(v -> ((ScalarNode) v.getKeyNode()).getValue()).toList();
            Set<String> duplicates = findDuplicates(keys);
            if (!duplicates.isEmpty()) {
                throw new TigerConfigurationException(
                        "Duplicate keys in yaml file ('" + String.join(",", duplicates) + "')!");
            }
        } catch (TigerConfigurationException tcex) {
            throw tcex;
        } catch (Exception e) {
            throw new TigerConfigurationException("Duplicate keys in yaml file!", e);
        }
        try {
            return super.constructMapping(node);
        } catch (IllegalStateException e) {
            throw new ParserException("while parsing MappingNode",
                node.getStartMark(), e.getMessage(), node.getEndMark());
        }
    }

    private <T> Set<T> findDuplicates(Collection<T> collection) {
        Set<T> uniques = new HashSet<>();
        return collection.stream()
            .filter(e -> !uniques.add(e))
            .collect(Collectors.toSet());
    }
}
