/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelNestedFacet;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import de.gematik.rbellogger.exceptions.RbelContentTreeConversionException;
import de.gematik.rbellogger.writer.tree.*;
import de.gematik.test.tiger.common.config.*;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.*;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RbelContentTreeConverter {

    public static final TigerConfigurationKey ENCODE_AS = new TigerConfigurationKey("rbel", "conversion", "encodeAs");
    private static final String TGR_ENCODE_AS = "tgrEncodeAs";
    private static final String TGR_FOR = "tgrFor";
    private static final String TGR_IF = "tgrIf";
    private List<RbelElementToContentTreeNodeConverter> converters = List.of(
        new RbelXmlElementToNodeConverter(),
        new RbelJsonElementToNodeConverter(),
        new RbelJwtElementToNodeConverter(),
        new RbelBearerTokenElementToNodeConverter()
    );
    private final RbelElement input;
    private final TigerJexlContext jexlContext;
    private Set<String> transitiveTypes = Set.of("xml", "json");

    public RbelContentTreeConverter(RbelElement input, TigerJexlContext jexlContext) {
        this.input = input;
        this.jexlContext = jexlContext;
    }

    public RbelContentTreeNode convertToContentTree() {
        return convertNode(input, null, initializeConversionContext()).get(0);
    }

    private static TigerConfigurationLoader initializeConversionContext() {
        TigerConfigurationLoader conversionContext = new TigerConfigurationLoader();
        TigerGlobalConfiguration.listSources().stream()
            .forEach(conversionContext::addConfigurationSource);
        return conversionContext;
    }

    public List<RbelContentTreeNode> convertNode(RbelElement input, String key, TigerConfigurationLoader conversionContext) {
        Optional<AbstractTigerConfigurationSource> encodingConfigurationSource = Optional.empty();
        if (isReservedKey(key)) {
            return List.of();
        }
        // tgrIf
        if (!evaluateTgrIfCondition(input)) {
            return List.of();
        }
        // tgrEncodeAs
        final Optional<String> encodeAsOptional = input.getFirst(TGR_ENCODE_AS)
            .flatMap(el -> extractEncodingType(conversionContext, el));
        if (encodeAsOptional.isPresent() && isTransitiveType(encodeAsOptional.get())) {
            encodingConfigurationSource = Optional.of(new BasicTigerConfigurationSource(SourceType.THREAD_CONTEXT,
                new TigerConfigurationKey(),
                Map.of(ENCODE_AS, encodeAsOptional.get())));
            conversionContext.addConfigurationSource(encodingConfigurationSource.get());
        }

        List<RbelContentTreeNode> result;

        // tgrFor
        if (input.getFirst(TGR_FOR).isPresent()) {
            result = executeTgrForLoop(input, key, conversionContext);
        } else {
            result = convertRbelElement(input, key, conversionContext);
        }

        encodingConfigurationSource.ifPresent(conversionContext::removeConfigurationSource);
        if (encodeAsOptional.isPresent()){
            result.stream()
                .forEach(node -> node.setType(RbelContentType.seekValueFor(encodeAsOptional.get())));
        }

        return result;
    }

    private boolean isTransitiveType(String encodingType) {
        return transitiveTypes.contains(encodingType);
    }

    private Optional<String> extractEncodingType(TigerConfigurationLoader conversionContext, RbelElement encodeAsOptional) {
        return Optional.ofNullable(encodeAsOptional)
            .map(el -> convertRbelElement(el, TGR_ENCODE_AS, conversionContext))
            .stream()
            .flatMap(List::stream)
            .map(el -> el.childNodes().stream().findFirst().orElse(el))
            .map(el -> new String(el.getContent(), el.getCharset()))
            .findFirst();
    }

    private static List<RbelContentTreeNode> evaluateTgrEncodeAsIfPresent(RbelElement element, List<RbelContentTreeNode> input) {
        // tgrEncodeWith
        if (element.getFirst(TGR_ENCODE_AS).isPresent()) {
            input.stream()
                .forEach(node -> node.setType(RbelContentType.valueOf(element.getFirst(TGR_ENCODE_AS).get().getRawStringContent())));
            return input;
        } else {
            return input;
        }
    }

    private List<RbelContentTreeNode> executeTgrForLoop(RbelElement input, String key, TigerConfigurationLoader conversionContext) {
        String loopStatement = findLoopStatement(input);
        final TigerJexlContext context = buildNewExpressionEvaluationContext();
        final RbelJexlExecutor rbelJexlExecutor = new RbelJexlExecutor();
        final Map<String, Object> jexlMapContext = rbelJexlExecutor.buildJexlMapContext(context.getRootElement(), Optional.ofNullable(key));
        context.putAll(jexlMapContext);
        rbelJexlExecutor.buildScript("t = " + loopStatement.split(":")[1]).execute(context);
        final List<RbelContentTreeNode> resultList = new ArrayList<>();
        for (Object iterate : ((Collection) context.get("t"))) {
            BasicTigerConfigurationSource localSource = new BasicTigerConfigurationSource(SourceType.THREAD_CONTEXT, new TigerConfigurationKey(),
                TigerConfigurationLoader.addYamlToMap(iterate, new TigerConfigurationKey(loopStatement.split(":")[0].trim()), new HashMap<>()));
            conversionContext.addConfigurationSource(localSource);

            resultList.addAll(convertRbelElement(input, key, conversionContext));

            conversionContext.removeConfigurationSource(localSource);
        }
        return evaluateTgrEncodeAsIfPresent(input, resultList);
    }

    private TigerJexlContext buildNewExpressionEvaluationContext() {
        final TigerJexlContext context = new TigerJexlContext();
        context.putAll(jexlContext);
        context.putAll(TigerGlobalConfiguration.instantiateConfigurationBean(Map.class).orElseThrow());
        return context;
    }

    private String findLoopStatement(RbelElement input) {
        final Optional<RbelElement> tgrFor = input.getFirst(TGR_FOR);
        if (tgrFor.isEmpty()) {
            throw new RbelContentTreeConversionException("tgrFor not present even though call stack should guarantee so!");
        }
        if (tgrFor.get().getChildNodes().size() == 1) {
            return tgrFor.get().getChildNodes().get(0).getRawStringContent();
        } else {
            return tgrFor.get().getRawStringContent();
        }
    }

    private boolean isReservedKey(String key) {
        if (key == null) {
            return false;
        }
        return List.of(TGR_IF, TGR_FOR, TGR_ENCODE_AS).contains(key);
    }

    private boolean evaluateTgrIfCondition(RbelElement input) {
        return input.getFirst(TGR_IF)
            // TODO handle invalid jexls! (currently false, should lead to exception!!!)
            .flatMap(this::retrieveTextContent)
            .map(text -> TigerJexlExecutor.matchesAsJexlExpression(text, buildNewExpressionEvaluationContext()))
            .orElse(true);
    }

    private Optional<String> retrieveTextContent(RbelElement rbelElement) {
        if (rbelElement.hasFacet(RbelXmlFacet.class)) {
            return rbelElement.getFacet(RbelXmlFacet.class)
                .map(RbelXmlFacet::getChildElements)
                .map(childs -> childs.get("text"))
                .filter(RbelElement.class::isInstance)
                .map(RbelElement.class::cast)
                .map(RbelElement::getRawStringContent);
        } else if (rbelElement.hasFacet(RbelJsonFacet.class)) {
            return rbelElement.getFacet(RbelNestedFacet.class)
                .map(RbelNestedFacet::getNestedElement)
                .filter(RbelElement.class::isInstance)
                .map(RbelElement.class::cast)
                .map(RbelElement::getRawStringContent);
        } else {
            return Optional.ofNullable(rbelElement.getRawStringContent());
        }
    }

    private List<RbelContentTreeNode> convertRbelElement(RbelElement input, String key, TigerConfigurationLoader conversionContext) {
        var result = converters.stream()
            .filter(entry -> entry.shouldConvert(input))
            .findFirst()
            .map(converter -> converter.convert(input, conversionContext, this))
            .orElseGet(() -> RbelElementWrapperContentTreeNode.constructFromRbelElement(input, conversionContext, jexlContext));
        result.setCharset(input.getElementCharset());
        result.setKey(key);
        return List.of(result);
    }

    public TigerJexlContext getJexlContext() {
        return jexlContext;
    }
}
