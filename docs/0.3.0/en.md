# relational-paths 0.3.0 — usage guide

[← Back to documentation and versions / Назад к документации и версиям](../index.md)

[English](en.md) · [Русский](ru.md)

[Versions](../index.md) · [README](../../README.md) · [Java features](java.en.md)

Turn runtime table metadata into deterministic, explainable JOIN plans. Resolve ambiguous relationships explicitly and render SELECT queries with bound values.

## Capabilities at a glance

| Capability | Use case | Example |
| --- | --- | --- |
| Install and run locally | For Java developers building query editors, reporting tools, or metadata-driven APIs. Start with JDK 17 or newer and a local build of version 0.3.0. | [Code and result](#install-and-run-locally) |
| Describe roles and relationships | Declare the physical tables, their logical roles, and allowed columns. Relationships are directed and include every equality column required by the join. | [Code and result](#describe-roles-and-relationships) |
| Plan weighted routes and inspect the result | Request every role needed by projections or filters. The planner chooses the unique minimum-cost route to each target and merges compatible shared prefixes. | [Code and result](#plan-weighted-routes-and-inspect-the-result) |
| Choose ambiguous links and use table roles | Two equally cheap routes can represent different meanings. The planner reports the ambiguity instead of silently selecting billing or shipping. | [Code and result](#choose-ambiguous-links-and-use-table-roles) |
| Render SELECT queries with bound values | SqlRenderer turns a validated plan into an INNER JOIN SELECT. Identifiers come from declared metadata; filter values remain separate from SQL text. | [Code and result](#render-select-queries-with-bound-values) |
| Limits, failures, and guarantees | The planner fails explicitly when it cannot produce a valid answer. It never returns a partial route merely to fit a configured search limit. | [Code and result](#limits-failures-and-guarantees) |
| Run the complete example and troubleshoot | Download Example.java and run it against the locally built JAR. It checks the documented routes, aliases, SQL, bindings, null behavior, limits, and immutable results without a database. | [Code and result](#run-the-complete-example-and-troubleshoot) |

## Install and run locally

For Java developers building query editors, reporting tools, or metadata-driven APIs. Start with JDK 17 or newer and a local build of version 0.3.0.

- Minimum Java 17. Supported LTS runtimes: 17, 21, and 25; Java 27 is also supported. No preview flags or runtime dependencies are required.
- The library has an MIT license. It plans joins and renders SQL; it does not connect to a database.

```bash
git clone https://github.com/59Alex/relational-paths.git
cd relational-paths
./gradlew build publishToMavenLocal
```

Build the checked-out version and publish it to your local Maven repository.

```text
build/libs/relational-paths-0.3.0.jar
build/libs/relational-paths-0.3.0-sources.jar
build/libs/relational-paths-0.3.0-javadoc.jar
```

Expected artifacts for version 0.3.0.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("io.github.59alex:relational-paths:0.3.0")
}
```

Add the locally published artifact to a consuming Gradle project.

> **Local installation**
>
> These instructions use mavenLocal. They do not claim that version 0.3.0 is published to Maven Central. If you use a different checkout version, keep the artifact version and file name consistent.

## Describe roles and relationships

Declare the physical tables, their logical roles, and allowed columns. Relationships are directed and include every equality column required by the join.

```java
import io.github.alex59.relationalpaths.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
```

Shared imports for every Java snippet below.

The following snippets are method-body statements. The schema, planning, rendering, null, and limit examples build on the variables introduced earlier. The downloadable Example.java combines them into a complete program.

```java
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
```

Declare three table roles and a two-column order-to-customer relationship.

```text
[customer, orders, region]
2
```

Expected output: sorted roles and two equality pairs in the composite key.

- Table.id is a logical role; Table.name is a physical table or schema.table. Multiple roles may refer to the same physical table.
- The customer_id/id and tenant_id/tenant_id pairs are both emitted into the JOIN condition. The library does not infer missing foreign-key columns.
- Duplicate IDs, unknown endpoints, undeclared relationship columns, and nonpositive costs fail during metadata construction.

## Plan weighted routes and inspect the result

Request every role needed by projections or filters. The planner chooses the unique minimum-cost route to each target and merges compatible shared prefixes.

```java
JoinPlan plan = new JoinPlanner(schema)
    .plan("orders", List.of("customer", "region"));
System.out.println(plan.routes().get("region"));
System.out.println(plan.aliases());
System.out.println(plan.joins().size());
System.out.println(plan.explain());
```

Continue with schema from the previous section.

```text
[order_customer, customer_region]
{orders=t0, customer=t1, region=t2}
2
ROOT orders AS t0
order_customer: orders -> customer AS t1 cost=1 on orders.customer_id = customer.id AND orders.tenant_id = customer.tenant_id
customer_region: customer -> region AS t2 cost=1 on customer.region_id = region.id
```

Expected routes, aliases, join count, and explanation.

- The two-hop customer route costs 1 + 1, while the direct order_region edge costs 5. Two shared joins cover both requested targets.
- Positive integer costs describe a business preference. They are not estimates of database execution time or query cardinality.
- Aliases and plans are deterministic regardless of metadata or target input order. Results and nested route lists are immutable.

> **Scope of optimization**
>
> The planner chooses per-target shortest paths. It is not a database optimizer and does not minimize the total cost of a shared join tree across all targets.

## Choose ambiguous links and use table roles

Two equally cheap routes can represent different meanings. The planner reports the ambiguity instead of silently selecting billing or shipping.

```java
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
```

An independent schema: inspect two witness routes, then explicitly choose shipping_ref.

```text
address
[[billing_ref], [shipping_ref]]
[shipping_ref]
```

Expected target, ambiguity witnesses, and selected route.

candidates() returns two deterministic witness paths, not every possible route. Explicit relationship-ID paths may be longer than the shortest path, but must start at the root, end at their target, and never repeat a logical role.

```java
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
```

To join both addresses, use two logical roles for the same physical table. This is a separate, independent schema.

```text
{orders=t0, billing=t1, shipping=t2}
```

Expected aliases: the same physical address table has two independent roles.

> **Conflicting routes**
>
> If paths to different targets enter the same role through different relationships, planning fails. Align the paths or model separate roles; the library will not silently duplicate a role or change its meaning.

## Render SELECT queries with bound values

SqlRenderer turns a validated plan into an INNER JOIN SELECT. Identifiers come from declared metadata; filter values remain separate from SQL text.

```java
BoundSql query = SqlRenderer.select(plan,
    List.of(new ColumnRef("orders", "id"), new ColumnRef("region", "name")),
    List.of(new Filter(new ColumnRef("customer", "name"),
        Filter.Operator.EQ, "Alice' OR 1=1 --")));
System.out.println(query.sql());
System.out.println(query.bindings());
```

Reuse plan from the weighted-route example. The hostile-looking string is deliberately treated as an ordinary value.

```sql
SELECT "t0"."id" AS "c0", "t2"."name" AS "c1" FROM "sales"."orders" AS "t0" INNER JOIN "sales"."customer" AS "t1" ON "t0"."customer_id" = "t1"."id" AND "t0"."tenant_id" = "t1"."tenant_id" INNER JOIN "sales"."region" AS "t2" ON "t1"."region_id" = "t2"."id" WHERE "t1"."name" = ?
```

Expected SQL. The output string is one line; selected columns have aliases c0 and c1.

```text
[Alice' OR 1=1 --]
```

Expected bindings. No part of this string is inserted into query.sql().

Execute query.sql() with your own JDBC PreparedStatement. Bind query.bindings() in list order, starting from JDBC index 1. Use driver-appropriate value conversion; never concatenate the values into SQL. This example does not open a database connection.

```java
BoundSql nullQuery = SqlRenderer.select(plan,
    List.of(new ColumnRef("customer", "id")),
    List.of(new Filter(new ColumnRef("customer", "name"), Filter.Operator.EQ, null)));
System.out.println(nullQuery.sql().endsWith("WHERE \"t1\".\"name\" IS NULL"));
System.out.println(nullQuery.bindings());
```

Reuse the same plan. EQ null renders IS NULL without a bound parameter.

```text
true
[]
```

Expected suffix check and empty bindings.

- Filters are AND-combined. Supported operators: EQ, NE, LT, LE, GT, GE. NE null becomes IS NOT NULL; ordered comparisons with null are rejected.
- Projected and filtered columns must be declared and their roles must be present in the plan. Names use ASCII identifiers; physical names are table or schema.table.

> **Authorization and SQL semantics**
>
> An allowlist prevents undeclared identifiers, but does not implement user permissions or row-level access control. INNER JOIN can exclude unmatched rows or multiply to-many results. The application owns authorization, transactions, and cardinality decisions.

## Limits, failures, and guarantees

The planner fails explicitly when it cannot produce a valid answer. It never returns a partial route merely to fit a configured search limit.

```java
try {
    new JoinPlanner(schema, new SearchLimits(10, 10, 0))
        .plan("orders", List.of("customer"));
    throw new AssertionError("Expected the edge-visit limit");
} catch (PlanningException error) {
    System.out.println(error.getMessage());
}
```

Continue with schema from the schema section. Zero allowed edge visits intentionally forces an error.

```text
Search exceeded maxEdgeVisits=0
```

Expected PlanningException message.

- Default bounds: 512 table roles, 8,192 relationships, and 8,192 edge visits per automatic search. Search examines every reachable role, even if the requested target is nearby.
- Dijkstra search retains at most two witness paths per role. Complete stored paths can consume O(V²) references in a chain; this is not a constant-memory planner.
- Metadata cycles are safe because weights are positive. An explicit route that revisits a logical role is rejected; use distinct roles for repeated physical tables.
- Schema, JoinPlanner, and JoinPlan are immutable and shareable across threads. Filter values and BoundSql binding values are retained by reference; supply immutable values or manage their lifecycle yourself.
- No database introspection, outer joins, aggregation, subqueries, query execution, or globally optimal join-tree solver is included. Another renderer can consume JoinPlan.

> **Failure types**
>
> AmbiguousPathException extends PlanningException. PlanningException extends IllegalArgumentException. Unknown tables or columns and invalid metadata also use IllegalArgumentException; mutation of immutable results throws UnsupportedOperationException.

## Run the complete example and troubleshoot

Download Example.java and run it against the locally built JAR. It checks the documented routes, aliases, SQL, bindings, null behavior, limits, and immutable results without a database.

Run these commands from the directory containing the downloaded Example.java. Replace /path/to/relational-paths with your checkout path. The commands below use Unix classpath separators; on Windows replace the colon in the classpath with a semicolon.

```bash
LIB=/path/to/relational-paths/build/libs/relational-paths-0.3.0.jar
mkdir -p /tmp/relational-paths-example
javac --release 17 -cp "$LIB" -d /tmp/relational-paths-example Example.java
java -cp "/tmp/relational-paths-example:$LIB" Example
```

Compile and run the downloadable file using JDK 17 or a supported newer JDK.

```text
allowlist, null rules and immutable results: OK
ALL CHECKS PASSED
```

Expected final lines. Earlier lines reproduce the outputs shown above.

```bash
./gradlew clean build -PtestJavaVersion=17
./gradlew clean build -PtestJavaVersion=21
./gradlew clean build -PtestJavaVersion=25
./gradlew clean build -PtestJavaVersion=27
```

Optional: run the library tests and its bundled example from the library checkout. Each selected JDK must be installed and discoverable by Gradle.

- Could not find io.github.59alex:relational-paths:0.3.0 → build and publishToMavenLocal the matching version, then add mavenLocal to the consumer.
- Ambiguous path to address → inspect candidates(), select an explicit relationship route, or separate billing and shipping roles.
- No directed path from ... → check relationship direction. Reverse traversal must be declared explicitly when meaningful.
- Table is absent from plan → add the role required by the projection or filter to the plan targets.
- Undeclared column → add an authorized column to metadata or correct the ColumnRef. Do not bypass access checks by accepting arbitrary identifiers.
- Search exceeded maxEdgeVisits → reduce the input schema or deliberately raise SearchLimits after checking memory and latency with your workload.

> **What was verified**
>
> The downloadable file was compiled with --release 17 and executed on a real Java 17 JVM against the 0.3.0 library JAR. It checks generated SQL strings and binding values; it does not execute a live database query or measure throughput.

## Downloadable examples

- [Example.java](examples/Example.java)

[MIT](../../LICENSE)

[← Back to documentation and versions / Назад к документации и версиям](../index.md)
