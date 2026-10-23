# relational-paths

**Explainable JOIN paths from runtime relational metadata.**

Build query editors, reporting tools, and metadata-driven APIs without spreading foreign-key navigation rules across application code. Describe logical table roles and their relationships once; request the roles needed by a query and inspect the resulting join plan.

- Finds minimum semantic-cost directed routes without enumerating every path combination.
- Reports equal-cost alternatives instead of silently choosing one; accepts explicit relationship routes.
- Preserves multiple foreign keys, composite keys, and separate roles for one physical table.
- Produces deterministic plans, aliases, and route explanations.
- Includes a small SQL renderer with column allowlists and bound filter values.
- Java 17+, no runtime dependencies, [MIT licensed](LICENSE).

This is a semantic path planner, not a database cost optimizer. It never opens a connection or executes a query.

## Quick start

```java
import io.github.alex59.relationalpaths.*;
import java.util.List;
import java.util.Set;

Schema schema = new Schema(List.of(
    new Table("orders", "sales.orders",
        Set.of("id", "customer_id", "tenant_id")),
    new Table("customer", "sales.customer",
        Set.of("id", "tenant_id", "name"))
), List.of(
    new Relationship("order_customer", "orders", "customer", 1, List.of(
        new ColumnPair("customer_id", "id"),
        new ColumnPair("tenant_id", "tenant_id")))
));

JoinPlan plan = new JoinPlanner(schema).plan("orders", List.of("customer"));
System.out.println(plan.explain());

BoundSql query = SqlRenderer.select(plan,
    List.of(new ColumnRef("orders", "id"), new ColumnRef("customer", "name")),
    List.of(new Filter(new ColumnRef("customer", "name"),
        Filter.Operator.EQ, "Alice")));
```

The plan explains the actual relationship and equality predicates:

```text
ROOT orders AS t0
order_customer: orders -> customer AS t1 cost=1 on orders.customer_id = customer.id AND orders.tenant_id = customer.tenant_id
```

`query.sql()` contains placeholders; `query.bindings()` contains `List.of("Alice")`:

```sql
SELECT "t0"."id" AS "c0", "t1"."name" AS "c1"
FROM "sales"."orders" AS "t0"
INNER JOIN "sales"."customer" AS "t1"
  ON "t0"."customer_id" = "t1"."id"
 AND "t0"."tenant_id" = "t1"."tenant_id"
WHERE "t1"."name" = ?
```

Execute with your own connection and transaction management:

```java
try (var statement = connection.prepareStatement(query.sql())) {
    for (int i = 0; i < query.bindings().size(); i++) {
        statement.setObject(i + 1, query.bindings().get(i));
    }
    try (var rows = statement.executeQuery()) {
        while (rows.next()) {
            System.out.println(rows.getObject("c0"));
        }
    }
}
```

Use JDBC-compatible values and any driver-specific binding required by your database. Never interpolate the bindings into SQL. The renderer uses ANSI double-quoted identifiers and JDBC `?` placeholders; PostgreSQL accepts this syntax. Other dialects may need a custom renderer over `JoinPlan`.

## Resolve ambiguity deliberately

If two foreign keys lead from `orders` to `address` with the same cost, automatic planning throws `AmbiguousPathException`. Its `target()` and `candidates()` identify the ambiguous role and two witness routes. The list is intentionally not exhaustive.

Choose a route by relationship IDs:

```java
JoinPlan plan = planner.plan("orders", List.of("address"),
    java.util.Map.of("address", List.of("order_shipping_address")));
```

Explicit routes must start at the root, end at the named target, and visit each logical role at most once. A longer explicit route is allowed. To join both billing and shipping addresses, register two `Table` roles with different IDs and the same physical name, then give each role its own relationship. The plan assigns separate aliases.

Routes for multiple targets share their common prefixes. Conflicting routes into the same role are rejected; the caller must choose consistent routes or introduce separate roles. The planner does not minimize the total cost of the resulting join tree across all targets.

## Contracts and boundaries

| Concern | Contract |
|---|---|
| Direction | Relationships are directed. Register a reverse edge explicitly when it is meaningful. |
| Costs | Positive integers express semantic preference. Equal total cost is ambiguous even when hop counts differ. |
| Determinism | Metadata and target input order do not change the plan. Projection and filter order are retained. |
| Roles | One occurrence per logical role in a plan. Use distinct roles for self-joins and repeated physical tables. |
| Search | Dijkstra with at most two witness paths per role; schema-size and edge-visit limits fail closed. |
| SQL | INNER JOIN, composite equality, explicit projections, AND-connected EQ/NE/LT/LE/GT/GE comparisons. |
| Nulls | EQ null renders IS NULL; NE null renders IS NOT NULL; ordered comparisons with null are rejected. |
| Identifiers | ASCII identifiers only; physical names are table or schema.table. All columns must be declared. |
| Concurrency | Metadata, planner, and plans are immutable and shareable. Bound values are retained by reference. |
| Authorization | The application decides which metadata and columns a user may access. Validation is not row-level authorization. |

Include roles required by filters as targets, as well as those needed for projections. An unjoined column is an error. INNER JOIN can exclude unmatched rows and multiply rows across to-many relations; the library does not infer cardinality, deduplicate results, or change join semantics to hide that behavior.

Not included: schema introspection, outer joins, aggregation, subqueries, arbitrary SQL fragments, query execution, pagination, or a fluent ORM. `JoinPlan` is the extension point for another renderer.

The defaults allow 512 roles, 8,192 relationships, and 8,192 edge visits per automatic search. Override them with `new JoinPlanner(schema, new SearchLimits(...))`. Search examines every reachable role, so a small target can still hit the configured limit on a large graph. Two witness paths bound ambiguity reporting; limits do not truncate a result.

## Build and use locally

Use JDK 17 or newer:

```sh
./gradlew build
./gradlew example
./gradlew publishToMavenLocal
```

The build produces the library, source, and Javadoc JARs and runs [the executable report example](src/examples/java/io/github/alex59/relationalpaths/examples/ReportQuery.java). To use the locally published artifact:

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies { implementation("io.github.59alex:relational-paths:0.1.0") }
```

These are local installation instructions, not a claim that this version is available from Maven Central.

Tests cover path ambiguity, positive-cost cycles, parallel foreign keys, deterministic plans, explicit-route conflicts, multiple targets, hard limits, composite joins, aliases, nulls, and SQL parameter isolation. See [architecture and failure behavior](docs/architecture.md) for the design and its trade-offs.

## License

[MIT](LICENSE), copyright 2026 Aleksandr Kashevarov. The Gradle Wrapper retains its upstream Apache-2.0 notices; see [third-party notices](THIRD_PARTY_NOTICES.md).
