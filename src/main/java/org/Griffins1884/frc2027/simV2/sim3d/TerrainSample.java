package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Pose3d;

public record TerrainSample(Pose3d pose3d, double rollRadians, double pitchRadians, double heightMeters) {}
