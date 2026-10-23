import io.github.alex59.relationalpaths.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runs the documentation examples without a database or runtime dependencies. */
public final class Example {
    private Example() { }

    public static void main(String[] args) {
        Schema schema = schemaExample();
        JoinPlan plan = planningExample(schema);
        ambiguityExample();
        rolesExample();
        renderingExample(plan);
        nullExample(plan);
        boundariesExample(schema, plan);
        System.out.println("ALL CHECKS PASSED");
    }

    private static Schema schemaExample() {
        // snippet:schema
        Schema schema = new Schema(List.of(
            new Table("orders", "sales.orders",
                Set.of("id", "customer_id", "tenant_id", "region_id")),
            new Table("customer", "sales.customer",
                Set.of("id", "tenant_id", "region_id", "name")),
            new Table("region", "sales.region", Set.of("id", "name"))
        ), List.of(
            new Relationship("order_customer", "orders", "customer", 1,
                List.of(new ColumnPair("customer_id", "id"),
                        new ColumnPair("tenant_id", "tenant_id"))),
            new Relationship("customer_region", "customer", "region", 1,
                List.of(new ColumnPair("region_id", "id"))),
            new Relationship("order_region", "orders", "region", 5,
                List.of(new ColumnPair("region_id", "id")))
        ));
        System.out.println(schema.tables().keySet());
        System.out.println(schema.relationship("order_customer").columns().size());
        // end:schema
        require(schema.relationship("order_customer").columns().size() == 2, "Composite key lost");
        return schema;
    }

    private static JoinPlan planningExample(Schema schema) {
        // snippet:planning
        JoinPlan plan = new JoinPlanner(schema)
            .plan("orders", List.of("customer", "region"));
        System.out.println(plan.routes().get("region"));
        System.out.println(plan.aliases());
        System.out.println(plan.joins().size());
        System.out.println(plan.explain());
        // end:planning
        require(plan.routes().get("region").equals(List.of("order_customer", "customer_region")), "Wrong weighted route");
        require(plan.joins().size() == 2, "Shared prefix duplicated");
        require(plan.aliases().get("region").equals("t2"), "Alias order changed");
        return plan;
    }

    private static void ambiguityExample() {
        // snippet:ambiguity
        Schema ambiguousSchema = new Schema(List.of(
            new Table("orders", "orders", Set.of("id", "billing_id", "shipping_id")),
            new Table("address", "address", Set.of("id", "city"))
        ), List.of(
            new Relationship("billing_ref", "orders", "address", 1,
                List.of(new ColumnPair("billing_id", "id"))),
            new Relationship("shipping_ref", "orders", "address", 1,
                List.of(new ColumnPair("shipping_id", "id")))
        ));
        JoinPlanner addressPlanner = new JoinPlanner(ambiguousSchema);
        try {
            addressPlanner.plan("orders", List.of("address"));
            throw new AssertionError("Expected an ambiguous path");
        } catch (AmbiguousPathException error) {
            System.out.println(error.target());
            System.out.println(error.candidates());
        }
        JoinPlan shippingPlan = addressPlanner.plan("orders", List.of("address"),
            Map.of("address", List.of("shipping_ref")));
        System.out.println(shippingPlan.routes().get("address"));
        // end:ambiguity
        require(shippingPlan.routes().get("address").equals(List.of("shipping_ref")), "Explicit choice ignored");
    }

    private static void rolesExample() {
        // snippet:roles
        Schema addressRoles = new Schema(List.of(
            new Table("orders", "orders", Set.of("id", "billing_id", "shipping_id")),
            new Table("billing", "address", Set.of("id", "city")),
            new Table("shipping", "address", Set.of("id", "city"))
        ), List.of(
            new Relationship("billing_ref", "orders", "billing", 1,
                List.of(new ColumnPair("billing_id", "id"))),
            new Relationship("shipping_ref", "orders", "shipping", 1,
                List.of(new ColumnPair("shipping_id", "id")))
        ));
        JoinPlan twoAddresses = new JoinPlanner(addressRoles)
            .plan("orders", List.of("shipping", "billing"));
        System.out.println(twoAddresses.aliases());
        // end:roles
        require(twoAddresses.aliases().get("billing").equals("t1"), "Billing alias changed");
        require(twoAddresses.aliases().get("shipping").equals("t2"), "Shipping alias changed");
    }

    private static void renderingExample(JoinPlan plan) {
        // snippet:rendering
        BoundSql query = SqlRenderer.select(plan,
            List.of(new ColumnRef("orders", "id"), new ColumnRef("region", "name")),
            List.of(new Filter(new ColumnRef("customer", "name"),
                Filter.Operator.EQ, "Alice' OR 1=1 --")));
        System.out.println(query.sql());
        System.out.println(query.bindings());
        // end:rendering
        String expectedSql = "SELECT \"t0\".\"id\" AS \"c0\", \"t2\".\"name\" AS \"c1\" FROM \"sales\".\"orders\" AS \"t0\""
            + " INNER JOIN \"sales\".\"customer\" AS \"t1\" ON \"t0\".\"customer_id\" = \"t1\".\"id\""
            + " AND \"t0\".\"tenant_id\" = \"t1\".\"tenant_id\""
            + " INNER JOIN \"sales\".\"region\" AS \"t2\" ON \"t1\".\"region_id\" = \"t2\".\"id\""
            + " WHERE \"t1\".\"name\" = ?";
        require(query.sql().equals(expectedSql), "Unexpected SQL rendering");
        require(query.bindings().equals(List.of("Alice' OR 1=1 --")), "Bound value changed");
        require(!query.sql().contains("Alice"), "Value leaked into SQL");
    }

    private static void nullExample(JoinPlan plan) {
        // snippet:nulls
        BoundSql nullQuery = SqlRenderer.select(plan,
            List.of(new ColumnRef("customer", "id")),
            List.of(new Filter(new ColumnRef("customer", "name"), Filter.Operator.EQ, null)));
        System.out.println(nullQuery.sql().endsWith("WHERE \"t1\".\"name\" IS NULL"));
        System.out.println(nullQuery.bindings());
        // end:nulls
        require(nullQuery.sql().endsWith("WHERE \"t1\".\"name\" IS NULL"), "Null comparison changed");
        require(nullQuery.bindings().isEmpty(), "Null predicate should not have a placeholder");
    }

    private static void boundariesExample(Schema schema, JoinPlan plan) {
        // snippet:limits
        try {
            new JoinPlanner(schema, new SearchLimits(10, 10, 0))
                .plan("orders", List.of("customer"));
            throw new AssertionError("Expected the edge-visit limit");
        } catch (PlanningException error) {
            System.out.println(error.getMessage());
        }
        // end:limits
        expect(IllegalArgumentException.class, () -> SqlRenderer.select(plan,
            List.of(new ColumnRef("customer", "undeclared")), List.of()));
        expect(IllegalArgumentException.class, () -> SqlRenderer.select(plan,
            List.of(new ColumnRef("customer", "id")),
            List.of(new Filter(new ColumnRef("customer", "name"), Filter.Operator.LT, null))));
        expect(UnsupportedOperationException.class, () -> plan.aliases().put("other", "t9"));
        expect(UnsupportedOperationException.class, () -> plan.routes().get("region").clear());
        System.out.println("allowlist, null rules and immutable results: OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Wrong exception type", failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
