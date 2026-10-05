package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.kinematics.ChassisVelocities;

/** Snapshot of the simulation state exposed to robot code and visualization. */
public record DriveSimulationState(
    Pose2d pose2d,
    Pose3d pose3d,
    ChassisState3d chassisState3d,
    TerrainContactSample terrainContactSample,
    SwerveTractionState tractionState,
    ChassisVelocities robotRelativeChassisSpeeds,
    ChassisVelocities fieldRelativeChassisSpeeds,
    SimImuSample imuSample,
    TerrainSample terrainSample) {}
