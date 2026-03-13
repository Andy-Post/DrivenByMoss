// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.daw.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.Parameter;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.constants.DeviceConstants;
import de.mossgrabers.framework.daw.data.FullParameterInfo;


/**
 * Implementation of an extended parameter bank.
 *
 * @author Jürgen Moßgraber
 */
public class ExtendedParameterBankImpl
{
    private static final int        NUM_PARAMS_PER_PAGE = 8;

    private final IHost             host;
    private final List<ParameterRef> parameters          = new ArrayList<> (DeviceConstants.EXTENDED_PARAMETER_BANK_SIZE);
    private final Map<String, Integer> nameLookup        = new HashMap<> ();


    /**
     * Constructor.
     *
     * @param host The host
     * @param device The Bitwig device
     */
    public ExtendedParameterBankImpl (final IHost host, final Device device)
    {
        this.host = host;

        final int numPages = DeviceConstants.EXTENDED_PARAMETER_BANK_SIZE / NUM_PARAMS_PER_PAGE;
        for (int i = 0; i < numPages; i++)
        {
            final int page = i;
            final CursorRemoteControlsPage remoteControls = device.createCursorRemoteControlsPage ("Ext Page " + page, NUM_PARAMS_PER_PAGE, "");
            remoteControls.pageCount ().addValueObserver (newValue -> this.reAdjust (remoteControls, page), -1);
            remoteControls.selectedPageIndex ().addValueObserver (newValue -> this.reAdjust (remoteControls, page), -1);

            for (int p = 0; p < NUM_PARAMS_PER_PAGE; p++)
            {
                final int index = i * NUM_PARAMS_PER_PAGE + p;
                final Parameter parameter = remoteControls.getParameter (p);
                final ParameterRef parameterRef = new ParameterRef (index, parameter);
                this.parameters.add (parameterRef);
            }
        }
    }


    /**
     * Get all existing parameters.
     *
     * @return The list
     */
    public FullParameterInfo [] getAllParameters ()
    {
        this.updateNameLookup ();

        final List<FullParameterInfo> result = new ArrayList<> (this.nameLookup.size ());
        for (final ParameterRef parameter: this.parameters)
        {
            if (parameter.exists)
                result.add (parameter.toInfo ());
        }
        return result.toArray (new FullParameterInfo [0]);
    }


    /**
     * Get a parameter by index.
     *
     * @param index The index
     * @return The parameter or null
     */
    public FullParameterInfo getParameterByIndex (final int index)
    {
        if (index < 0 || index >= this.parameters.size ())
            return null;

        final ParameterRef parameter = this.parameters.get (index);
        return parameter.exists ? parameter.toInfo () : null;
    }


    /**
     * Get a parameter by name.
     *
     * @param name The name
     * @return The parameter or null
     */
    public FullParameterInfo getParameterByName (final String name)
    {
        final String normalizedName = normalizeName (name);
        if (normalizedName.isEmpty ())
            return null;

        this.updateNameLookup ();
        final Integer index = this.nameLookup.get (normalizedName);
        if (index == null)
            return null;

        return this.parameters.get (index.intValue ()).toInfo ();
    }


    /**
     * Get the number of existing parameters.
     *
     * @return The number of existing parameters
     */
    public int getExistingParameterCount ()
    {
        int count = 0;
        for (final ParameterRef parameter: this.parameters)
        {
            if (parameter.exists)
                count++;
        }
        return count;
    }


    private static String normalizeName (final String name)
    {
        if (name == null)
            return "";
        return name.trim ().toLowerCase (Locale.US);
    }


    private void updateNameLookup ()
    {
        this.nameLookup.clear ();
        for (final ParameterRef parameter: this.parameters)
        {
            if (!parameter.exists)
                continue;

            final String normalizedName = normalizeName (parameter.name);
            if (normalizedName.isEmpty ())
                continue;

            this.nameLookup.putIfAbsent (normalizedName, Integer.valueOf (parameter.index));
        }
    }


    private void reAdjust (final CursorRemoteControlsPage remoteControls, final int page)
    {
        if (page < remoteControls.pageCount ().get () && remoteControls.selectedPageIndex ().get () != page)
            this.host.scheduleTask ( () -> remoteControls.selectedPageIndex ().set (page), 500);
    }


    private static class ParameterRef
    {
        private final int    index;
        private final Parameter parameter;

        private volatile boolean exists;
        private volatile String  name = "";
        private volatile double  normalizedValue;
        private volatile String  displayValue = "";


        /**
         * Constructor.
         *
         * @param index The parameter index
         * @param parameter The parameter
         */
        public ParameterRef (final int index, final Parameter parameter)
        {
            this.index = index;
            this.parameter = parameter;

            parameter.exists ().markInterested ();
            parameter.name ().markInterested ();
            parameter.value ().markInterested ();
            parameter.displayedValue ().markInterested ();

            this.exists = parameter.exists ().get ();
            this.name = parameter.name ().get ();
            this.normalizedValue = parameter.value ().get ();
            this.displayValue = parameter.displayedValue ().get ();

            parameter.exists ().addValueObserver (value -> this.exists = value && !this.name.isBlank ());
            parameter.name ().addValueObserver (value -> {
                this.name = value;
                this.exists = this.parameter.exists ().get () && !value.isBlank ();
            });
            parameter.value ().addValueObserver (value -> this.normalizedValue = value);
            parameter.displayedValue ().addValueObserver (value -> this.displayValue = value);
        }


        public FullParameterInfo toInfo ()
        {
            return new FullParameterInfo (this.index, this.name, normalizeName (this.name), this.normalizedValue, this.displayValue, this.exists);
        }
    }
}
