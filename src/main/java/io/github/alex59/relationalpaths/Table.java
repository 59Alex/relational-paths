package io.github.alex59.relationalpaths;

import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;
import java.util.Objects;

/**
 * One logical table role and its allowed columns. Multiple roles may reference the same physical table.
 * @param id unique logical role, for example billing_address
 * @param name physical table or schema.table; ASCII SQL identifiers only
 * @param columns nonempty column allowlist
 */
public record Table(String id, String name, Set<String> columns) {
    /** Validates identifiers and snapshots the column allowlist.
     * @param id logical role ID
     * @param name table or schema.table
     * @param columns declared columns
     */
    public Table {
        Names.identifier(id);
        Names.qualified(name);
        Objects.requireNonNull(columns, "columns");
        if (columns.isEmpty()) throw new IllegalArgumentException("Table needs columns: " + id);
        TreeSet<String> copy = new TreeSet<>();
        for (String column : columns) copy.add(Names.identifier(column));
        columns = Collections.unmodifiableSet(copy);
    }
}
