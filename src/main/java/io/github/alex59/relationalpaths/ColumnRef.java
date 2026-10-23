package io.github.alex59.relationalpaths;

/** A column in a logical table role, validated against the plan at rendering time.
 * @param table logical role ID, not a physical table name
 * @param column declared column name
 */
public record ColumnRef(String table, String column) {
    /** Validates both identifiers.
     * @param table logical table role
     * @param column declared column
     */
    public ColumnRef { Names.identifier(table); Names.identifier(column); }
}
