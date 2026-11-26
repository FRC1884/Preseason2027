package org.Griffins1884.frc2027.diagnostics;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.StatusCode;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.hardware.TalonFX;
import org.Griffins1884.frc2027.subsystems.swerve.SwerveSubsystem;
import org.wpilib.math.geometry.Pose2d;

public class DiagnosticCommands {
  public static TalonFX initTalonFX(int canID, CANBus bus) {
    TalonFX activeMotor;
    TalonFXConfiguration configuration;

    activeMotor = new TalonFX(canID, bus);
    configuration = new TalonFXConfiguration();
    configuration.CurrentLimits.SupplyCurrentLimit = 40;
    configuration.CurrentLimits.SupplyCurrentLimitEnable = true;

    activeMotor.getConfigurator().apply(configuration);

    return activeMotor;
  }

  public String getCanStatus(TalonFX motor) {
    StatusCode status = motor.getSupplyVoltage().refresh().getStatus();

    return status.getDescription();
  }

  public static void runMotorVoltage(TalonFX activeMotor, double volts) {
    activeMotor.setVoltage(volts);
  }

  public static void runMotorThrottle(TalonFX activeMotor, double throttle) {
    activeMotor.setThrottle(throttle);
  }

  public static void stopMotor(TalonFX activeMotor) {
    activeMotor.stopMotor();
  }

  public static Pose2d getRobotPose(SwerveSubsystem swerve) {
    return swerve.getPose();
  }

  public static MotorGroup createMotorGroup(int[] canIDs, boolean[] active, CANBus bus) {
    return new MotorGroup(canIDs, active, bus);
  }
}
