package org.Griffins1884.frc2027.subsystems.swerve;

import static org.Griffins1884.frc2027.subsystems.swerve.SwerveConstants.*;

import com.ctre.phoenix6.hardware.TalonFX;
import org.wpilib.math.util.MathUtil;
import org.wpilib.driverstation.internal.DriverStationBackend;
import org.wpilib.math.controller.SimpleMotorFeedforward;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.math.system.DCMotor;
import org.wpilib.util.Alert;
import java.util.List;
import lombok.Getter;

import org.Griffins1884.frc2027.runtime.RuntimeModeManager;
import org.Griffins1884.frc2027.util.LoggedTunableNumber;
import org.littletonrobotics.junction.Logger;

public class Module {
  private static final double ANGLE_JUMP_THRESHOLD_RAD = Math.toRadians(35.0);
  private static final double ANGLE_JUMP_MAX_TURN_RATE_RAD_PER_SEC = 1.0;
  private static final double SPEED_RATIO_EPSILON_MPS = 0.15;
  private static final LoggedTunableNumber krakenDrivekS =
      new LoggedTunableNumber("Drive/Module/DrivekS");
  private static final LoggedTunableNumber krakenDrivekV =
      new LoggedTunableNumber("Drive/Module/DrivekV");
  private static final LoggedTunableNumber krakenDrivekT =
      new LoggedTunableNumber("Drive/Module/DrivekT");
  private static final LoggedTunableNumber krakenDrivekP =
      new LoggedTunableNumber("Drive/Module/DrivekP");
  private static final LoggedTunableNumber krakenDrivekD =
      new LoggedTunableNumber("Drive/Module/DrivekD");
  private static final LoggedTunableNumber krakenTurnkP =
      new LoggedTunableNumber("Drive/Module/TurnkP");
  private static final LoggedTunableNumber krakenTurnkD =
      new LoggedTunableNumber("Drive/Module/TurnkD");

  static {
    krakenDrivekS.initDefault(KRAKEN_DRIVE_TORQUE_GAINS.kS().get());
    krakenDrivekV.initDefault(KRAKEN_DRIVE_TORQUE_GAINS.kV().get());
    krakenDrivekT.initDefault(
        SwerveConstants.KRAKEN_DRIVE_GEAR_RATIO / DCMotor.getKrakenX60Foc(1).Kt);
    krakenDrivekP.initDefault(KRAKEN_DRIVE_TORQUE_GAINS.kP().get());
    krakenDrivekD.initDefault(KRAKEN_DRIVE_TORQUE_GAINS.kD().get());
    krakenTurnkP.initDefault(KRAKEN_TURN_TORQUE_GAINS.kP().get());
    krakenTurnkD.initDefault(KRAKEN_TURN_TORQUE_GAINS.kD().get());
  }

  private final ModuleIO io;
  private final ModuleIOInputsAutoLogged inputs = new ModuleIOInputsAutoLogged();
  private final int index;
  private SimpleMotorFeedforward krakenFfModel =
      new SimpleMotorFeedforward(krakenDrivekS.get(), krakenDrivekV.get());

  @SuppressWarnings("unused")
  private final String keyInputsDriveConnected;

  @SuppressWarnings("unused")
  private final String keyInputsDrivePositionRad;

  @SuppressWarnings("unused")
  private final String keyInputsDriveVelocityRadPerSec;

  @SuppressWarnings("unused")
  private final String keyInputsDriveAppliedVolts;

  @SuppressWarnings("unused")
  private final String keyInputsDriveCurrentAmps;

  @SuppressWarnings("unused")
  private final String keyInputsTurnConnected;

  @SuppressWarnings("unused")
  private final String keyInputsTurnPosition;

  @SuppressWarnings("unused")
  private final String keyInputsTurnVelocityRadPerSec;

  @SuppressWarnings("unused")
  private final String keyInputsTurnAppliedVolts;

  @SuppressWarnings("unused")
  private final String keyInputsTurnCurrentAmps;

  @SuppressWarnings("unused")
  private final String keyAngleJumpDetected;

  @SuppressWarnings("unused")
  private final String keyAngleJumpCount;

  @SuppressWarnings("unused")
  private final String keyZeroTrimRotations;

  @SuppressWarnings("unused")
  private final String keyInputsOdometryTimestamps;
  
  @SuppressWarnings("unused")
  private final String keyInputsOdometryDrivePositionsRad;

  @SuppressWarnings("unused")
  private final String keyInputsOdometryTurnPositions;

  @SuppressWarnings("unused")
  private final String keyInputsOdometryTurnPositionsRotations;

  @SuppressWarnings("unused")
  private final String keyDesiredSpeedMps;

  @SuppressWarnings("unused")
  private final String keyActualSpeedMps;

  @SuppressWarnings("unused")
  private final String keySpeedErrorMps;

  @SuppressWarnings("unused")
  private final String keySpeedRatio;

  @SuppressWarnings("unused")
  private final String keyDesiredAngleRad;

  @SuppressWarnings("unused")
  private final String keyActualAngleRad;

  @SuppressWarnings("unused")
  private final String keyAbsoluteAngleRad;

  @SuppressWarnings("unused")
  private final String keyAngleErrorRad;

  @SuppressWarnings("unused")
  private final String keyLastAngleDeltaRad;

  @SuppressWarnings("unused")
  private final String keyInvalidOdometryBatches;

  private final Alert driveDisconnectedAlert;
  private final Alert turnDisconnectedAlert;
  private double desiredSpeedMetersPerSec = 0.0;
  private Rotation2d desiredAngle = new Rotation2d();
  private Rotation2d lastTurnPosition = new Rotation2d();
  private boolean angleJumpDetected = false;
  private int angleJumpCount = 0;
  private double lastAngleDeltaRad = 0.0;

  private ModuleConfiguration desiredConfiguration;
  private ModuleConfiguration requestedConfiguration;
  private double desiredKs = krakenDrivekS.get();
  private double desiredKv = krakenDrivekV.get();
  private double appliedKs = desiredKs;
  private double appliedKv = desiredKv;

  @SuppressWarnings("unused")
  private double lastLoggedTrim = Double.NaN;

  @SuppressWarnings("unused")
  private int invalidOdometryBatches;

  @SuppressWarnings("unused")
  private ModuleConfigurationWorker.Status lastConfigurationStatus;

  @SuppressWarnings("unused")
  private final String configurationDesiredRevisionKey;

  @SuppressWarnings("unused")
  private final String configurationAppliedRevisionKey;

  @SuppressWarnings("unused")
  private final String configurationInFlightKey;

  @SuppressWarnings("unused")
  private final String configurationFailedKey;

  @SuppressWarnings("unused")
  private final String configurationInhibitedKey;

  @SuppressWarnings("unused")
  private final String configurationAttemptsKey;

  @SuppressWarnings("unused")
  private final String configurationCompletionsKey;

  @SuppressWarnings("unused")
  private final String configurationDurationKey;

  @SuppressWarnings("unused")
  private final String configurationErrorKey;
  
  @SuppressWarnings("unused")
  private final String configurationDesiredGainsKey;

  @SuppressWarnings("unused")
  private final String appliedFeedforwardKey;

  @SuppressWarnings("unused")
  private double loggedAppliedKs = Double.NaN;

  @SuppressWarnings("unused")
  private double loggedAppliedKv = Double.NaN;

  @SuppressWarnings("unused")
  private final String configurationFailureCountKey;

  @SuppressWarnings("unused")
  private final String configurationLastFailedRevisionKey;

  @SuppressWarnings("unused")
  private final String configurationLastFailureErrorKey;

  @SuppressWarnings("unused")
  private final String telemetryTimingKey;

  @SuppressWarnings("unused")
  private final String odometryTimingKey;

  /** -- GETTER -- Returns the module positions received this cycle. */
  @Getter private SwerveModulePosition[] odometryPositions = new SwerveModulePosition[] {};

  public Module(ModuleIO io, int index) {
    this.io = io;
    this.index = index;
    telemetryTimingKey = "Swerve/Module" + index + "/Performance/TelemetryAndFaultEvaluationMS";
    odometryTimingKey = "Swerve/Module" + index + "/Performance/OdometryConversionMS";
    String configurationKey = "Swerve/Module" + index + "/Configuration";
    configurationDesiredRevisionKey = configurationKey + "/DesiredRevision";
    configurationAppliedRevisionKey = configurationKey + "/AppliedRevision";
    configurationInFlightKey = configurationKey + "/InFlight";
    configurationFailedKey = configurationKey + "/Failed";
    configurationInhibitedKey = configurationKey + "/Inhibited";
    configurationAttemptsKey = configurationKey + "/Attempts";
    configurationCompletionsKey = configurationKey + "/Completions";
    configurationDurationKey = configurationKey + "/LastDurationMS";
    configurationErrorKey = configurationKey + "/Error";
    configurationDesiredGainsKey = configurationKey + "/DesiredGains";
    appliedFeedforwardKey = configurationKey + "/AppliedFeedforward";
    configurationFailureCountKey = configurationKey + "/FailureCount";
    configurationLastFailedRevisionKey = configurationKey + "/LastFailedRevision";
    configurationLastFailureErrorKey = configurationKey + "/LastFailureError";
    keyInputsDriveConnected = "Swerve/Module" + index + "/Inputs/DriveConnected";
    keyInputsDrivePositionRad = "Swerve/Module" + index + "/Inputs/DrivePositionRad";
    keyInputsDriveVelocityRadPerSec = "Swerve/Module" + index + "/Inputs/DriveVelocityRadPerSec";
    keyInputsDriveAppliedVolts = "Swerve/Module" + index + "/Inputs/DriveAppliedVolts";
    keyInputsDriveCurrentAmps = "Swerve/Module" + index + "/Inputs/DriveCurrentAmps";
    keyInputsTurnConnected = "Swerve/Module" + index + "/Inputs/TurnConnected";
    keyInputsTurnPosition = "Swerve/Module" + index + "/Inputs/TurnPosition";
    keyInputsTurnVelocityRadPerSec = "Swerve/Module" + index + "/Inputs/TurnVelocityRadPerSec";
    keyInputsTurnAppliedVolts = "Swerve/Module" + index + "/Inputs/TurnAppliedVolts";
    keyInputsTurnCurrentAmps = "Swerve/Module" + index + "/Inputs/TurnCurrentAmps";
    keyAngleJumpDetected = "Swerve/Module" + index + "/AngleJumpDetected";
    keyAngleJumpCount = "Swerve/Module" + index + "/AngleJumpCount";
    keyZeroTrimRotations = "Swerve/Module" + index + "/ZeroTrimRotations";
    keyInputsOdometryTimestamps = "Swerve/Module" + index + "/Inputs/OdometryTimestamps";
    keyInputsOdometryDrivePositionsRad =
        "Swerve/Module" + index + "/Inputs/OdometryDrivePositionsRad";
    keyInputsOdometryTurnPositions = "Swerve/Module" + index + "/Inputs/OdometryTurnPositions";
    keyInputsOdometryTurnPositionsRotations =
        "Swerve/Module" + index + "/Inputs/OdometryTurnPositionsRotations";
    keyDesiredSpeedMps = "Swerve/Module" + index + "/DesiredSpeedMps";
    keyActualSpeedMps = "Swerve/Module" + index + "/ActualSpeedMps";
    keySpeedErrorMps = "Swerve/Module" + index + "/SpeedErrorMps";
    keySpeedRatio = "Swerve/Module" + index + "/SpeedRatio";
    keyDesiredAngleRad = "Swerve/Module" + index + "/DesiredAngleRad";
    keyActualAngleRad = "Swerve/Module" + index + "/ActualAngleRad";
    keyAbsoluteAngleRad = "Swerve/Module" + index + "/AbsoluteAngleRad";
    keyAngleErrorRad = "Swerve/Module" + index + "/AngleErrorRad";
    keyLastAngleDeltaRad = "Swerve/Module" + index + "/LastAngleDeltaRad";
    keyInvalidOdometryBatches = "Swerve/Module" + index + "/InvalidOdometryBatches";

    desiredConfiguration = readConfiguration();
    requestedConfiguration = desiredConfiguration;
    advanceGainChecks();
    io.initializeConfiguration(desiredConfiguration);
    driveDisconnectedAlert =
        new Alert("Drive Module Disconnect" + index, "Disconnected drive motor on module " + index + ".", Alert.Level.HIGH);
    turnDisconnectedAlert =
        new Alert("Turn Module Disconnect" + index, "Disconnected turn motor on module " + index + ".", Alert.Level.HIGH);
  }

  private boolean advanceGainChecks() {
    // Every stateful check must run, including on the first call.
    boolean changed = krakenDrivekS.hasChanged(hashCode());
    changed |= krakenDrivekV.hasChanged(hashCode());
    changed |= krakenDrivekP.hasChanged(hashCode());
    changed |= krakenDrivekD.hasChanged(hashCode());
    changed |= krakenTurnkP.hasChanged(hashCode());
    changed |= krakenTurnkD.hasChanged(hashCode());
    return changed;
  }

  private ModuleConfiguration readConfiguration() {
    return new ModuleConfiguration(
        krakenDrivekP.get(), 0.0, krakenDrivekD.get(), krakenTurnkP.get(), 0.0, krakenTurnkD.get());
  }

  public void addOrchestraInstruments(List<TalonFX> instruments) {
    if (instruments == null) {
      return;
    }
    io.addOrchestraInstruments(instruments);
  }

  public void periodic() {
    if (krakenDrivekS.hasChanged(hashCode()) || krakenDrivekV.hasChanged(hashCode())) {
      krakenFfModel = new SimpleMotorFeedforward(krakenDrivekS.get(), krakenDrivekV.get());
    }
    if (krakenDrivekP.hasChanged(hashCode()) || krakenDrivekD.hasChanged(hashCode())) {
      io.setDrivePID(krakenDrivekP.get(), 0.0, krakenDrivekD.get());
    }
    if (krakenTurnkP.hasChanged(hashCode()) || krakenTurnkD.hasChanged(hashCode())) {
      io.setTurnPID(krakenTurnkP.get(), 0.0, krakenTurnkD.get());
    }

    io.updateInputs(inputs);
    Logger.processInputs("Swerve/Module" + index, inputs);

    double actualSpeedMetersPerSec = getVelocityMetersPerSec();
    double angleErrorRad = MathUtil.angleModulus(desiredAngle.minus(getAngle()).getRadians());
    double speedErrorMetersPerSec = desiredSpeedMetersPerSec - actualSpeedMetersPerSec;
    double speedRatio =
        Math.abs(desiredSpeedMetersPerSec) > SPEED_RATIO_EPSILON_MPS
            ? actualSpeedMetersPerSec / desiredSpeedMetersPerSec
            : 1.0;
    lastAngleDeltaRad = MathUtil.angleModulus(getAngle().minus(lastTurnPosition).getRadians());
    boolean suspiciousJump =
        Math.abs(lastAngleDeltaRad) > ANGLE_JUMP_THRESHOLD_RAD
            && Math.abs(inputs.turnVelocityRadPerSec) < ANGLE_JUMP_MAX_TURN_RATE_RAD_PER_SEC;
    if (suspiciousJump) {
      angleJumpCount++;
    }
    angleJumpDetected = suspiciousJump;
    lastTurnPosition = getAngle();

    Logger.recordOutput("Swerve/Module" + index + "/DesiredSpeedMps", desiredSpeedMetersPerSec);
    Logger.recordOutput("Swerve/Module" + index + "/ActualSpeedMps", actualSpeedMetersPerSec);
    Logger.recordOutput("Swerve/Module" + index + "/SpeedErrorMps", speedErrorMetersPerSec);
    Logger.recordOutput("Swerve/Module" + index + "/SpeedRatio", speedRatio);
    Logger.recordOutput("Swerve/Module" + index + "/DesiredAngleRad", desiredAngle.getRadians());
    Logger.recordOutput("Swerve/Module" + index + "/ActualAngleRad", getAngle().getRadians());
    Logger.recordOutput(
        "Swerve/Module" + index + "/AbsoluteAngleRad", inputs.turnAbsolutePosition.getRadians());
    Logger.recordOutput(
        "Swerve/Module" + index + "/ZeroTrimRotations", inputs.turnZeroTrimRotations);
    Logger.recordOutput("Swerve/Module" + index + "/AngleErrorRad", angleErrorRad);
    Logger.recordOutput("Swerve/Module" + index + "/LastAngleDeltaRad", lastAngleDeltaRad);
    Logger.recordOutput("Swerve/Module" + index + "/AngleJumpDetected", angleJumpDetected);
    Logger.recordOutput("Swerve/Module" + index + "/AngleJumpCount", angleJumpCount);

    // Calculate positions for odometry
    int sampleCount = inputs.odometryTimestamps.length; // All signals are sampled together
    odometryPositions = new SwerveModulePosition[sampleCount];
    for (int i = 0; i < sampleCount; i++) {
      double positionMeters =
          getCompensatedDrivePositionRad(inputs.odometryDrivePositionsRad[i], i)
              * getWheelRadiusMeters();
      Rotation2d angle = inputs.odometryTurnPositions[i];
      odometryPositions[i] = new SwerveModulePosition(positionMeters, angle);
    }

    // Update alerts
    driveDisconnectedAlert.set(!inputs.driveConnected);
    turnDisconnectedAlert.set(!inputs.turnConnected);
  }

  /** Runs the module with the specified setpoint state. Mutates the state to optimize it. */
  public void runSetpoint(SwerveModuleVelocity state) {
    desiredSpeedMetersPerSec = state.velocity;
    desiredAngle = state.angle;
    // Mechanical Advantage-style control for full Kraken modules
    double speedRadPerSec = state.velocity / getWheelRadiusMeters();
    io.setDriveVelocity(speedRadPerSec, krakenFfModel.calculate(speedRadPerSec));
    if (Math.abs(state.angle.minus(getAngle()).getDegrees()) < TURN_DEADBAND_DEGREES) {
      io.setTurnOpenLoop(0.0);
    } else {
      io.setTurnPosition(state.angle);
    }
  }

  /** Runs the module with the specified output while controlling to zeroRotation degrees. */
  public void runCharacterization(double output) {
    desiredSpeedMetersPerSec = 0.0;
    desiredAngle = new Rotation2d();
    io.setDriveOpenLoop(output);
    io.setTurnPosition(new Rotation2d());
  }

  /** Runs a steer-only SysId sweep while keeping the drive stage disabled. */
  public void runTurnCharacterization(double output) {
    desiredSpeedMetersPerSec = 0.0;
    desiredAngle = new Rotation2d();
    io.setDriveOpenLoop(0.0);
    io.setTurnOpenLoop(output);
  }

  /** Disables all outputs to motors. */
  public void stop() {
    desiredSpeedMetersPerSec = 0.0;
    desiredAngle = getAngle();
    io.setDriveOpenLoop(0.0);
    io.setTurnOpenLoop(0.0);
  }

  /** Returns the current turn angle of the module. */
  public Rotation2d getAngle() {
    return inputs.turnPosition;
  }

  /** Returns the current drive position of the module in meters. */
  public double getPositionMeters() {
    return getCompensatedDrivePositionRad(inputs.drivePositionRad) * getWheelRadiusMeters();
  }

  /** Returns the current drive velocity of the module in meters per second. */
  public double getVelocityMetersPerSec() {
    return getCompensatedDriveVelocityRadPerSec(inputs.driveVelocityRadPerSec)
        * getWheelRadiusMeters();
  }

  /** Returns the module position (turn angle and drive position). */
  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(getPositionMeters(), getAngle());
  }

  /** Returns the module state (turn angle and drive velocity). */
  public SwerveModuleVelocity getState() {
    return new SwerveModuleVelocity(getVelocityMetersPerSec(), getAngle());
  }

  /** Returns the timestamps of the samples received this cycle. */
  public double[] getOdometryTimestamps() {
    return inputs.odometryTimestamps;
  }

  /** Returns the module position in radians. */
  public double getWheelRadiusCharacterizationPosition() {
    return getCompensatedDrivePositionRad(inputs.drivePositionRad);
  }

  /** Returns the module velocity in rad/sec. */
  public double getFFCharacterizationVelocity() {
    return inputs.driveVelocityRadPerSec;
  }

  public double getVoltage() {
    return inputs.driveAppliedVolts;
  }

  public double getDriveVoltage() {
    return inputs.driveAppliedVolts;
  }

  public double getTurnVoltage() {
    return inputs.turnAppliedVolts;
  }

  public double getTurnPositionRad() {
    return inputs.turnPosition.getRadians();
  }

  public double getTurnVelocityRadPerSec() {
    return inputs.turnVelocityRadPerSec;
  }

  public int getIndex() {
    return index;
  }

  public double getDesiredSpeedMetersPerSec() {
    return desiredSpeedMetersPerSec;
  }

  public Rotation2d getDesiredAngle() {
    return desiredAngle;
  }

  public double getSpeedErrorMetersPerSec() {
    return desiredSpeedMetersPerSec - getVelocityMetersPerSec();
  }

  public double getSpeedRatio() {
    return Math.abs(desiredSpeedMetersPerSec) > SPEED_RATIO_EPSILON_MPS
        ? getVelocityMetersPerSec() / desiredSpeedMetersPerSec
        : 1.0;
  }

  public double getAngleErrorRad() {
    return MathUtil.angleModulus(desiredAngle.minus(getAngle()).getRadians());
  }

  public boolean isAngleJumpDetected() {
    return angleJumpDetected;
  }

  public int getAngleJumpCount() {
    return angleJumpCount;
  }

  public Rotation2d getAbsoluteAngle() {
    return inputs.turnAbsolutePosition;
  }

  void close() {
    io.close();
  }

  void updateConfiguration(boolean disabled) {
    io.updateConfigurationState(disabled);
    if (RuntimeModeManager.allowsTuning(false)) {
      if (advanceGainChecks()) {
        desiredConfiguration = readConfiguration();
        desiredKs = krakenDrivekS.get();
        desiredKv = krakenDrivekV.get();
      }
      if (disabled
          && DriverStationBackend.isDisabled()
          && !desiredConfiguration.equals(requestedConfiguration)) {
        if (io.requestConfiguration(desiredConfiguration))
          requestedConfiguration = desiredConfiguration;
      }
      if (disabled
          && DriverStationBackend.isDisabled()
          && io.isConfigurationReady()
          && desiredConfiguration.equals(requestedConfiguration)
          && (appliedKs != desiredKs || appliedKv != desiredKv)) {
        krakenFfModel = new SimpleMotorFeedforward(desiredKs, desiredKv);
        appliedKs = desiredKs;
        appliedKv = desiredKv;
      }
    }
  }

  public void captureZeroTrim() {
    io.captureZeroTrim();
  }

  public void clearZeroTrim() {
    io.clearZeroTrim();
  }

  public double getZeroTrimRotations() {
    return inputs.turnZeroTrimRotations;
  }

  private double getWheelRadiusMeters() {
    return SwerveConstants.getWheelRadiusMeters();
  }

  private double getCompensatedDrivePositionRad(double rawDrivePositionRad) {
    return getCompensatedDrivePositionRad(rawDrivePositionRad, -1);
  }

  private double getCompensatedDrivePositionRad(double rawDrivePositionRad, int sampleIndex) {
    double steerPositionRotations =
        sampleIndex >= 0 && sampleIndex < inputs.odometryTurnPositionsRotations.length
            ? inputs.odometryTurnPositionsRotations[sampleIndex]
            : inputs.turnPositionRotations;
    return rawDrivePositionRad
        - steerPositionRotations
            * 2.0
            * Math.PI
            * SwerveConstants.getCouplingWheelRadiansPerSteerRadian();
  }

  private double getCompensatedDriveVelocityRadPerSec(double rawDriveVelocityRadPerSec) {
    return rawDriveVelocityRadPerSec
        - inputs.turnVelocityRadPerSec * SwerveConstants.getCouplingWheelRadiansPerSteerRadian();
  }
}
