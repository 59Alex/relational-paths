# Java support

[← Back to documentation and versions / Назад к документации и версиям](index.md)

Version **0.3.0 requires Java 17 or newer**. The supported and tested LTS matrix is **Java 17, Java 21, and Java 25**; **Java 27** is also supported as an additional runtime. The same library artifact is used on all four runtimes; it is not a multi-release JAR.

| Runtime | Support | Artifact target |
|---|---|---|
| Java 17 LTS | Minimum supported runtime | Java 17, class-file major version 61 |
| Java 21 LTS | Supported runtime | The same Java 17-targeted artifact |
| Java 25 LTS | Supported runtime | The same Java 17-targeted artifact |
| Java 27 | Additional supported runtime | The same Java 17-targeted artifact |

Build compilation uses `--release 17` for main code, tests, and the executable example. This checks both language syntax and the Java API surface: compiling with a newer JDK does not permit accidental use of APIs absent from Java 17. Preview and incubator features are not enabled.

Java 27 support does not restore the earlier minimum-Java-27 requirement. Both compilation target and published JVM metadata remain Java 17; newer APIs are not required by the library.

## Preserved behavior

- `JoinPlan.aliases()` remains an immutable `Map` with the root first, then joins in dependency order. Internally, a `LinkedHashMap` preserves that encounter order.
- `JoinPlan.routes()` remains an immutable `Map` sorted by target role. A `TreeMap` provides the order, and each nested route list is copied and immutable.
- The map snapshots cannot be changed through entry, key, or value views. Newer sequenced-map APIs are not required or exposed as part of the contract.
- Dijkstra search, positive costs, two ambiguity witnesses, explicit-route checks, composite keys, SQL allowlists, and positional parameter binding retain their existing semantics.
- The report example uses the conventional `public static void main(String[] args)` entry point and runs on every supported JDK.

The compatibility change does not discard bug fixes or reduce graph and SQL test coverage. Records and other stable Java 17 features remain in use. There is no reason to add virtual threads or other newer APIs to an in-memory synchronous graph search.

## Test a specific runtime

Use the repository's Gradle wrapper. Install the selected JDK and make it discoverable to Gradle, or set `JAVA_HOME` to its installation. Select the actual test/example launcher with the project property:

```sh
./gradlew clean build -PtestJavaVersion=17
./gradlew clean build -PtestJavaVersion=21
./gradlew clean build -PtestJavaVersion=25
./gradlew clean build -PtestJavaVersion=27
```

Each `build` runs the graph and SQL tests and launches the report example. Changing `testJavaVersion` selects the runtime used to execute those checks; it does not raise the compiled artifact's Java 17 target. The CI workflow defines the same LTS matrix plus Java 27.

There is no requirement for `--enable-preview`, `--add-modules`, a Java agent, or native-access flags. The library itself has no runtime dependencies.

## Migrate to 0.3.0

1. Use Java 17, 21, 25, or 27 for your application runtime and build environment.
2. Update the dependency to `io.github.59alex:relational-paths:0.3.0` and rebuild your application. The README documents local publication; these coordinates are not a claim of Maven Central availability.
3. Keep existing metadata and query assertions. There are no public method-signature or SQL contract changes in this compatibility update.
4. If you used the Java 27-only 0.2.0 release, replace that artifact entirely. Its compiled classes cannot run on Java 17/21/25. The former baseline notes are retained as [historical documentation](JAVA-27.md), not current setup instructions.

The public return type remains `Map`; casts to a particular collection implementation were never part of the API. Consumers should use ordinary `Map` iteration and the documented encounter order.

## Operational limits

A lower bytecode target changes compatibility, not planning guarantees or execution strategy. Search bounds still fail closed, complete witness paths can retain O(V²) references in a chain, and SQL execution remains the application's responsibility. Choose heap, GC, transaction, and timeout settings for the consuming application and verify them against its workload.

The runtime matrix checks compatibility. It does not establish a live-database integration result, production latency SLA, or performance gain on any particular JDK.

[← Back to documentation and versions / Назад к документации и версиям](index.md)
