package io.github.alex59.relationalpaths;

import java.util.List;

/** Two or more equal-cost paths exist. Supply a relationship-ID route to resolve the choice. */
public final class AmbiguousPathException extends PlanningException {
    private static final long serialVersionUID = 1L;
    /** Ambiguous target.
     * @serial ambiguous target role
     */
    private final String target;
    /** Diagnostic witnesses.
     * @serial two witness routes
     */
    private final List<List<String>> candidates;
    AmbiguousPathException(String target, List<List<String>> candidates) {
        super("Ambiguous path to " + target + ": " + candidates + "; supply an explicit relationship route");
        this.target = target;
        this.candidates = candidates.stream().map(List::copyOf).toList();
    }
    /** Returns the described immutable value.
     * @return ambiguous target role
     */
    public String target() { return target; }
    /** Returns the described immutable value.
     * @return two deterministic witness paths, not an exhaustive list
     */
    public List<List<String>> candidates() { return candidates; }
}
