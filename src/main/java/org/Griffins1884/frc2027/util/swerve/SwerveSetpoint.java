package org.Griffins1884.frc2027.util.swerve;

import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveModuleState;

public record SwerveSetpoint(ChassisVelocities chassisSpeeds, SwerveModuleState[] moduleStates) {}
