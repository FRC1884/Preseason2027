package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.kinematics.ChassisVelocities;

/**
 * SIM-only extension for drive simulations that can be commanded each update.
 *
 * <p>This keeps the stable read/reset adapter intact while giving Maple-backed and GriffinSim-native
 * backends a shared write-side seam for chassis-speed commands.
 */
public interface CommandableDriveSimulationAdapter extends DriveSimulationAdapter {
  void setCommandedRobotRelativeSpeeds(ChassisVelocities robotRelativeSpeeds);
}
