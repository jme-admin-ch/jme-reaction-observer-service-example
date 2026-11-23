package ch.admin.bit.jeap.jme.reactionobserver.test;

import io.restassured.path.json.JsonPath;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads the reactions of a graph answered by the Reaction Observer Service, in the notation
 * "trigger -> {actions}", with an empty trigger for a reaction without trigger. An interface is named by its
 * message type if it is a message, and by its FQN otherwise.
 * <p>
 * The API v1 and the API v2 answer the same reactions in different formats, so there is one reader per version
 * with the same result: what the two versions show can be compared.
 * <p>
 * The result is a sorted list rather than a set: two reactions with the same notation, e.g. reactions that differ
 * only in HTTP operations as the API v1 shows them, stay two entries. The actions of a reaction are sorted.
 */
final class ReactionGraphs {

    private static final String NODES = "graph.nodes";
    private static final String EDGES = "graph.edges";
    private static final String NODE_TYPE = "nodeType";
    private static final String EDGE_TYPE = "edgeType";
    private static final String REACTION = "REACTION";
    private static final String TRIGGER = "TRIGGER";
    private static final String ACTION = "ACTION";
    private static final String ID = "id";

    private ReactionGraphs() {
    }

    /**
     * The reactions of a graph of the API v1, which shows messages only. Its ids are numbers that are unique per
     * kind of node only, and an edge names its reaction in a field of its own.
     */
    static List<String> ofApiV1(JsonPath graph) {
        Map<String, String> interfaces = new HashMap<>();
        Set<String> reactions = new TreeSet<>();
        for (Map<String, Object> node : nodes(graph)) {
            if ("MESSAGE".equals(node.get(NODE_TYPE))) {
                interfaces.put("M" + node.get(ID), (String) node.get("messageType"));
            } else {
                reactions.add("R" + node.get(ID));
            }
        }

        Map<String, String> triggers = new HashMap<>();
        Map<String, Set<String>> actions = new HashMap<>();
        for (Map<String, Object> edge : edges(graph)) {
            if (TRIGGER.equals(edge.get(EDGE_TYPE))) {
                triggers.put("R" + edge.get("targetReactionId"), interfaces.get("M" + edge.get("sourceId")));
            } else {
                actions.computeIfAbsent("R" + edge.get("sourceReactionId"), _ -> new TreeSet<>())
                        .add(interfaces.get("M" + edge.get("targetId")));
            }
        }
        return notation(reactions, triggers, actions);
    }

    /**
     * The reactions of a graph of the API v2, which shows every kind of interface. Reads the graph as the contract
     * of the API v2 asks its consumers to: a node that is not a reaction is an interface, whatever its node type, and
     * edges of other types than trigger and action are ignored.
     */
    static List<String> ofApiV2(JsonPath graph) {
        return notationsOfApiV2(graph).values().stream().sorted().toList();
    }

    /**
     * How often the reactions of a graph of the API v2 have been observed: the median of the daily observation
     * counts of each reaction, by its notation. With observations of a single day, this is the count of that day.
     * The medians of reactions with the same notation are added up.
     */
    static Map<String, Integer> mediansOfApiV2(JsonPath graph) {
        Map<String, String> notations = notationsOfApiV2(graph);
        Map<String, Integer> medians = new TreeMap<>();
        for (Map<String, Object> node : nodes(graph)) {
            if (REACTION.equals(node.get(NODE_TYPE))) {
                medians.merge(notations.get((String) node.get(ID)), ((Number) node.get("median")).intValue(), Integer::sum);
            }
        }
        return medians;
    }

    /** The notations of the reactions of a graph of the API v2 by the id of their node. */
    private static Map<String, String> notationsOfApiV2(JsonPath graph) {
        Map<String, String> interfaces = new HashMap<>();
        Set<String> reactions = new TreeSet<>();
        for (Map<String, Object> node : nodes(graph)) {
            String id = (String) node.get(ID);
            if (REACTION.equals(node.get(NODE_TYPE))) {
                reactions.add(id);
            } else {
                Object messageType = node.get("messageType");
                interfaces.put(id, (String) (messageType != null ? messageType : node.get("fqn")));
            }
        }

        Map<String, String> triggers = new HashMap<>();
        Map<String, Set<String>> actions = new HashMap<>();
        for (Map<String, Object> edge : edges(graph)) {
            String source = (String) edge.get("sourceId");
            String target = (String) edge.get("targetId");
            if (TRIGGER.equals(edge.get(EDGE_TYPE))) {
                triggers.put(target, interfaces.get(source));
            } else if (ACTION.equals(edge.get(EDGE_TYPE))) {
                actions.computeIfAbsent(source, _ -> new TreeSet<>()).add(interfaces.get(target));
            }
        }
        Map<String, String> notations = new HashMap<>();
        reactions.forEach(reaction -> notations.put(reaction,
                notation(triggers.get(reaction), actions.getOrDefault(reaction, Set.of()))));
        return notations;
    }

    /**
     * The HTTP operations of a graph of the API v2 with the fields of their kind, each as
     * "provider=..., method=..., path=...".
     */
    static List<String> httpOperationsOfApiV2(JsonPath graph) {
        return nodes(graph).stream()
                .filter(node -> "HTTP_OPERATION".equals(node.get(NODE_TYPE)))
                .map(node -> "provider=" + node.get("provider") + ", method=" + node.get("method") + ", path=" + node.get("path"))
                .sorted()
                .toList();
    }

    /**
     * @param trigger the trigger, null for a reaction without trigger
     * @return the reaction in the notation "trigger -> {actions}", with the actions sorted
     */
    static String notation(String trigger, Collection<String> actions) {
        return (trigger != null ? trigger + " " : "") + "-> {" + String.join(", ", new TreeSet<>(actions)) + "}";
    }

    private static List<Map<String, Object>> nodes(JsonPath graph) {
        return graph.getList(NODES);
    }

    private static List<Map<String, Object>> edges(JsonPath graph) {
        return graph.getList(EDGES);
    }

    private static List<String> notation(Set<String> reactions, Map<String, String> triggers,
                                         Map<String, Set<String>> actions) {
        return reactions.stream()
                .map(reaction -> notation(triggers.get(reaction), actions.getOrDefault(reaction, Set.of())))
                .sorted()
                .toList();
    }
}
