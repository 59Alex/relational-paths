package io.github.alex59.relationalpaths;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JoinPlannerTest {
    static Table table(String id) { return new Table(id, id, Set.of("id", "parent_id", "tenant_id")); }
    static Relationship edge(String id, String from, String to, int cost) {
        return new Relationship(id, from, to, cost, List.of(new ColumnPair("parent_id", "id")));
    }
    static Schema graph(List<Relationship> edges, String... ids) {
        return new Schema(Arrays.stream(ids).map(JoinPlannerTest::table).toList(), edges);
    }

    @Test void cheaperRouteWinsEvenWithMoreHopsAndCyclesTerminate() {
        Schema schema = graph(List.of(edge("direct", "a", "c", 10), edge("ab", "a", "b", 2),
                edge("bc", "b", "c", 2), edge("ca", "c", "a", 1)), "a", "b", "c");
        JoinPlan plan = new JoinPlanner(schema).plan("a", List.of("c"));
        assertEquals(List.of("ab", "bc"), plan.routes().get("c"));
        assertEquals(Map.of("a", "t0", "b", "t1", "c", "t2"), plan.aliases());
        assertTrue(plan.explain().contains("ab: a -> b AS t1 cost=2 on a.parent_id = b.id"));
    }

    @Test void diamondReportsTwoWitnessesAndRequiresChoice() {
        Schema schema = diamond();
        AmbiguousPathException failure = assertThrows(AmbiguousPathException.class,
                () -> new JoinPlanner(schema).plan("a", List.of("d")));
        assertEquals("d", failure.target());
        assertEquals(List.of(List.of("ab", "bd"), List.of("ac", "cd")), failure.candidates());
        JoinPlan chosen = new JoinPlanner(schema).plan("a", List.of("d"), Map.of("d", List.of("ac", "cd")));
        assertEquals(List.of("ac", "cd"), chosen.routes().get("d"));
    }

    @Test void equalCostDirectAndIndirectRoutesAreAmbiguousWithoutHopTieBreaker() {
        Schema schema = graph(List.of(edge("ab", "a", "b", 1), edge("bc", "b", "c", 1),
                edge("ac", "a", "c", 2)), "a", "b", "c");
        assertThrows(AmbiguousPathException.class, () -> new JoinPlanner(schema).plan("a", List.of("c")));
    }

    @Test void ambiguityPropagatesBeyondMergeAndRetainsOnlyTwoWitnesses() {
        List<Relationship> edges = new ArrayList<>(diamond().relationships().values());
        edges.add(edge("de", "d", "e", 1));
        AmbiguousPathException error = assertThrows(AmbiguousPathException.class,
                () -> new JoinPlanner(graph(edges, "a", "b", "c", "d", "e")).plan("a", List.of("e")));
        assertEquals(2, error.candidates().size());
        assertEquals(List.of("ab", "bd", "de"), error.candidates().get(0));
    }

    @Test void parallelForeignKeysAreNotCollapsed() {
        Schema schema = graph(List.of(edge("billing", "orders", "address", 1),
                edge("shipping", "orders", "address", 1)), "orders", "address");
        assertThrows(AmbiguousPathException.class, () -> new JoinPlanner(schema).plan("orders", List.of("address")));
        assertEquals("shipping", new JoinPlanner(schema).plan("orders", List.of("address"),
                Map.of("address", List.of("shipping"))).joins().get(0).id());
    }

    @Test void multiTargetSharesPrefixAndIgnoresInputOrdering() {
        List<Relationship> edges = new ArrayList<>(List.of(edge("ab", "a", "b", 1), edge("bc", "b", "c", 1), edge("bd", "b", "d", 1)));
        List<Table> tables = new ArrayList<>(List.of(table("a"), table("b"), table("c"), table("d")));
        String expected = new JoinPlanner(new Schema(tables, edges)).plan("a", List.of("d", "c", "a")).explain();
        Random random = new Random(77);
        for (int i = 0; i < 50; i++) {
            Collections.shuffle(tables, random); Collections.shuffle(edges, random);
            JoinPlan plan = new JoinPlanner(new Schema(tables, edges)).plan("a", List.of("c", "a", "d", "c"));
            assertEquals(3, plan.joins().size());
            assertEquals(expected, plan.explain());
        }
    }

    @Test void compatibleSeparateBranchesAreAllowed() {
        JoinPlan plan = new JoinPlanner(diamond()).plan("a", List.of("b", "d"),
                Map.of("b", List.of("ab"), "d", List.of("ac", "cd")));
        assertEquals(3, plan.joins().size());
    }

    @Test void routeConflictAtSharedDestinationFails() {
        List<Relationship> edges = new ArrayList<>(diamond().relationships().values());
        edges.add(edge("de", "d", "e", 1));
        Schema schema = graph(edges, "a", "b", "c", "d", "e");
        assertThrows(PlanningException.class, () -> new JoinPlanner(schema).plan("a", List.of("d", "e"),
                Map.of("d", List.of("ab", "bd"), "e", List.of("ac", "cd", "de"))));
    }

    @Test void invalidRoutesUnknownTargetsAndUnreachableTargetsFailClearly() {
        JoinPlanner planner = new JoinPlanner(diamond());
        assertThrows(PlanningException.class, () -> planner.plan("d", List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> planner.plan("a", List.of("unknown")));
        assertThrows(PlanningException.class, () -> planner.plan("a", List.of("d"), Map.of("d", List.of("bd"))));
        assertThrows(PlanningException.class, () -> planner.plan("a", List.of("d"), Map.of("d", List.of("ab"))));
        assertThrows(PlanningException.class, () -> planner.plan("a", List.of("b"), Map.of("d", List.of("ab", "bd"))));
    }

    @Test void explicitCyclesAreRejectedAndRootOnlyPlanWorks() {
        Schema schema = graph(List.of(edge("ab", "a", "b", 1), edge("ba", "b", "a", 1)), "a", "b", "unused");
        JoinPlanner planner = new JoinPlanner(schema);
        assertThrows(PlanningException.class, () -> planner.plan("a", List.of("a"), Map.of("a", List.of("ab", "ba"))));
        assertEquals(List.of(), planner.plan("a", List.of("a")).joins());
        assertEquals(List.of(), planner.plan("a", List.of()).joins());
    }

    @Test void limitsFailClosedAndDoNotReturnPartialPlans() {
        assertThrows(PlanningException.class, () -> new JoinPlanner(diamond(), new SearchLimits(3, 20, 20)));
        JoinPlanner limited = new JoinPlanner(diamond(), new SearchLimits(10, 20, 1));
        assertThrows(PlanningException.class, () -> limited.plan("a", List.of("b")));
        assertEquals(2, limited.plan("a", List.of("d"), Map.of("d", List.of("ab", "bd"))).joins().size());
    }

    @Test void metadataAndResultsCannotBeMutated() {
        Schema schema = diamond();
        JoinPlan plan = new JoinPlanner(schema).plan("a", List.of("b"));
        assertThrows(UnsupportedOperationException.class, () -> schema.tables().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.routes().get("b").clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.aliases().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.joins().clear());
    }

    @Test void invalidMetadataFailsBeforePlanning() {
        assertThrows(IllegalArgumentException.class, () -> graph(List.of(edge("ab", "a", "b", 1)), "a"));
        assertThrows(IllegalArgumentException.class, () -> edge("ab", "a", "b", 0));
        assertThrows(IllegalArgumentException.class, () -> edge("self", "a", "a", 1));
        assertThrows(IllegalArgumentException.class, () -> new Schema(List.of(table("a"), table("a")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> graph(List.of(edge("same", "a", "b", 1), edge("same", "a", "b", 2)), "a", "b"));
        assertThrows(IllegalArgumentException.class, () -> new Schema(List.of(table("a"), table("b")),
                List.of(new Relationship("bad", "a", "b", 1, List.of(new ColumnPair("missing", "id"))))));
    }

    @Test void shortestPathsAndAmbiguityMatchExhaustiveOracleOnSmallRandomGraphs() {
        Random random = new Random(311);
        for (int sample = 0; sample < 100; sample++) {
            List<Relationship> edges = new ArrayList<>();
            for (int from = 0; from < 6; from++) {
                for (int to = 0; to < 6; to++) {
                    if (from != to && random.nextDouble() < 0.35) {
                        edges.add(edge("e" + from + "_" + to, "n" + from, "n" + to, 1 + random.nextInt(4)));
                    }
                }
            }
            Schema schema = graph(edges, "n0", "n1", "n2", "n3", "n4", "n5");
            JoinPlanner planner = new JoinPlanner(schema);
            for (int t = 1; t < 6; t++) {
                String target = "n" + t;
                List<List<Relationship>> all = new ArrayList<>();
                enumerateSimplePaths(schema, "n0", target, new HashSet<>(Set.of("n0")), new ArrayList<>(), all);
                if (all.isEmpty()) {
                    assertThrows(PlanningException.class, () -> planner.plan("n0", List.of(target)));
                    continue;
                }
                int minimum = all.stream().mapToInt(path -> path.stream().mapToInt(Relationship::cost).sum()).min().orElseThrow();
                List<List<Relationship>> best = all.stream()
                        .filter(path -> path.stream().mapToInt(Relationship::cost).sum() == minimum).toList();
                if (best.size() > 1) {
                    AmbiguousPathException failure = assertThrows(AmbiguousPathException.class,
                            () -> planner.plan("n0", List.of(target)));
                    List<List<String>> valid = best.stream().map(path -> path.stream().map(Relationship::id).toList()).toList();
                    assertEquals(2, failure.candidates().size());
                    assertTrue(valid.containsAll(failure.candidates()));
                } else {
                    assertEquals(best.get(0).stream().map(Relationship::id).toList(),
                            planner.plan("n0", List.of(target)).routes().get(target));
                }
            }
        }
    }

    private static void enumerateSimplePaths(Schema schema, String current, String target, Set<String> visited,
                                             List<Relationship> path, List<List<Relationship>> result) {
        if (current.equals(target)) { result.add(List.copyOf(path)); return; }
        for (Relationship edge : schema.relationships().values()) {
            if (edge.from().equals(current) && visited.add(edge.to())) {
                path.add(edge);
                enumerateSimplePaths(schema, edge.to(), target, visited, path, result);
                path.remove(path.size() - 1);
                visited.remove(edge.to());
            }
        }
    }

    static Schema diamond() {
        return graph(List.of(edge("ab", "a", "b", 1), edge("ac", "a", "c", 1),
                edge("bd", "b", "d", 1), edge("cd", "c", "d", 1)), "a", "b", "c", "d");
    }
}
