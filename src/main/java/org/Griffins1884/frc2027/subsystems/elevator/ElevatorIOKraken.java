package org.Griffins1884.frc2027.subsystems.elevator;

import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.hardware.TalonFX;
import org.Griffins1884.frc2027.CanIDConstants;

public class ElevatorIOKraken implements ElevatorIO {
  private final TalonFX leader;
  private final TalonFX follower;

  private final TalonFXConfiguration leaderConfig;
  private final TalonFXConfiguration followerConfig;

  public ElevatorIOKraken() {
    this.leader = new TalonFX(CanIDConstants.ELEVATOR_CAN[0], CanIDConstants.ELEVATOR_CAN_BUS);
    this.follower = new TalonFX(CanIDConstants.ELEVATOR_CAN[1], CanIDConstants.ELEVATOR_CAN_BUS);

    this.leaderConfig = new TalonFXConfiguration();
    this.followerConfig = new TalonFXConfiguration();

    leaderConfig.Slot0.kA = ElevatorConstants.kA.getAsDouble();
    leaderConfig.Slot0.kG = ElevatorConstants.kG.getAsDouble();
    leaderConfig.Slot0.kV = ElevatorConstants.kV.getAsDouble();
    leaderConfig.Slot0.kS = ElevatorConstants.kS.getAsDouble();
    leaderConfig.Slot0.kP = ElevatorConstants.kP.getAsDouble();
    leaderConfig.Slot0.kI = ElevatorConstants.kI.getAsDouble();
    leaderConfig.Slot0.kD = ElevatorConstants.kD.getAsDouble();

    followerConfig.Slot0.kA = ElevatorConstants.kA.getAsDouble();
    followerConfig.Slot0.kG = ElevatorConstants.kG.getAsDouble();
    followerConfig.Slot0.kV = ElevatorConstants.kV.getAsDouble();
    followerConfig.Slot0.kS = ElevatorConstants.kS.getAsDouble();
    followerConfig.Slot0.kP = ElevatorConstants.kP.getAsDouble();
    followerConfig.Slot0.kI = ElevatorConstants.kI.getAsDouble();
    followerConfig.Slot0.kD = ElevatorConstants.kD.getAsDouble();

    leader.getConfigurator().apply(leaderConfig);
    follower.getConfigurator().apply(followerConfig);
  }

  private double calcPositionBasedOnRotations() {
    return leader.getPosition().getValueAsDouble() * ElevatorConstants.ROTATION_TO_HEIGHT_RATIO;
  }

  private double calcVelocityBasedOnRotations() {
    return leader.getVelocity().getValueAsDouble() * ElevatorConstants.ROTATION_TO_HEIGHT_RATIO;
  }

  public void updateInputs(ElevatorIOInputs inputs) {
    inputs.positionMeters = calcPositionBasedOnRotations();
    inputs.velocityMeterPerSecond = calcVelocityBasedOnRotations();
    inputs.appliedVolts = leader.getMotorVoltage().getValueAsDouble();
    inputs.statorCurrentAmps =
        leader.getStatorCurrent().getValueAsDouble()
            + follower.getStatorCurrent().getValueAsDouble();
    inputs.supplyCurrentAmps =
        leader.getSupplyCurrent().getValueAsDouble()
            + follower.getSupplyCurrent().getValueAsDouble();
  }

  public void setVoltage(double volts) {
    leader.setVoltage(volts);
    follower.setVoltage(volts);
  }

  public void stop() {
    leader.stopMotor();
    follower.stopMotor();
  }
}
