package io.github.alex59.relationalpaths;

import java.util.*;

/** Immutable runtime metadata with validated relationship endpoints and columns. */
public final class Schema {
    private final Map<String, Table> tables;
    private final Map<String, Relationship> relationships;
    private final Map<String, List<Relationship>> outgoing;

    /**
     * Creates a schema. IDs must be unique and every referenced column must be declared.
     * @param tables logical table roles
     * @param relationships directed join edges
     */
    public Schema(Collection<Table> tables, Collection<Relationship> relationships) {
        TreeMap<String, Table> ts = new TreeMap<>();
        for (Table table : tables) {
            Objects.requireNonNull(table, "table");
            if (ts.putIfAbsent(table.id(), table) != null) throw new IllegalArgumentException("Duplicate table: " + table.id());
        }
        if (ts.isEmpty()) throw new IllegalArgumentException("Schema needs a table");
        TreeMap<String, Relationship> rs = new TreeMap<>();
        TreeMap<String, List<Relationship>> edges = new TreeMap<>();
        for (String table : ts.keySet()) edges.put(table, new ArrayList<>());
        for (Relationship relation : relationships) {
            Objects.requireNonNull(relation, "relationship");
            if (rs.putIfAbsent(relation.id(), relation) != null) throw new IllegalArgumentException("Duplicate relationship: " + relation.id());
            Table from = ts.get(relation.from()), to = ts.get(relation.to());
            if (from == null || to == null) throw new IllegalArgumentException("Unknown endpoint: " + relation.id());
            for (ColumnPair pair : relation.columns()) {
                if (!from.columns().contains(pair.fromColumn()) || !to.columns().contains(pair.toColumn())) {
                    throw new IllegalArgumentException("Unknown column in relationship: " + relation.id());
                }
            }
            edges.get(relation.from()).add(relation);
        }
        edges.replaceAll((id, list) -> list.stream().sorted(Comparator.comparing(Relationship::id)).toList());
        this.tables = Collections.unmodifiableMap(ts);
        this.relationships = Collections.unmodifiableMap(rs);
        this.outgoing = Collections.unmodifiableMap(edges);
    }

    /** Returns the described immutable value.
     * @return tables in lexicographic role order
     */
    public Map<String, Table> tables() { return tables; }
    /** Returns the described immutable value.
     * @return relationships in lexicographic ID order
     */
    public Map<String, Relationship> relationships() { return relationships; }
    /** Creates or resolves the described metadata value.
     * @param id logical role
     * @return matching table
     * @throws IllegalArgumentException if unknown
     */
    public Table table(String id) {
        Table value = tables.get(Objects.requireNonNull(id, "table id"));
        if (value == null) throw new IllegalArgumentException("Unknown table: " + id);
        return value;
    }
    /** Creates or resolves the described metadata value.
     * @param id relationship ID
     * @return matching edge
     * @throws IllegalArgumentException if unknown
     */
    public Relationship relationship(String id) {
        Relationship value = relationships.get(Objects.requireNonNull(id, "relationship id"));
        if (value == null) throw new IllegalArgumentException("Unknown relationship: " + id);
        return value;
    }
    List<Relationship> outgoing(String id) { return outgoing.get(id); }
}
