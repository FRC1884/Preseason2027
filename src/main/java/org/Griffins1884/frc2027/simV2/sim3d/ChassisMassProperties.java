package org.Griffins1884.frc2027.simV2.sim3d;

/**
 * Mass and geometry terms needed for simple load-transfer estimation.
 *
 * <p>These values are simulation-side approximations used for traction and wheel loading, not a full
 * rigid-body inertia model.
 */
public record ChassisMassProperties(
    double massKg,
    double centerOfGravityHeightMeters,
    double wheelBaseMeters,
    double trackWidthMeters,
    double nominalTireGripCoefficient) {}
