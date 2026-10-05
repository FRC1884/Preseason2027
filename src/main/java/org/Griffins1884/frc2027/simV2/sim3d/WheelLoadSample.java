package org.Griffins1884.frc2027.simV2.sim3d;

/** Per-wheel normal load and derived traction capacity estimate. */
public record WheelLoadSample(
    double normalForceNewtons, double tractionCapacityNewtons, double normalizedLoad) {}
