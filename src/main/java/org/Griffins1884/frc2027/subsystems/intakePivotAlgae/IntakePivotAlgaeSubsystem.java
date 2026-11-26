package org.Griffins1884.frc2027.subsystems.intakePivotAlgae;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.Griffins1884.frc2027.mechanisms.MechanismDefinition;
import org.Griffins1884.frc2027.mechanisms.MechanismHealth;
import org.Griffins1884.frc2027.mechanisms.RobotMechanismDefinitions;
import org.Griffins1884.frc2027.mechanisms.arms.MechanismArmIO;
import org.Griffins1884.frc2027.mechanisms.arms.PositionArmMechanism;
import org.Griffins1884.frc2027.util.LoggedTunableNumber;
import org.littletonrobotics.junction.Logger;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.command2.sysid.SysIdRoutine;
import org.wpilib.hardware.discrete.DigitalInput;

public class IntakePivotAlgaeSubsystem extends SubsystemBase {
  @RequiredArgsConstructor
  @Getter
  public enum IntakePivotAlgaeGoal implements PositionArmMechanism.PivotGoal {
    IDLING(IntakePivotAlgaeConstants.IDLE_ANGLE_RAD),
    PICKUP(IntakePivotAlgaeConstants.PICKUP_RAD),
    TESTING(new LoggedTunableNumber("IntakePivotAlgae/Test", 0.0));

    private final DoubleSupplier angleSupplier;

    @Override
    public DoubleSupplier getAngle() {
      return () -> angleSupplier.getAsDouble();
    }
  }

  @Setter @Getter private IntakePivotAlgaeGoal goal = IntakePivotAlgaeGoal.IDLING;

  private final IntakePivotAlgaeArm primary;
  private final IntakePivotAlgaeArm secondary;
  private final DigitalInput primaryZeroLimitSwitch;
  private final DigitalInput secondaryZeroLimitSwitch;
  private boolean zeroingRequested = false;
  private boolean manualZeroSeekRequested = false;
  private boolean zeroingLatched = false;
  private String zeroingAction = "IDLE";
  private int zeroingDetectSamples = 0;
  private static final double LOOP_PERIOD_SEC = 0.02;

  public IntakePivotAlgaeSubsystem(
      String name, IntakePivotAlgaeIO primaryIO, IntakePivotAlgaeIO secondaryIO) {
    primary = new IntakePivotAlgaeSubsystem.IntakePivotAlgaeArm(name, primaryIO, () -> goal);
    secondary =
        new IntakePivotAlgaeSubsystem.IntakePivotAlgaeArm(
            name + "Follower", secondaryIO, () -> goal);
    primaryZeroLimitSwitch =
        createLimitSwitch(IntakePivotAlgaeConstants.PRIMARY_ZERO_LIMIT_SWITCH_DIO_CHANNEL);
    secondaryZeroLimitSwitch =
        createLimitSwitch(IntakePivotAlgaeConstants.SECONDARY_ZERO_LIMIT_SWITCH_DIO_CHANNEL);
  }

  public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
    return Commands.parallel(
        primary.sysIdQuasistatic(direction), secondary.sysIdQuasistatic(direction));
  }

  public Command sysIdDynamic(SysIdRoutine.Direction direction) {
    return Commands.parallel(primary.sysIdDynamic(direction), secondary.sysIdDynamic(direction));
  }

  public void clearGoalOverride() {
    clearGoalOverrideInternal();
  }

  public void setGoalPosition(double position) {
    setGoalPositionInternal(position);
  }

  public void requestZeroCalibration() {
    zeroingRequested = true;
    manualZeroSeekRequested = false;
    zeroingLatched = false;
    zeroingAction = "REQUESTED";
    zeroingDetectSamples = 0;
    stopOpenLoopInternal();
    clearGoalOverrideInternal();
  }

  public void cancelZeroCalibration() {
    zeroingRequested = false;
    manualZeroSeekRequested = false;
    zeroingLatched = false;
    zeroingAction = "CANCELLED";
    zeroingDetectSamples = 0;
  }

  public void requestManualZeroSeek() {
    zeroingRequested = false;
    manualZeroSeekRequested = true;
    zeroingLatched = false;
    zeroingAction = "MANUAL_REQUESTED";
    zeroingDetectSamples = 0;
    stopOpenLoopInternal();
    clearGoalOverrideInternal();
  }

  public void cancelManualZeroSeek() {
    manualZeroSeekRequested = false;
    zeroingLatched = false;
    zeroingAction = "MANUAL_CANCELLED";
    zeroingDetectSamples = 0;
  }

  public boolean isZeroCalibrationInProgress() {
    return zeroingRequested;
  }

  public boolean isManualZeroSeekInProgress() {
    return manualZeroSeekRequested;
  }

  public String getZeroCalibrationAction() {
    return zeroingAction;
  }

  public void setOpenLoop(double percent) {
    primary.setOpenLoop(percent);
    secondary.setOpenLoop(percent);
  }

  public void stopOpenLoop() {
    primary.stopOpenLoop();
    secondary.stopOpenLoop();
  }

  public boolean isAtGoal() {
    return primary.isAtGoal() && secondary.isAtGoal();
  }

  public double getPosition() {
    return (primary.getPosition() + secondary.getPosition()) * 0.5;
  }

  public void setBrakeMode(boolean enabled) {
    primary.setBrakeMode(enabled);
    secondary.setBrakeMode(enabled);
  }

  public boolean isConnected() {
    return primary.isConnected() && secondary.isConnected();
  }

  public MechanismHealth getHealth() {
    if (!primary.isConnected() || !secondary.isConnected()) {
      return MechanismHealth.OFFLINE;
    }
    if (primary.getHealth() == MechanismHealth.FAULTED
        || secondary.getHealth() == MechanismHealth.FAULTED) {
      return MechanismHealth.FAULTED;
    }
    if (primary.getHealth() == MechanismHealth.DEGRADED
        || secondary.getHealth() == MechanismHealth.DEGRADED) {
      return MechanismHealth.DEGRADED;
    }
    return MechanismHealth.NOMINAL;
  }

  public String getControlModeName() {
    if (primary.getControlMode() == PositionArmMechanism.ControlMode.OPEN_LOOP
        || secondary.getControlMode() == PositionArmMechanism.ControlMode.OPEN_LOOP) {
      return PositionArmMechanism.ControlMode.OPEN_LOOP.name();
    }
    return PositionArmMechanism.ControlMode.CLOSED_LOOP.name();
  }

  @Override
  public void periodic() {
    IntakePivotAlgaeSubsystem.IntakePivotAlgaeGoal activeGoal = goal;
    boolean openLoopActive = isOpenLoopControlActive();
    if (!openLoopActive) {
      setGoalPositionInternal(activeGoal.getAngle().getAsDouble());
    }

    double seekPosition = IntakePivotAlgaeConstants.HARDSTOP_STOW_SEEK_POSITION.get();
    boolean zeroConditionDetected = false;
    if (manualZeroSeekRequested) {
      setGoalPositionInternal(seekPosition);
      zeroingAction = "MANUAL_SEEKING";
    } else if (zeroingRequested) {
      setGoalPositionInternal(seekPosition);
      zeroConditionDetected = isZeroingConditionMet();
      zeroingDetectSamples = zeroConditionDetected ? zeroingDetectSamples + 1 : 0;

      int requiredSamples =
          Math.max(
              1,
              (int)
                  Math.ceil(
                      IntakePivotAlgaeConstants.HARDSTOP_SPIKE_DEBOUNCE_SEC.get()
                          / LOOP_PERIOD_SEC));
      if (zeroingDetectSamples >= requiredSamples) {
        zeroingLatched = true;
        zeroingRequested = false;
        zeroPositionInternal();
        stopOpenLoopInternal();
        clearGoalOverrideInternal();
        zeroingAction = "ZEROED";
      } else {
        zeroingAction = "SEEKING";
      }
    }

    if (!zeroingRequested
        && !manualZeroSeekRequested
        && !zeroingLatched
        && !"CANCELLED".equals(zeroingAction)
        && !"MANUAL_CANCELLED".equals(zeroingAction)) {
      zeroingAction = "IDLE";
    }
    logZeroingStatus(activeGoal, seekPosition, zeroConditionDetected);
  }

  private static DigitalInput createLimitSwitch(int channel) {
    if (channel < 0) {
      return null;
    }
    return new DigitalInput(channel);
  }

  private void clearGoalOverrideInternal() {
    primary.clearGoalOverride();
    secondary.clearGoalOverride();
  }

  private void setGoalPositionInternal(double position) {
    double primaryPosition = primary.getPosition();
    double secondaryPosition = secondary.getPosition();
    double averagePosition = (primaryPosition + secondaryPosition) * 0.5;
    double safePosition = Double.isFinite(position) ? position : averagePosition;
    double syncError = primaryPosition - secondaryPosition;
    if (!Double.isFinite(syncError)) {
      syncError = 0.0;
    }

    double deadband = Math.max(0.0, IntakePivotAlgaeConstants.SYNC_DEADBAND_RAD.get());
    if (Math.abs(syncError) < deadband) {
      syncError = 0.0;
    }

    double maxTrim = Math.max(0.0, IntakePivotAlgaeConstants.SYNC_MAX_TRIM_RAD.get());
    double correction =
        clamp(syncError * IntakePivotAlgaeConstants.SYNC_CORRECTION_KP.get(), -maxTrim, maxTrim);

    double primaryGoal = safePosition - (correction * 0.5);
    double secondaryGoal = safePosition + (correction * 0.5);

    primary.setGoalPosition(primaryGoal);
    secondary.setGoalPosition(secondaryGoal);

    Logger.recordOutput("IntakePivot/Sync/PrimaryPositionRad", primaryPosition);
    Logger.recordOutput("IntakePivot/Sync/SecondaryPositionRad", secondaryPosition);
    Logger.recordOutput("IntakePivot/Sync/ErrorRad", syncError);
    Logger.recordOutput("IntakePivot/Sync/CorrectionRad", correction);
    Logger.recordOutput("IntakePivot/Sync/PrimaryGoalRad", primaryGoal);
    Logger.recordOutput("IntakePivot/Sync/SecondaryGoalRad", secondaryGoal);
  }

  private void stopOpenLoopInternal() {
    primary.stopOpenLoop();
    secondary.stopOpenLoop();
  }

  private void zeroPositionInternal() {
    primary.zeroPosition();
    secondary.zeroPosition();
  }

  private boolean isZeroingConditionMet() {
    Logger.recordOutput(
        "IntakePivot/Zeroing/DetectionMode",
        IntakePivotAlgaeConstants.ZEROING_DETECTION_MODE.name());
    return switch (IntakePivotAlgaeConstants.ZEROING_DETECTION_MODE) {
      case LIMIT_SWITCH -> isLimitSwitchZeroDetected();
      case CURRENT_SPIKE -> isCurrentSpikeZeroDetected();
    };
  }

  private boolean isLimitSwitchZeroDetected() {
    boolean primaryConfigured = primaryZeroLimitSwitch != null;
    boolean secondaryConfigured = secondaryZeroLimitSwitch != null;
    boolean primaryPressed = readLimitSwitch(primaryZeroLimitSwitch);
    boolean secondaryPressed = readLimitSwitch(secondaryZeroLimitSwitch);

    boolean detected;
    if (!primaryConfigured && !secondaryConfigured) {
      detected = false;
    } else if (primaryConfigured
        && secondaryConfigured
        && IntakePivotAlgaeConstants.ZERO_LIMIT_SWITCH_REQUIRE_BOTH) {
      detected = primaryPressed && secondaryPressed;
    } else {
      detected = primaryPressed || secondaryPressed;
    }

    Logger.recordOutput("IntakePivot/Zeroing/LimitSwitchPrimaryConfigured", primaryConfigured);
    Logger.recordOutput("IntakePivot/Zeroing/LimitSwitchSecondaryConfigured", secondaryConfigured);
    Logger.recordOutput("IntakePivot/Zeroing/LimitSwitchPrimaryPressed", primaryPressed);
    Logger.recordOutput("IntakePivot/Zeroing/LimitSwitchSecondaryPressed", secondaryPressed);
    Logger.recordOutput("IntakePivot/Zeroing/LimitSwitchDetected", detected);
    return detected;
  }

  private boolean readLimitSwitch(DigitalInput limitSwitch) {
    if (limitSwitch == null) {
      return false;
    }
    boolean rawState = limitSwitch.get();
    return IntakePivotAlgaeConstants.ZERO_LIMIT_SWITCH_ACTIVE_LOW ? !rawState : rawState;
  }

  private boolean isOpenLoopControlActive() {
    return primary.getControlMode() == PositionArmMechanism.ControlMode.OPEN_LOOP
        || secondary.getControlMode() == PositionArmMechanism.ControlMode.OPEN_LOOP;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private boolean isCurrentSpikeZeroDetected() {
    double maxCurrentAmps =
        Math.max(
            Math.abs(primary.getSupplyCurrentAmps()), Math.abs(secondary.getSupplyCurrentAmps()));
    double maxVelocityRadPerSec =
        Math.max(Math.abs(primary.getVelocity()), Math.abs(secondary.getVelocity()));
    double currentThresholdAmps = IntakePivotAlgaeConstants.HARDSTOP_STOW_CURRENT_AMPS.get();

    Logger.recordOutput("IntakePivot/HardStop/CurrentAmps", maxCurrentAmps);
    Logger.recordOutput("IntakePivot/HardStop/VelocityRadPerSec", maxVelocityRadPerSec);
    Logger.recordOutput("IntakePivot/HardStop/CurrentThresholdAmps", currentThresholdAmps);
    return maxCurrentAmps >= currentThresholdAmps
        && maxVelocityRadPerSec
            <= IntakePivotAlgaeConstants.HARDSTOP_MAX_VELOCITY_RAD_PER_SEC.get();
  }

  private void logZeroingStatus(
      IntakePivotAlgaeSubsystem.IntakePivotAlgaeGoal activeGoal,
      double seekPosition,
      boolean zeroConditionDetected) {
    Logger.recordOutput("IntakePivot/Zeroing/Requested", zeroingRequested);
    Logger.recordOutput("IntakePivot/Zeroing/ManualSeekRequested", manualZeroSeekRequested);
    Logger.recordOutput("IntakePivot/Zeroing/Goal", activeGoal.toString());
    Logger.recordOutput("IntakePivot/Zeroing/SeekPosition", seekPosition);
    Logger.recordOutput("IntakePivot/Zeroing/Latched", zeroingLatched);
    Logger.recordOutput("IntakePivot/Zeroing/Action", zeroingAction);
    Logger.recordOutput("IntakePivot/Zeroing/DetectSamples", zeroingDetectSamples);
    Logger.recordOutput("IntakePivot/Zeroing/ConditionDetected", zeroConditionDetected);
  }

  private static final class IntakePivotAlgaeArm
      extends PositionArmMechanism<IntakePivotAlgaeSubsystem.IntakePivotAlgaeGoal> {
    private final Supplier<IntakePivotAlgaeSubsystem.IntakePivotAlgaeGoal> goalSupplier;

    private IntakePivotAlgaeArm(
        String name,
        MechanismArmIO io,
        Supplier<IntakePivotAlgaeSubsystem.IntakePivotAlgaeGoal> goalSupplier) {
      super(
          name,
          sideDefinition(name),
          io,
          new ArmConfig(
              IntakePivotAlgaeConstants.GAINS.kP(),
              IntakePivotAlgaeConstants.GAINS.kI(),
              IntakePivotAlgaeConstants.GAINS.kD(),
              IntakePivotAlgaeConstants.GAINS.kS(),
              IntakePivotAlgaeConstants.GAINS.kG(),
              IntakePivotAlgaeConstants.GAINS.kV(),
              IntakePivotAlgaeConstants.GAINS.kA(),
              IntakePivotAlgaeConstants.MOTION_MAGIC_CRUISE_VEL,
              IntakePivotAlgaeConstants.MOTION_MAGIC_ACCEL,
              IntakePivotAlgaeConstants.MOTION_MAGIC_JERK,
              IntakePivotAlgaeConstants.POSITION_TOLERANCE,
              IntakePivotAlgaeConstants.SOFT_LIMITS_ENABLED,
              IntakePivotAlgaeConstants.SOFT_LIMIT_MIN,
              IntakePivotAlgaeConstants.SOFT_LIMIT_MAX,
              IntakePivotAlgaeConstants.MAX_VOLTAGE));
      this.goalSupplier = goalSupplier;
    }

    @Override
    public IntakePivotAlgaeSubsystem.IntakePivotAlgaeGoal getGoal() {
      return goalSupplier.get();
    }

    private static MechanismDefinition sideDefinition(String name) {
      MechanismDefinition base = RobotMechanismDefinitions.ALGAE_INTAKE_PIVOT;
      String normalizedName = name.replaceAll("[^A-Za-z0-9]+", "");
      String key = "intakePivot" + normalizedName;
      return new MechanismDefinition(
          key,
          name,
          base.mechanismType(),
          base.config(),
          base.telemetry(),
          base.faultPolicy(),
          base.simulation());
    }
  }
}
