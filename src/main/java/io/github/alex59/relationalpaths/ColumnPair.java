package io.github.alex59.relationalpaths;

/** An equality between a column in a relationship's source and destination table.
 * @param fromColumn column in the source table
 * @param toColumn column in the destination table
 */
public record ColumnPair(String fromColumn, String toColumn) {
    /** Validates both column identifiers.
     * @param fromColumn source column
     * @param toColumn destination column
     */
    public ColumnPair { Names.identifier(fromColumn); Names.identifier(toColumn); }
}
