package org.Griffins1884.frc2027.util.swerve;

import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveModuleVelocity;

public record SwerveSetpoint(ChassisVelocities chassisSpeeds, SwerveModuleVelocity[] moduleStates) {}
