package org.Griffins1884.frc2027.subsystems.vision;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Translation2d;
import java.util.Optional;

public interface VisionTargetProvider {
  Optional<Translation2d> getBestTargetTranslation(Pose2d robotPose);
}
