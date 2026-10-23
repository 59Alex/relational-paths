package io.github.alex59.relationalpaths;

/** A route cannot be planned without changing metadata, targets, explicit paths, or limits. */
public class PlanningException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /** Creates or resolves the described metadata value.
     * @param message actionable planning failure
     */
    public PlanningException(String message) { super(message); }
}
