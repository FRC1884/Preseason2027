package org.Griffins1884.frc2027.simulation.shooter;

import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Translation3d;

/** Solved field-space shot data used for prediction and projectile spawning. */
public record SimulatedShot(
    boolean feasible,
    Pose3d releasePose,
    Translation3d initialVelocityMetersPerSecond,
    Pose3d[] predictedSamplePoses,
    Pose3d predictedImpactPose,
    double closestApproachErrorMeters,
    double timeOfFlightSeconds) {}
