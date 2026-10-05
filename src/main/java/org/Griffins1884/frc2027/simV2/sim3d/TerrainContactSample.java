package org.Griffins1884.frc2027.simV2.sim3d;

/**
 * Terrain and clearance sample for a specific robot footprint at a field pose.
 *
 * <p>This is the Phase 3 geometry handoff. It does not solve rigid-body contact yet, but it makes
 * the bump/trench/tunnel constraints explicit for later simulation backends.
 */
public record TerrainContactSample(
    TerrainSample terrainSample,
    TerrainFeature feature,
    double overheadClearanceMeters,
    double underbodyClearanceMarginMeters,
    double overheadClearanceMarginMeters,
    boolean traversableSurface,
    boolean clearanceSatisfied) {}
