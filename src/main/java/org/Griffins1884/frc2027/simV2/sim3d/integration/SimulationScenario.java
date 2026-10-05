package org.Griffins1884.frc2027.simV2.sim3d.integration;

import org.wpilib.math.geometry.Pose2d;

/** Repeatable simulation scenario definition for autonomous and regression checks. */
public record SimulationScenario(String name, String description, Pose2d startPose, Pose2d targetPose) {}
