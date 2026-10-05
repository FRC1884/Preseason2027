package org.Griffins1884.frc2027.simV2.sim3d.native6dof;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Quaternion;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.geometry.Translation3d;
import org.wpilib.math.kinematics.ChassisVelocities;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import org.Griffins1884.frc2027.simV2.sim3d.AngularVelocity3d;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisState3d;
import org.Griffins1884.frc2027.simV2.sim3d.CommandableDriveSimulationAdapter;
import org.Griffins1884.frc2027.simV2.sim3d.DriveSimulationState;
import org.Griffins1884.frc2027.simV2.sim3d.PlanarObstacleContactModel;
import org.Griffins1884.frc2027.simV2.sim3d.PlanarObstacleContactSample;
import org.Griffins1884.frc2027.simV2.sim3d.SimImuSample;
import org.Griffins1884.frc2027.simV2.sim3d.SwerveTractionState;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainContactModel;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainContactSample;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainFeature;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainModel;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainSample;
import org.Griffins1884.frc2027.simV2.sim3d.WheelLoadSample;

/**
 * First GriffinSim-native rigid-body swerve simulator.
 *
 * <p>This adapter owns its own 6DOF chassis state and no longer depends on Maple for XY/yaw. The
 * current implementation is intentionally the smallest viable native path: rigid chassis state,
 * four wheel contact points with compliant contact, steer-angle-dependent drive/lateral forces, and
 * free-flight when contact is lost.
 */
public final class NativeSixDofSwerveSimulation implements CommandableDriveSimulationAdapter {
  private static final double GRAVITY_MPS2 = 9.80665;
  private static final double MAX_STEP_SEC = 0.005;
  private static final double MAX_STATIC_PLANAR_OBSTACLE_CORRECTION_METERS = 0.02;
  private final NativeSixDofSwerveConfig config;
  private final TerrainModel terrainModel;
  private final TerrainContactModel terrainContactModel;
  private final PlanarObstacleContactModel planarObstacleContactModel;
  private final DoubleSupplier clockSecondsSupplier;
  private final double[] moduleSteerAnglesRad;
  private final double[] wheelNormalForcesNewtons;

  private double lastUpdateTimestampSec = Double.NaN;
  private double xMeters = 0.0;
  private double yMeters = 0.0;
  private double zMeters = 0.0;
  private double rollRad = 0.0;
  private double pitchRad = 0.0;
  private double yawRad = 0.0;
  private Translation3d fieldLinearVelocityMetersPerSec = new Translation3d();
  private Translation3d fieldLinearAccelerationMetersPerSecSq = new Translation3d();
  private Translation3d bodyAngularVelocityRadPerSec = new Translation3d();
  private ChassisVelocities commandedRobotRelativeSpeeds = new ChassisVelocities();

  public NativeSixDofSwerveSimulation(
      NativeSixDofSwerveConfig config, TerrainModel terrainModel) {
    this(config, terrainModel, null, () -> System.nanoTime() * 1.0e-9);
  }

  public NativeSixDofSwerveSimulation(
      NativeSixDofSwerveConfig config,
      TerrainContactModel terrainContactModel,
      DoubleSupplier clockSecondsSupplier) {
    this(config, terrainContactModel, terrainContactModel, clockSecondsSupplier);
  }

  public NativeSixDofSwerveSimulation(
      NativeSixDofSwerveConfig config,
      TerrainModel terrainModel,
      DoubleSupplier clockSecondsSupplier) {
    this(config, terrainModel, null, clockSecondsSupplier);
  }

  private NativeSixDofSwerveSimulation(
      NativeSixDofSwerveConfig config,
      TerrainModel terrainModel,
      TerrainContactModel terrainContactModel,
      DoubleSupplier clockSecondsSupplier) {
    this.config = Objects.requireNonNull(config);
    this.terrainModel = Objects.requireNonNull(terrainModel);
    this.terrainContactModel = terrainContactModel;
    this.planarObstacleContactModel = resolvePlanarObstacleContactModel(terrainModel, terrainContactModel);
    this.clockSecondsSupplier = Objects.requireNonNull(clockSecondsSupplier);
    this.moduleSteerAnglesRad = new double[config.moduleLocationsMeters().length];
    this.wheelNormalForcesNewtons = new double[config.moduleLocationsMeters().length];
    resetPose(new Pose2d());
  }

  @Override
  public synchronized Pose2d getPose2d() {
    advanceToNow();
    return currentPose2d();
  }

  @Override
  public synchronized Pose3d getPose3d() {
    return getChassisState3d().pose();
  }

  @Override
  public synchronized ChassisState3d getChassisState3d() {
    advanceToNow();
    return currentChassisState();
  }

  @Override
  public synchronized TerrainContactSample getTerrainContactSample() {
    advanceToNow();
    return currentTerrainContactSample();
  }

  @Override
  public synchronized SwerveTractionState getTractionState() {
    advanceToNow();
    return currentTractionState(currentTerrainContactSample());
  }

  @Override
  public synchronized ChassisVelocities getRobotRelativeChassisSpeeds() {
    advanceToNow();
    return currentRobotRelativeChassisSpeeds();
  }

  @Override
  public synchronized ChassisVelocities getFieldRelativeChassisSpeeds() {
    advanceToNow();
    return currentFieldRelativeChassisSpeeds();
  }

  @Override
  public synchronized SimImuSample getImuSample() {
    advanceToNow();
    return currentImuSample();
  }

  @Override
  public synchronized TerrainSample getTerrainSample() {
    advanceToNow();
    return currentTerrainSample();
  }

  @Override
  public synchronized DriveSimulationState getState() {
    advanceToNow();
    Pose2d pose2d = currentPose2d();
    ChassisState3d chassisState = currentChassisState();
    TerrainContactSample terrainContactSample = currentTerrainContactSample();
    SwerveTractionState tractionState = currentTractionState(terrainContactSample);
    ChassisVelocities robotRelativeSpeeds = currentRobotRelativeChassisSpeeds();
    ChassisVelocities fieldRelativeSpeeds = currentFieldRelativeChassisSpeeds();
    SimImuSample imuSample = currentImuSample();
    return new DriveSimulationState(
        pose2d,
        chassisState.pose(),
        chassisState,
        terrainContactSample,
        tractionState,
        robotRelativeSpeeds,
        fieldRelativeSpeeds,
        imuSample,
        terrainContactSample.terrainSample());
  }

  @Override
  public synchronized void resetPose(Pose2d pose) {
    TerrainSample terrainSample = terrainModel.sample(pose);
    xMeters = pose.getX();
    yMeters = pose.getY();
    yawRad = pose.getRotation().getRadians();
    rollRad = terrainSample.rollRadians();
    pitchRad = terrainSample.pitchRadians();
    zMeters = terrainSample.heightMeters() + config.chassisFootprint().groundClearanceMeters();
    fieldLinearVelocityMetersPerSec = new Translation3d();
    fieldLinearAccelerationMetersPerSecSq = new Translation3d();
    bodyAngularVelocityRadPerSec = new Translation3d();
    commandedRobotRelativeSpeeds = new ChassisVelocities();
    for (int i = 0; i < moduleSteerAnglesRad.length; i++) {
      moduleSteerAnglesRad[i] = 0.0;
      wheelNormalForcesNewtons[i] = 0.0;
    }
    lastUpdateTimestampSec = clockSecondsSupplier.getAsDouble();
  }

  @Override
  public synchronized void resetState(Pose2d pose, ChassisVelocities robotRelativeSpeeds) {
    resetPose(pose);
    setCommandedRobotRelativeSpeeds(robotRelativeSpeeds);
    fieldLinearVelocityMetersPerSec =
        rotateRobotPlanarToField(
            robotRelativeSpeeds.vx,
            robotRelativeSpeeds.vy,
            yawRad);
    bodyAngularVelocityRadPerSec =
        new Translation3d(0.0, 0.0, robotRelativeSpeeds.omega);
  }

  public synchronized void setCommandedRobotRelativeSpeeds(ChassisVelocities robotRelativeSpeeds) {
    commandedRobotRelativeSpeeds = Objects.requireNonNull(robotRelativeSpeeds);
  }

  synchronized void setBodyAngularVelocityForTesting(Translation3d angularVelocityBodyRadPerSec) {
    bodyAngularVelocityRadPerSec = Objects.requireNonNull(angularVelocityBodyRadPerSec);
  }

  synchronized void setFieldLinearVelocityForTesting(Translation3d fieldLinearVelocityMetersPerSec) {
    this.fieldLinearVelocityMetersPerSec = Objects.requireNonNull(fieldLinearVelocityMetersPerSec);
  }

  synchronized void setPose3dForTesting(Pose3d pose) {
    Objects.requireNonNull(pose);
    xMeters = pose.getX();
    yMeters = pose.getY();
    zMeters = pose.getZ();
    rollRad = pose.getRotation().getX();
    pitchRad = pose.getRotation().getY();
    yawRad = pose.getRotation().getZ();
  }

  private void advanceToNow() {
    double nowSec = clockSecondsSupplier.getAsDouble();
    if (!Double.isFinite(lastUpdateTimestampSec)) {
      lastUpdateTimestampSec = nowSec;
      return;
    }

    double remainingDtSec = Math.max(0.0, nowSec - lastUpdateTimestampSec);
    while (remainingDtSec > 1e-9) {
      double stepSec = Math.min(MAX_STEP_SEC, remainingDtSec);
      integrateStep(stepSec);
      remainingDtSec -= stepSec;
      lastUpdateTimestampSec += stepSec;
    }
  }

  private void integrateStep(double dtSec) {
    Translation3d previousFieldVelocity = fieldLinearVelocityMetersPerSec;
    double previousXMeters = xMeters;
    double previousYMeters = yMeters;
    double previousRollRad = rollRad;
    double previousPitchRad = pitchRad;
    double previousYawRad = yawRad;
    Translation3d totalForceWorld =
        new Translation3d(0.0, 0.0, -config.chassisMassProperties().massKg() * GRAVITY_MPS2);
    Translation3d totalTorqueWorld = new Translation3d();
    double nominalGrip = config.chassisMassProperties().nominalTireGripCoefficient();
    Translation2d[] moduleLocations = config.moduleLocationsMeters();
    DesiredWheelState[] desiredWheelStates = desiredWheelStates(commandedRobotRelativeSpeeds, moduleLocations);
    Translation3d angularVelocityWorld =
        rotateBodyToWorld(bodyAngularVelocityRadPerSec, rollRad, pitchRad, yawRad);

    for (int i = 0; i < moduleLocations.length; i++) {
      moduleSteerAnglesRad[i] =
          advanceAngle(
              moduleSteerAnglesRad[i],
              desiredWheelStates[i].steerAngleRad(),
              config.maxSteerRateRadPerSec() * dtSec);

      Translation3d contactOffsetBody =
          new Translation3d(
              moduleLocations[i].getX(),
              moduleLocations[i].getY(),
              -config.chassisFootprint().groundClearanceMeters());
      Translation3d contactOffsetWorld = rotateBodyToWorld(contactOffsetBody, rollRad, pitchRad, yawRad);
      Translation3d contactPositionWorld =
          new Translation3d(
              xMeters + contactOffsetWorld.getX(),
              yMeters + contactOffsetWorld.getY(),
              zMeters + contactOffsetWorld.getZ());
      TerrainPlane terrainPlane =
          sampleTerrainPlane(contactPositionWorld.getX(), contactPositionWorld.getY());

      Translation3d contactVelocityWorld =
          add(fieldLinearVelocityMetersPerSec, cross(angularVelocityWorld, contactOffsetWorld));
      double penetrationMeters =
          -signedDistanceToPlane(
              contactPositionWorld, terrainPlane.pointOnPlaneWorld(), terrainPlane.normalWorld());
      double normalVelocityMetersPerSec = dot(contactVelocityWorld, terrainPlane.normalWorld());
      if (penetrationMeters <= 0.0) {
        wheelNormalForcesNewtons[i] = 0.0;
        continue;
      }

      double normalForceNewtons =
          Math.max(
              0.0,
              (config.wheelContactStiffnessNewtonsPerMeter() * Math.max(0.0, penetrationMeters))
                  - (config.wheelContactDampingNewtonsPerMeterPerSec()
                      * normalVelocityMetersPerSec));
      wheelNormalForcesNewtons[i] = normalForceNewtons;
      if (normalForceNewtons <= 1e-9) {
        continue;
      }

      Translation3d wheelHeadingWorld =
          rotateBodyToWorld(
              new Translation3d(
                  Math.cos(moduleSteerAnglesRad[i]), Math.sin(moduleSteerAnglesRad[i]), 0.0),
              rollRad,
              pitchRad,
              yawRad);
      Translation3d wheelLongitudinalWorld =
          normalize(projectOntoPlane(wheelHeadingWorld, terrainPlane.normalWorld()));
      if (magnitude(wheelLongitudinalWorld) <= 1e-9) {
        wheelLongitudinalWorld =
            normalize(
                projectOntoPlane(
                    rotateBodyToWorld(new Translation3d(1.0, 0.0, 0.0), rollRad, pitchRad, yawRad),
                    terrainPlane.normalWorld()));
      }
      Translation3d wheelLateralWorld =
          normalize(cross(terrainPlane.normalWorld(), wheelLongitudinalWorld));
      double currentLongitudinalVelocity = dot(contactVelocityWorld, wheelLongitudinalWorld);
      double currentLateralVelocity = dot(contactVelocityWorld, wheelLateralWorld);
      double driveForceNewtons =
          clamp(
              config.longitudinalVelocityGainNewtonsPerMeterPerSec()
                  * (desiredWheelStates[i].speedMetersPerSecond() - currentLongitudinalVelocity),
              -config.maxDriveForcePerWheelNewtons(),
              config.maxDriveForcePerWheelNewtons());
      double lateralForceNewtons =
          clamp(
              -config.lateralVelocityGainNewtonsPerMeterPerSec() * currentLateralVelocity,
              -(nominalGrip * normalForceNewtons * config.lateralGripFraction()),
              nominalGrip * normalForceNewtons * config.lateralGripFraction());

      Translation3d tangentialForceWorld =
          add(
              scale(wheelLongitudinalWorld, driveForceNewtons),
              scale(wheelLateralWorld, lateralForceNewtons));
      double maxFrictionForceNewtons = nominalGrip * normalForceNewtons;
      double tangentialMagnitude = magnitude(tangentialForceWorld);
      if (tangentialMagnitude > maxFrictionForceNewtons && tangentialMagnitude > 1e-9) {
        tangentialForceWorld =
            scale(tangentialForceWorld, maxFrictionForceNewtons / tangentialMagnitude);
      }

      Translation3d wheelForceWorld =
          add(tangentialForceWorld, scale(terrainPlane.normalWorld(), normalForceNewtons));
      totalForceWorld = add(totalForceWorld, wheelForceWorld);
      totalTorqueWorld = add(totalTorqueWorld, cross(contactOffsetWorld, wheelForceWorld));
    }

    for (Translation3d bodyPerimeterOffsetBody : bodyPerimeterContactOffsets()) {
      Translation3d bodyPerimeterOffsetWorld =
          rotateBodyToWorld(bodyPerimeterOffsetBody, rollRad, pitchRad, yawRad);
      Translation3d bodyPerimeterPointWorld =
          new Translation3d(
              xMeters + bodyPerimeterOffsetWorld.getX(),
              yMeters + bodyPerimeterOffsetWorld.getY(),
              zMeters + bodyPerimeterOffsetWorld.getZ());
      Translation3d bodyPerimeterVelocityWorld =
          add(
              fieldLinearVelocityMetersPerSec,
              cross(angularVelocityWorld, bodyPerimeterOffsetWorld));
      Translation3d planarObstacleForceWorld =
          planarObstacleForceWorld(bodyPerimeterPointWorld, bodyPerimeterVelocityWorld);
      if (magnitude(planarObstacleForceWorld) <= 1e-9) {
        continue;
      }
      totalForceWorld = add(totalForceWorld, planarObstacleForceWorld);
      totalTorqueWorld =
          add(totalTorqueWorld, cross(bodyPerimeterOffsetWorld, planarObstacleForceWorld));
    }

    TerrainPlane centerTerrainPlane = sampleTerrainPlane(xMeters, yMeters);
    Translation3d bodyContactOffsetWorld =
        rotateBodyToWorld(
            new Translation3d(0.0, 0.0, -config.chassisFootprint().groundClearanceMeters()),
            rollRad,
            pitchRad,
            yawRad);
    Translation3d bodyContactPointWorld =
        new Translation3d(
            xMeters + bodyContactOffsetWorld.getX(),
            yMeters + bodyContactOffsetWorld.getY(),
            zMeters + bodyContactOffsetWorld.getZ());
    double bodyPenetrationMeters =
        -signedDistanceToPlane(
            bodyContactPointWorld,
            centerTerrainPlane.pointOnPlaneWorld(),
            centerTerrainPlane.normalWorld());
    double bodyNormalVelocityMetersPerSec =
        dot(
            add(
                fieldLinearVelocityMetersPerSec,
                cross(angularVelocityWorld, bodyContactOffsetWorld)),
            centerTerrainPlane.normalWorld());
    if (bodyPenetrationMeters > 0.0) {
      double bodyNormalForceNewtons =
          Math.max(
              0.0,
              (config.bodyContactStiffnessNewtonsPerMeter() * Math.max(0.0, bodyPenetrationMeters))
                  - (config.bodyContactDampingNewtonsPerMeterPerSec()
                      * bodyNormalVelocityMetersPerSec));
      totalForceWorld =
          add(
              totalForceWorld,
              scale(centerTerrainPlane.normalWorld(), bodyNormalForceNewtons));
    }

    fieldLinearAccelerationMetersPerSecSq =
        scale(totalForceWorld, 1.0 / config.chassisMassProperties().massKg());
    fieldLinearVelocityMetersPerSec =
        add(fieldLinearVelocityMetersPerSec, scale(fieldLinearAccelerationMetersPerSecSq, dtSec));
    xMeters += fieldLinearVelocityMetersPerSec.getX() * dtSec;
    yMeters += fieldLinearVelocityMetersPerSec.getY() * dtSec;
    zMeters += fieldLinearVelocityMetersPerSec.getZ() * dtSec;

    Translation3d totalTorqueBody = rotateWorldToBody(totalTorqueWorld, rollRad, pitchRad, yawRad);
    Translation3d angularAccelerationBody =
        bodyAngularAcceleration(totalTorqueBody, bodyAngularVelocityRadPerSec);
    bodyAngularVelocityRadPerSec =
        add(bodyAngularVelocityRadPerSec, scale(angularAccelerationBody, dtSec));
    Rotation3d nextOrientation =
        integrateOrientation(
            new Rotation3d(rollRad, pitchRad, yawRad), bodyAngularVelocityRadPerSec, dtSec);
    rollRad = nextOrientation.getX();
    pitchRad = nextOrientation.getY();
    yawRad = wrapRadians(nextOrientation.getZ());
    resolvePlanarObstaclePenetration(
        previousXMeters, previousYMeters, previousRollRad, previousPitchRad, previousYawRad);

    if (fieldLinearVelocityMetersPerSec.getZ() == 0.0 && previousFieldVelocity.getZ() == 0.0) {
      fieldLinearAccelerationMetersPerSecSq =
          new Translation3d(
              fieldLinearAccelerationMetersPerSecSq.getX(),
              fieldLinearAccelerationMetersPerSecSq.getY(),
              0.0);
    }
  }

  private ChassisState3d currentChassisState() {
    return new ChassisState3d(
        new Pose3d(xMeters, yMeters, zMeters, new Rotation3d(rollRad, pitchRad, yawRad)),
        fieldLinearVelocityMetersPerSec,
        fieldLinearAccelerationMetersPerSecSq,
        new AngularVelocity3d(
            bodyAngularVelocityRadPerSec.getX(),
            bodyAngularVelocityRadPerSec.getY(),
            bodyAngularVelocityRadPerSec.getZ()));
  }

  private Pose2d currentPose2d() {
    return new Pose2d(xMeters, yMeters, Rotation2d.fromRadians(yawRad));
  }

  private TerrainSample currentTerrainSample() {
    return terrainModel.sample(currentPose2d());
  }

  private TerrainContactSample currentTerrainContactSample() {
    Pose2d pose2d = currentPose2d();
    if (terrainContactModel != null) {
      return terrainContactModel.sampleContact(pose2d, config.chassisFootprint());
    }

    TerrainSample terrainSample = terrainModel.sample(pose2d);
    return new TerrainContactSample(
        terrainSample,
        TerrainFeature.FLAT,
        Double.POSITIVE_INFINITY,
        zMeters - terrainSample.heightMeters(),
        Double.POSITIVE_INFINITY,
        true,
        true);
  }

  private SwerveTractionState currentTractionState(TerrainContactSample terrainContactSample) {
    double baseWheelLoadNewtons =
        (config.chassisMassProperties().massKg() * GRAVITY_MPS2) / wheelNormalForcesNewtons.length;
    double totalNormalForceNewtons = 0.0;
    for (double wheelNormalForceNewtons : wheelNormalForcesNewtons) {
      totalNormalForceNewtons += wheelNormalForceNewtons;
    }

    boolean contactAvailable = totalNormalForceNewtons > 1e-3;
    boolean traversableSurface =
        terrainContactSample == null
            || (terrainContactSample.traversableSurface() && terrainContactSample.clearanceSatisfied());
    boolean tractionAvailable = contactAvailable && traversableSurface;
    double gripCoefficient =
        tractionAvailable ? config.chassisMassProperties().nominalTireGripCoefficient() : 0.0;

    WheelLoadSample frontLeft =
        wheelLoad(wheelNormalForcesNewtons[0], baseWheelLoadNewtons, gripCoefficient);
    WheelLoadSample frontRight =
        wheelLoad(wheelNormalForcesNewtons[1], baseWheelLoadNewtons, gripCoefficient);
    WheelLoadSample rearLeft =
        wheelLoad(wheelNormalForcesNewtons[2], baseWheelLoadNewtons, gripCoefficient);
    WheelLoadSample rearRight =
        wheelLoad(wheelNormalForcesNewtons[3], baseWheelLoadNewtons, gripCoefficient);

    return new SwerveTractionState(
        frontLeft,
        frontRight,
        rearLeft,
        rearRight,
        totalNormalForceNewtons,
        (frontLeft.normalizedLoad()
                + frontRight.normalizedLoad()
                + rearLeft.normalizedLoad()
                + rearRight.normalizedLoad())
            / 4.0,
        tractionAvailable);
  }

  private ChassisVelocities currentRobotRelativeChassisSpeeds() {
    Translation3d robotFrameVelocity =
        rotateWorldToBody(fieldLinearVelocityMetersPerSec, rollRad, pitchRad, yawRad);
    return new ChassisVelocities(
        robotFrameVelocity.getX(), robotFrameVelocity.getY(), bodyAngularVelocityRadPerSec.getZ());
  }

  private ChassisVelocities currentFieldRelativeChassisSpeeds() {
    return new ChassisVelocities(
        fieldLinearVelocityMetersPerSec.getX(),
        fieldLinearVelocityMetersPerSec.getY(),
        bodyAngularVelocityRadPerSec.getZ());
  }

  private SimImuSample currentImuSample() {
    return new SimImuSample(
        new Rotation3d(rollRad, pitchRad, yawRad),
        new AngularVelocity3d(
            bodyAngularVelocityRadPerSec.getX(),
            bodyAngularVelocityRadPerSec.getY(),
            bodyAngularVelocityRadPerSec.getZ()));
  }

  private static WheelLoadSample wheelLoad(
      double normalForceNewtons, double baseWheelLoadNewtons, double gripCoefficient) {
    return new WheelLoadSample(
        normalForceNewtons,
        normalForceNewtons * gripCoefficient,
        baseWheelLoadNewtons > 1e-9 ? normalForceNewtons / baseWheelLoadNewtons : 0.0);
  }

  private static double advanceAngle(double current, double target, double maxDelta) {
    double error = wrapRadians(target - current);
    double delta = clamp(error, -maxDelta, maxDelta);
    return current + delta;
  }

  private static double wrapRadians(double angleRad) {
    double wrapped = angleRad;
    while (wrapped > Math.PI) {
      wrapped -= 2.0 * Math.PI;
    }
    while (wrapped < -Math.PI) {
      wrapped += 2.0 * Math.PI;
    }
    return wrapped;
  }

  private static Translation3d rotateRobotPlanarToField(double robotVx, double robotVy, double yawRad) {
    double cosYaw = Math.cos(yawRad);
    double sinYaw = Math.sin(yawRad);
    return new Translation3d(
        (robotVx * cosYaw) - (robotVy * sinYaw),
        (robotVx * sinYaw) + (robotVy * cosYaw),
        0.0);
  }

  private static Translation3d rotateBodyToWorld(
      Translation3d vectorBody, double rollRad, double pitchRad, double yawRad) {
    double cr = Math.cos(rollRad);
    double sr = Math.sin(rollRad);
    double cp = Math.cos(pitchRad);
    double sp = Math.sin(pitchRad);
    double cy = Math.cos(yawRad);
    double sy = Math.sin(yawRad);

    double x1 = vectorBody.getX();
    double y1 = (cr * vectorBody.getY()) - (sr * vectorBody.getZ());
    double z1 = (sr * vectorBody.getY()) + (cr * vectorBody.getZ());
    double x2 = (cp * x1) + (sp * z1);
    double y2 = y1;
    double z2 = (-sp * x1) + (cp * z1);

    return new Translation3d(
        (cy * x2) - (sy * y2),
        (sy * x2) + (cy * y2),
        z2);
  }

  private static Translation3d rotateWorldToBody(
      Translation3d vectorWorld, double rollRad, double pitchRad, double yawRad) {
    double cr = Math.cos(rollRad);
    double sr = Math.sin(rollRad);
    double cp = Math.cos(pitchRad);
    double sp = Math.sin(pitchRad);
    double cy = Math.cos(yawRad);
    double sy = Math.sin(yawRad);

    double x1 = (cy * vectorWorld.getX()) + (sy * vectorWorld.getY());
    double y1 = (-sy * vectorWorld.getX()) + (cy * vectorWorld.getY());
    double z1 = vectorWorld.getZ();
    double x2 = (cp * x1) - (sp * z1);
    double y2 = y1;
    double z2 = (sp * x1) + (cp * z1);

    return new Translation3d(
        x2,
        (cr * y2) + (sr * z2),
        (-sr * y2) + (cr * z2));
  }

  @SuppressWarnings("unused")
  private static Translation3d normalizePlanar(Translation3d vector) {
    double magnitude = planarMagnitude(vector);
    if (magnitude <= 1e-9) {
      return new Translation3d();
    }
    return new Translation3d(vector.getX() / magnitude, vector.getY() / magnitude, 0.0);
  }

  private static double planarMagnitude(Translation3d vector) {
    return Math.hypot(vector.getX(), vector.getY());
  }

  private static double magnitude(Translation3d vector) {
    return Math.sqrt(
        (vector.getX() * vector.getX())
            + (vector.getY() * vector.getY())
            + (vector.getZ() * vector.getZ()));
  }

  private static Translation3d normalize(Translation3d vector) {
    double magnitude = magnitude(vector);
    if (magnitude <= 1e-9) {
      return new Translation3d();
    }
    return new Translation3d(
        vector.getX() / magnitude, vector.getY() / magnitude, vector.getZ() / magnitude);
  }

  private static double dot(Translation3d first, Translation3d second) {
    return (first.getX() * second.getX())
        + (first.getY() * second.getY())
        + (first.getZ() * second.getZ());
  }

  private static Translation3d subtract(Translation3d first, Translation3d second) {
    return new Translation3d(
        first.getX() - second.getX(),
        first.getY() - second.getY(),
        first.getZ() - second.getZ());
  }

  private static Translation3d projectOntoPlane(Translation3d vector, Translation3d unitNormal) {
    return subtract(vector, scale(unitNormal, dot(vector, unitNormal)));
  }

  private static double signedDistanceToPlane(
      Translation3d point, Translation3d pointOnPlane, Translation3d unitNormal) {
    return dot(subtract(point, pointOnPlane), unitNormal);
  }

  private static Translation3d add(Translation3d first, Translation3d second) {
    return new Translation3d(
        first.getX() + second.getX(),
        first.getY() + second.getY(),
        first.getZ() + second.getZ());
  }

  private static Translation3d scale(Translation3d vector, double scalar) {
    return new Translation3d(
        vector.getX() * scalar,
        vector.getY() * scalar,
        vector.getZ() * scalar);
  }

  private static Translation3d interpolate(
      Translation3d start, Translation3d end, double interpolationFraction) {
    return add(start, scale(subtract(end, start), interpolationFraction));
  }

  private static Translation3d cross(Translation3d first, Translation3d second) {
    return new Translation3d(
        (first.getY() * second.getZ()) - (first.getZ() * second.getY()),
        (first.getZ() * second.getX()) - (first.getX() * second.getZ()),
        (first.getX() * second.getY()) - (first.getY() * second.getX()));
  }

  private Translation3d bodyAngularAcceleration(
      Translation3d totalTorqueBody, Translation3d bodyAngularVelocityRadPerSec) {
    double inertiaX = config.rollMomentOfInertiaKgMetersSq();
    double inertiaY = config.pitchMomentOfInertiaKgMetersSq();
    double inertiaZ = config.yawMomentOfInertiaKgMetersSq();
    double p = bodyAngularVelocityRadPerSec.getX();
    double q = bodyAngularVelocityRadPerSec.getY();
    double r = bodyAngularVelocityRadPerSec.getZ();
    return new Translation3d(
        (totalTorqueBody.getX() - ((inertiaZ - inertiaY) * q * r)) / inertiaX,
        (totalTorqueBody.getY() - ((inertiaX - inertiaZ) * r * p)) / inertiaY,
        (totalTorqueBody.getZ() - ((inertiaY - inertiaX) * p * q)) / inertiaZ);
  }

  private TerrainPlane sampleTerrainPlane(double xMeters, double yMeters) {
    TerrainSample terrainSample =
        terrainModel.sample(new Pose2d(xMeters, yMeters, Rotation2d.fromRadians(yawRad)));
    Rotation3d terrainRotation = terrainSample.pose3d().getRotation();
    Translation3d normalWorld =
        normalize(
            rotateBodyToWorld(
                new Translation3d(0.0, 0.0, 1.0),
                terrainRotation.getX(),
                terrainRotation.getY(),
                terrainRotation.getZ()));
    return new TerrainPlane(
        new Translation3d(
            terrainSample.pose3d().getX(),
            terrainSample.pose3d().getY(),
            terrainSample.heightMeters()),
        normalWorld);
  }

  private static Rotation3d integrateOrientation(
      Rotation3d orientation, Translation3d bodyAngularVelocityRadPerSec, double dtSec) {
    Quaternion orientationQuaternion = orientation.getQuaternion();
    Quaternion bodyRateQuaternion =
        new Quaternion(
            0.0,
            bodyAngularVelocityRadPerSec.getX(),
            bodyAngularVelocityRadPerSec.getY(),
            bodyAngularVelocityRadPerSec.getZ());
    Quaternion integratedQuaternion =
        orientationQuaternion
            .plus(orientationQuaternion.times(bodyRateQuaternion).times(0.5 * dtSec))
            .normalize();
    return new Rotation3d(integratedQuaternion);
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private Translation3d planarObstacleForceWorld(
      Translation3d contactPointWorld, Translation3d contactVelocityWorld) {
    return new Translation3d();
  }

  private PlanarObstacleContactSample selectPlanarObstacleContact(
      Translation3d contactPointWorld, Translation3d contactVelocityWorld) {
    if (planarObstacleContactModel == null) {
      return null;
    }

    PlanarObstacleContactSample[] contacts =
        planarObstacleContactModel.samplePlanarObstacleContacts(
            new Translation2d(contactPointWorld.getX(), contactPointWorld.getY()),
            Math.max(0.0, contactPointWorld.getZ()));
    PlanarObstacleContactSample selectedContact = null;
    double mostInboundVelocityMetersPerSec = Double.POSITIVE_INFINITY;
    for (PlanarObstacleContactSample contact : contacts) {
      if (contact == null || contact.penetrationMeters() <= 0.0) {
        continue;
      }
      Translation3d outwardNormalWorld =
          normalize(
              new Translation3d(
                  contact.outwardNormal().getX(), contact.outwardNormal().getY(), 0.0));
      double normalVelocityMetersPerSec = dot(contactVelocityWorld, outwardNormalWorld);
      if (normalVelocityMetersPerSec < mostInboundVelocityMetersPerSec) {
        mostInboundVelocityMetersPerSec = normalVelocityMetersPerSec;
        selectedContact = contact;
      }
    }

    if (selectedContact == null) {
      return null;
    }

    if (mostInboundVelocityMetersPerSec >= -1e-4) {
      double minimumPenetrationMeters = Double.POSITIVE_INFINITY;
      for (PlanarObstacleContactSample contact : contacts) {
        if (contact == null || contact.penetrationMeters() <= 0.0) {
          continue;
        }
        if (contact.penetrationMeters() < minimumPenetrationMeters) {
          minimumPenetrationMeters = contact.penetrationMeters();
          selectedContact = contact;
        }
      }
    }
    return selectedContact;
  }

  private void resolvePlanarObstaclePenetration(
      double previousXMeters,
      double previousYMeters,
      double previousRollRad,
      double previousPitchRad,
      double previousYawRad) {
    if (planarObstacleContactModel == null) {
      return;
    }

    for (int iteration = 0; iteration < 6; iteration++) {
      SweptPlanarObstacleContact earliestContact = null;
      Translation3d earliestPreviousPointWorld = null;
      Translation3d earliestCurrentPointWorld = null;
      Translation3d earliestCurrentOffsetWorld = null;
      for (Translation3d bodyPerimeterOffsetBody : bodyPerimeterContactOffsets()) {
        Translation3d previousOffsetWorld =
            rotateBodyToWorld(
                bodyPerimeterOffsetBody,
                previousRollRad,
                previousPitchRad,
                previousYawRad);
        Translation3d currentOffsetWorld =
            rotateBodyToWorld(bodyPerimeterOffsetBody, rollRad, pitchRad, yawRad);
        Translation3d currentPointWorld =
            new Translation3d(
                xMeters + currentOffsetWorld.getX(),
                yMeters + currentOffsetWorld.getY(),
                zMeters + currentOffsetWorld.getZ());
        Translation3d previousPointWorld =
            new Translation3d(
                previousXMeters + previousOffsetWorld.getX(),
                previousYMeters + previousOffsetWorld.getY(),
                zMeters + previousOffsetWorld.getZ());
        Translation3d contactVelocityWorld =
            add(
                fieldLinearVelocityMetersPerSec,
                cross(
                    rotateBodyToWorld(bodyAngularVelocityRadPerSec, rollRad, pitchRad, yawRad),
                    currentOffsetWorld));
        SweptPlanarObstacleContact sweptContact =
            findSweptPlanarObstacleContact(
                previousPointWorld, currentPointWorld, contactVelocityWorld);
        if (sweptContact == null || sweptContact.contact().penetrationMeters() <= 1e-6) {
          continue;
        }
        if (earliestContact == null
            || sweptContact.contactFraction() < earliestContact.contactFraction()
            || (Math.abs(sweptContact.contactFraction() - earliestContact.contactFraction()) < 1e-6
                && sweptContact.contact().penetrationMeters()
                    > earliestContact.contact().penetrationMeters())) {
          earliestContact = sweptContact;
          earliestPreviousPointWorld = previousPointWorld;
          earliestCurrentPointWorld = currentPointWorld;
          earliestCurrentOffsetWorld = currentOffsetWorld;
        }
      }
      if (earliestContact == null) {
        break;
      }

      Translation3d outwardNormalWorld =
          normalize(
              new Translation3d(
                  earliestContact.contact().outwardNormal().getX(),
                  earliestContact.contact().outwardNormal().getY(),
                  0.0));
      Translation3d projectedPointWorld =
          add(
              earliestContact.contactPointWorld(),
              scale(outwardNormalWorld, earliestContact.contact().penetrationMeters()));
      Translation3d correctionWorld = subtract(projectedPointWorld, earliestCurrentPointWorld);
      xMeters += correctionWorld.getX();
      yMeters += correctionWorld.getY();
      double inwardSpeedMetersPerSec = dot(fieldLinearVelocityMetersPerSec, outwardNormalWorld);
      if (inwardSpeedMetersPerSec < 0.0) {
        fieldLinearVelocityMetersPerSec =
            subtract(
                fieldLinearVelocityMetersPerSec,
                scale(outwardNormalWorld, inwardSpeedMetersPerSec));
      }
      previousXMeters = earliestPreviousPointWorld.getX() - earliestCurrentOffsetWorld.getX();
      previousYMeters = earliestPreviousPointWorld.getY() - earliestCurrentOffsetWorld.getY();
    }

    for (int iteration = 0; iteration < 3; iteration++) {
      PlanarObstacleContactSample selectedContact = null;
      double mostInboundVelocityMetersPerSec = Double.POSITIVE_INFINITY;
      double deepestPenetrationMeters = 0.0;
      for (Translation3d bodyPerimeterOffsetBody : bodyPerimeterContactOffsets()) {
        Translation3d currentOffsetWorld =
            rotateBodyToWorld(bodyPerimeterOffsetBody, rollRad, pitchRad, yawRad);
        Translation3d currentPointWorld =
            new Translation3d(
                xMeters + currentOffsetWorld.getX(),
                yMeters + currentOffsetWorld.getY(),
                zMeters + currentOffsetWorld.getZ());
        Translation3d contactVelocityWorld =
            add(
                fieldLinearVelocityMetersPerSec,
                cross(
                    rotateBodyToWorld(bodyAngularVelocityRadPerSec, rollRad, pitchRad, yawRad),
                    currentOffsetWorld));
        PlanarObstacleContactSample contact =
            selectPlanarObstacleContact(currentPointWorld, contactVelocityWorld);
        if (contact == null || contact.penetrationMeters() <= 1e-6) {
          continue;
        }

        Translation3d outwardNormalWorld =
            normalize(
                new Translation3d(contact.outwardNormal().getX(), contact.outwardNormal().getY(), 0.0));
        double inwardSpeedMetersPerSec = dot(contactVelocityWorld, outwardNormalWorld);
        boolean betterInboundContact =
            inwardSpeedMetersPerSec < -1e-4
                && (selectedContact == null
                    || mostInboundVelocityMetersPerSec >= -1e-4
                    || inwardSpeedMetersPerSec < mostInboundVelocityMetersPerSec
                    || (Math.abs(inwardSpeedMetersPerSec - mostInboundVelocityMetersPerSec) < 1e-6
                        && contact.penetrationMeters() > deepestPenetrationMeters));
        boolean betterStaticContact =
            inwardSpeedMetersPerSec >= -1e-4
                && mostInboundVelocityMetersPerSec >= -1e-4
                && contact.penetrationMeters() > deepestPenetrationMeters;
        if (betterInboundContact || betterStaticContact) {
          selectedContact = contact;
          mostInboundVelocityMetersPerSec = inwardSpeedMetersPerSec;
          deepestPenetrationMeters = contact.penetrationMeters();
        }
      }
      if (selectedContact == null) {
        break;
      }
      Translation3d outwardNormalWorld =
          normalize(
              new Translation3d(
                  selectedContact.outwardNormal().getX(), selectedContact.outwardNormal().getY(), 0.0));
      Translation3d correctionWorld =
          scale(
              outwardNormalWorld,
              Math.min(
                  selectedContact.penetrationMeters(),
                  MAX_STATIC_PLANAR_OBSTACLE_CORRECTION_METERS));
      xMeters += correctionWorld.getX();
      yMeters += correctionWorld.getY();
      double inwardSpeedMetersPerSec = dot(fieldLinearVelocityMetersPerSec, outwardNormalWorld);
      if (inwardSpeedMetersPerSec < 0.0) {
        fieldLinearVelocityMetersPerSec =
            subtract(
                fieldLinearVelocityMetersPerSec,
                scale(outwardNormalWorld, inwardSpeedMetersPerSec));
      }
    }
  }

  private SweptPlanarObstacleContact findSweptPlanarObstacleContact(
      Translation3d previousPointWorld,
      Translation3d currentPointWorld,
      Translation3d contactVelocityWorld) {
    if (planarObstacleContactModel == null) {
      return null;
    }

    double deltaMeters = magnitude(subtract(currentPointWorld, previousPointWorld));
    PlanarObstacleContactSample currentContact =
        selectPlanarObstacleContact(currentPointWorld, contactVelocityWorld);
    if (deltaMeters <= 1e-6) {
      return currentContact == null
          ? null
          : new SweptPlanarObstacleContact(currentContact, currentPointWorld, 1.0);
    }

    double lowT = 0.0;
    int sweepSamples = Math.max(10, (int) Math.ceil(deltaMeters / 0.01));
    for (int step = 1; step <= sweepSamples; step++) {
      double highT = step / (double) sweepSamples;
      Translation3d highPoint = interpolate(previousPointWorld, currentPointWorld, highT);
      PlanarObstacleContactSample highContact =
          selectPlanarObstacleContact(highPoint, contactVelocityWorld);
      if (highContact == null) {
        lowT = highT;
        continue;
      }

      double searchLowT = lowT;
      double searchHighT = highT;
      Translation3d searchHighPoint = highPoint;
      PlanarObstacleContactSample searchHighContact = highContact;
      for (int iteration = 0; iteration < 8; iteration++) {
        double midT = (searchLowT + searchHighT) * 0.5;
        Translation3d midPoint = interpolate(previousPointWorld, currentPointWorld, midT);
        PlanarObstacleContactSample midContact =
            selectPlanarObstacleContact(midPoint, contactVelocityWorld);
        if (midContact == null) {
          searchLowT = midT;
        } else {
          searchHighT = midT;
          searchHighPoint = midPoint;
          searchHighContact = midContact;
        }
      }
      return new SweptPlanarObstacleContact(searchHighContact, searchHighPoint, searchHighT);
    }
    return currentContact == null
        ? null
        : new SweptPlanarObstacleContact(currentContact, currentPointWorld, 1.0);
  }

  private Translation3d[] bodyPerimeterContactOffsets() {
    double halfLength = config.chassisFootprint().lengthMeters() * 0.5;
    double halfWidth = config.chassisFootprint().widthMeters() * 0.5;
    double quarterLength = halfLength * 0.5;
    double quarterWidth = halfWidth * 0.5;
    double contactZ = -config.chassisFootprint().groundClearanceMeters();
    return new Translation3d[] {
      new Translation3d(halfLength, 0.0, contactZ),
      new Translation3d(halfLength, quarterWidth, contactZ),
      new Translation3d(halfLength, -quarterWidth, contactZ),
      new Translation3d(-halfLength, 0.0, contactZ),
      new Translation3d(-halfLength, quarterWidth, contactZ),
      new Translation3d(-halfLength, -quarterWidth, contactZ),
      new Translation3d(0.0, halfWidth, contactZ),
      new Translation3d(quarterLength, halfWidth, contactZ),
      new Translation3d(-quarterLength, halfWidth, contactZ),
      new Translation3d(0.0, -halfWidth, contactZ),
      new Translation3d(quarterLength, -halfWidth, contactZ),
      new Translation3d(-quarterLength, -halfWidth, contactZ),
      new Translation3d(halfLength, halfWidth, contactZ),
      new Translation3d(halfLength, -halfWidth, contactZ),
      new Translation3d(-halfLength, halfWidth, contactZ),
      new Translation3d(-halfLength, -halfWidth, contactZ)
    };
  }

  private static PlanarObstacleContactModel resolvePlanarObstacleContactModel(
      TerrainModel terrainModel, TerrainContactModel terrainContactModel) {
    if (terrainContactModel instanceof PlanarObstacleContactModel obstacleContactModel) {
      return obstacleContactModel;
    }
    if (terrainModel instanceof PlanarObstacleContactModel obstacleContactModel) {
      return obstacleContactModel;
    }
    return null;
  }

  private static DesiredWheelState[] desiredWheelStates(
      ChassisVelocities commandedRobotRelativeSpeeds, Translation2d[] moduleLocations) {
    DesiredWheelState[] desiredStates = new DesiredWheelState[moduleLocations.length];
    for (int i = 0; i < moduleLocations.length; i++) {
      double wheelVx =
          commandedRobotRelativeSpeeds.vx
              - (commandedRobotRelativeSpeeds.omega * moduleLocations[i].getY());
      double wheelVy =
          commandedRobotRelativeSpeeds.vx
              + (commandedRobotRelativeSpeeds.omega * moduleLocations[i].getX());
      desiredStates[i] =
          new DesiredWheelState(Math.hypot(wheelVx, wheelVy), Math.atan2(wheelVy, wheelVx));
    }
    return desiredStates;
  }

  private record DesiredWheelState(double speedMetersPerSecond, double steerAngleRad) {}

  private record TerrainPlane(Translation3d pointOnPlaneWorld, Translation3d normalWorld) {}

  private record SweptPlanarObstacleContact(
      PlanarObstacleContactSample contact,
      Translation3d contactPointWorld,
      double contactFraction) {}
}
