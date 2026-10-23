package io.github.alex59.relationalpaths;

/**
 * Hard bounds on schema size and Dijkstra edge relaxations per plan request.
 * The limit causes an exception, never a partial or arbitrarily selected route.
 * @param maxTables maximum logical table count
 * @param maxRelationships maximum relationship count
 * @param maxEdgeVisits maximum outgoing edges examined in one automatic search
 */
public record SearchLimits(int maxTables, int maxRelationships, int maxEdgeVisits) {
    /** Conservative defaults for runtime metadata used in interactive query builders. */
    public static final SearchLimits DEFAULT = new SearchLimits(512, 8192, 8192);
    /** Validates nonnegative limits and a positive table count.
     * @param maxTables maximum number of roles
     * @param maxRelationships maximum number of edges
     * @param maxEdgeVisits maximum edges examined by search
     */
    public SearchLimits {
        if (maxTables < 1 || maxRelationships < 0 || maxEdgeVisits < 0) {
            throw new IllegalArgumentException("Invalid search bounds");
        }
    }
}
