package org.Griffins1884.frc2027.subsystems.vision;

import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;

public record VisionFieldPoseEstimate(
    Pose2d visionRobotPoseMeters,
    double timestampSeconds,
    Matrix<N3, N1> visionMeasurementStdDevs,
    int numTags) {
  public VisionFieldPoseEstimate {
    if (visionRobotPoseMeters == null) {
      visionRobotPoseMeters = new Pose2d();
    }
  }
}
