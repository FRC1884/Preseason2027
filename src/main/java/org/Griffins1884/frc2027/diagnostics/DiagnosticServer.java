package org.Griffins1884.frc2027.diagnostics;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.hardware.TalonFX;
import java.util.*;
import org.Griffins1884.frc2027.CanIDConstants;
import org.wpilib.networktables.*;

public class DiagnosticServer {
  private static Map<String, TalonFX> data = new HashMap<>();

  private static Map<String, Integer> canIDs = new HashMap<>();
  private static Map<String, Boolean> states = new HashMap<>();
  private static Map<String, Double> values = new HashMap<>();
  private static Map<String, String> controlModes = new HashMap<>();

  private static Map<String, IntegerPublisher> canIDPublishers = new HashMap<>();
  private static Map<String, BooleanPublisher> statePublishers = new HashMap<>();
  private static Map<String, StringPublisher> controlModePublishers = new HashMap<>();
  private static Map<String, DoublePublisher> valuePublishers = new HashMap<>();
  private static Map<String, DoublePublisher> encoderPublishers = new HashMap<>();
  private static Map<String, StringPublisher> canFaultPublishers = new HashMap<>();

  private static NetworkTable table = NetworkTableInstance.getDefault().getTable("MotorData");

  private static NetworkTable canSubTable = table.getSubTable("CAN");
  private static NetworkTable stateSubTable = table.getSubTable("States");
  private static NetworkTable controlModeSubTable = table.getSubTable("ControlMode");
  private static NetworkTable valueSubTable = table.getSubTable("Value");
  private static NetworkTable encoderPositionSubTable = table.getSubTable("Encoder");
  private static NetworkTable canFaultSubTable = table.getSubTable("CANFaults");

  private static Map<String, StringSubscriber> controlModeSubs = new HashMap<>();
  private static Map<String, BooleanSubscriber> stateSubs = new HashMap<>();
  private static Map<String, DoubleSubscriber> valueSubs = new HashMap<>();

  public static void publishMotorData() {
    data.put(
        "Intake Pivot 1",
        DiagnosticCommands.initTalonFX(
            CanIDConstants.INTAKE_PIVOT_IDS[0], CanIDConstants.INTAKE_PIVOT_BUS));
    data.put(
        "Intake Pivot 2",
        DiagnosticCommands.initTalonFX(
            CanIDConstants.INTAKE_PIVOT_IDS[1], CanIDConstants.INTAKE_PIVOT_BUS));

    data.put(
        "Intake Rollers",
        DiagnosticCommands.initTalonFX(CanIDConstants.INTAKE_IDS[0], CanIDConstants.INTAKE_BUS));

    data.put(
        "Turret",
        DiagnosticCommands.initTalonFX(CanIDConstants.TURRET_ID, CanIDConstants.TURRET_CAN_BUS));

    data.put(
        "Shooter 1",
        DiagnosticCommands.initTalonFX(
            CanIDConstants.SHOOTER_IDS[0], CanIDConstants.SHOOTER_CAN_BUS));
    data.put(
        "Shooter 2",
        DiagnosticCommands.initTalonFX(
            CanIDConstants.SHOOTER_IDS[1], CanIDConstants.SHOOTER_CAN_BUS));

    data.put(
        "Shooter Pivot",
        DiagnosticCommands.initTalonFX(
            CanIDConstants.SHOOTER_PIVOT_IDS[0], CanIDConstants.SHOOTER_CAN_BUS));

    data.put(
        "Indexer",
        DiagnosticCommands.initTalonFX(CanIDConstants.INDEXER_IDS[0], CanIDConstants.INDEXER_BUS));

    data.put(
        "Swerve FLR",
        DiagnosticCommands.initTalonFX(CanIDConstants.FLR_ID, new CANBus(CanIDConstants.SWERVE)));
    data.put(
        "Swerve FRR",
        DiagnosticCommands.initTalonFX(CanIDConstants.FRR_ID, new CANBus(CanIDConstants.SWERVE)));
    data.put(
        "Swerve BLR",
        DiagnosticCommands.initTalonFX(CanIDConstants.BLR_ID, new CANBus(CanIDConstants.SWERVE)));
    data.put(
        "Swerve BRR",
        DiagnosticCommands.initTalonFX(CanIDConstants.BRR_ID, new CANBus(CanIDConstants.SWERVE)));

    data.put(
        "Swerve FLD",
        DiagnosticCommands.initTalonFX(CanIDConstants.FLD_ID, new CANBus(CanIDConstants.SWERVE)));
    data.put(
        "Swerve FRD",
        DiagnosticCommands.initTalonFX(CanIDConstants.FRD_ID, new CANBus(CanIDConstants.SWERVE)));
    data.put(
        "Swerve BLD",
        DiagnosticCommands.initTalonFX(CanIDConstants.BLR_ID, new CANBus(CanIDConstants.SWERVE)));
    data.put(
        "Swerve BRD",
        DiagnosticCommands.initTalonFX(CanIDConstants.BLR_ID, new CANBus(CanIDConstants.SWERVE)));

    data.forEach((name, motor) -> {
      canIDs.put(name, motor.getDeviceID());
      states.put(name, false);
      controlModes.put(name, "Voltage");
      values.put(name, 0.0);
    });

    publishData();
  }

  public static void publishData() {
    for (Map.Entry<String, Integer> entry : canIDs.entrySet()) {
      IntegerPublisher pub =
          canIDPublishers.computeIfAbsent(
              entry.getKey(), key -> canSubTable.getIntegerTopic(key).publish());
      pub.set(entry.getValue());
    }

    for (Map.Entry<String, Boolean> entry : states.entrySet()) {
      BooleanPublisher pub =
          statePublishers.computeIfAbsent(
              entry.getKey(), key -> stateSubTable.getBooleanTopic(key).publish());
      pub.set(entry.getValue());
    }

    for (Map.Entry<String, String> entry : controlModes.entrySet()) {
      StringPublisher pub =
          controlModePublishers.computeIfAbsent(
              entry.getKey(), key -> controlModeSubTable.getStringTopic(key).publish());
      pub.set(entry.getValue());
    }

    for (Map.Entry<String, Double> entry : values.entrySet()) {
      DoublePublisher pub =
          valuePublishers.computeIfAbsent(
              entry.getKey(), key -> valueSubTable.getDoubleTopic(key).publish());
      pub.set(entry.getValue());
    }

    for (Map.Entry<String, TalonFX> entry : data.entrySet()) {
      DoublePublisher pub =
          encoderPublishers.computeIfAbsent(
              entry.getKey(), key -> encoderPositionSubTable.getDoubleTopic(key).publish());
      pub.set(DiagnosticCommands.getEncoderPosition(entry.getValue()));

      StringPublisher pub2 =
          canFaultPublishers.computeIfAbsent(
              entry.getKey(), key -> canFaultSubTable.getStringTopic(key).publish());
      pub2.set(DiagnosticCommands.getCanStatus(entry.getValue()));
    }
  }

  public static void initSubscribers() {
    for (String key : data.keySet()) {
      StringSubscriber controlModeSub =
          controlModeSubTable.getStringTopic(key).subscribe("Voltage");
      BooleanSubscriber stateSub = stateSubTable.getBooleanTopic(key).subscribe(false);
      DoubleSubscriber valueSub = valueSubTable.getDoubleTopic(key).subscribe(0.0);

      controlModeSubs.put(key, controlModeSub);
      stateSubs.put(key, stateSub);
      valueSubs.put(key, valueSub);
    }
  }

  public static void updateMotorStates(String key) {
    String requestedControlMode = controlModes.get(key);
    Boolean requestedState = states.get(key);
    Double requestedValue = values.get(key);
    TalonFX motor = data.get(key);

    if (!requestedState) {
      DiagnosticCommands.stopMotor(motor);
      return;
    }
    if ("Voltage".equals(requestedControlMode)) {
      DiagnosticCommands.runMotorVoltage(motor, Math.clamp(requestedValue, -12.0, 12.0));
      System.out.println(DiagnosticCommands.getEncoderPosition(motor));
    } else if ("Velocity".equals(requestedControlMode)) {
      DiagnosticCommands.runMotorThrottle(motor, Math.clamp(requestedValue, -1.0, 1.0));
    }
  }

  public static void periodic() {
    for (Map.Entry<String, StringSubscriber> entry : controlModeSubs.entrySet()) {
      controlModes.put(entry.getKey(), entry.getValue().get());
    }
    for (Map.Entry<String, BooleanSubscriber> entry : stateSubs.entrySet()) {
      states.put(entry.getKey(), entry.getValue().get());
    }
    for (Map.Entry<String, DoubleSubscriber> entry : valueSubs.entrySet()) {
      values.put(entry.getKey(), entry.getValue().get());
    }
    for (Map.Entry<String, TalonFX> entry : data.entrySet()) {
      updateMotorStates(entry.getKey());
    }
    publishData();
  }
}
