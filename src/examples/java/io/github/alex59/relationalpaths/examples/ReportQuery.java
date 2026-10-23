package io.github.alex59.relationalpaths.examples;

import io.github.alex59.relationalpaths.*;
import java.util.List;
import java.util.Set;

/** Run with ./gradlew example; no database is needed to inspect the query. */
public final class ReportQuery {
    void main() {
        Schema schema = new Schema(List.of(
                new Table("orders", "sales.orders", Set.of("id", "customer_id", "tenant_id")),
                new Table("customer", "sales.customer", Set.of("id", "tenant_id", "name"))
        ), List.of(new Relationship("order_customer", "orders", "customer", 1,
                List.of(new ColumnPair("customer_id", "id"), new ColumnPair("tenant_id", "tenant_id")))));

        JoinPlan plan = new JoinPlanner(schema).plan("orders", List.of("customer"));
        BoundSql query = SqlRenderer.select(plan,
                List.of(new ColumnRef("orders", "id"), new ColumnRef("customer", "name")),
                List.of(new Filter(new ColumnRef("customer", "name"), Filter.Operator.EQ, "Alice")));

        IO.println(plan.explain());
        IO.println(query.sql());
        IO.println("Bindings: " + query.bindings());
    }
}
