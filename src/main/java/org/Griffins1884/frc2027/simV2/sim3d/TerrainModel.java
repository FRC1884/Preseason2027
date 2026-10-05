package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Pose2d;

public interface TerrainModel {
  TerrainSample sample(Pose2d robotPose);
}
