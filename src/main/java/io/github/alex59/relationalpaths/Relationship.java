package io.github.alex59.relationalpaths;

import java.util.List;
import java.util.HashSet;

/**
 * A directed, named join relationship. Direction is never inferred from a foreign key.
 * @param id unique relationship identifier
 * @param from source logical table role
 * @param to destination logical table role
 * @param cost positive semantic preference weight, not a database execution cost
 * @param columns equality pairs; several pairs represent a composite key
 */
public record Relationship(String id, String from, String to, int cost, List<ColumnPair> columns) {
    /** Validates a directed relationship and snapshots its equality pairs.
     * @param id relationship ID
     * @param from source logical role
     * @param to destination logical role
     * @param cost positive semantic weight
     * @param columns equality pairs
     */
    public Relationship {
        Names.identifier(id); Names.identifier(from); Names.identifier(to);
        if (from.equals(to)) throw new IllegalArgumentException("Self-joins need distinct logical table roles");
        if (cost <= 0) throw new IllegalArgumentException("Relationship cost must be positive");
        columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("A relationship needs column pairs");
        if (new HashSet<>(columns).size() != columns.size()) throw new IllegalArgumentException("Duplicate column pair");
    }
}
