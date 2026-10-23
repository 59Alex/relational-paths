package io.github.alex59.relationalpaths;

import java.util.List;
import java.util.Objects;

/**
 * SQL and positional bindings. Never concatenate bindings into SQL; use PreparedStatement.
 * @param sql ANSI-quoted SELECT statement with JDBC question-mark placeholders
 * @param bindings values in placeholder order (JDBC indices start at one)
 */
public record BoundSql(String sql, List<Object> bindings) {
    /** Snapshots the bindings without converting them into SQL text.
     * @param sql parameterized SQL
     * @param bindings ordered values
     */
    public BoundSql { Objects.requireNonNull(sql, "sql"); bindings = List.copyOf(bindings); }
}
