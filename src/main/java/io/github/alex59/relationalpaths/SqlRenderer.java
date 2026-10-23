package io.github.alex59.relationalpaths;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders an INNER JOIN SELECT with ANSI-quoted identifiers and JDBC positional bindings.
 * It does not execute SQL or discover metadata. Database permissions remain the caller's responsibility.
 */
public final class SqlRenderer {
    private SqlRenderer() { }

    /**
     * Renders the complete plan. Columns must be allowlisted in metadata and included in the plan.
     * Output columns are named c0, c1, etc. in projection order. Filter values never enter SQL text.
     * @param plan validated join plan
     * @param projection ordered, nonempty selected columns
     * @param filters ordered AND-connected comparisons
     * @return SQL and ordered bind values
     */
    public static BoundSql select(JoinPlan plan, List<ColumnRef> projection, List<Filter> filters) {
        Objects.requireNonNull(plan, "plan");
        projection = List.copyOf(projection);
        filters = List.copyOf(filters);
        if (projection.isEmpty()) throw new IllegalArgumentException("At least one projection is required");
        List<String> expressions = new ArrayList<>();
        for (int i = 0; i < projection.size(); i++) expressions.add(column(plan, projection.get(i)) + " AS " + Names.quote("c" + i));
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", expressions))
                .append(" FROM ").append(table(plan, plan.root()));
        for (Relationship join : plan.joins()) {
            sql.append(" INNER JOIN ").append(table(plan, join.to())).append(" ON ");
            sql.append(join.columns().stream().map(pair -> column(plan, new ColumnRef(join.from(), pair.fromColumn()))
                    + " = " + column(plan, new ColumnRef(join.to(), pair.toColumn())))
                    .collect(Collectors.joining(" AND ")));
        }
        List<Object> bindings = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        for (Filter filter : filters) {
            String field = column(plan, filter.column());
            if (filter.value() == null) predicates.add(field + (filter.operator() == Filter.Operator.EQ ? " IS NULL" : " IS NOT NULL"));
            else {
                predicates.add(field + " " + filter.operator().sql + " ?");
                bindings.add(filter.value());
            }
        }
        if (!predicates.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", predicates));
        return new BoundSql(sql.toString(), bindings);
    }

    private static String table(JoinPlan plan, String id) {
        return Names.quoteTable(plan.schema().table(id).name()) + " AS " + Names.quote(plan.aliases().get(id));
    }
    private static String column(JoinPlan plan, ColumnRef column) {
        Table table = plan.schema().table(column.table());
        if (!table.columns().contains(column.column())) throw new IllegalArgumentException("Undeclared column: " + column);
        String alias = plan.aliases().get(column.table());
        if (alias == null) throw new IllegalArgumentException("Table is absent from plan: " + column.table());
        return Names.quote(alias) + "." + Names.quote(column.column());
    }
}
