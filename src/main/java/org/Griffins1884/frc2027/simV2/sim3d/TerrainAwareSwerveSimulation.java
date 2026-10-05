package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Translation3d;
import org.wpilib.math.kinematics.ChassisVelocities;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import org.Griffins1884.frc2027.simV2.sim3d.maple.MapleSwerveDriveBackend;
import org.Griffins1884.frc2027.simV2.simulation.drivesims.GyroSimulation;
import org.Griffins1884.frc2027.simV2.simulation.drivesims.SwerveDriveSimulation;
import org.Griffins1884.frc2027.simV2.simulation.drivesims.SwerveModuleSimulation;

/**
 * Shared terrain-aware wrapper around Maple's swerve simulator.
 *
 * <p>Maple remains the source of truth for planar drivetrain motion. This wrapper adds shared roll,
 * pitch, and height state derived from a caller-provided terrain model so gyro simulation,
 * autonomous, and 3D visualization use the same terrain sample.
 */
public final class TerrainAwareSwerveSimulation implements CommandableDriveSimulationAdapter {
  private static final double GRAVITY_MPS2 = 9.80665;
  private static final double SUPPORTED_ATTITUDE_TIME_CONSTANT_SEC = 0.05;
  private static final double RECONTACT_ATTITUDE_TIME_CONSTANT_SEC = 0.08;
  private static final double AIRBORNE_ATTITUDE_DAMPING = 2.0;
  private static final double LAUNCH_VERTICAL_SPEED_THRESHOLD_MPS = 0.35;
  private static final double LAUNCH_SEPARATION_MARGIN_METERS = 0.01;
  private static final double MEANINGFUL_AIRBORNE_GAP_METERS = 0.01;
  private static final double MAX_SUPPORTED_UPWARD_VELOCITY_MPS = 0.15;
  private static final double SUPPORT_CONTACT_TOLERANCE_METERS = 0.005;
  private static final double SUPPORT_FACTOR_FADE_METERS = 0.02;

  private final SwerveDriveBackend driveBackend;
  private final TerrainModel terrainModel;
  private final TerrainContactModel terrainContactModel;
  private final ChassisFootprint chassisFootprint;
  private final ChassisMassProperties chassisMassProperties;
  private final DoubleSupplier clockSecondsSupplier;

  private double lastSampleTimestampSec = Double.NaN;
  private Pose2d lastSamplePose2d = null;
  private ChassisVelocities lastRobotRelativeSpeeds = null;
  private ChassisVelocities lastFieldRelativeSpeeds = null;
  private TerrainSample cachedSample = null;
  private TerrainContactSample cachedTerrainContactSample = null;
  private SupportPlaneSample cachedSupportPlaneSample = null;
  private ChassisState3d cachedChassisState = null;
  private SwerveTractionState cachedTractionState = null;
  private double rollRateRadPerSec = 0.0;
  private double pitchRateRadPerSec = 0.0;
  private boolean bodySupported = true;
  private double bodyHeightMeters = 0.0;
  private double bodyVerticalVelocityMps = 0.0;
  private double bodyRollRad = 0.0;
  private double bodyPitchRad = 0.0;

  public TerrainAwareSwerveSimulation(
      SwerveDriveSimulation mapleSimulation, TerrainModel terrainModel) {
    this(new MapleSwerveDriveBackend(mapleSimulation), terrainModel);
  }

  public TerrainAwareSwerveSimulation(SwerveDriveBackend driveBackend, TerrainModel terrainModel) {
    this(driveBackend, terrainModel, null, null, null, () -> System.nanoTime() * 1.0e-9);
  }

  public TerrainAwareSwerveSimulation(
      SwerveDriveBackend driveBackend,
      TerrainContactModel terrainContactModel,
      ChassisFootprint chassisFootprint,
      ChassisMassProperties chassisMassProperties) {
    this(
        driveBackend,
        terrainContactModel,
        terrainContactModel,
        chassisFootprint,
        chassisMassProperties,
        () -> System.nanoTime() * 1.0e-9);
  }

  public TerrainAwareSwerveSimulation(
      SwerveDriveBackend driveBackend,
      TerrainModel terrainModel,
      DoubleSupplier clockSecondsSupplier) {
    this(driveBackend, terrainModel, null, null, null, clockSecondsSupplier);
  }

  public TerrainAwareSwerveSimulation(
      SwerveDriveBackend driveBackend,
      TerrainContactModel terrainContactModel,
      ChassisFootprint chassisFootprint,
      ChassisMassProperties chassisMassProperties,
      DoubleSupplier clockSecondsSupplier) {
    this(
        driveBackend,
        terrainContactModel,
        terrainContactModel,
        chassisFootprint,
        chassisMassProperties,
        clockSecondsSupplier);
  }

  private TerrainAwareSwerveSimulation(
      SwerveDriveBackend driveBackend,
      TerrainModel terrainModel,
      TerrainContactModel terrainContactModel,
      ChassisFootprint chassisFootprint,
      ChassisMassProperties chassisMassProperties,
      DoubleSupplier clockSecondsSupplier) {
    this.driveBackend = Objects.requireNonNull(driveBackend);
    this.terrainModel = Objects.requireNonNull(terrainModel);
    this.terrainContactModel = terrainContactModel;
    this.chassisFootprint = chassisFootprint;
    this.chassisMassProperties = chassisMassProperties;
    this.clockSecondsSupplier = Objects.requireNonNull(clockSecondsSupplier);
  }

  public SwerveDriveSimulation mapleSimulation() {
    if (driveBackend instanceof MapleSwerveDriveBackend mapleBackend) {
      return mapleBackend.mapleSimulation();
    }
    throw new IllegalStateException("Current backend is not backed by Maple SwerveDriveSimulation");
  }

  public GyroSimulation getGyroSimulation() {
    return driveBackend.getGyroSimulation();
  }

  public SwerveModuleSimulation[] getModules() {
    return driveBackend.getModuleSimulations();
  }

  public Pose2d getSimulatedDriveTrainPose() {
    return getPose2d();
  }

  public void setSimulationWorldPose(Pose2d pose) {
    resetPose(pose);
  }

  @Override
  public Pose2d getPose2d() {
    return driveBackend.getPose2d();
  }

  @Override
  public org.wpilib.math.geometry.Pose3d getPose3d() {
    return getChassisState3d().pose();
  }

  @Override
  public synchronized ChassisState3d getChassisState3d() {
    return sampleChassisState();
  }

  @Override
  public synchronized TerrainContactSample getTerrainContactSample() {
    sampleChassisState();
    return cachedTerrainContactSample;
  }

  @Override
  public synchronized SwerveTractionState getTractionState() {
    sampleChassisState();
    return cachedTractionState;
  }

  @Override
  public ChassisVelocities getRobotRelativeChassisSpeeds() {
    return driveBackend.getRobotRelativeChassisSpeeds();
  }

  @Override
  public ChassisVelocities getFieldRelativeChassisSpeeds() {
    return driveBackend.getFieldRelativeChassisSpeeds();
  }

  @Override
  public synchronized SimImuSample getImuSample() {
    ChassisState3d chassisState3d = getChassisState3d();
    return new SimImuSample(chassisState3d.pose().getRotation(), chassisState3d.angularVelocityRadPerSec());
  }

  @Override
  public synchronized DriveSimulationState getState() {
    ChassisState3d chassisState3d = getChassisState3d();
    TerrainSample terrainSample = cachedSample;
    return new DriveSimulationState(
        getPose2d(),
        chassisState3d.pose(),
        chassisState3d,
        cachedTerrainContactSample,
        cachedTractionState,
        getRobotRelativeChassisSpeeds(),
        getFieldRelativeChassisSpeeds(),
        getImuSample(),
        terrainSample);
  }

  @Override
  public void resetPose(Pose2d pose) {
    driveBackend.setPose(pose);
    invalidateCachedTerrainState();
  }

  @Override
  public void resetState(Pose2d pose, ChassisVelocities robotRelativeSpeeds) {
    driveBackend.setPose(pose);
    driveBackend.setRobotRelativeChassisSpeeds(robotRelativeSpeeds);
    invalidateCachedTerrainState();
  }

  @Override
  public void setCommandedRobotRelativeSpeeds(ChassisVelocities robotRelativeSpeeds) {
    driveBackend.setRobotRelativeChassisSpeeds(robotRelativeSpeeds);
  }

  private void invalidateCachedTerrainState() {
    lastSampleTimestampSec = Double.NaN;
    lastSamplePose2d = null;
    lastRobotRelativeSpeeds = null;
    lastFieldRelativeSpeeds = null;
    cachedSample = null;
    cachedTerrainContactSample = null;
    cachedSupportPlaneSample = null;
    cachedChassisState = null;
    cachedTractionState = null;
    rollRateRadPerSec = 0.0;
    pitchRateRadPerSec = 0.0;
    bodySupported = true;
    bodyHeightMeters = 0.0;
    bodyVerticalVelocityMps = 0.0;
    bodyRollRad = 0.0;
    bodyPitchRad = 0.0;
  }

  public synchronized TerrainSample getTerrainSample() {
    sampleChassisState();
    return cachedSample;
  }

  private ChassisState3d sampleChassisState() {
    Pose2d pose2d = driveBackend.getPose2d();
    ChassisVelocities robotRelativeChassisSpeeds = driveBackend.getRobotRelativeChassisSpeeds();
    ChassisVelocities fieldRelativeChassisSpeeds = driveBackend.getFieldRelativeChassisSpeeds();
    if (cachedChassisState != null
        && samePose(pose2d, lastSamplePose2d)
        && sameChassisSpeeds(robotRelativeChassisSpeeds, lastRobotRelativeSpeeds)
        && sameChassisSpeeds(fieldRelativeChassisSpeeds, lastFieldRelativeSpeeds)) {
      return cachedChassisState;
    }

    double now = clockSecondsSupplier.getAsDouble();

    TerrainContactSample newTerrainContactSample = null;
    TerrainSample newSample;
    SupportPlaneSample newSupportPlaneSample;
    if (terrainContactModel != null && chassisFootprint != null) {
      newTerrainContactSample = terrainContactModel.sampleContact(pose2d, chassisFootprint);
      newSample = newTerrainContactSample.terrainSample();
      newSupportPlaneSample = computeSupportPlaneSample(pose2d, newSample);
    } else {
      newSample = terrainModel.sample(pose2d);
      newSupportPlaneSample = SupportPlaneSample.fromTerrainSample(newSample);
    }
    Translation3d previousFieldVelocity =
        cachedChassisState == null
            ? new Translation3d()
            : cachedChassisState.fieldRelativeLinearVelocityMetersPerSec();
    Translation3d fieldVelocity;
    Translation3d fieldAcceleration = new Translation3d();
    if (cachedSample != null && Double.isFinite(lastSampleTimestampSec)) {
      double dt =
          resolveDtSeconds(
              now, pose2d, robotRelativeChassisSpeeds, fieldRelativeChassisSpeeds);
      if (dt > 1e-5) {
        double supportVerticalVelocity =
            cachedSupportPlaneSample == null
                ? 0.0
                : (newSupportPlaneSample.heightMeters() - cachedSupportPlaneSample.heightMeters()) / dt;
        updateBodyState(newSupportPlaneSample, supportVerticalVelocity, previousFieldVelocity.getZ(), dt);
        fieldVelocity =
            new Translation3d(
                fieldRelativeChassisSpeeds.vx,
                fieldRelativeChassisSpeeds.vx,
                bodyVerticalVelocityMps);
        fieldAcceleration =
            new Translation3d(
                (fieldVelocity.getX() - previousFieldVelocity.getX()) / dt,
                (fieldVelocity.getY() - previousFieldVelocity.getY()) / dt,
                (fieldVelocity.getZ() - previousFieldVelocity.getZ()) / dt);
      } else {
        initializeBodyState(newSupportPlaneSample);
        fieldVelocity =
            new Translation3d(
                fieldRelativeChassisSpeeds.vx,
                fieldRelativeChassisSpeeds.vx,
                bodyVerticalVelocityMps);
      }
    } else {
      initializeBodyState(newSupportPlaneSample);
      fieldVelocity =
          new Translation3d(
              fieldRelativeChassisSpeeds.vx,
              fieldRelativeChassisSpeeds.vx,
              bodyVerticalVelocityMps);
    }

    Pose3d pose3d =
        new Pose3d(
            pose2d.getX(),
            pose2d.getY(),
            bodyHeightMeters,
            new org.wpilib.math.geometry.Rotation3d(
                bodyRollRad, bodyPitchRad, pose2d.getRotation().getRadians()));

    cachedSample = newSample;
    cachedTerrainContactSample = newTerrainContactSample;
    cachedSupportPlaneSample = newSupportPlaneSample;
    cachedChassisState =
        new ChassisState3d(
            pose3d,
            fieldVelocity,
            fieldAcceleration,
            new AngularVelocity3d(
                rollRateRadPerSec,
                pitchRateRadPerSec,
                robotRelativeChassisSpeeds.omega));
    cachedTractionState =
        !bodySupported
            ? SwerveTractionState.unsupported()
            : cachedTerrainContactSample != null && chassisMassProperties != null
            ? applySupportMask(
                SwerveLoadTransferEstimator.estimate(
                    cachedChassisState, cachedTerrainContactSample, chassisMassProperties),
                newSupportPlaneSample)
            : null;
    lastSamplePose2d = pose2d;
    lastRobotRelativeSpeeds = robotRelativeChassisSpeeds;
    lastFieldRelativeSpeeds = fieldRelativeChassisSpeeds;
    lastSampleTimestampSec = now;
    return cachedChassisState;
  }

  private double resolveDtSeconds(
      double now,
      Pose2d pose2d,
      ChassisVelocities robotRelativeChassisSpeeds,
      ChassisVelocities fieldRelativeChassisSpeeds) {
    if (lastSamplePose2d != null) {
      double planarDeltaMeters =
          pose2d.getTranslation().getDistance(lastSamplePose2d.getTranslation());
      double averageLinearSpeedMps =
          averageLinearSpeedMetersPerSecond(fieldRelativeChassisSpeeds, lastFieldRelativeSpeeds);
      if (planarDeltaMeters > 1e-6 && averageLinearSpeedMps > 1e-6) {
        double kinematicDt = planarDeltaMeters / averageLinearSpeedMps;
        if (kinematicDt > 1e-5 && kinematicDt <= 0.1) {
          return kinematicDt;
        }
      }

      double headingDeltaRad =
          Math.abs(pose2d.getRotation().minus(lastSamplePose2d.getRotation()).getRadians());
      double averageOmegaRadPerSec =
          averageOmegaRadiansPerSecond(robotRelativeChassisSpeeds, lastRobotRelativeSpeeds);
      if (headingDeltaRad > 1e-6 && averageOmegaRadPerSec > 1e-6) {
        double rotationalDt = headingDeltaRad / averageOmegaRadPerSec;
        if (rotationalDt > 1e-5 && rotationalDt <= 0.1) {
          return rotationalDt;
        }
      }
    }

    return now - lastSampleTimestampSec;
  }

  private static double averageLinearSpeedMetersPerSecond(
      ChassisVelocities current, ChassisVelocities previous) {
    if (current == null && previous == null) {
      return 0.0;
    }
    if (current == null) {
      return Math.hypot(previous.vx, previous.vx);
    }
    if (previous == null) {
      return Math.hypot(current.vx, current.vx);
    }
    return 0.5
        * (Math.hypot(current.vx, current.vx)
            + Math.hypot(previous.vx, previous.vx));
  }

  private static double averageOmegaRadiansPerSecond(
      ChassisVelocities current, ChassisVelocities previous) {
    if (current == null && previous == null) {
      return 0.0;
    }
    if (current == null) {
      return Math.abs(previous.omega);
    }
    if (previous == null) {
      return Math.abs(current.omega);
    }
    return 0.5
        * (Math.abs(current.omega) + Math.abs(previous.omega));
  }

  private static boolean samePose(Pose2d first, Pose2d second) {
    if (first == second) {
      return true;
    }
    if (first == null || second == null) {
      return false;
    }
    return Math.abs(first.getX() - second.getX()) < 1e-9
        && Math.abs(first.getY() - second.getY()) < 1e-9
        && Math.abs(first.getRotation().getRadians() - second.getRotation().getRadians()) < 1e-9;
  }

  private static boolean sameChassisSpeeds(ChassisVelocities first, ChassisVelocities second) {
    if (first == second) {
      return true;
    }
    if (first == null || second == null) {
      return false;
    }
    return Math.abs(first.vx - second.vx) < 1e-9
        && Math.abs(first.vy - second.vy) < 1e-9
        && Math.abs(first.omega - second.omega) < 1e-9;
  }

  @SuppressWarnings("unused")
  private void initializeBodyState(TerrainSample terrainSample) {
    bodySupported = true;
    bodyHeightMeters = terrainSample.heightMeters();
    bodyVerticalVelocityMps = 0.0;
    bodyRollRad = terrainSample.rollRadians();
    bodyPitchRad = terrainSample.pitchRadians();
    rollRateRadPerSec = 0.0;
    pitchRateRadPerSec = 0.0;
  }

  private void initializeBodyState(SupportPlaneSample supportPlaneSample) {
    bodySupported = true;
    bodyHeightMeters = supportPlaneSample.heightMeters();
    bodyVerticalVelocityMps = 0.0;
    bodyRollRad = supportPlaneSample.rollRadians();
    bodyPitchRad = supportPlaneSample.pitchRadians();
    rollRateRadPerSec = 0.0;
    pitchRateRadPerSec = 0.0;
  }

  private void updateBodyState(
      SupportPlaneSample supportPlaneSample,
      double supportVerticalVelocity,
      double previousBodyVerticalVelocity,
      double dt) {
    boolean recontactedThisStep = false;
    if (bodySupported
        && previousBodyVerticalVelocity > LAUNCH_VERTICAL_SPEED_THRESHOLD_MPS
        && supportVerticalVelocity + LAUNCH_SEPARATION_MARGIN_METERS / dt < previousBodyVerticalVelocity) {
      bodySupported = false;
      bodyVerticalVelocityMps = previousBodyVerticalVelocity;
      bodyHeightMeters += bodyVerticalVelocityMps * dt;
    }

    if (!bodySupported) {
      bodyVerticalVelocityMps -= GRAVITY_MPS2 * dt;
      bodyHeightMeters += bodyVerticalVelocityMps * dt;
      if (bodyHeightMeters <= supportPlaneSample.heightMeters()) {
        bodySupported = true;
        bodyHeightMeters = supportPlaneSample.heightMeters();
        bodyVerticalVelocityMps = limitSupportedVerticalVelocity(supportVerticalVelocity);
        recontactedThisStep = true;
      }
    }

    if (bodySupported) {
      bodyHeightMeters = supportPlaneSample.heightMeters();
      bodyVerticalVelocityMps = limitSupportedVerticalVelocity(supportVerticalVelocity);
      followSupportedAttitude(
          supportPlaneSample,
          dt,
          recontactedThisStep
              ? RECONTACT_ATTITUDE_TIME_CONSTANT_SEC
              : SUPPORTED_ATTITUDE_TIME_CONSTANT_SEC);
      return;
    }

    followAirborneAttitude(dt);
  }

  private void followSupportedAttitude(
      SupportPlaneSample supportPlaneSample, double dt, double timeConstantSeconds) {
    double previousRoll = bodyRollRad;
    double previousPitch = bodyPitchRad;
    double blend = 1.0 - Math.exp(-dt / timeConstantSeconds);
    bodyRollRad += (supportPlaneSample.rollRadians() - bodyRollRad) * blend;
    bodyPitchRad += (supportPlaneSample.pitchRadians() - bodyPitchRad) * blend;
    rollRateRadPerSec = (bodyRollRad - previousRoll) / dt;
    pitchRateRadPerSec = (bodyPitchRad - previousPitch) / dt;
  }

  private static double limitSupportedVerticalVelocity(double supportVerticalVelocity) {
    if (!Double.isFinite(supportVerticalVelocity)) {
      return 0.0;
    }
    return Math.min(supportVerticalVelocity, MAX_SUPPORTED_UPWARD_VELOCITY_MPS);
  }

  private void followAirborneAttitude(double dt) {
    rollRateRadPerSec -= rollRateRadPerSec * AIRBORNE_ATTITUDE_DAMPING * dt;
    pitchRateRadPerSec -= pitchRateRadPerSec * AIRBORNE_ATTITUDE_DAMPING * dt;
    bodyRollRad += rollRateRadPerSec * dt;
    bodyPitchRad += pitchRateRadPerSec * dt;
  }

  public synchronized double getRollRateRadPerSec() {
    getTerrainSample();
    return rollRateRadPerSec;
  }

  public synchronized double getPitchRateRadPerSec() {
    getTerrainSample();
    return pitchRateRadPerSec;
  }

  public synchronized boolean isBodySupported() {
    getTerrainSample();
    return bodySupported;
  }

  public synchronized SupportDiagnostics getSupportDiagnostics() {
    sampleChassisState();
    double terrainHeightMeters = cachedSample == null ? Double.NaN : cachedSample.heightMeters();
    double supportPlaneHeightMeters =
        cachedSupportPlaneSample == null ? Double.NaN : cachedSupportPlaneSample.heightMeters();
    int supportContactCount =
        !bodySupported || cachedSupportPlaneSample == null
            ? 0
            : cachedSupportPlaneSample.supportedCornerCount();
    double chassisZAboveTerrainMeters =
        Double.isFinite(terrainHeightMeters) ? bodyHeightMeters - terrainHeightMeters : Double.NaN;
    double bodyBottomToTerrainGapMeters =
        Double.isFinite(supportPlaneHeightMeters)
            ? bodyHeightMeters - supportPlaneHeightMeters
            : Double.NaN;
    boolean actualAirborne =
        supportContactCount == 0
            && Double.isFinite(bodyBottomToTerrainGapMeters)
            && bodyBottomToTerrainGapMeters >= MEANINGFUL_AIRBORNE_GAP_METERS;
    return new SupportDiagnostics(
        bodySupported,
        supportContactCount,
        terrainHeightMeters,
        supportPlaneHeightMeters,
        chassisZAboveTerrainMeters,
        bodyBottomToTerrainGapMeters,
        cachedSupportPlaneSample != null && cachedSupportPlaneSample.frontLeftSupported(),
        cachedSupportPlaneSample != null && cachedSupportPlaneSample.frontRightSupported(),
        cachedSupportPlaneSample != null && cachedSupportPlaneSample.rearLeftSupported(),
        cachedSupportPlaneSample != null && cachedSupportPlaneSample.rearRightSupported(),
        actualAirborne);
  }

  private SupportPlaneSample computeSupportPlaneSample(Pose2d robotPose, TerrainSample fallbackSample) {
    double halfLength = chassisFootprint.lengthMeters() * 0.5;
    double halfWidth = chassisFootprint.widthMeters() * 0.5;
    CornerHeight[] cornerHeights =
        new CornerHeight[] {
          sampleCornerHeight(robotPose, SwerveCorner.FRONT_LEFT, halfLength, halfWidth),
          sampleCornerHeight(robotPose, SwerveCorner.FRONT_RIGHT, halfLength, -halfWidth),
          sampleCornerHeight(robotPose, SwerveCorner.REAR_LEFT, -halfLength, halfWidth),
          sampleCornerHeight(robotPose, SwerveCorner.REAR_RIGHT, -halfLength, -halfWidth)
        };

    SupportPlaneSolution solution = solveSupportPlane(cornerHeights);
    if (solution == null) {
      return SupportPlaneSample.fromTerrainSample(fallbackSample);
    }

    return new SupportPlaneSample(
        solution.centerHeightMeters(),
        Math.atan(solution.leftSlopeMetersPerMeter()),
        -Math.atan(solution.forwardSlopeMetersPerMeter()),
        supportFactorFor(
            solution.predictedHeightMeters(SwerveCorner.FRONT_LEFT),
            cornerHeights[0].heightMeters()),
        supportFactorFor(
            solution.predictedHeightMeters(SwerveCorner.FRONT_RIGHT),
            cornerHeights[1].heightMeters()),
        supportFactorFor(
            solution.predictedHeightMeters(SwerveCorner.REAR_LEFT),
            cornerHeights[2].heightMeters()),
        supportFactorFor(
            solution.predictedHeightMeters(SwerveCorner.REAR_RIGHT),
            cornerHeights[3].heightMeters()));
  }

  private static double supportFactorFor(double predictedHeightMeters, double actualHeightMeters) {
    double marginMeters = predictedHeightMeters - actualHeightMeters;
    if (marginMeters >= -SUPPORT_CONTACT_TOLERANCE_METERS) {
      return 1.0;
    }
    return clamp(
        1.0 + ((marginMeters + SUPPORT_CONTACT_TOLERANCE_METERS) / SUPPORT_FACTOR_FADE_METERS),
        0.0,
        1.0);
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private CornerHeight sampleCornerHeight(
      Pose2d robotPose, SwerveCorner corner, double localX, double localY) {
    Pose2d cornerPose =
        new Pose2d(
            robotPose
                .getTranslation()
                .plus(new org.wpilib.math.geometry.Translation2d(localX, localY).rotateBy(robotPose.getRotation())),
            robotPose.getRotation());
    return new CornerHeight(corner, localX, localY, terrainModel.sample(cornerPose).heightMeters());
  }

  private SupportPlaneSolution solveSupportPlane(CornerHeight[] cornerHeights) {
    SupportPlaneSolution best = null;
    int[][] supportTriples = {{0, 1, 2}, {0, 1, 3}, {0, 2, 3}, {1, 2, 3}};
    for (int[] supportTriple : supportTriples) {
      double[] planeCoefficients =
          solvePlaneCoefficients(
              cornerHeights[supportTriple[0]],
              cornerHeights[supportTriple[1]],
              cornerHeights[supportTriple[2]]);
      if (planeCoefficients == null) {
        continue;
      }

      SupportPlaneSolution candidate =
          new SupportPlaneSolution(
              planeCoefficients[0], planeCoefficients[1], planeCoefficients[2], cornerHeights);
      if (!candidate.supportsAllCorners()) {
        continue;
      }
      if (best == null || candidate.centerHeightMeters() < best.centerHeightMeters()) {
        best = candidate;
      }
    }
    return best;
  }

  private double[] solvePlaneCoefficients(
      CornerHeight firstCorner, CornerHeight secondCorner, CornerHeight thirdCorner) {
    double[][] matrix = {
      {1.0, firstCorner.localX(), firstCorner.localY()},
      {1.0, secondCorner.localX(), secondCorner.localY()},
      {1.0, thirdCorner.localX(), thirdCorner.localY()}
    };
    double[] rhs = {
      firstCorner.heightMeters(), secondCorner.heightMeters(), thirdCorner.heightMeters()
    };
    return solve3x3(matrix, rhs);
  }

  private static double[] solve3x3(double[][] matrix, double[] rhs) {
    double[][] augmented = new double[3][4];
    for (int row = 0; row < 3; row++) {
      System.arraycopy(matrix[row], 0, augmented[row], 0, 3);
      augmented[row][3] = rhs[row];
    }

    for (int pivot = 0; pivot < 3; pivot++) {
      int bestRow = pivot;
      for (int row = pivot + 1; row < 3; row++) {
        if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[bestRow][pivot])) {
          bestRow = row;
        }
      }
      if (Math.abs(augmented[bestRow][pivot]) < 1e-9) {
        return null;
      }
      if (bestRow != pivot) {
        double[] tmp = augmented[pivot];
        augmented[pivot] = augmented[bestRow];
        augmented[bestRow] = tmp;
      }

      double pivotValue = augmented[pivot][pivot];
      for (int column = pivot; column < 4; column++) {
        augmented[pivot][column] /= pivotValue;
      }
      for (int row = 0; row < 3; row++) {
        if (row == pivot) {
          continue;
        }
        double factor = augmented[row][pivot];
        for (int column = pivot; column < 4; column++) {
          augmented[row][column] -= factor * augmented[pivot][column];
        }
      }
    }

    return new double[] {augmented[0][3], augmented[1][3], augmented[2][3]};
  }

  private SwerveTractionState applySupportMask(
      SwerveTractionState tractionState, SupportPlaneSample supportPlaneSample) {
    if (supportPlaneSample == null || supportPlaneSample.allCornersSupported()) {
      return tractionState;
    }

    double totalNormalForce = tractionState.totalNormalForceNewtons();
    double baseWheelLoad = totalNormalForce / 4.0;
    double[] supportFactors = {
      supportPlaneSample.supportFactor(SwerveCorner.FRONT_LEFT),
      supportPlaneSample.supportFactor(SwerveCorner.FRONT_RIGHT),
      supportPlaneSample.supportFactor(SwerveCorner.REAR_LEFT),
      supportPlaneSample.supportFactor(SwerveCorner.REAR_RIGHT)
    };
    WheelLoadSample[] maskedLoads = {
      scaleWheelLoad(tractionState.frontLeft(), supportFactors[0]),
      scaleWheelLoad(tractionState.frontRight(), supportFactors[1]),
      scaleWheelLoad(tractionState.rearLeft(), supportFactors[2]),
      scaleWheelLoad(tractionState.rearRight(), supportFactors[3])
    };

    double supportedNormalForce =
        maskedLoads[0].normalForceNewtons()
            + maskedLoads[1].normalForceNewtons()
            + maskedLoads[2].normalForceNewtons()
            + maskedLoads[3].normalForceNewtons();
    if (supportedNormalForce <= 1e-9) {
      return SwerveTractionState.unsupported();
    }

    double supportScale = totalNormalForce / supportedNormalForce;
    WheelLoadSample frontLeft = renormalizeWheelLoad(maskedLoads[0], supportScale, baseWheelLoad);
    WheelLoadSample frontRight = renormalizeWheelLoad(maskedLoads[1], supportScale, baseWheelLoad);
    WheelLoadSample rearLeft = renormalizeWheelLoad(maskedLoads[2], supportScale, baseWheelLoad);
    WheelLoadSample rearRight = renormalizeWheelLoad(maskedLoads[3], supportScale, baseWheelLoad);

    return new SwerveTractionState(
        frontLeft,
        frontRight,
        rearLeft,
        rearRight,
        totalNormalForce,
        (frontLeft.normalizedLoad()
                + frontRight.normalizedLoad()
                + rearLeft.normalizedLoad()
                + rearRight.normalizedLoad())
            / 4.0,
        tractionState.tractionAvailable());
  }

  private static WheelLoadSample scaleWheelLoad(WheelLoadSample wheelLoadSample, double scale) {
    return new WheelLoadSample(
        wheelLoadSample.normalForceNewtons() * scale,
        wheelLoadSample.tractionCapacityNewtons() * scale,
        wheelLoadSample.normalizedLoad() * scale);
  }

  private static WheelLoadSample renormalizeWheelLoad(
      WheelLoadSample wheelLoadSample, double scale, double baseWheelLoad) {
    double normalForce = wheelLoadSample.normalForceNewtons() * scale;
    return new WheelLoadSample(
        normalForce,
        wheelLoadSample.tractionCapacityNewtons() * scale,
        baseWheelLoad > 1e-9 ? normalForce / baseWheelLoad : 0.0);
  }

  private record CornerHeight(
      SwerveCorner corner, double localX, double localY, double heightMeters) {}

  private record SupportPlaneSample(
      double heightMeters,
      double rollRadians,
      double pitchRadians,
      double frontLeftSupportFactor,
      double frontRightSupportFactor,
      double rearLeftSupportFactor,
      double rearRightSupportFactor) {
    static SupportPlaneSample fromTerrainSample(TerrainSample terrainSample) {
      return new SupportPlaneSample(
          terrainSample.heightMeters(),
          terrainSample.rollRadians(),
          terrainSample.pitchRadians(),
          1.0,
          1.0,
          1.0,
          1.0);
    }

    boolean allCornersSupported() {
      return frontLeftSupported()
          && frontRightSupported()
          && rearLeftSupported()
          && rearRightSupported();
    }

    int supportedCornerCount() {
      int count = 0;
      if (frontLeftSupported()) {
        count++;
      }
      if (frontRightSupported()) {
        count++;
      }
      if (rearLeftSupported()) {
        count++;
      }
      if (rearRightSupported()) {
        count++;
      }
      return count;
    }

    boolean frontLeftSupported() {
      return frontLeftSupportFactor >= 0.5;
    }

    boolean frontRightSupported() {
      return frontRightSupportFactor >= 0.5;
    }

    boolean rearLeftSupported() {
      return rearLeftSupportFactor >= 0.5;
    }

    boolean rearRightSupported() {
      return rearRightSupportFactor >= 0.5;
    }

    double supportFactor(SwerveCorner corner) {
      return switch (corner) {
        case FRONT_LEFT -> frontLeftSupportFactor;
        case FRONT_RIGHT -> frontRightSupportFactor;
        case REAR_LEFT -> rearLeftSupportFactor;
        case REAR_RIGHT -> rearRightSupportFactor;
      };
    }
  }

  private final class SupportPlaneSolution {
    private final double centerHeightMeters;
    private final double forwardSlopeMetersPerMeter;
    private final double leftSlopeMetersPerMeter;
    private final CornerHeight[] cornerHeights;

    private SupportPlaneSolution(
        double centerHeightMeters,
        double forwardSlopeMetersPerMeter,
        double leftSlopeMetersPerMeter,
        CornerHeight[] cornerHeights) {
      this.centerHeightMeters = centerHeightMeters;
      this.forwardSlopeMetersPerMeter = forwardSlopeMetersPerMeter;
      this.leftSlopeMetersPerMeter = leftSlopeMetersPerMeter;
      this.cornerHeights = cornerHeights;
    }

    double centerHeightMeters() {
      return centerHeightMeters;
    }

    double forwardSlopeMetersPerMeter() {
      return forwardSlopeMetersPerMeter;
    }

    double leftSlopeMetersPerMeter() {
      return leftSlopeMetersPerMeter;
    }

    double predictedHeightMeters(SwerveCorner corner) {
      for (CornerHeight cornerHeight : cornerHeights) {
        if (cornerHeight.corner() == corner) {
          return centerHeightMeters
              + (forwardSlopeMetersPerMeter * cornerHeight.localX())
              + (leftSlopeMetersPerMeter * cornerHeight.localY());
        }
      }
      throw new IllegalArgumentException("Unknown corner " + corner);
    }

    boolean supportsAllCorners() {
      for (CornerHeight cornerHeight : cornerHeights) {
        if (predictedHeightMeters(cornerHeight.corner()) + SUPPORT_CONTACT_TOLERANCE_METERS
            < cornerHeight.heightMeters()) {
          return false;
        }
      }
      return true;
    }
  }

  public record SupportDiagnostics(
      boolean bodySupported,
      int supportContactCount,
      double terrainHeightMeters,
      double supportPlaneHeightMeters,
      double chassisZAboveTerrainMeters,
      double bodyBottomToTerrainGapMeters,
      boolean frontLeftSupported,
      boolean frontRightSupported,
      boolean rearLeftSupported,
      boolean rearRightSupported,
      boolean actualAirborne) {}
}
