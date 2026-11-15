package org.Griffins1884.frc2027.subsystems.vision;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.Griffins1884.frc2027.subsystems.swerve.SwerveSubsystem;
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
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.system.Timer;

public class VisionIOSim extends AprilTagVisionIOLimelight {

  // Vision Simulation:
  private static VisionSystemSim visionSim;
  private static double lastSimUpdateTimestamp = -1.0;
  private static Field tagLayout;

  private final PhotonCamera camera;
  private final PhotonCameraSim cameraSim;
  private final SwerveSubsystem drive;
  private final PhotonPoseEstimator poseEstimator;

  public VisionIOSim(CameraConstants cameraConstants, SwerveSubsystem drive) {
    // Just does all the same things as the APrilTagVisionIOLimelight.java
    super(cameraConstants, drive);

    if (visionSim == null) {
      visionSim = new VisionSystemSim("main");
      tagLayout = Field.loadField(Fields.DEFAULT_FIELD);
      visionSim.addAprilTags(tagLayout);
    }

    poseEstimator = new PhotonPoseEstimator(tagLayout, cameraConstants.robotToCamera());

    this.drive = drive;

    camera = new PhotonCamera(cameraConstants.cameraName());

    SimCameraProperties cameraProp = new SimCameraProperties();
    cameraProp.setCalibration(960, 720, Rotation2d.fromDegrees(90)); // Resolution & diagonal FOV
    cameraProp.setCalibError(0.25, 0.08); // Average & std dev pixel noise
    cameraProp.setFPS(50); // 50 FPS so it should keep up with the sim
    cameraProp.setAvgLatencyMs(35);
    cameraProp.setLatencyStdDevMs(5);

    cameraSim = new PhotonCameraSim(camera, cameraProp);

    cameraSim.enableProcessedStream(true);
    cameraSim.enableDrawWireframe(
        true); // Might want to turn this off if your GPU & CPU are getting overloaded

    visionSim.addCamera(cameraSim, cameraConstants.robotToCamera());
  }

  @Override
  public void updateInputs(VisionIOInputs inputs) {
    inputs.connected =
        camera.isConnected(); // Since it's a simulation, I'll assume they are connected
    inputs.standardDeviations = AprilTagVisionConstants.getLimelightStandardDeviations();

    // It's only supposed to be updated once per robot loop
    double now = Timer.getTimestamp();
    if (now != lastSimUpdateTimestamp) {
      visionSim.update(drive.getPose());
      lastSimUpdateTimestamp = now;
    }

    var results = camera.getAllUnreadResults();

    if (!results.isEmpty()) {
      inputs.seesTarget = false;
      inputs.megatagCount = 0;
      inputs.pose3d = null;
      inputs.megatagPoseEstimate = null;
    }

    Logger.recordOutput("VisionSim/" + camera.getName() + "/ResultCount", results.size());

    List<PoseObservation> poseObservations = new ArrayList<>();
    Set<Integer> tagIds = new HashSet<>();

    for (PhotonPipelineResult result : results) {
      Optional<EstimatedRobotPose> visionEst = poseEstimator.estimateCoprocMultiTagPose(result);
      if (visionEst.isEmpty()) {
        visionEst = poseEstimator.estimateLowestAmbiguityPose(result);
      }

      if (result.hasTargets()) inputs.seesTarget = true;

      if (result.hasTargets()) {
        var bestTarget = result.getBestTarget();
        inputs.latestTargetObservation =
            new TargetObservation(
                Rotation2d.fromDegrees(bestTarget.getYaw()),
                Rotation2d.fromDegrees(bestTarget.getPitch()));
      }

      // Runs if it detected any tags
      visionEst.ifPresent(est -> {
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
        double ambiguity = tagCount > 1 ? 0.0 : est.targetsUsed.get(0).poseAmbiguity;

        inputs.pose3d = est.estimatedPose;
        inputs.megatagCount = tagCount;
        inputs.fiducialObservations = fiducials.toArray(new FiducialObservation[0]);

        inputs.megatagPoseEstimate =
            new MegatagPoseEstimate(
                est.estimatedPose.toPose2d(),
                est.timestampSeconds,
                0,
                avgTagArea,
                avgTagDistance,
                1.0 - ambiguity,
                currentTagIds,
                0.0);

        poseObservations.add(
            new PoseObservation(
                est.timestampSeconds, est.estimatedPose, ambiguity, tagCount, avgTagDistance));
      });
    }

    inputs.poseObservations = poseObservations.toArray(new PoseObservation[0]);

    inputs.tagIds = tagIds.stream().mapToInt(Integer::intValue).toArray();
  }
}
