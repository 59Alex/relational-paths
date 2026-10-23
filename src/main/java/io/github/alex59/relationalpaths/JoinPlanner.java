package io.github.alex59.relationalpaths;

import java.util.*;

/**
 * Finds minimum semantic-cost directed paths and merges compatible paths into a join tree.
 * Equal-cost alternatives are rejected. This does not estimate database execution costs.
 * Instances are immutable and can be shared across threads.
 */
public final class JoinPlanner {
    private final Schema schema;
    private final SearchLimits limits;

    /** Creates or resolves the described metadata value.
     * @param schema validated metadata
     */
    public JoinPlanner(Schema schema) { this(schema, SearchLimits.DEFAULT); }

    /** Creates or resolves the described metadata value.
     * @param schema validated metadata
     * @param limits hard planning bounds
     */
    public JoinPlanner(Schema schema, SearchLimits limits) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.limits = Objects.requireNonNull(limits, "limits");
        if (schema.tables().size() > limits.maxTables() || schema.relationships().size() > limits.maxRelationships()) {
            throw new PlanningException("Schema exceeds configured table/relationship limits");
        }
    }

    /**
     * Plans unambiguous shortest paths for all targets.
     * @param root root logical table role
     * @param targets logical table roles needed by projections or filters
     * @return a deterministic join tree
     */
    public JoinPlan plan(String root, Collection<String> targets) { return plan(root, targets, Map.of()); }

    /**
     * Plans paths, honoring explicit relationship-ID routes for selected targets.
     * Explicit routes may be longer than the shortest path. They must start at root,
     * end at their target, and cannot repeat a logical role.
     * @param root root logical role
     * @param targets requested roles; duplicates are harmless
     * @param explicitRoutes target role to ordered relationship IDs
     * @return validated join plan
     * @throws AmbiguousPathException if an automatic target has multiple minimum-cost paths
     * @throws PlanningException if unreachable, inconsistent, cyclic, or over search limits
     */
    public JoinPlan plan(String root, Collection<String> targets, Map<String, List<String>> explicitRoutes) {
        schema.table(root);
        TreeSet<String> orderedTargets = new TreeSet<>();
        for (String target : targets) {
            schema.table(target);
            orderedTargets.add(target);
        }
        if (!orderedTargets.containsAll(explicitRoutes.keySet())) {
            throw new PlanningException("Explicit route supplied for an unrequested target");
        }
        Map<String, Node> shortest = orderedTargets.stream().anyMatch(t -> !t.equals(root) && !explicitRoutes.containsKey(t))
                ? shortestPaths(root) : Map.of();
        Map<String, Relationship> incoming = new TreeMap<>();
        Map<String, List<String>> routes = new TreeMap<>();
        for (String target : orderedTargets) {
            List<Relationship> path;
            if (explicitRoutes.containsKey(target)) {
                path = explicitPath(root, target, explicitRoutes.get(target));
            } else if (root.equals(target)) {
                path = List.of();
            } else {
                Node node = shortest.get(target);
                if (node == null) throw new PlanningException("No directed path from " + root + " to " + target);
                if (node.paths.size() > 1) {
                    throw new AmbiguousPathException(target, node.paths.stream()
                            .map(p -> p.stream().map(Relationship::id).toList()).toList());
                }
                path = node.paths.get(0);
            }
            routes.put(target, path.stream().map(Relationship::id).toList());
            for (Relationship relation : path) {
                Relationship previous = incoming.putIfAbsent(relation.to(), relation);
                if (previous != null && !previous.equals(relation)) {
                    throw new PlanningException("Conflicting paths enter role " + relation.to() + " through "
                            + previous.id() + " and " + relation.id() + "; use consistent paths or distinct table roles");
                }
            }
        }
        Set<String> reached = new HashSet<>(Set.of(root));
        List<Relationship> joins = new ArrayList<>();
        while (!incoming.isEmpty()) {
            Relationship next = incoming.values().stream().filter(r -> reached.contains(r.from()))
                    .min(Comparator.comparing(Relationship::id)).orElseThrow(() -> new PlanningException("Cyclic join plan"));
            joins.add(next);
            reached.add(next.to());
            incoming.remove(next.to());
        }
        return new JoinPlan(schema, root, joins, routes);
    }

    private Map<String, Node> shortestPaths(String root) {
        Map<String, Node> nodes = new HashMap<>();
        nodes.put(root, new Node(0, new ArrayList<>(List.of(List.of()))));
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingLong(Visit::distance).thenComparing(Visit::table));
        queue.add(new Visit(root, 0));
        int visits = 0;
        while (!queue.isEmpty()) {
            Visit visit = queue.remove();
            Node source = nodes.get(visit.table());
            if (source.distance != visit.distance()) continue;
            for (Relationship relation : schema.outgoing(visit.table())) {
                if (++visits > limits.maxEdgeVisits()) throw new PlanningException("Search exceeded maxEdgeVisits=" + limits.maxEdgeVisits());
                long distance = source.distance + relation.cost();
                Node destination = nodes.get(relation.to());
                if (destination == null || distance < destination.distance) {
                    destination = new Node(distance, new ArrayList<>());
                    nodes.put(relation.to(), destination);
                    queue.add(new Visit(relation.to(), distance));
                }
                if (distance == destination.distance && destination.paths.size() < 2) {
                    // Positive costs guarantee every predecessor is settled before its destination.
                    // Two witness paths are enough to prove ambiguity; no exponential enumeration.
                    for (List<Relationship> prefix : source.paths) {
                        List<Relationship> path = new ArrayList<>(prefix);
                        path.add(relation);
                        List<Relationship> immutable = List.copyOf(path);
                        if (!destination.paths.contains(immutable)) destination.paths.add(immutable);
                        if (destination.paths.size() == 2) break;
                    }
                }
            }
        }
        return nodes;
    }

    private List<Relationship> explicitPath(String root, String target, List<String> ids) {
        Objects.requireNonNull(ids, "explicit route");
        if (ids.size() >= schema.tables().size()) throw new PlanningException("Explicit route is too long for a simple path");
        List<Relationship> path = new ArrayList<>();
        Set<String> visited = new HashSet<>(Set.of(root));
        String current = root;
        for (String id : ids) {
            Relationship relation = schema.relationship(id);
            if (!relation.from().equals(current)) throw new PlanningException("Disconnected explicit route at " + id);
            if (!visited.add(relation.to())) throw new PlanningException("Explicit route repeats role " + relation.to());
            path.add(relation);
            current = relation.to();
        }
        if (!current.equals(target)) throw new PlanningException("Explicit route ends at " + current + ", expected " + target);
        return List.copyOf(path);
    }

    private record Visit(String table, long distance) { }
    private static final class Node {
        final long distance;
        final List<List<Relationship>> paths;
        Node(long distance, List<List<Relationship>> paths) { this.distance = distance; this.paths = paths; }
    }
}
