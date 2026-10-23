package io.github.alex59.relationalpaths;

import java.util.*;

/** A validated immutable join tree with deterministic aliases and per-target route explanations. */
public final class JoinPlan {
    private final Schema schema;
    private final String root;
    private final List<Relationship> joins;
    private final SequencedMap<String, String> aliases;
    private final SequencedMap<String, List<String>> routes;
    JoinPlan(Schema schema, String root, List<Relationship> joins, Map<String, List<String>> routes) {
        this.schema = schema;
        this.root = root;
        this.joins = List.copyOf(joins);
        SequencedMap<String, String> aliases = LinkedHashMap.newLinkedHashMap(joins.size() + 1);
        aliases.putLast(root, "t0");
        for (Relationship join : joins) aliases.putLast(join.to(), "t" + aliases.size());
        this.aliases = Collections.unmodifiableSequencedMap(aliases);
        TreeMap<String, List<String>> copy = new TreeMap<>();
        routes.forEach((target, route) -> copy.put(target, List.copyOf(route)));
        this.routes = Collections.unmodifiableSequencedMap(copy);
    }
    /** Returns the described immutable value.
     * @return immutable input metadata
     */
    public Schema schema() { return schema; }
    /** Returns the described immutable value.
     * @return root logical role
     */
    public String root() { return root; }
    /** Returns the described immutable value.
     * @return dependency-ordered joins; each destination occurs once
     */
    public List<Relationship> joins() { return joins; }
    /** Returns the described immutable value.
     * @return logical role to generated SQL alias, in join order
     */
    public Map<String, String> aliases() { return aliases; }
    /** Returns the described immutable value.
     * @return relationship IDs for every requested target, including explicit routes
     */
    public Map<String, List<String>> routes() { return routes; }
    /** Returns the described immutable value.
     * @return a human-readable plan, including roles, aliases, costs, and join columns
     */
    public String explain() {
        StringBuilder result = new StringBuilder("ROOT ").append(root).append(" AS t0");
        for (Relationship join : joins) {
            result.append("\n").append(join.id()).append(": ").append(join.from()).append(" -> ")
                    .append(join.to()).append(" AS ").append(aliases.get(join.to()))
                    .append(" cost=").append(join.cost()).append(" on ");
            result.append(join.columns().stream().map(pair -> join.from() + "." + pair.fromColumn()
                    + " = " + join.to() + "." + pair.toColumn()).collect(java.util.stream.Collectors.joining(" AND ")));
        }
        return result.toString();
    }
}
