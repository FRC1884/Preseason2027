package org.Griffins1884.frc2027.subsystems.turret;

import org.Griffins1884.frc2027.CanIDConstants;
import org.Griffins1884.frc2027.mechanisms.turrets.MechanismTurretIOKraken;

public class TurretIOKraken extends MechanismTurretIOKraken implements TurretIO {
  public TurretIOKraken() {
    super(
        TurretConstants.TURRET_ID,
        TurretConstants.CURRENT_LIMIT_AMPS,
        TurretConstants.INVERTED,
        TurretConstants.BRAKE_MODE,
        TurretConstants.GEAR_RATIO,
        CanIDConstants.TURRET_CAN_BUS,
        TurretConstants.KRAKEN_FEATURES);
  }
}
