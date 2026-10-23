package io.github.alex59.relationalpaths;

import java.util.Objects;

/**
 * A bound comparison. Filters are AND-combined; null supports only EQ and NE.
 * Values are retained by reference; applications should supply immutable JDBC-compatible values.
 * @param column column reference
 * @param operator comparison operator
 * @param value bound value, or null for IS NULL / IS NOT NULL
 */
public record Filter(ColumnRef column, Operator operator, Object value) {
    /** Validates the comparison and SQL null semantics.
     * @param column declared field
     * @param operator comparison
     * @param value bound value or null
     */
    public Filter {
        Objects.requireNonNull(column, "column"); Objects.requireNonNull(operator, "operator");
        if (value == null && operator != Operator.EQ && operator != Operator.NE) {
            throw new IllegalArgumentException("Null supports only EQ and NE");
        }
    }
    /** Supported comparisons; SQL text cannot be supplied by the caller. */
    public enum Operator {
        /** Equal. */ EQ("="), /** Not equal. */ NE("<>"),
        /** Less than. */ LT("<"), /** Less than or equal. */ LE("<="),
        /** Greater than. */ GT(">"), /** Greater than or equal. */ GE(">=");
        final String sql;
        Operator(String sql) { this.sql = sql; }
    }
}
