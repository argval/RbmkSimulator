/*
 * Copyright (C) 2026 RBMK Simulator contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hartrusion.rbmksim.jev;

/**
 * Code owns the scram. Jev only answers the two typed questions. This class
 * turns those answers into "stage the insertion" or "insert everything now".
 * <p>
 * {@link #SPIKE_NOUL_THRESHOLD} is the gate from TypeSafe's own example of
 * branching on a noul ({@code if (answers.escalate.noul > 0.7)}). Staging
 * also requires the choice answer to be {@code stage_insertion}. If the call
 * cannot be made, {@link LocalJevStandIn} supplies both answers.
 */
public final class Az5Guard {

    /** Act on the spike question only when its noul is at least this. */
    public static final double SPIKE_NOUL_THRESHOLD = 0.7;

    private Az5Guard() {
    }

    /**
     * {@code JEV_GUARD=off} restores the original simultaneous AZ-5. Any other
     * value, including unset, leaves the guard on.
     */
    public static boolean isEnabled() {
        String value = System.getenv("JEV_GUARD");
        if (value == null) {
            return true;
        }
        return !value.equalsIgnoreCase("off")
                && !value.equalsIgnoreCase("false")
                && !value.equals("0");
    }

    public static Az5Decision evaluate(Az5PlantState state) {
        String mode = System.getenv("JEV_MODE");
        if (mode == null || mode.isBlank()) {
            mode = "auto";
        }
        if (mode.equalsIgnoreCase("stand-in") || mode.equalsIgnoreCase("standalone")) {
            return LocalJevStandIn.decide(state,
                    "JEV_MODE=" + mode + " selected the local stand-in.");
        }
        try {
            return JevClient.evaluate(state);
        } catch (JevUnavailableException ex) {
            String reason = ex.getMessage() == null ? "Jev call failed." : ex.getMessage();
            return LocalJevStandIn.decide(state, reason);
        }
    }
}
