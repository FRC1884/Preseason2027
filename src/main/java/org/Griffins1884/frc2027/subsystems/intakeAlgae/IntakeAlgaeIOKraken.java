package org.Griffins1884.frc2027.subsystems.intakeAlgae;

import org.Griffins1884.frc2027.CanIDConstants;
import org.Griffins1884.frc2027.mechanisms.rollers.MechanismRollerIOKraken;

public class IntakeAlgaeIOKraken extends MechanismRollerIOKraken implements IntakeAlgaeIO {
  public IntakeAlgaeIOKraken() {
    super(
        CanIDConstants.ALGAE_INTAKE_IDS,
        IntakeAlgaeConstants.CURRENT_LIMIT_AMPS,
        IntakeAlgaeConstants.INVERTED,
        IntakeAlgaeConstants.BRAKE,
        IntakeAlgaeConstants.REDUCTION,
        CanIDConstants.ALGAE_INTAKE_BUS);
  }
}
