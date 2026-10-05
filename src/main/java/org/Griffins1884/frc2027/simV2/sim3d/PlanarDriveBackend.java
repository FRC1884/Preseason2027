package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.Griffins1884.frc2027.simV2.simulation.drivesims.GyroSimulation;

/**
 * Abstraction for the planar drivetrain backend currently driving XY and yaw motion.
 *
 * <p>Phase 1 uses this boundary to decouple robot integration code from Maple-specific classes. A
 * later GriffinSim-native backend can implement the same contract while carrying richer 6DOF state.
 */
public interface PlanarDriveBackend {
  Pose2d getPose2d();

  ChassisVelocities getRobotRelativeChassisSpeeds();

  ChassisVelocities getFieldRelativeChassisSpeeds();

  GyroSimulation getGyroSimulation();

  void setPose(Pose2d pose);

  void setRobotRelativeChassisSpeeds(ChassisVelocities robotRelativeSpeeds);
}
