package org.Griffins1884.frc2027.subsystems.elevator;

import org.Griffins1884.frc2027.util.LoggedTunableNumber;

public final class ElevatorConstants {
  public final double minHeight = 0.0; // TODO change;
  public final double maxHeight = 0.75; // TODO change;
  public final double maxVelocity = 0.0;
  public final LoggedTunableNumber kV = new LoggedTunableNumber("Elevator/kV", 0.0);
  public final LoggedTunableNumber kA = new LoggedTunableNumber("Elevator/kA", 0.0);
}
