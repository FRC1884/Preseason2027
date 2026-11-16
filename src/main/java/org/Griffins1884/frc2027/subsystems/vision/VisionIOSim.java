package org.Griffins1884.frc2027.subsystems.vision;

import static org.wpilib.math.util.Units.radiansToDegrees;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.Getter;
import org.Griffins1884.frc2027.GlobalConstants;
import org.Griffins1884.frc2027.simulation.maple.Rebuilt2026FieldModel;
import org.littletonrobotics.junction.Logger;
import org.photonvision.EstimatedRobotPose;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.simulation.PhotonCameraSim;
import org.photonvision.simulation.SimCameraProperties;
import org.photonvision.simulation.VisionSystemSim;
import org.photonvision.targeting.PhotonPipelineResult;
import org.wpilib.fields.Field;
import org.wpilib.fields.Fields;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.system.Timer;

public class VisionIOSim implements VisionIO {

  // Vision Simulation:
  private static VisionSystemSim visionSim;
  private static double lastSimUpdateTimestamp = -1.0;
  private static Field tagLayout;

  private final String logPrefix;

  private final PhotonCamera camera;
  private final Supplier<Pose2d> simPoseSupplier;
  private final Supplier<Pose2d> perceivedPoseSupplier;
  private final PhotonPoseEstimator poseEstimator;
  @Getter private final CameraConstants cameraConstants;

  public VisionIOSim(
      CameraConstants cameraConstants,
      Supplier<Pose2d> perceivedPoseSupplier,
      Supplier<Pose2d> simPoseSupplier) {

    if (visionSim == null) {
      visionSim = new VisionSystemSim("main");
      tagLayout = Field.loadField(Fields.DEFAULT_FIELD);
      visionSim.addAprilTags(tagLayout);
    }

    poseEstimator = new PhotonPoseEstimator(tagLayout, cameraConstants.robotToCamera());

    this.simPoseSupplier = simPoseSupplier;
    this.perceivedPoseSupplier = perceivedPoseSupplier;
    this.cameraConstants = cameraConstants;

    camera = new PhotonCamera(cameraConstants.cameraName());

    logPrefix = "VisionSim/" + camera.getName();

    // These constants should be in CameraConstants, but I don't really know what's going on in
    // there.
    SimCameraProperties cameraProp = new SimCameraProperties();
    cameraProp.setCalibration(960, 720, Rotation2d.fromDegrees(90)); // Resolution & diagonal FOV
    cameraProp.setCalibError(0.25, 0.08); // Average & std dev pixel noise
    cameraProp.setFPS(50); // 50 FPS so it should keep up with the sim
    cameraProp.setAvgLatencyMs(35);
    cameraProp.setLatencyStdDevMs(5);

    PhotonCameraSim cameraSim = new PhotonCameraSim(camera, cameraProp);
    cameraSim.enableProcessedStream(true);
    // Might want to turn this off if your GPU & CPU are getting overloaded
    cameraSim.enableDrawWireframe(true);

    visionSim.addCamera(cameraSim, cameraConstants.robotToCamera());

    // setLLSettings(cameraConstants.cameraName(), cameraConstants); Might be necessary
  }

  @Override
  public void updateInputs(VisionIOInputs inputs) {
    inputs.connected = camera.isConnected();
    inputs.standardDeviations = AprilTagVisionConstants.getLimelightStandardDeviations();

    Pose3d perceivedPose = new Pose3d(perceivedPoseSupplier.get());
    Pose3d realPose = getRealPose();

    updateVisionSim(realPose);

    var results = camera.getAllUnreadResults(); // Can only run once per loop, since it empties it

    resetInputs(inputs);

    processResults(inputs, results);

    // Log values
    Logger.recordOutput(logPrefix + "/ResultCount", results.size());

    Logger.recordOutput(logPrefix + "/PerceivedPose", perceivedPose);
    Logger.recordOutput(logPrefix + "/RealPose", realPose);
    if (inputs.pose3d != null) {
      Logger.recordOutput(logPrefix + "/RawCameraPose", inputs.pose3d);
    }
  }

  private void updateVisionSim(Pose3d realPose) {
    // Skip if another camera already updated the sim this loop
    double now = Timer.getTimestamp();
    if (now - lastSimUpdateTimestamp >= 0.01) { // To add a bit of tolerance
      visionSim.update(realPose);
      lastSimUpdateTimestamp = now;
    }
  }

  private Pose3d getRealPose() {
    Pose2d simPose = simPoseSupplier.get();
    return GlobalConstants.MODE == GlobalConstants.RobotMode.SIM
        ? Rebuilt2026FieldModel.terrainAdjustedRobotPose(simPose)
        : new Pose3d(simPose);
  }

  private void processResults(VisionIOInputs inputs, List<PhotonPipelineResult> results) {
    List<PoseObservation> poseObservations = new ArrayList<>();
    Set<Integer> tagIds = new HashSet<>();

    for (PhotonPipelineResult result : results) {
      Optional<EstimatedRobotPose> visionEst = poseEstimator.estimateCoprocMultiTagPose(result);
      if (visionEst.isEmpty()) {
        visionEst = poseEstimator.estimateLowestAmbiguityPose(result);
      }

      if (result.hasTargets()) {
        var bestTarget = result.getBestTarget();
        inputs.seesTarget = true;
        inputs.latestTargetObservation =
            new TargetObservation(
                Rotation2d.fromDegrees(bestTarget.getYaw()),
                Rotation2d.fromDegrees(bestTarget.getPitch()));
      }

      // Runs if it detected any tags
      if (visionEst.isEmpty() || visionEst.get().targetsUsed.isEmpty()) {
        continue;
      }

      var est = visionEst.get();

      double totalTagDistance = 0;
      double totalTagArea = 0;

      int[] currentTagIds = new int[est.targetsUsed.size()];

      List<FiducialObservation> fiducials = new ArrayList<>();

      for (int i = 0; i < est.targetsUsed.size(); i++) {
        var target = est.targetsUsed.get(i);
        double dist = target.getBestCameraToTarget().getTranslation().getNorm();
        double area = target.getArea();
        totalTagDistance += dist;
        totalTagArea += area;

        currentTagIds[i] = target.getFiducialId();
        tagIds.add(target.getFiducialId());

        fiducials.add(
            new FiducialObservation(
                target.getFiducialId(),
                target.getYaw(),
                target.getPitch(),
                target.getPoseAmbiguity(),
                area,
                dist));
      }

      int tagCount = est.targetsUsed.size();
      double avgTagDistance = totalTagDistance / tagCount;
      double avgTagArea = totalTagArea / tagCount;
      double ambiguity =
          Math.max(0.0, tagCount > 1 ? 0.0 : est.targetsUsed.get(0).getPoseAmbiguity());

      inputs.pose3d = est.estimatedPose;
      inputs.megatagCount = tagCount;
      inputs.fiducialObservations = fiducials.toArray(new FiducialObservation[0]);

      inputs.megatagPoseEstimate =
          new MegatagPoseEstimate(
              est.estimatedPose.toPose2d(),
              est.timestampSeconds,
              0.0, // Latency
              avgTagArea,
              avgTagDistance,
              1.0 - ambiguity,
              currentTagIds,
              0.0); // Residual Translation

      poseObservations.add(
          new PoseObservation(
              est.timestampSeconds, est.estimatedPose, ambiguity, tagCount, avgTagDistance));
    }

    inputs.poseObservations = poseObservations.toArray(new PoseObservation[0]);

    inputs.tagIds = tagIds.stream().mapToInt(Integer::intValue).toArray();
  }

  private void resetInputs(VisionIOInputs inputs) {
    inputs.seesTarget = false;
    inputs.megatagCount = 0;
    inputs.pose3d = null;
    inputs.megatagPoseEstimate = null;
    inputs.fiducialObservations = new FiducialObservation[0];
  }

  /** Configures Limelight camera poses in robot coordinate system. */
  private void setLLSettings(String cameraName, CameraConstants cameraConstants) {
    LimelightHelpers.setCameraPose_RobotSpace(
        cameraName,
        cameraConstants.robotToCamera().getX(),
        cameraConstants.robotToCamera().getY(),
        cameraConstants.robotToCamera().getZ(),
        radiansToDegrees(cameraConstants.robotToCamera().getRotation().getX()),
        radiansToDegrees(cameraConstants.robotToCamera().getRotation().getY()),
        radiansToDegrees(cameraConstants.robotToCamera().getRotation().getZ()));
  }

  // Only necessary for unit testing
  static void reset() {
    visionSim = null;
    tagLayout = null;
    lastSimUpdateTimestamp = -1.0;
  }
}
