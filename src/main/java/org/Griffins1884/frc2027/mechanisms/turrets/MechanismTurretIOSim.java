package org.Griffins1884.frc2027.mechanisms.turrets;

import static org.wpilib.math.system.Models.singleJointedArmFromPhysicalConstants;

import org.wpilib.math.util.MathUtil;
import org.wpilib.math.system.DCMotor;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.simulation.DCMotorSim;

public class MechanismTurretIOSim implements MechanismTurretIO {
  private final DCMotorSim sim;
  private final double invertSign;
  private double appliedVolts = 0.0;
  private double positionOffsetRad = 0.0;

  public MechanismTurretIOSim(DCMotor motorModel, double gearRatio, double moi) {
    this(motorModel, gearRatio, moi, false);
  }

  public MechanismTurretIOSim(DCMotor motorModel, double gearRatio, double moi, boolean inverted) {
    sim = new DCMotorSim(singleJointedArmFromPhysicalConstants(motorModel, moi, gearRatio), motorModel);
    invertSign = inverted ? -1.0 : 1.0;
  }

  @Override
  public void updateInputs(MechanismTurretIOInputs inputs) {
    if (RobotState.isDisabled()) {
      sim.setInputVoltage(appliedVolts);
    }
    sim.update(0.02);
    if (inputs.connected.length != 1) {
      inputs.connected = new boolean[] {true};
    } else {
      inputs.connected[0] = true;
    }
    double rawPositionRad = sim.getAngularPosition() * invertSign;
    inputs.positionRad = rawPositionRad + positionOffsetRad;
    inputs.velocityRadPerSec = sim.getAngularVelocity() * invertSign;
    inputs.motorPositionRotations = Double.NaN;
    inputs.motorPositionTicks = Double.NaN;
    inputs.motorGoalRotations = Double.NaN;
    inputs.motorGoalTicks = Double.NaN;
    inputs.appliedVoltage = appliedVolts;
    inputs.supplyCurrentAmps = sim.getCurrentDraw();
    inputs.torqueCurrentAmps = inputs.supplyCurrentAmps;
    inputs.tempCelsius = 0.0;
    inputs.absoluteConnected = false;
    inputs.absolutePositionRad = 0.0;
  }

  @Override
  public void setVoltage(double volts) {
    appliedVolts = Math.clamp(volts, -12.0, 12.0);
    sim.setInputVoltage(appliedVolts * invertSign);
  }

  @Override
  public void setPosition(double positionRad) {
    positionOffsetRad = positionRad - sim.getAngularPosition() * invertSign;
  }
}
