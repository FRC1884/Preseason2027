package org.Griffins1884.frc2027.mechanisms.rollers;

import static org.wpilib.math.system.LinearSystemId.createDCMotorSystem;

import org.wpilib.math.util.MathUtil;
import org.wpilib.math.system.DCMotor;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.simulation.DCMotorSim;

public class MechanismRollerIOSim implements MechanismRollerIO {
  private final DCMotorSim sim;
  private double appliedVoltage = 0.0;

  public MechanismRollerIOSim(DCMotor motorModel, double reduction, double moi) {
    sim = new DCMotorSim(createDCMotorSystem(motorModel, reduction, moi), motorModel);
  }

  @Override
  public void updateInputs(MechanismRollerIOInputs inputs) {
    if (RobotState.isDisabled()) {
      runVolts(appliedVoltage);
    }

    sim.update(0.02);
    if (inputs.connected.length != 1) {
      inputs.connected = new boolean[] {true};
    } else {
      inputs.connected[0] = true;
    }
    inputs.positionRads = sim.getAngularPositionRad();
    inputs.velocityRadsPerSec = sim.getAngularVelocityRadPerSec();
    inputs.velocity = sim.getAngularVelocityRPM();
    inputs.appliedVoltage = appliedVoltage;
    inputs.supplyCurrentAmps = sim.getCurrentDrawAmps();
    inputs.torqueCurrentAmps = inputs.supplyCurrentAmps;
  }

  @Override
  public void runVolts(double volts) {
    appliedVoltage = Math.clamp(volts, -12.0, 12.0);
    sim.setInputVoltage(appliedVoltage);
  }

  @Override
  public void stop() {
    runVolts(0.0);
  }
}
