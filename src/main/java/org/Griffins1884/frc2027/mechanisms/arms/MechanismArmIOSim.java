package org.Griffins1884.frc2027.mechanisms.arms;

import org.wpilib.math.util.MathUtil;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.util.Units;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.simulation.SingleJointedArmSim;

public class MechanismArmIOSim implements MechanismArmIO {
  private SingleJointedArmSim sim;

  private double appliedVolts = 0.0;
  private double positionOffset = 0.0;

  public MechanismArmIOSim(int numMotors, double startingAngle) {
    sim =
        new SingleJointedArmSim(
            DCMotor.getNeoVortex(numMotors),
            (10 / Units.metersToInches(0.012) / 0.5),
            1,
            0.3126232,
            0,
            Units.degreesToRadians(110),
            true,
            startingAngle);
  }

  @Override
  public void updateInputs(MechanismArmIOInputs inputs) {
    if (RobotState.isDisabled()) {
      sim.setInputVoltage(appliedVolts);
    }
    sim.update(0.02);
    inputs.encoderPosition = sim.getAngle() + positionOffset;
    inputs.velocity = sim.getVelocity();
    inputs.appliedVoltage = appliedVolts;
    inputs.supplyCurrentAmps = sim.getCurrentDraw();
    inputs.torqueCurrentAmps = inputs.supplyCurrentAmps;
    if (inputs.connected.length != 1) {
      inputs.connected = new boolean[] {true};
    } else {
      inputs.connected[0] = true;
    }
  }

  @Override
  public void setVoltage(double volts) {
    appliedVolts = Math.clamp(volts, -12.0, 12.0);
    sim.setInputVoltage(appliedVolts);
  }

  @Override
  public void setPosition(double position) {
    positionOffset = position - sim.getAngle();
  }
}
