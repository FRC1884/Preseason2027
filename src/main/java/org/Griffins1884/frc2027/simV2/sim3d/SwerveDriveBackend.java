package org.Griffins1884.frc2027.simV2.sim3d;

import org.Griffins1884.frc2027.simV2.simulation.drivesims.SwerveModuleSimulation;

/** Planar drivetrain backend with swerve module access for encoder-style simulation consumers. */
public interface SwerveDriveBackend extends PlanarDriveBackend {
  SwerveModuleSimulation[] getModuleSimulations();
}
