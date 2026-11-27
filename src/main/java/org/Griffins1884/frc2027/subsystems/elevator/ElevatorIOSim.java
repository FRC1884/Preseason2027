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
      double minHeight,
      double maxHeight,
      double startingHeight,
      boolean simulateGravity) {
    sim = new ElevatorSim(kV, kA, gearbox, minHeight, maxHeight, simulateGravity, startingHeight);
  }

  public ElevatorIOSim() {
    this(
        ElevatorConstants.kV.getAsDouble(),
        ElevatorConstants.kA.getAsDouble(),
        DCMotor.getKrakenX60(2),
        ElevatorConstants.MIN_HEIGHT,
        ElevatorConstants.MAX_HEIGHT,
        ElevatorConstants.MIN_HEIGHT,
        true);
  }

  public double getPosition() {
    return sim.getPosition();
  }

  public void updateInputs(ElevatorIOInputs inputs) {
    double clampedVolts = Math.clamp(appliedVoltsDemand, -12.0, 12.0);
    sim.setInputVoltage(clampedVolts);
    inputs.positionMeters = sim.getPosition();
    inputs.velocityMeterPerSecond = sim.getVelocity();
    inputs.appliedVolts = clampedVolts;
    inputs.statorCurrentAmps = Math.abs(sim.getCurrentDraw());
    inputs.supplyCurrentAmps = Math.abs(sim.getCurrentDraw());
    sim.update(0.02);
  }

  public void setVoltage(double volts) {
    appliedVoltsDemand = volts;
  }

  public void stopActuators() {
    setVoltage(0.0);
  }
}
