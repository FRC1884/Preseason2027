package org.Griffins1884.frc2027.simV2.sim3d.integration;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.kinematics.ChassisVelocities;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisState3d;
import org.Griffins1884.frc2027.simV2.sim3d.SwerveTractionState;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainContactSample;

/**
 * Generic simulation hooks for holonomic autonomous libraries.
 *
 * <p>These hooks map directly onto the suppliers and reset callbacks typically required by
 * pose-based path followers such as PathPlanner and Choreo integrations.
 */
public record HolonomicAutoHooks(
    Supplier<Pose2d> poseSupplier,
    Supplier<ChassisVelocities> robotRelativeChassisSpeedsSupplier,
    Consumer<Pose2d> poseResetter,
    Supplier<ChassisState3d> chassisState3dSupplier,
    Supplier<TerrainContactSample> terrainContactSupplier,
    Supplier<SwerveTractionState> tractionStateSupplier) {}
