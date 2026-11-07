package org.Griffins1884.frc2027.subsystems.elevator;

import lombok.extern.java.Log;
import org.Griffins1884.frc2027.util.LoggedTunableNumber;

public final class ElevatorConstants {
    public final double GEAR_RATIO = 0.0; //TODO chage;
    public final double CARRIAGE_MASS_KGS = 0.0; //TODO change;
    public final double MIN_HEIGHT = 0.0; //TODO change;
    public final double MAX_HEIGHT = 0.75; //TODO change;
    public final double MAX_VELOCITY = 0.0;
    public final LoggedTunableNumber kV = new LoggedTunableNumber("Elevator/kV", 0.0);
    public final LoggedTunableNumber kA = new LoggedTunableNumber("Elevator/kA", 0.0);

}
