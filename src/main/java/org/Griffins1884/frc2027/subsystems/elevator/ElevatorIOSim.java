package org.Griffins1884.frc2027.subsystems.elevator;

import org.wpilib.math.system.DCMotor;
import org.wpilib.simulation.ElevatorSim;

public class ElevatorIOSim implements ElevatorIO {
  private ElevatorSim sim;
  private double appliedVoltsDemand = 0.0;
  public ElevatorIOSim(
      double kV,
      double kA,
      DCMotor gearbox,
      double MIN_HEIGHT,
      double MAX_HEIGHT,
      double startingHeight,
      boolean simulateGravity) {
    sim =
        new ElevatorSim(
            kV,
            kA,
            gearbox,
            MIN_HEIGHT,
            MAX_HEIGHT,
            simulateGravity,
            startingHeight,0);
  }

  public void updateInputs(ElevatorIOInputs inputs) {
    double clampedVolts = Math.clamp(appliedVoltsDemand, -12.0, 12.0);
    sim.setInputVoltage(clampedVolts);
    inputs.positionMeters = sim.getPosition();
    inputs.velocityMeterPerSecond = sim.getVelocity();
    inputs.appliedVolts = clampedVolts;
    inputs.statorCurrentAmps = Math.abs(sim.getCurrentDraw());
    inputs.supplyCurrentAmps = Math.abs(sim.getCurrentDraw());

    inputs.atLowerLimit = (inputs.positionMeters <= ElevatorConstants.minHeight);
    inputs.atUpperLimit = (inputs.positionMeters >= ElevatorConstants.maxHeight);
  }

    public void setVoltage(double volts) {
        appliedVoltsDemand = volts;
    }

    public void stopActuators() {
        setVoltage(0.0);
    }
}
