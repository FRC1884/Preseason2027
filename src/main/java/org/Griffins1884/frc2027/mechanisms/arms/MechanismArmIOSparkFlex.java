package org.Griffins1884.frc2027.mechanisms.arms;

import static com.revrobotics.spark.config.SparkBaseConfig.IdleMode.*;

import org.wpilib.hardware.bus.CANPort;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel;
import com.revrobotics.spark.config.SparkBaseConfig;
import com.revrobotics.spark.config.SparkFlexConfig;

public class MechanismArmIOSparkFlex implements MechanismArmIO {
  private final SparkFlex[] motors;
  private final AbsoluteEncoder absoluteEncoder;
  private final RelativeEncoder relativeEncoder;
  private SparkBaseConfig config;
  private final SparkFlex leader;
  private final double positionCoefficient;
  private final CANPort canPort;

  public MechanismArmIOSparkFlex(
      int[] ids, int currentLimitAmps, boolean brake, double forwardLimit, double reverseLimit, CANPort canPort) {
    this(ids, currentLimitAmps, brake, forwardLimit, reverseLimit, 1.0, canPort);
  }

  public MechanismArmIOSparkFlex(
      int[] ids,
      int currentLimitAmps,
      boolean brake,
      double forwardLimit,
      double reverseLimit,
      double positionCoefficient, CANPort canPort) {
    this.positionCoefficient = positionCoefficient;
    this.canPort=canPort;

    motors = new SparkFlex[ids.length];
    config =
        new SparkFlexConfig().smartCurrentLimit(currentLimitAmps).idleMode(brake ? kBrake : kCoast);
    config
        .softLimit
        .forwardSoftLimit(forwardLimit / positionCoefficient)
        .reverseSoftLimit(reverseLimit / positionCoefficient);

    leader = motors[0] = new SparkFlex(this.canPort, ids[0], SparkLowLevel.MotorType.kBrushless);
    leader.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    if (ids.length > 1) {
      for (int i = 1; i < ids.length; i++) {
        motors[i] = new SparkFlex(this.canPort, ids[i], SparkLowLevel.MotorType.kBrushless);
        motors[i].configure(
            new SparkFlexConfig().apply(config).follow(leader),
            ResetMode.kResetSafeParameters,
            PersistMode.kPersistParameters);
      }
    }
    absoluteEncoder = leader.getAbsoluteEncoder();
    relativeEncoder = leader.getEncoder();
  }

  public void updateInputs(MechanismArmIOInputs inputs) {
    if (motors != null) {
      if (inputs.connected.length != motors.length) {
        inputs.connected = new boolean[motors.length];
      }
      for (int i = 0; i < motors.length; i++) {
        inputs.connected[i] = true;
      }
      double positionRotations =
          (absoluteEncoder != null) ? absoluteEncoder.getPosition().get() : relativeEncoder.getPosition().get();
      double velocityRpm =
          (absoluteEncoder != null) ? absoluteEncoder.getVelocity().get() : relativeEncoder.getVelocity().get();
      inputs.encoderPosition = positionRotations * positionCoefficient;
      inputs.velocity = velocityRpm * positionCoefficient / 60.0;
      inputs.appliedVoltage = leader.getAppliedOutput().get() * leader.getBusVoltage().get();
      inputs.supplyCurrentAmps = leader.getOutputCurrent().get();
      inputs.torqueCurrentAmps = leader.getOutputCurrent().get();
      inputs.tempCelsius = leader.getMotorTemperature().get();
    }
  }

  @Override
  public void setVoltage(double volts) {
    leader.setVoltage(volts);
  }

  @Override
  public void setBrakeMode(boolean enabled) {
    config = config.idleMode(enabled ? kBrake : kCoast);
    leader.configure(config, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
  }

  @Override
  public void setPosition(double position) {
    if (relativeEncoder != null) {
      relativeEncoder.setPosition(position / positionCoefficient);
    }
  }

  protected void invert(int id) {
    motors[id].configure(
        new SparkFlexConfig().inverted(true),
        ResetMode.kNoResetSafeParameters,
        PersistMode.kNoPersistParameters);
  }
}
