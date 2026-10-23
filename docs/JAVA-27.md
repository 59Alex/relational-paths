# Historical Java 27 baseline — superseded in 0.3.0

> Historical document for version 0.2.0 only. Version **0.3.0 restores Java 17 as the minimum** and supports LTS runtimes 17, 21, and 25. Do not apply the Java 27 upgrade instructions below to the current version. Use [Java support and migration](JAVA-SUPPORT.md) instead. The sequenced-collection and instance-main implementation described here was replaced with Java 17-compatible equivalents.

Version 0.2.0 requires **Java 27 or newer** to build and run. Preview and incubator features are not enabled. This is a runtime baseline change from the Java 17 target of version 0.1.0; upgrading the dependency requires upgrading the application runtime as well.

## Adopted stable features

The APIs below became stable before Java 27. They are used where they express an existing invariant more clearly, rather than being presented as features introduced by Java 27.

| Feature | Use in this library | Reason |
|---|---|---|
| `SequencedMap` and `putLast` | Internal alias construction in `JoinPlan` | Makes the root-first, then dependency-ordered join sequence explicit. |
| `Collections.unmodifiableSequencedMap` | Alias and route snapshots | Preserves encounter order and prevents mutation through ordinary, reversed, and sequenced views. |
| `List.getFirst` | Selecting the single shortest-path witness | Expresses the nonempty-path invariant after unreachable and ambiguous cases have been handled. |
| `HashMap.newHashMap` | Per-search node state | Uses the known upper bound of schema roles as the expected mapping count, without manual load-factor arithmetic. It is not a throughput claim. |
| Instance `main` and `IO.println` | Executable `ReportQuery` example | Keeps the example focused on metadata, planning, and rendered SQL instead of static launcher boilerplate. |

Records already express immutable table, relationship, column, filter, and SQL values. They remain ordinary stable records; there is no need to add pattern matching or concurrency to a synchronous graph walk.

The public signatures of `JoinPlan.aliases()` and `JoinPlan.routes()` still return `Map`. Existing callers can keep their code and rely on the documented iteration order; a `SequencedMap` cast is not necessary. The internal representation does not introduce a new mutable view.

## Migration

1. Install JDK 27 for development, CI, and production. Set `JAVA_HOME` consistently and check `java --version` and `./gradlew --version`.
2. Use the repository's Gradle wrapper. Build with `./gradlew clean build`; the `check` task runs the executable report example as well as the tests.
3. Upgrade locally published coordinates to `io.github.59alex:relational-paths:0.2.0`. The README describes local installation; it does not assert availability in Maven Central.
4. Rebuild the consuming application and update its runtime image. A Java 17/21/25/26 runtime cannot load a Java 27-targeted artifact.
5. Keep existing metadata and query tests. This update does not change positive relationship costs, ambiguity handling, alias order, explicit route validation, composite-key rendering, or positional binding order.

No `--enable-preview`, `--add-modules`, or native-access flag is required. The library has no native dependency or Java agent.

## Runtime boundaries

The baseline change does not make the planner asynchronous or parallel. There is no blocking I/O inside it, so virtual threads or structured concurrency would not improve its contract. The application still owns database connections, JDBC execution, transaction policy, and timeouts.

There are no new collector or JVM tuning requirements. Choose GC and heap settings for the consuming application and measure with its actual metadata. The existing limits still apply: schema-size and edge-visit bounds fail closed, and storing complete witness paths can retain O(V²) references in a chain. A newer JVM does not remove that algorithmic memory bound.

## Verification

The existing graph tests include exhaustive-oracle comparison on 100 small random graphs and 500 root/target combinations. SQL tests check exact statements, binding order, composite keys, role aliases, null handling, and hostile value isolation. The added view test covers encounter order and rejection of mutations through a reversed map and sequenced entries. `./gradlew example` verifies that the instance-main example launches through the Java launcher.

These checks do not establish a live-database integration result or a performance improvement.

## API references

- [SequencedMap encounter order and reverse views](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/SequencedMap.html)
- [HashMap expected-size factory](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/HashMap.html#newHashMap(int))
- [Java IO line-oriented output](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/IO.html)
