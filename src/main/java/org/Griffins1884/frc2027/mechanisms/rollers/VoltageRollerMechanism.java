package org.Griffins1884.frc2027.mechanisms.rollers;

import static org.wpilib.units.Units.Radian;
import static org.wpilib.units.Units.RadiansPerSecond;
import static org.wpilib.units.Units.Seconds;
import static org.wpilib.units.Units.Volts;

import org.wpilib.util.Alert;
import org.wpilib.util.Alert.Level;
import org.wpilib.driverstation.RobotState;
import org.wpilib.system.Timer;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.command2.sysid.SysIdRoutine;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import org.Griffins1884.frc2027.mechanisms.MechanismDefinition;
import org.Griffins1884.frc2027.mechanisms.MechanismHealth;
import org.Griffins1884.frc2027.mechanisms.MechanismTelemetry;
import org.Griffins1884.frc2027.runtime.RuntimeModeManager;
import org.littletonrobotics.junction.Logger;

/** Mechanism-backed replacement for the old generic voltage roller base. */
public abstract class VoltageRollerMechanism<G extends VoltageRollerMechanism.VoltageGoal>
    extends SubsystemBase {
  public enum ControlMode {
    GOAL,
    MANUAL
  }

  public record VoltageRollerConfig(double maxVoltage) {}

  public interface VoltageGoal {
    DoubleSupplier getVoltageSupplier();
  }

  public abstract G getGoal();

  private final String name;
  private final MechanismDefinition definition;
  private final String mechanismKey;
  private final MechanismRollerIO io;
  protected final MechanismRollerIOInputsAutoLogged inputs =
      new MechanismRollerIOInputsAutoLogged();
  private final Alert disconnected;
  protected final Timer stateTimer = new Timer();
  private G lastGoal;
  private final SysIdRoutine sysIdRoutine;
  private final VoltageRollerConfig config;

  private ControlMode controlMode = ControlMode.GOAL;
  private double goalVoltage = 0.0;
  private double manualVoltage = 0.0;
  private boolean connected = false;
  private MechanismHealth health = MechanismHealth.OFFLINE;

  protected VoltageRollerMechanism(
      String name, MechanismDefinition definition, MechanismRollerIO io) {
    this(name, definition, io, new VoltageRollerConfig(12.0));
  }

  protected VoltageRollerMechanism(
      String name,
      MechanismDefinition definition,
      MechanismRollerIO io,
      VoltageRollerConfig config) {
    this.name = Objects.requireNonNull(name, "name");
    this.definition = Objects.requireNonNull(definition, "definition");
    this.mechanismKey = definition.key();
    this.io = Objects.requireNonNull(io, "io");
    this.config = Objects.requireNonNull(config, "config");

    sysIdRoutine =
        new SysIdRoutine(
            new SysIdRoutine.Config(
                null,
                null,
                Seconds.of(4),
                state -> {
                  if (RuntimeModeManager.isDebugEnabled(mechanismKey)) {
                    Logger.recordOutput("Rollers/" + name + "/SysIdState", state.toString());
                  }
                }),
            new SysIdRoutine.Mechanism(
                voltage -> io.runVolts(voltage.in(Volts)),
                log ->
                    log.motor(name)
                        .voltage(Volts.of(inputs.appliedVoltage))
                        .angularVelocity(RadiansPerSecond.of(inputs.velocityRadsPerSec))
                        .angularPosition(Radian.of(inputs.positionRads)),
                this));

    disconnected = new Alert("Voltage Roller Motor Disconnected" + name ,name + " motor disconnected!", Level.MEDIUM);
    stateTimer.start();
    recordConfigSnapshot();
  }

  public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
    return sysIdRoutine.quasistatic(direction);
  }

  public Command sysIdDynamic(SysIdRoutine.Direction direction) {
    return sysIdRoutine.dynamic(direction);
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    if (RuntimeModeManager.isDebugEnabled(mechanismKey)) {
      Logger.processInputs(name, inputs);
    }
    boolean anyDisconnected = false;
    for (boolean isConnected : inputs.connected) {
      if (!isConnected) {
        anyDisconnected = true;
        break;
      }
    }
    disconnected.set(anyDisconnected);
    connected = !anyDisconnected;
    health = connected ? MechanismHealth.NOMINAL : MechanismHealth.OFFLINE;

    if (getGoal() != lastGoal) {
      stateTimer.reset();
      lastGoal = getGoal();
    }

    double requestedVoltage =
        controlMode == ControlMode.MANUAL
            ? manualVoltage
            : getGoal().getVoltageSupplier().getAsDouble();
    goalVoltage = Math.clamp(requestedVoltage, -config.maxVoltage(), config.maxVoltage());

    logOutputs(anyDisconnected);

    if (RobotState.isDisabled()) {
      io.runVolts(0.0);
      return;
    }

    io.runVolts(goalVoltage);
    if (RuntimeModeManager.isDebugEnabled(mechanismKey)) {
      Logger.recordOutput("Rollers/" + name + "Goal", getGoal().toString());
    }
  }

  public void setManualVoltage(double volts) {
    manualVoltage = volts;
    controlMode = ControlMode.MANUAL;
  }

  public void clearGoalOverride() {
    controlMode = ControlMode.GOAL;
  }

  public double getAppliedVolts() {
    return inputs.appliedVoltage;
  }

  public double getGoalVoltage() {
    return goalVoltage;
  }

  public double getSupplyCurrentAmps() {
    return inputs.supplyCurrentAmps;
  }

  public double getTorqueCurrentAmps() {
    return inputs.torqueCurrentAmps;
  }

  public ControlMode getControlMode() {
    return controlMode;
  }

  public void setBrakeMode(boolean enabled) {
    io.setBrakeMode(enabled);
  }

  public MechanismDefinition getDefinition() {
    return definition;
  }

  public boolean isConnected() {
    return connected;
  }

  public MechanismHealth getHealth() {
    return health;
  }

  private void logOutputs(boolean anyDisconnected) {
    String mechanismRoot = "Mechanisms/" + mechanismKey;
    if (RuntimeModeManager.shouldLog(MechanismTelemetry.Signal.CONNECTION, mechanismKey)) {
      Logger.recordOutput(mechanismRoot + "/Connected", !anyDisconnected);
    }
    if (RuntimeModeManager.shouldLog(MechanismTelemetry.Signal.HEALTH, mechanismKey)) {
      Logger.recordOutput(
          mechanismRoot + "/Health",
          anyDisconnected ? MechanismHealth.OFFLINE.name() : MechanismHealth.NOMINAL.name());
    }
    if (RuntimeModeManager.shouldLog(MechanismTelemetry.Signal.TARGET, mechanismKey)) {
      Logger.recordOutput(mechanismRoot + "/TargetVoltage", goalVoltage);
    }
    if (RuntimeModeManager.shouldLog(MechanismTelemetry.Signal.CURRENT, mechanismKey)) {
      Logger.recordOutput(mechanismRoot + "/SupplyCurrentAmps", inputs.supplyCurrentAmps);
      Logger.recordOutput(mechanismRoot + "/TorqueCurrentAmps", inputs.torqueCurrentAmps);
    }
    if (RuntimeModeManager.shouldLog(MechanismTelemetry.Signal.VOLTAGE, mechanismKey)) {
      Logger.recordOutput(mechanismRoot + "/AppliedVoltage", inputs.appliedVoltage);
    }
    if (RuntimeModeManager.isDebugEnabled(mechanismKey)) {
      Logger.recordOutput("Rollers/" + name + "/GoalVolts", goalVoltage);
      Logger.recordOutput("Rollers/" + name + "/ControlMode", controlMode.toString());
    }
  }

  private void recordConfigSnapshot() {
    if (RuntimeModeManager.shouldLog(MechanismTelemetry.Signal.CONFIG_SNAPSHOT, mechanismKey)) {
      Logger.recordOutput("Mechanisms/" + mechanismKey + "/Definition", definition.toString());
    }
  }
}
