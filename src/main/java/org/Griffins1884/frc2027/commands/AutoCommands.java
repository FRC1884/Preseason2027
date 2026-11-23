package org.Griffins1884.frc2027.commands;

import com.pathplanner.lib.auto.NamedCommands;
import org.wpilib.command2.Commands;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.Griffins1884.frc2027.subsystems.Superstructure;
import org.Griffins1884.frc2027.subsystems.leds.LEDSubsystem;
import org.Griffins1884.frc2027.subsystems.swerve.SwerveSubsystem;
import org.Griffins1884.frc2027.commands.ChoreoVars;

public class AutoCommands {
  private AutoCommands() {}

  public static void registerAutoCommands(
      Superstructure superstructure, SwerveSubsystem drive, LEDSubsystem leds) {

    AutoAlignToPoseCommand alignToFrontLeftBlueCoral1 = new AutoAlignToPoseCommand(drive, new Pose2d(ChoreoVars.FrontCoral1X, 
      ChoreoVars.LeftBlueCoral1Y, new Rotation2d(ChoreoVars.FrontLeftBlueCoralAngle)));

    AutoAlignToPoseCommand alignToFrontLeftBlueCoral2 = new AutoAlignToPoseCommand(drive, new Pose2d(ChoreoVars.FrontLeftBlueCoral2X, 
      ChoreoVars.LeftBlueCoral2Y, new Rotation2d(ChoreoVars.FrontLeftBlueCoralAngle)));
    
    AutoAlignToPoseCommand alignToFrontRightBlueCoral1 = new AutoAlignToPoseCommand(drive, new Pose2d(ChoreoVars.FrontCoral1X, 
      ChoreoVars.RightBlueCoral1Y, new Rotation2d(ChoreoVars.FrontRightBlueCoralAngle)));

    AutoAlignToPoseCommand alignToFrontRightBlueCoral2 = new AutoAlignToPoseCommand(drive, new Pose2d(ChoreoVars.FrontLeftBlueCoral2X, 
      ChoreoVars.RightBlueCoral2Y, new Rotation2d(ChoreoVars.FrontRightBlueCoralAngle)));

    AutoAlignToPoseCommand alignToLeftBlueHumanPlayer = new AutoAlignToPoseCommand(drive, new Pose2d(ChoreoVars.BlueHumanPlayerX, 
      ChoreoVars.LeftBlueHumanPlayerY, new Rotation2d(ChoreoVars.LeftBlueHumanPlayerAngle)));
    
    AutoAlignToPoseCommand alignToRightBlueHumanPlayer = new AutoAlignToPoseCommand(drive, new Pose2d(ChoreoVars.BlueHumanPlayerX, 
      ChoreoVars.RightBlueHumanPlayerY, new Rotation2d(ChoreoVars.RightBlueHumanPlayerAngle)));

    NamedCommands.registerCommand("AutoAlignToPose", Commands.runOnce(() -> alignToFrontLeftBlueCoral1.execute()));

    NamedCommands.registerCommand("AlignToReef", DriveCommands.alignToHPd(drive));

    NamedCommands.registerCommand("AlignToDepot", DriveCommands.alignToDepot(drive));

    NamedCommands.registerCommand("AlignToReef", null);

    NamedCommands.registerCommand("AlignToDepot2025", null);

    NamedCommands.registerCommand("OuttakeCoralHigh", null);

    NamedCommands.registerCommand("OuttakeCoralMid", null);

    NamedCommands.registerCommand("Intake2025", null);

    NamedCommands.registerCommand("Shoot", Commands.runOnce(superstructure::toggleShootEnabled));

    NamedCommands.registerCommand(
        "Idling",
        Commands.sequence(superstructure.setSuperStateCmd(Superstructure.SuperState.IDLING)));

    NamedCommands.registerCommand(
        "Intake", superstructure.setSuperStateCmd(Superstructure.SuperState.INTAKING));

    // NamedCommands.registerCommand("WhiteFlashLEDs", leds.whiteFlash());

    // NamedCommands.registerCommand("RainbowLEDs", leds.rainbow());
  }
}
