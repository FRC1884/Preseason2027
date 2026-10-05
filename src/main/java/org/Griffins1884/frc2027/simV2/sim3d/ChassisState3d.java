package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Translation3d;

/**
 * Backend-owned 3D chassis snapshot.
 *
 * <p>This becomes the stable state handoff between the underlying simulation engine and higher-level
 * robot integration code.
 */
public record ChassisState3d(
    Pose3d pose,
    Translation3d fieldRelativeLinearVelocityMetersPerSec,
    Translation3d fieldRelativeLinearAccelerationMetersPerSecSq,
    AngularVelocity3d angularVelocityRadPerSec) {}
