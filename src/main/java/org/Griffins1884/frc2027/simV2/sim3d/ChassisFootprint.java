package org.Griffins1884.frc2027.simV2.sim3d;

/**
 * Simple robot footprint description for terrain and clearance checks.
 *
 * <p>Length and width represent the contact envelope on the field. Height is the highest point that
 * must clear under overhead structures. Ground clearance is the nominal minimum underbody clearance on
 * flat ground.
 */
public record ChassisFootprint(
    double lengthMeters, double widthMeters, double heightMeters, double groundClearanceMeters) {}
