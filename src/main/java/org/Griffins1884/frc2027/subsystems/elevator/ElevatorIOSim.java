package org.Griffins1884.frc2027.subsystems.elevator;

import org.wpilib.math.system.DCMotor;
import org.wpilib.simulation.ElevatorSim;

public class ElevatorIOSim implements ElevatorIO {

  public ElevatorIOSim(
      double kV,
      double kA,
      DCMotor gearbox,
      double minHeight,
      double maxHeight,
      double maxVelocity,
      double startingHeight,
      boolean simulateGravity) {
    ElevatorSim sim =
        new ElevatorSim(
            gearbox,
            minHeight,
            maxHeight,
            maxVelocity,
            startingHeight,
            simulateGravity);
  }

  public void updateInputs(ElevatorIOInputs inputs) {}
}
