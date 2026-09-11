package com.shardeya.builder.document;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * B-11 §7 / §11 "template sandbox": a restricted expression language, not a
 * general templating engine -- deliberately hand-rolled rather than reaching
 * for FreeMarker/Thymeleaf/Handlebars-on-the-JVM, all of which expose either
 * arbitrary method invocation or a class-loading surface that a "no
 * arbitrary code execution" requirement can't just configure away with
 * confidence. The grammar is exactly two constructs:
 *
 * <ul>
 *   <li>{@code {{path.to.var}}} -- interpolation, ALWAYS HTML-escaped</li>
 *   <li>{@code {{#each collectionName}} ... {{/each}}} -- one level of
 *       looping over a server-supplied {@code List<Map<String,Object>>};
 *       bare names inside the block resolve against the current loop item</li>
 * </ul>
 *
 * Nesting a second {@code #each} inside another is parseable (the tokenizer
 * is stack-based) but never validates against the allowlist, since every
 * {@link DocumentVariableAllowlist} collection declares only flat per-item
 * fields -- exactly matching B-11 §11 "no loops beyond the provided
 * collections", not general recursion.
 */
@Service
public class TemplateRenderer {

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*(#each\\s+[A-Za-z0-9_]+|/each|[A-Za-z0-9_.]+)\\s*\\}\\}");

    private sealed interface Node permits Text, Var, Each {
    }

    private record Text(String value) implements Node {
    }

    private record Var(String path) implements Node {
    }

    private record Each(String collection, List<Node> body) implements Node {
    }

    /** Every {@code {{...}}} token referenced anywhere in the template, in the shape activation validates against. */
    public record VariableReference(String path, boolean insideEach, String eachCollection) {
    }

    public List<VariableReference> extractReferences(String template) {
        List<VariableReference> refs = new ArrayList<>();
        Deque<String> eachStack = new ArrayDeque<>();
        Matcher m = TOKEN.matcher(template == null ? "" : template);
        while (m.find()) {
            String token = m.group(1).trim();
            if (token.startsWith("#each ")) {
                String collection = token.substring(6).trim();
                refs.add(new VariableReference(collection, !eachStack.isEmpty(), eachStack.peek()));
                eachStack.push(collection);
            } else if (token.equals("/each")) {
                if (!eachStack.isEmpty()) eachStack.pop();
            } else {
                refs.add(new VariableReference(token, !eachStack.isEmpty(), eachStack.peek()));
            }
        }
        return refs;
    }

    /**
     * B-11 §11 "a template referencing an unknown variable cannot be
     * activated" -- returns the first offending variable name, or empty if
     * every reference in body/header/footer resolves against the allowlist
     * for this doc type. Called by DocumentTemplateService.activate(),
     * never at generation time (generation-time failure would mean a user
     * already sees "Dear {{buyer.name}}" on a live document -- exactly what
     * this guards against).
     */
    public java.util.Optional<String> firstUnresolvableVariable(DocumentTemplate.DocType docType, String... htmlParts) {
        Set<String> topLevel = DocumentVariableAllowlist.topLevelVariables(docType);
        Map<String, Set<String>> collections = DocumentVariableAllowlist.collections(docType);
        for (String html : htmlParts) {
            if (html == null) continue;
            for (VariableReference ref : extractReferences(html)) {
                if (!ref.insideEach()) {
                    // A bare #each declaration itself must name a known collection.
                    if (collections.containsKey(ref.path())) continue;
                    if (topLevel.contains(ref.path())) continue;
                    return java.util.Optional.of(ref.path());
                } else {
                    Set<String> itemFields = collections.get(ref.eachCollection());
                    if (itemFields == null || !itemFields.contains(ref.path())) {
                        return java.util.Optional.of(ref.eachCollection() + "[]." + ref.path());
                    }
                }
            }
        }
        return java.util.Optional.empty();
    }

    /** Distinct top-level variable paths referenced, for the `variables` JSONB column (display only, not re-validated from it). */
    public Set<String> distinctTopLevelPaths(String... htmlParts) {
        Set<String> out = new LinkedHashSet<>();
        for (String html : htmlParts) {
            if (html == null) continue;
            for (VariableReference ref : extractReferences(html)) {
                if (!ref.insideEach()) out.add(ref.path());
            }
        }
        return out;
    }

    /** Renders against an already-validated template -- every interpolated value is HTML-escaped, never raw. */
    public String render(String template, Map<String, Object> context) {
        if (template == null) return "";
        List<Node> nodes = parse(template);
        StringBuilder sb = new StringBuilder();
        renderNodes(nodes, context, sb);
        return sb.toString();
    }

    private void renderNodes(List<Node> nodes, Map<String, Object> context, StringBuilder sb) {
        for (Node node : nodes) {
            switch (node) {
                case Text t -> sb.append(t.value());
                case Var v -> sb.append(HtmlUtils.htmlEscape(stringify(resolve(v.path(), context))));
                case Each e -> {
                    Object raw = context.get(e.collection());
                    List<?> items = raw instanceof List<?> list ? list : Collections.emptyList();
                    for (Object item : items) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = item instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                        renderNodes(e.body(), itemMap, sb);
                    }
                }
            }
        }
    }

    private Object resolve(String dotPath, Map<String, Object> context) {
        String[] parts = dotPath.split("\\.");
        Object current = context;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<Node> parse(String template) {
        Deque<List<Node>> stack = new ArrayDeque<>();
        Deque<String> collectionStack = new ArrayDeque<>();
        List<Node> root = new ArrayList<>();
        stack.push(root);

        Matcher m = TOKEN.matcher(template);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                stack.peek().add(new Text(template.substring(last, m.start())));
            }
            String token = m.group(1).trim();
            if (token.startsWith("#each ")) {
                String collection = token.substring(6).trim();
                List<Node> body = new ArrayList<>();
                stack.peek().add(new Each(collection, body));
                stack.push(body);
                collectionStack.push(collection);
            } else if (token.equals("/each")) {
                if (stack.size() > 1) {
                    stack.pop();
                    collectionStack.pop();
                }
            } else {
                stack.peek().add(new Var(token));
            }
            last = m.end();
        }
        if (last < template.length()) {
            stack.peek().add(new Text(template.substring(last)));
        }
        return root;
    }
}
