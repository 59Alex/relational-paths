package io.github.alex59.relationalpaths;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SqlRendererTest {
    @Test void readmeExampleProducesParameterizedSqlAndCompositeJoin() {
        Schema schema = new Schema(List.of(
                new Table("orders", "sales.orders", Set.of("id", "customer_id", "tenant_id")),
                new Table("customer", "sales.customer", Set.of("id", "tenant_id", "name"))
        ), List.of(new Relationship("order_customer", "orders", "customer", 1,
                List.of(new ColumnPair("customer_id", "id"), new ColumnPair("tenant_id", "tenant_id")))));
        JoinPlan plan = new JoinPlanner(schema).plan("orders", List.of("customer"));
        String hostileValue = "Alice' OR 1=1 --";
        BoundSql query = SqlRenderer.select(plan,
                List.of(new ColumnRef("orders", "id"), new ColumnRef("customer", "name")),
                List.of(new Filter(new ColumnRef("customer", "name"), Filter.Operator.EQ, hostileValue)));
        assertEquals("SELECT \"t0\".\"id\" AS \"c0\", \"t1\".\"name\" AS \"c1\" FROM \"sales\".\"orders\" AS \"t0\""
                + " INNER JOIN \"sales\".\"customer\" AS \"t1\" ON \"t0\".\"customer_id\" = \"t1\".\"id\""
                + " AND \"t0\".\"tenant_id\" = \"t1\".\"tenant_id\" WHERE \"t1\".\"name\" = ?", query.sql());
        assertEquals(List.of(hostileValue), query.bindings());
        assertFalse(query.sql().contains(hostileValue));
    }

    @Test void twoRolesForOnePhysicalTableGetIndependentAliases() {
        Schema schema = new Schema(List.of(
                new Table("orders", "orders", Set.of("id", "billing_id", "shipping_id")),
                new Table("billing", "address", Set.of("id", "city")),
                new Table("shipping", "address", Set.of("id", "city"))
        ), List.of(new Relationship("billing_ref", "orders", "billing", 1, List.of(new ColumnPair("billing_id", "id"))),
                new Relationship("shipping_ref", "orders", "shipping", 1, List.of(new ColumnPair("shipping_id", "id")))));
        BoundSql query = SqlRenderer.select(new JoinPlanner(schema).plan("orders", List.of("shipping", "billing")),
                List.of(new ColumnRef("billing", "city"), new ColumnRef("shipping", "city")), List.of());
        assertTrue(query.sql().contains("\"address\" AS \"t1\""));
        assertTrue(query.sql().contains("\"address\" AS \"t2\""));
        assertTrue(query.sql().contains("\"t0\".\"billing_id\" = \"t1\".\"id\""));
        assertTrue(query.sql().contains("\"t0\".\"shipping_id\" = \"t2\".\"id\""));
    }

    @Test void nullComparisonsAndBindingOrderAreCorrect() {
        JoinPlan plan = new JoinPlanner(JoinPlannerTest.diamond()).plan("a", List.of());
        BoundSql query = SqlRenderer.select(plan, List.of(new ColumnRef("a", "id")), List.of(
                new Filter(new ColumnRef("a", "id"), Filter.Operator.GT, 4),
                new Filter(new ColumnRef("a", "parent_id"), Filter.Operator.EQ, null),
                new Filter(new ColumnRef("a", "tenant_id"), Filter.Operator.NE, null),
                new Filter(new ColumnRef("a", "id"), Filter.Operator.LT, 9)));
        assertEquals(List.of(4, 9), query.bindings());
        assertTrue(query.sql().endsWith("WHERE \"t0\".\"id\" > ? AND \"t0\".\"parent_id\" IS NULL AND \"t0\".\"tenant_id\" IS NOT NULL AND \"t0\".\"id\" < ?"));
        assertThrows(IllegalArgumentException.class, () -> new Filter(new ColumnRef("a", "id"), Filter.Operator.LT, null));
    }

    @Test void unsafeIdentifiersAndUndeclaredOrUnjoinedColumnsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Table("bad", "users; DROP TABLE users", Set.of("id")));
        assertThrows(IllegalArgumentException.class, () -> new Table("bad", "catalog.schema.table", Set.of("id")));
        assertThrows(IllegalArgumentException.class, () -> new Table("bad", "schema.", Set.of("id")));
        assertThrows(IllegalArgumentException.class, () -> new ColumnRef("a", "id\" OR true"));
        JoinPlan plan = new JoinPlanner(JoinPlannerTest.diamond()).plan("a", List.of());
        assertThrows(IllegalArgumentException.class, () -> SqlRenderer.select(plan, List.of(new ColumnRef("a", "secret")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> SqlRenderer.select(plan, List.of(new ColumnRef("b", "id")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> SqlRenderer.select(plan, List.of(), List.of()));
    }
}
