// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.osc.module;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.mossgrabers.controller.osc.OSCConfiguration;
import de.mossgrabers.controller.osc.exception.IllegalParameterException;
import de.mossgrabers.controller.osc.exception.MissingCommandException;
import de.mossgrabers.controller.osc.exception.UnknownCommandException;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.DeviceID;
import de.mossgrabers.framework.daw.data.EqualizerBandType;
import de.mossgrabers.framework.daw.data.IChannel;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.IDevice;
import de.mossgrabers.framework.daw.data.IEqualizerDevice;
import de.mossgrabers.framework.daw.data.ILayer;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.ISpecificDevice;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IDeviceBank;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.data.bank.ILayerBank;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.data.empty.EmptyLayer;
import de.mossgrabers.framework.daw.data.empty.EmptyParameter;
import de.mossgrabers.framework.osc.IOpenSoundControlWriter;
import de.mossgrabers.framework.parameter.IFocusedParameter;
import de.mossgrabers.framework.parameter.IParameter;


/**
 * All device related commands.
 *
 * @author Jürgen Moßgraber
 */
public class DeviceModule extends AbstractModule
{
    private static final String     ALL_PARAMS_ADDRESS = "/device/allparams/";

    private final OSCConfiguration   configuration;


    /**
     * Constructor.
     *
     * @param host The host
     * @param model The model
     * @param writer The writer
     * @param configuration The configuration
     */
    public DeviceModule (final IHost host, final IModel model, final IOpenSoundControlWriter writer, final OSCConfiguration configuration)
    {
        super (host, model, writer);

        this.configuration = configuration;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getSupportedCommands ()
    {
        return new String []
        {
            "device",
            "primary",
            "eq"
        };
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final String command, final LinkedList<String> path, final Object value) throws IllegalParameterException, UnknownCommandException, MissingCommandException
    {
        switch (command)
        {
            case "device":
                this.parseCursorDeviceValue (this.model.getCursorDevice (), path, value);
                break;

            case "primary":
                this.parseDeviceValue (this.model.getSpecificDevice (DeviceID.FIRST_INSTRUMENT), path, value);
                break;

            case "eq":
                final IEqualizerDevice specificDevice = (IEqualizerDevice) this.model.getSpecificDevice (DeviceID.EQ);
                if (!this.parseEqValue (specificDevice, path, value))
                    this.parseDeviceValue (specificDevice, path, value);
                break;

            default:
                throw new UnknownCommandException (command);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void flush (final boolean dump)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();
        this.flushDevice (this.writer, "/device/", cd, dump);
        this.writer.sendOSC ("/device/pinned", cd.isPinned (), dump);
        this.writer.sendOSC (ALL_PARAMS_ADDRESS + "count", this.getExistingParameterCount (cd), dump);
        if (cd.hasDrumPads ())
        {
            final IDrumPadBank drumPadBank = cd.getDrumPadBank ();
            for (int i = 0; i < drumPadBank.getPageSize (); i++)
                this.flushDeviceLayer (this.writer, "/device/drumpad/" + (i + 1) + "/", drumPadBank.getItem (i), dump);
        }
        final ILayerBank layerBank = cd.getLayerBank ();
        for (int i = 0; i < layerBank.getPageSize (); i++)
            this.flushDeviceLayer (this.writer, "/device/layer/" + (i + 1) + "/", layerBank.getItem (i), dump);
        final Optional<ILayer> selectedLayer = layerBank.getSelectedItem ();
        this.flushDeviceLayer (this.writer, "/device/layer/selected/", selectedLayer.isEmpty () ? EmptyLayer.getInstance (layerBank.getPageSize ()) : selectedLayer.get (), dump);

        this.flushDevice (this.writer, "/primary/", this.model.getSpecificDevice (DeviceID.FIRST_INSTRUMENT), dump);
        this.flushDevice (this.writer, "/eq/", this.model.getSpecificDevice (DeviceID.EQ), dump);

        // Last hovered/clicked parameter
        final Optional<IFocusedParameter> focusedParameter = this.model.getFocusedParameter ();
        final IParameter param = focusedParameter.isPresent () ? focusedParameter.get () : EmptyParameter.INSTANCE;
        this.flushParameterData (this.writer, "/device/lastparam/", param, dump);
    }


    /**
     * Flush all data of a device.
     *
     * @param writer Where to send the messages to
     * @param deviceAddress The start address for the device
     * @param device The device
     * @param dump Forces a flush if true otherwise only changed values are flushed
     */
    private void flushDevice (final IOpenSoundControlWriter writer, final String deviceAddress, final ISpecificDevice device, final boolean dump)
    {
        writer.sendOSC (deviceAddress + TAG_EXISTS, device.doesExist (), dump);
        writer.sendOSC (deviceAddress + TAG_NAME, device.getName (), dump);
        writer.sendOSC (deviceAddress + TAG_BYPASS, !device.isEnabled (), dump);
        writer.sendOSC (deviceAddress + "expand", device.isExpanded (), dump);
        writer.sendOSC (deviceAddress + "parameters", device.isParameterPageSectionVisible (), dump);
        writer.sendOSC (deviceAddress + "window", device.isWindowOpen (), dump);

        if (device instanceof final IEqualizerDevice equalizer)
        {
            for (int i = 0; i < equalizer.getBandCount (); i++)
            {
                final int oneplus = i + 1;

                writer.sendOSC (deviceAddress + "type/" + oneplus + "/value", equalizer.getTypeID (i).name ().toLowerCase (), dump);
                this.flushParameterData (writer, deviceAddress + "gain/" + oneplus + "/", equalizer.getGainParameter (i), dump);
                this.flushParameterData (writer, deviceAddress + "freq/" + oneplus + "/", equalizer.getFrequencyParameter (i), dump);
                this.flushParameterData (writer, deviceAddress + "q/" + oneplus + "/", equalizer.getQParameter (i), dump);
            }
            return;
        }

        if (device instanceof final ICursorDevice cursorDevice)
        {
            final int positionInBank = device.getIndex ();
            final IDeviceBank deviceBank = cursorDevice.getDeviceBank ();
            for (int i = 0; i < deviceBank.getPageSize (); i++)
            {
                final int oneplus = i + 1;
                final IDevice siblingDevice = deviceBank.getItem (i);
                final String siblingAddress = deviceAddress + "sibling/" + oneplus + "/";
                writer.sendOSC (siblingAddress + TAG_EXISTS, siblingDevice.doesExist (), dump);
                writer.sendOSC (siblingAddress + TAG_NAME, siblingDevice.getName (), dump);
                writer.sendOSC (siblingAddress + TAG_BYPASS, !siblingDevice.isEnabled (), dump);
                writer.sendOSC (siblingAddress + TAG_SELECTED, i == positionInBank, dump);
            }
        }

        final IParameterBank parameterBank = device.getParameterBank ();
        for (int i = 0; i < parameterBank.getPageSize (); i++)
        {
            final int oneplus = i + 1;
            this.flushParameterData (writer, deviceAddress + "param/" + oneplus + "/", parameterBank.getItem (i), dump);
        }

        final IParameterPageBank parameterPageBank = device.getParameterBank ().getPageBank ();
        final int selectedParameterPage = parameterPageBank.getSelectedItemIndex ();
        for (int i = 0; i < parameterPageBank.getPageSize (); i++)
        {
            final int oneplus = i + 1;
            final String pageName = parameterPageBank.getItem (i);
            final String pageAddress = deviceAddress + "page/" + oneplus + "/";
            writer.sendOSC (pageAddress + TAG_EXISTS, !pageName.isBlank (), dump);
            writer.sendOSC (pageAddress, pageName, dump);
            writer.sendOSC (pageAddress + TAG_NAME, pageName, dump);
            writer.sendOSC (pageAddress + TAG_SELECTED, selectedParameterPage == i, dump);
        }
        final Optional<String> selectedItem = parameterPageBank.getSelectedItem ();
        writer.sendOSC (deviceAddress + "page/selected/" + TAG_NAME, selectedItem.isPresent () ? selectedItem.get () : "", dump);
    }


    /**
     * Flush all data of a device layer.
     *
     * @param writer Where to send the messages to
     * @param deviceAddress The start address for the device
     * @param channel The channel of the layer
     * @param dump Forces a flush if true otherwise only changed values are flushed
     */
    private void flushDeviceLayer (final IOpenSoundControlWriter writer, final String deviceAddress, final IChannel channel, final boolean dump)
    {
        if (channel == null)
            return;

        writer.sendOSC (deviceAddress + TAG_EXISTS, channel.doesExist (), dump);
        writer.sendOSC (deviceAddress + TAG_ACTIVATED, channel.isActivated (), dump);
        writer.sendOSC (deviceAddress + TAG_SELECTED, channel.isSelected (), dump);
        writer.sendOSC (deviceAddress + TAG_NAME, channel.getName (), dump);
        writer.sendOSC (deviceAddress + "volumeStr", channel.getVolumeStr (), dump);
        writer.sendOSC (deviceAddress + TAG_VOLUME, channel.getVolume (), dump);
        writer.sendOSC (deviceAddress + "panStr", channel.getPanStr (), dump);
        writer.sendOSC (deviceAddress + "pan", channel.getPan (), dump);
        writer.sendOSC (deviceAddress + "mute", channel.isMute (), dump);
        writer.sendOSC (deviceAddress + "solo", channel.isSolo (), dump);

        final ISendBank sendBank = channel.getSendBank ();
        for (int i = 0; i < sendBank.getPageSize (); i++)
            this.flushParameterData (writer, deviceAddress + "send/" + (i + 1) + "/", sendBank.getItem (i), dump);

        if (this.configuration.isEnableVUMeters ())
            writer.sendOSC (deviceAddress + "vu", channel.getVu (), dump);

        final ColorEx color = channel.getColor ();
        writer.sendOSCColor (deviceAddress + TAG_COLOR, color.getRed (), color.getGreen (), color.getBlue (), dump);
    }


    private void parseCursorDeviceValue (final ICursorDevice cursorDevice, final LinkedList<String> path, final Object value) throws UnknownCommandException, MissingCommandException, IllegalParameterException
    {
        final String command = getSubCommand (path);
        switch (command)
        {
            case "sibling":
                final String siblingIndex = getSubCommand (path);
                final int siblingNo = Integer.parseInt (siblingIndex);
                final String subCommand2 = getSubCommand (path);
                switch (subCommand2)
                {
                    case TAG_SELECT, TAG_SELECTED:
                        if (isTrigger (value) && cursorDevice != null)
                            cursorDevice.getDeviceBank ().getItem (siblingNo - 1).select ();
                        break;

                    default:
                        throw new UnknownCommandException (subCommand2);
                }
                break;

            case "bank":
                if (cursorDevice != null)
                {
                    final String subCommand3 = getSubCommand (path);
                    if (TAG_PAGE.equals (subCommand3))
                    {
                        final String directionCommand = getSubCommand (path);
                        if ("+".equals (directionCommand))
                            cursorDevice.getDeviceBank ().selectNextPage ();
                        else // "-"
                            cursorDevice.getDeviceBank ().selectPreviousPage ();
                    }
                    else
                        throw new UnknownCommandException (subCommand3);
                }
                break;

            case "lastparam":
                final Optional<IFocusedParameter> focusedParameter = this.model.getFocusedParameter ();
                if (focusedParameter.isPresent () && focusedParameter.get ().doesExist ())
                    parseFXParamValue (focusedParameter.get (), path, value);
                break;

            case "allparams":
                this.parseAllParamsValue (cursorDevice, path, value);
                break;

            case "+":
                if (isTrigger (value))
                    cursorDevice.selectNext ();
                break;

            case "-":
                if (isTrigger (value))
                    cursorDevice.selectPrevious ();
                break;

            case "pinned":
                if (value == null)
                    cursorDevice.togglePinned ();
                else
                    cursorDevice.setPinned (isTrigger (value));
                break;

            default:
                path.add (0, command);
                this.parseDeviceValue (cursorDevice, path, value);
                break;
        }
    }


    private void parseDeviceValue (final ISpecificDevice device, final LinkedList<String> path, final Object value) throws UnknownCommandException, MissingCommandException, IllegalParameterException
    {
        final IParameterBank parameterBank = device.getParameterBank ();
        final IParameterPageBank parameterPageBank = parameterBank.getPageBank ();

        final String command = getSubCommand (path);
        switch (command)
        {
            case TAG_PAGE:
                final String subCommand = getSubCommand (path);
                switch (subCommand)
                {
                    case TAG_SELECT, TAG_SELECTED:
                        parameterPageBank.selectPage (toInteger (value) - 1);
                        break;

                    default:
                        try
                        {
                            final int index = Integer.parseInt (subCommand) - 1;
                            parameterPageBank.selectPage (index);
                        }
                        catch (final NumberFormatException ex)
                        {
                            throw new UnknownCommandException (subCommand);
                        }
                        break;
                }
                break;

            case TAG_DUPLICATE:
                device.duplicate ();
                break;

            case TAG_REMOVE:
                device.remove ();
                break;

            case TAG_BYPASS:
                device.toggleEnabledState ();
                break;

            case "expand":
                device.toggleExpanded ();
                break;

            case "parameters":
                device.toggleParameterPageSectionVisible ();
                break;

            case "window":
                device.toggleWindowOpen ();
                break;

            case TAG_INDICATE:
                final String subCommand4 = getSubCommand (path);
                if (TAG_PARAM.equals (subCommand4))
                {
                    for (int i = 0; i < parameterBank.getPageSize (); i++)
                        parameterBank.getItem (i).setIndication (isTrigger (value));
                }
                else
                    throw new UnknownCommandException (subCommand4);
                break;

            case TAG_PARAM:
                final String subCommand5 = getSubCommand (path);
                try
                {
                    final int paramNo = Integer.parseInt (subCommand5) - 1;
                    parseFXParamValue (parameterBank.getItem (paramNo), path, value);
                }
                catch (final NumberFormatException ex)
                {
                    if (isTrigger (value))
                    {
                        switch (subCommand5)
                        {
                            case "+":
                                parameterPageBank.scrollForwards ();
                                break;
                            case "-":
                                parameterPageBank.scrollBackwards ();
                                break;

                            case "bank":
                                final String subCommand6 = getSubCommand (path);
                                if (TAG_PAGE.equals (subCommand6))
                                {
                                    final String subCommand7 = getSubCommand (path);
                                    if ("+".equals (subCommand7))
                                        parameterPageBank.selectNextPage ();
                                    else // "-"
                                        parameterPageBank.selectPreviousPage ();
                                }
                                else
                                    throw new UnknownCommandException (subCommand6);
                                break;

                            default:
                                throw new UnknownCommandException (subCommand5);
                        }
                    }
                }
                break;

            case "drumpad":
                if (device.hasDrumPads ())
                    this.parseLayerOrDrumpad (device, path, value);
                break;

            case "layer":
                this.parseLayerOrDrumpad (device, path, value);
                break;

            default:
                this.host.println ("Unknown Device command: " + command);
                break;
        }
    }


    private void parseAllParamsValue (final ICursorDevice cursorDevice, final LinkedList<String> path, final Object value) throws MissingCommandException, UnknownCommandException, IllegalParameterException
    {
        if (!cursorDevice.doesExist ())
        {
            this.sendAllParametersError ("no device selected");
            return;
        }

        final String command = getSubCommand (path);
        switch (command)
        {
            case "count":
                this.writer.sendOSC (ALL_PARAMS_ADDRESS + "count", this.getExistingParameterCount (cursorDevice), true);
                this.writer.flush ();
                break;

            case "byname":
                this.parseAllParamsByName (cursorDevice, value);
                break;

            case "list":
                this.parseAllParamsList (cursorDevice, path);
                break;

            default:
                this.parseAllParamsIndexValue (cursorDevice, command, path, value);
                break;
        }
    }


    private void parseAllParamsByName (final ICursorDevice cursorDevice, final Object value) throws IllegalParameterException
    {
        if (!(value instanceof final Object [] values) || values.length < 2)
            throw new IllegalParameterException ("Expected name and value parameters");

        final String name = values[0].toString ();
        final int numericValue = toInteger (values[1]);
        final IParameter parameter = this.findParameterByName (cursorDevice, name);
        if (parameter == null)
        {
            this.sendAllParametersError ("parameter not found: " + name);
            return;
        }

        parameter.setValue (numericValue);
    }


    private void parseAllParamsList (final ICursorDevice cursorDevice, final LinkedList<String> path)
    {
        int offset = 0;
        if (!path.isEmpty ())
        {
            final String offsetText = path.removeFirst ();
            try
            {
                offset = Integer.parseInt (offsetText);
            }
            catch (final NumberFormatException ex)
            {
                this.sendAllParametersError ("index out of range: " + offsetText + ", max: " + Math.max (0, this.getExistingParameterCount (cursorDevice) - 1));
                return;
            }
        }

        this.sendAllParametersList (cursorDevice, offset);
    }


    private void parseAllParamsIndexValue (final ICursorDevice cursorDevice, final String indexText, final LinkedList<String> path, final Object value) throws MissingCommandException, UnknownCommandException, IllegalParameterException
    {
        final int index;
        try
        {
            index = Integer.parseInt (indexText);
        }
        catch (final NumberFormatException ex)
        {
            this.sendAllParametersError ("index out of range: " + indexText + ", max: " + this.getExistingParameterCount (cursorDevice));
            return;
        }

        final IParameter parameter = this.getParameterAtIndex (cursorDevice, index);
        if (parameter == null)
        {
            final int max = Math.max (0, this.getExistingParameterCount (cursorDevice) - 1);
            this.sendAllParametersError ("index out of range: " + index + ", max: " + max);
            return;
        }

        final String command = getSubCommand (path);
        switch (command)
        {
            case "value":
                parameter.setValue (toInteger (value));
                break;

            case TAG_NAME:
                this.writer.sendOSC (ALL_PARAMS_ADDRESS + index + "/name", parameter.getName (), true);
                this.writer.flush ();
                break;

            default:
                throw new UnknownCommandException (command);
        }
    }


    private void sendAllParametersList (final ICursorDevice cursorDevice, final int offset)
    {
        final List<IParameter> parameters = this.getExistingParameters (cursorDevice);
        final int count = parameters.size ();
        if (offset < 0 || offset >= count && count > 0)
        {
            this.sendAllParametersError ("index out of range: " + offset + ", max: " + Math.max (0, count - 1));
            return;
        }

        this.writer.sendOSC (ALL_PARAMS_ADDRESS + "begin", List.of (cursorDevice.getName (), Integer.valueOf (count)), true);
        for (int i = offset; i < count; i++)
        {
            final IParameter parameter = parameters.get (i);
            this.writer.sendOSC (ALL_PARAMS_ADDRESS + "item", List.of (Integer.valueOf (i), parameter.getName (), parameter.getDisplayedValue (), Float.valueOf ((float) this.toNormalized (parameter))), true);
        }
        this.writer.sendOSC (ALL_PARAMS_ADDRESS + "end", List.of (), true);
        this.writer.flush ();
    }


    private IParameter findParameterByName (final ICursorDevice cursorDevice, final String name)
    {
        final String loweredName = name.toLowerCase (Locale.US);
        for (final IParameter parameter: this.getExistingParameters (cursorDevice))
        {
            if (parameter.getName ().toLowerCase (Locale.US).equals (loweredName))
                return parameter;
        }
        return null;
    }


    private IParameter getParameterAtIndex (final ICursorDevice cursorDevice, final int index)
    {
        if (index < 0)
            return null;

        final List<IParameter> parameters = this.getExistingParameters (cursorDevice);
        return index < parameters.size () ? parameters.get (index) : null;
    }


    private int getExistingParameterCount (final ICursorDevice cursorDevice)
    {
        return this.getExistingParameters (cursorDevice).size ();
    }


    private List<IParameter> getExistingParameters (final ICursorDevice cursorDevice)
    {
        final List<IParameter> parameters = cursorDevice.getParameterList ().getParameters ();
        final List<IParameter> existingParameters = new ArrayList<> (parameters.size ());
        for (final IParameter parameter: parameters)
        {
            if (parameter.doesExist ())
                existingParameters.add (parameter);
        }
        return existingParameters;
    }


    private double toNormalized (final IParameter parameter)
    {
        final int upperBound = this.model.getValueChanger ().getUpperBound ();
        return upperBound > 0 ? (double) parameter.getValue () / upperBound : 0.0;
    }


    private void sendAllParametersError (final String message)
    {
        this.writer.sendOSC (ALL_PARAMS_ADDRESS + "error", message, true);
        this.writer.flush ();
    }


    private boolean parseEqValue (final IEqualizerDevice equalizerDevice, final LinkedList<String> path, final Object value) throws MissingCommandException, UnknownCommandException, IllegalParameterException
    {
        final String command = getSubCommand (path);
        switch (command)
        {
            case "type":
                final String subCommand1 = getSubCommand (path);
                try
                {
                    final int bandNo = Integer.parseInt (subCommand1) - 1;
                    equalizerDevice.setType (bandNo, EqualizerBandType.valueOf (toString (value).toUpperCase ()));
                }
                catch (final NumberFormatException ex)
                {
                    throw new UnknownCommandException (subCommand1);
                }
                return true;

            case "gain":
                final String subCommand2 = getSubCommand (path);
                try
                {
                    final int bandNo = Integer.parseInt (subCommand2) - 1;
                    equalizerDevice.getGainParameter (bandNo).setValue (toInteger (value));
                }
                catch (final NumberFormatException ex)
                {
                    throw new UnknownCommandException (subCommand2);
                }
                return true;

            case "freq":
                final String subCommand3 = getSubCommand (path);
                try
                {
                    final int bandNo = Integer.parseInt (subCommand3) - 1;
                    equalizerDevice.getFrequencyParameter (bandNo).setValue (toInteger (value));
                }
                catch (final NumberFormatException ex)
                {
                    throw new UnknownCommandException (subCommand3);
                }
                return true;

            case "q":
                final String subCommand4 = getSubCommand (path);
                try
                {
                    final int bandNo = Integer.parseInt (subCommand4) - 1;
                    equalizerDevice.getQParameter (bandNo).setValue (toInteger (value));
                }
                catch (final NumberFormatException ex)
                {
                    throw new UnknownCommandException (subCommand4);
                }
                return true;

            case "add":
                final ITrack cursorTrack = this.model.getCursorTrack ();
                if (cursorTrack.doesExist () && isTrigger (value))
                    cursorTrack.addEqualizerDevice ();
                return true;

            default:
                // Let this be handled by the normal device parser
                path.add (0, command);
                return false;
        }
    }


    private void parseLayerOrDrumpad (final ISpecificDevice device, final LinkedList<String> path, final Object value) throws MissingCommandException, UnknownCommandException, IllegalParameterException
    {
        final ILayerBank layerBank = device.getLayerBank ();

        final String command = getSubCommand (path);
        try
        {
            final int layerNo;
            if (TAG_SELECTED.equals (command) || TAG_SELECT.equals (command))
            {
                final Optional<ILayer> selectedLayer = layerBank.getSelectedItem ();
                layerNo = selectedLayer.isEmpty () ? -1 : selectedLayer.get ().getIndex ();
            }
            else
            {
                layerNo = Integer.parseInt (command) - 1;
            }
            this.parseDeviceLayerValue (device, layerNo, path, value);
        }
        catch (final NumberFormatException ex)
        {
            switch (command)
            {
                case "parent":
                    if (device.doesExist () && device instanceof final ICursorDevice cursorDevice)
                    {
                        cursorDevice.selectParent ();
                        cursorDevice.selectChannel ();
                    }
                    break;

                case "+":
                    layerBank.selectNextItem ();
                    break;

                case "-":
                    layerBank.selectPreviousItem ();
                    break;

                case TAG_PAGE:
                    if (path.isEmpty ())
                    {
                        this.host.println ("Missing Layer/Drumpad Page subcommand: " + command);
                        return;
                    }
                    if ("+".equals (path.get (0)))
                        layerBank.selectNextPage ();
                    else
                        layerBank.selectPreviousPage ();
                    break;

                default:
                    throw new UnknownCommandException (command);
            }
        }
    }


    private void parseDeviceLayerValue (final ISpecificDevice cursorDevice, final int layerIndex, final LinkedList<String> path, final Object value) throws UnknownCommandException, IllegalParameterException, MissingCommandException
    {
        final String command = getSubCommand (path);
        final ILayerBank layerBank = cursorDevice.getLayerBank ();
        if (layerIndex >= layerBank.getPageSize ())
        {
            this.host.println ("Layer or drumpad index larger than page size: " + layerIndex);
            return;
        }

        final IChannel layer = layerBank.getItem (layerIndex);
        switch (command)
        {
            case TAG_ACTIVATED:
                layer.setIsActivated (toInteger (value) > 0);
                break;

            case TAG_SELECT, TAG_SELECTED:
                layer.select ();
                break;

            case TAG_NAME:
                if (value != null)
                    layer.setName (value.toString ());
                break;

            case TAG_VOLUME:
                if (path.isEmpty ())
                    layer.setVolume (toInteger (value));
                else if (TAG_INDICATE.equals (path.get (0)))
                    layer.setVolumeIndication (isTrigger (value));
                else if (TAG_RESET.equals (path.get (0)))
                    layer.resetVolume ();
                else if (TAG_TOUCHED.equals (path.get (0)))
                    layer.touchVolume (isTrigger (value));
                break;

            case "pan":
                if (path.isEmpty ())
                    layer.setPan (toInteger (value));
                else if (TAG_INDICATE.equals (path.get (0)))
                    layer.setPanIndication (isTrigger (value));
                else if (TAG_RESET.equals (path.get (0)))
                    layer.resetPan ();
                else if (TAG_TOUCHED.equals (path.get (0)))
                    layer.touchPan (isTrigger (value));
                break;

            case "mute":
                if (value == null)
                    layer.toggleMute ();
                else
                    layer.setMute (isTrigger (value));
                break;

            case "solo":
                if (value == null)
                    layer.toggleSolo ();
                else
                    layer.setSolo (isTrigger (value));
                break;

            case "send":
                final int sendNo = Integer.parseInt (path.removeFirst ()) - 1;
                if (path.isEmpty ())
                    return;

                final String cmd = path.removeFirst ();
                final ISend send = layer.getSendBank ().getItem (sendNo);
                if (TAG_VOLUME.equals (cmd))
                {
                    if (path.isEmpty ())
                        send.setValue (toInteger (value));
                    else if (TAG_INDICATE.equals (path.get (0)))
                        send.setIndication (isTrigger (value));
                    else if (TAG_TOUCHED.equals (path.get (0)))
                        send.touchValue (isTrigger (value));
                }
                else if (TAG_ACTIVATED.equals (cmd))
                {
                    if (isTrigger (value))
                        send.toggleEnabled ();
                }
                else
                    throw new UnknownCommandException (cmd);
                break;

            case "enter":
                layer.enter ();
                break;

            default:
                throw new UnknownCommandException (command);
        }
    }


    private static void parseFXParamValue (final IParameter param, final LinkedList<String> path, final Object value) throws MissingCommandException, IllegalParameterException, UnknownCommandException
    {
        final String command = getSubCommand (path);
        switch (command)
        {
            case "value":
                param.setValue (toInteger (value));
                break;

            case TAG_INDICATE:
                param.setIndication (isTrigger (value));
                break;

            case TAG_RESET:
                param.resetValue ();
                break;

            case TAG_TOUCHED:
                param.touchValue (isTrigger (value));
                break;

            default:
                throw new UnknownCommandException (command);
        }
    }
}
