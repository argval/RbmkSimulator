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
 * The Jev HTTP call could not be completed. The guard falls back to the
 * local stand-in and keeps this message on the decision.
 */
public class JevUnavailableException extends Exception {

    public JevUnavailableException(String message) {
        super(message);
    }

    public JevUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
