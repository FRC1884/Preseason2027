package org.Griffins1884.frc2027.simV2.sim3d.maple;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.Griffins1884.frc2027.simV2.sim3d.SwerveDriveBackend;
import org.Griffins1884.frc2027.simV2.simulation.drivesims.GyroSimulation;
import org.Griffins1884.frc2027.simV2.simulation.drivesims.SwerveDriveSimulation;
import org.Griffins1884.frc2027.simV2.simulation.drivesims.SwerveModuleSimulation;

/** Maple-backed implementation of the GriffinSim planar drivetrain backend contract. */
public final class MapleSwerveDriveBackend implements SwerveDriveBackend {
  private final SwerveDriveSimulation mapleSimulation;

  public MapleSwerveDriveBackend(SwerveDriveSimulation mapleSimulation) {
    this.mapleSimulation = mapleSimulation;
  }

  public SwerveDriveSimulation mapleSimulation() {
    return mapleSimulation;
  }

  @Override
  public Pose2d getPose2d() {
    return mapleSimulation.getSimulatedDriveTrainPose();
  }

  @Override
  public ChassisVelocities getRobotRelativeChassisSpeeds() {
    return mapleSimulation.getDriveTrainSimulatedChassisVelocitiesRobotRelative();
  }

  @Override
  public ChassisVelocities getFieldRelativeChassisSpeeds() {
    return mapleSimulation.getDriveTrainSimulatedChassisVelocitiesFieldRelative();
  }

  @Override
  public GyroSimulation getGyroSimulation() {
    return mapleSimulation.getGyroSimulation();
  }

  @Override
  public void setPose(Pose2d pose) {
    mapleSimulation.setSimulationWorldPose(pose);
  }

  @Override
  public void setRobotRelativeChassisSpeeds(ChassisVelocities robotRelativeSpeeds) {
    mapleSimulation.setRobotVelocities(robotRelativeSpeeds);
  }

  @Override
  public SwerveModuleSimulation[] getModuleSimulations() {
    return mapleSimulation.getModules();
  }
}
