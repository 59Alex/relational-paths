# Architecture

## Purpose and context

An application with runtime metadata needs to explain how requested fields can be reached from a root model. The library converts that metadata into a deterministic, inspectable join tree. A report builder or API owns metadata visibility, authorization, SQL execution, and result handling.

```text
Application metadata + requested roles
                 |
                 v
     Schema -- JoinPlanner -- JoinPlan
                                  |
                        projections + filters
                                  |
                                  v
                             SqlRenderer
                                  |
                         SQL + bound values
                                  |
                                  v
                  Application JDBC / transaction
                                  |
                                  v
                              Database
```

This is a library inside the caller's process, not an independently deployed service. There is no persistence, network operation, background worker, global mutable state, or implicit transaction. The database's own optimizer remains responsible for execution plans.

## Boundaries and ownership

| Component | Owns | Does not own |
|---|---|---|
| `Schema` | Immutable role definitions, declared columns, named directed relationships | Metadata discovery, user access policy |
| `JoinPlanner` | Route search, ambiguity detection, consistency checks, bounded work | Database statistics, globally minimal join trees |
| `JoinPlan` | Root, dependency-ordered edges, aliases, per-target explanations | Selected data or execution state |
| `SqlRenderer` | Allowlisted identifier rendering, predicates, placeholders | Driver value conversion, connections, row limits |
| Caller | Metadata authorization, projections, filters, JDBC binding, transaction, timeouts | No responsibilities are transferred implicitly |

The Java 17 baseline uses an insertion-ordered `LinkedHashMap` for aliases and a sorted `TreeMap` for routes. Both are wrapped as unmodifiable snapshots, including immutable nested route lists; public accessors retain their `Map` signatures. [Java support](JAVA-SUPPORT.md) documents the supported LTS runtime matrix.

A role ID is independent of a physical table name. This preserves two foreign-key roles such as billing and shipping addresses. A relationship carries explicit equality column pairs, so composite keys remain intact.

## Planning sequence

1. Validate metadata once: unique roles and relationship IDs, known endpoints and columns, positive costs, no edge from a role to itself.
2. Validate requested roles and explicit-route target keys.
3. If any non-root target is automatic, run Dijkstra from the root. Distances use `long`; each role retains at most two witness paths. Positive edge weights ensure every predecessor is processed before its destination.
4. For each target in sorted role order, take its unique shortest path or validate the supplied explicit route. No path is an error; two equally cheap paths are an ambiguity error.
5. Merge paths by destination role. Different incoming relationships for the same role are a conflict, even if both are individually valid.
6. Emit joins in dependency order, breaking ties by relationship ID, and generate aliases `t0`, `t1`, etc. Preserve each target's full route for explanation.
7. Validate projected and filtered columns against the plan. Render quoted identifiers and placeholders, retaining filter values in positional binding order.

All output is constructed in local variables. A failure returns no partial plan. There is no database side effect to roll back.

## Invariants

- Every joined role is reachable from the root through its emitted parent, and appears once.
- Every equality references declared columns on that relationship's endpoints.
- Automatic routing never chooses silently between equal-cost alternatives.
- Explicit routes cannot revisit roles or introduce inconsistent parentage.
- Input iteration order never selects the winner or changes aliases.
- SQL text contains no filter values. SQL operator text comes from an enum; identifiers follow a restricted grammar and metadata allowlist.
- Metadata authorization remains external. A syntactically valid field is not necessarily a field the current user should be allowed to read.

## Decisions and trade-offs

**Dijkstra instead of all-path enumeration.** Runtime metadata can contain cycles and many path combinations. Positive weights and at most two witness routes avoid enumerating all alternatives. Distances and priority-queue traversal use the usual graph-search approach, but retaining copied witness paths adds memory and copying proportional to path length. In a chain, the retained complete witnesses use O(V²) references, rather than O(V + E) storage. This implementation intentionally caps metadata size and edge visits rather than claiming suitability for graphs with millions of nodes. Replace witness copies with compact predecessor chains if measured metadata sizes justify it.

**Ambiguity instead of an arbitrary tie-break.** The shorter route in hop count is not necessarily the intended business relationship. Equal total semantic costs require a choice. A caller can change metadata weights or supply an explicit path. The two witnesses are sufficient to explain ambiguity but are not a complete route-selection UI catalogue.

**Merge per-target shortest paths instead of a global optimizer.** This keeps route selection explainable and deterministic. It does not solve the Steiner-tree problem or optimize shared route cost across targets. Explicit routes provide control when common paths matter. Conflicting role meanings fail rather than silently duplicating a physical table or changing filters.

**Small SQL renderer instead of an ORM.** The initial renderer covers common metadata-driven reads without a runtime dependency. ANSI quoting and JDBC bindings are a narrow contract, not portability to every SQL dialect. Build another renderer over `JoinPlan` for dialect-specific features, outer joins, pagination, or jOOQ integration.

**Separate logical roles instead of unrestricted repeated nodes.** Cycles in metadata are safe to search, but one result plan is a tree of roles. Repeated uses of a physical table must be explicit in metadata. This avoids ambiguous column references; dynamic unbounded recursive joins are outside the scope.

## Failures and checks

| Failure | Observable result and recovery | Automated check |
|---|---|---|
| Missing path | `PlanningException`; register a meaningful directed edge or change targets | Disconnected/reverse traversal test |
| Equally cheap paths | `AmbiguousPathException` with two witnesses; choose an explicit route | Diamond, parallel-FK, unequal-hop and downstream ambiguity tests |
| Invalid explicit route | Exception before any plan; repair endpoint order or repeated role | Disconnected/end mismatch/cycle tests |
| Two routes disagree on one role | Exception; align routes or create distinct roles | Shared-destination conflict test |
| Over budget | Exception, never a partial result; tune limits or reduce authorized schema | Schema and edge-visit limit tests |
| Unknown or unsafe identifier | Validation exception; supply declared metadata | Identifier and column tests |
| Hostile filter text | Remains a bound value; no structural SQL change | Parameter isolation test |
| Metadata reordered | Same plan and aliases | 50 deterministic shuffle cases |
| Connection lost or SQL timeout | Handled by caller; this library has not opened a connection | Outside library test scope |

SQL tests assert exact rendered syntax and ordered bindings. They do not claim a live database integration test. There is no throughput benchmark or production latency SLA.

## What to know

Metadata determines semantic routing. The planner enforces deterministic, bounded, unambiguous choices. The renderer isolates values from SQL syntax. The caller still owns authorization, join cardinality consequences, database execution, transactions, and operational limits.
