package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.kinematics.ChassisVelocities;

/**
 * Stable robot-facing view of a drivetrain simulation.
 *
 * <p>Implementations may be backed by Maple, a GriffinSim-native 6DOF solver, or another simulation
 * engine. Consumers should use this interface rather than depending directly on the backend.
 */
public interface DriveSimulationAdapter {
  Pose2d getPose2d();

  Pose3d getPose3d();

  ChassisState3d getChassisState3d();

  TerrainContactSample getTerrainContactSample();

  SwerveTractionState getTractionState();

  ChassisVelocities getRobotRelativeChassisSpeeds();

  ChassisVelocities getFieldRelativeChassisSpeeds();

  SimImuSample getImuSample();

  TerrainSample getTerrainSample();

  DriveSimulationState getState();

  void resetPose(Pose2d pose);

  void resetState(Pose2d pose, ChassisVelocities robotRelativeSpeeds);
}
