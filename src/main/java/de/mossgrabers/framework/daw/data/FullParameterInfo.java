// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.daw.data;


/**
 * Information about a parameter of the extended parameter bank.
 *
 * @param index The index of the parameter
 * @param name The parameter name
 * @param normalizedName The normalized parameter name
 * @param normalizedValue The current normalized value
 * @param displayValue The current display value
 * @param exists True if the parameter exists
 *
 * @author Jürgen Moßgraber
 */
public record FullParameterInfo (int index, String name, String normalizedName, double normalizedValue, String displayValue, boolean exists)
{
    // Intentionally empty
}
