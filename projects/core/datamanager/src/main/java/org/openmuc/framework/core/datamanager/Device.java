/*
 * Copyright 2011-14 Fraunhofer ISE
 *
 * This file is part of OpenMUC.
 * For more information visit http://www.openmuc.org
 *
 * OpenMUC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenMUC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenMUC.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.openmuc.framework.core.datamanager;

import org.openmuc.framework.config.ChannelConfig;
import org.openmuc.framework.data.Flag;
import org.openmuc.framework.dataaccess.ChannelState;
import org.openmuc.framework.dataaccess.DeviceState;
import org.openmuc.framework.datalogger.spi.LogChannel;
import org.openmuc.framework.driver.spi.DeviceConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

public final class Device {

    private final static Logger logger = LoggerFactory.getLogger(Device.class);

    DeviceConfigImpl deviceConfig;
    DataManager dataManager;
    DeviceState state = null;
    DeviceConnection connection;

    private final List<DeviceEvent> eventList = new ArrayList<DeviceEvent>();
    private List<DeviceTask> taskList;

    public Device(DataManager dataManager,
                  DeviceConfigImpl deviceConfig,
                  long currentTime,
                  List<LogChannel> logChannels) {
        this.dataManager = dataManager;
        this.deviceConfig = deviceConfig;

        if (deviceConfig.interfaceAddress.isEmpty()) {
            taskList = new ArrayList<DeviceTask>();
        } else {
            taskList = deviceConfig.driverParent.deviceTasksByInterfaceAddress.get(deviceConfig.interfaceAddress);
        }

        if (deviceConfig.isDisabled()) {
            state = DeviceState.DISABLED;
            for (ChannelConfigImpl channelConfig : deviceConfig.channelConfigsById.values()) {
                channelConfig.channel = new ChannelImpl(dataManager,
                                                        channelConfig,
                                                        ChannelState.DISABLED,
                                                        Flag.DISABLED,
                                                        currentTime,
                                                        logChannels);
            }
        } else {
            if (deviceConfig.driverParent.activeDriver == null) {
                state = DeviceState.DRIVER_UNAVAILABLE;
                for (ChannelConfigImpl channelConfig : deviceConfig.channelConfigsById.values()) {
                    channelConfig.channel = new ChannelImpl(dataManager,
                                                            channelConfig,
                                                            ChannelState.DRIVER_UNAVAILABLE,
                                                            Flag.DRIVER_UNAVAILABLE,
                                                            currentTime,
                                                            logChannels);
                }
            } else {
                state = DeviceState.CONNECTING;
                for (ChannelConfigImpl channelConfig : deviceConfig.channelConfigsById.values()) {
                    channelConfig.channel = new ChannelImpl(dataManager,
                                                            channelConfig,
                                                            ChannelState.CONNECTING,
                                                            Flag.CONNECTING,
                                                            currentTime,
                                                            logChannels);
                }
            }
        }
    }

    public void configChangedSignal(DeviceConfigImpl newDeviceConfig,
                                    long currentTime,
                                    List<LogChannel> logChannels) {
        DeviceConfigImpl oldDeviceConfig = deviceConfig;
        deviceConfig = newDeviceConfig;
        newDeviceConfig.device = this;

        if (state == DeviceState.DISABLED) {
            if (newDeviceConfig.isDisabled()) {
                setStatesForNewDevice(oldDeviceConfig,
                                      DeviceState.DISABLED,
                                      ChannelState.DISABLED,
                                      Flag.DISABLED,
                                      currentTime,
                                      logChannels);
            } else {
                if (deviceConfig.driverParent.activeDriver == null) {
                    setStatesForNewDevice(oldDeviceConfig,
                                          DeviceState.DRIVER_UNAVAILABLE,
                                          ChannelState.DRIVER_UNAVAILABLE,
                                          Flag.DRIVER_UNAVAILABLE,
                                          currentTime,
                                          logChannels);
                } else {
                    setStatesForNewDevice(oldDeviceConfig,
                                          DeviceState.CONNECTING,
                                          ChannelState.CONNECTING,
                                          Flag.CONNECTING,
                                          currentTime,
                                          logChannels);
                    connect();
                }
            }
        } else if (state == DeviceState.DRIVER_UNAVAILABLE) {
            if (newDeviceConfig.isDisabled()) {
                setStatesForNewDevice(oldDeviceConfig,
                                      DeviceState.DISABLED,
                                      ChannelState.DISABLED,
                                      Flag.DISABLED,
                                      currentTime,
                                      logChannels);
            } else {
                setStatesForNewDevice(oldDeviceConfig,
                                      DeviceState.DRIVER_UNAVAILABLE,
                                      ChannelState.DRIVER_UNAVAILABLE,
                                      Flag.DRIVER_UNAVAILABLE,
                                      currentTime,
                                      logChannels);
            }
        } else if (state == DeviceState.CONNECTING) {
            setStatesForNewDevice(oldDeviceConfig,
                                  DeviceState.CONNECTING,
                                  ChannelState.CONNECTING,
                                  Flag.CONNECTING,
                                  currentTime,
                                  logChannels);
            if (newDeviceConfig.isDisabled()) {
                addEvent(DeviceEvent.DISABLED);
            } else if (oldDeviceConfig.isDisabled()) {
                eventList.remove(DeviceEvent.DISABLED);
            }
        } else if (state == DeviceState.DISCONNECTING) {
            setStatesForNewDevice(oldDeviceConfig,
                                  DeviceState.DISCONNECTING,
                                  ChannelState.DISCONNECTING,
                                  Flag.DISCONNECTING,
                                  currentTime,
                                  logChannels);
            if (newDeviceConfig.isDisabled()) {
                addEvent(DeviceEvent.DISABLED);
            } else if (oldDeviceConfig.isDisabled()) {
                eventList.remove(DeviceEvent.DISABLED);
            }
        } else if (state == DeviceState.WAITING_FOR_CONNECTION_RETRY) {
            if (newDeviceConfig.isDisabled()) {
                setStatesForNewDevice(oldDeviceConfig,
                                      DeviceState.DISABLED,
                                      ChannelState.DISABLED,
                                      Flag.DISABLED,
                                      currentTime,
                                      logChannels);
            } else {
                setStatesForNewDevice(oldDeviceConfig,
                                      DeviceState.WAITING_FOR_CONNECTION_RETRY,
                                      ChannelState.WAITING_FOR_CONNECTION_RETRY,
                                      Flag.WAITING_FOR_CONNECTION_RETRY,
                                      currentTime,
                                      logChannels);
            }
        } else {
            if (newDeviceConfig.isDisabled()) {
                if (state == DeviceState.CONNECTED) {
                    eventList.add(DeviceEvent.DISABLED);
                    // TODO disable all readworkers
                    setStatesForNewConnectedDevice(oldDeviceConfig,
                                                   DeviceState.DISCONNECTING,
                                                   ChannelState.DISCONNECTING,
                                                   Flag.DISCONNECTING,
                                                   currentTime,
                                                   logChannels);
                    disconnect();
                } else {
                    // Adding the disabled event will automatically disconnect the device as soon as the active task is
                    // finished
                    eventList.add(DeviceEvent.DISABLED);
                    // Update channels anyway to update the log channels
                    updateChannels(oldDeviceConfig,
                                   ChannelState.DISCONNECTING,
                                   Flag.DISCONNECTING,
                                   currentTime,
                                   logChannels);
                }
            } else {
                updateChannels(oldDeviceConfig,
                               ChannelState.CONNECTED,
                               Flag.NO_VALUE_RECEIVED_YET,
                               currentTime,
                               logChannels);
            }
        }

    }

    private void updateChannels(DeviceConfigImpl oldDeviceConfig,
                                ChannelState channelState,
                                Flag flag,
                                long currentTime,
                                List<LogChannel> logChannels) {
        List<ChannelRecordContainerImpl> listeningChannels = null;
        for (Entry<String, ChannelConfigImpl> newChannelConfigEntry : deviceConfig.channelConfigsById
                .entrySet()) {
            ChannelConfigImpl oldChannelConfig = oldDeviceConfig.channelConfigsById.get(
                    newChannelConfigEntry.getKey());
            ChannelConfigImpl newChannelConfig = newChannelConfigEntry.getValue();
            if (oldChannelConfig == null) {
                if (newChannelConfig.state != ChannelState.DISABLED) {
                    if (newChannelConfig.listening == true) {
                        if (listeningChannels == null) {
                            listeningChannels = new LinkedList<ChannelRecordContainerImpl>();
                        }
                        newChannelConfig.channel = new ChannelImpl(dataManager,
                                                                   newChannelConfig,
                                                                   ChannelState.LISTENING,
                                                                   Flag.NO_VALUE_RECEIVED_YET,
                                                                   currentTime,
                                                                   logChannels);
                        listeningChannels.add(newChannelConfig.channel.createChannelRecordContainer());
                    } else if (newChannelConfig.samplingInterval
                               != ChannelConfig.SAMPLING_INTERVAL_DEFAULT) {
                        newChannelConfig.channel = new ChannelImpl(dataManager,
                                                                   newChannelConfig,
                                                                   ChannelState.SAMPLING,
                                                                   Flag.NO_VALUE_RECEIVED_YET,
                                                                   currentTime,
                                                                   logChannels);
                        dataManager.addToSamplingCollections(newChannelConfig.channel, currentTime);
                    } else {
                        newChannelConfig.channel = new ChannelImpl(dataManager,
                                                                   newChannelConfig,
                                                                   channelState,
                                                                   flag,
                                                                   currentTime,
                                                                   logChannels);
                    }
                } else {
                    newChannelConfig.channel = new ChannelImpl(dataManager,
                                                               newChannelConfig,
                                                               channelState,
                                                               flag,
                                                               currentTime,
                                                               logChannels);
                }
            } else {
                newChannelConfig.channel = oldChannelConfig.channel;
                newChannelConfig.channel.config = newChannelConfig;
                newChannelConfig.channel.setNewDeviceState(oldChannelConfig.state,
                                                           newChannelConfig.channel.currentSample.getFlag());
                if (!newChannelConfig.isDisabled() && (newChannelConfig.loggingInterval > 0)) {
                    dataManager.addToLoggingCollections(newChannelConfig.channel, currentTime);
                    logChannels.add(newChannelConfig);
                } else if (!oldChannelConfig.disabled && oldChannelConfig.loggingInterval > 0) {
                    dataManager.removeFromLoggingCollections(newChannelConfig.channel);
                }
                if (newChannelConfig.isSampling()) {
                    dataManager.addToSamplingCollections(newChannelConfig.channel, currentTime);
                } else if (oldChannelConfig.isSampling()) {
                    dataManager.removeFromSamplingCollections(newChannelConfig.channel);
                }
                if (!newChannelConfig.channelAddress.equals(oldChannelConfig.channelAddress)) {
                    newChannelConfig.channel.handle = null;
                }
            }
        }

        if (listeningChannels != null) {
            addStartListeningTask(new StartListeningTask(dataManager, this, listeningChannels));
        }
    }

    private void addEvent(DeviceEvent event) {
        Iterator<DeviceEvent> i = eventList.iterator();
        while (i.hasNext()) {
            if (i.next() == event) {
                return;
            }
        }
        eventList.add(event);
    }

    private void setStatesForNewDevice(DeviceConfigImpl oldDeviceConfig,
                                       DeviceState DeviceState,
                                       ChannelState channelState,
                                       Flag flag,
                                       long currentTime,
                                       List<LogChannel> logChannels) {
        state = DeviceState;
        for (Entry<String, ChannelConfigImpl> newChannelConfigEntry : deviceConfig.channelConfigsById
                .entrySet()) {
            ChannelConfigImpl oldChannelConfig = oldDeviceConfig.channelConfigsById.get(
                    newChannelConfigEntry.getKey());
            if (oldChannelConfig == null) {
                newChannelConfigEntry.getValue().channel = new ChannelImpl(dataManager,
                                                                           newChannelConfigEntry.getValue(),
                                                                           channelState,
                                                                           flag,
                                                                           currentTime,
                                                                           logChannels);
            } else {
                newChannelConfigEntry.getValue().channel = oldChannelConfig.channel;
                newChannelConfigEntry.getValue().channel.config = newChannelConfigEntry.getValue();
                newChannelConfigEntry.getValue().channel.setNewDeviceState(channelState, flag);
                if (!newChannelConfigEntry.getValue().isDisabled()
                    && (newChannelConfigEntry.getValue().loggingInterval > 0)) {
                    dataManager.addToLoggingCollections(newChannelConfigEntry.getValue().channel,
                                                        currentTime);
                    logChannels.add(newChannelConfigEntry.getValue());
                }
            }
        }
    }

    private void setStatesForNewConnectedDevice(DeviceConfigImpl oldDeviceConfig,
                                                DeviceState DeviceState,
                                                ChannelState channelState,
                                                Flag flag,
                                                long currentTime,
                                                List<LogChannel> logChannels) {
        state = DeviceState;
        List<ChannelRecordContainerImpl> listeningChannels = null;
        for (Entry<String, ChannelConfigImpl> newChannelConfigEntry : deviceConfig.channelConfigsById
                .entrySet()) {
            ChannelConfigImpl oldChannelConfig = oldDeviceConfig.channelConfigsById.get(
                    newChannelConfigEntry.getKey());
            ChannelConfigImpl newChannelConfig = newChannelConfigEntry.getValue();
            if (oldChannelConfig == null) {
                if (newChannelConfig.state != ChannelState.DISABLED) {
                    if (newChannelConfig.listening == true) {
                        if (listeningChannels == null) {
                            listeningChannels = new LinkedList<ChannelRecordContainerImpl>();
                        }
                        listeningChannels.add(newChannelConfig.channel.createChannelRecordContainer());
                        newChannelConfig.channel = new ChannelImpl(dataManager,
                                                                   newChannelConfig,
                                                                   ChannelState.LISTENING,
                                                                   Flag.NO_VALUE_RECEIVED_YET,
                                                                   currentTime,
                                                                   logChannels);
                    } else if (newChannelConfig.samplingInterval
                               != ChannelConfig.SAMPLING_INTERVAL_DEFAULT) {
                        newChannelConfig.channel = new ChannelImpl(dataManager,
                                                                   newChannelConfig,
                                                                   ChannelState.SAMPLING,
                                                                   Flag.NO_VALUE_RECEIVED_YET,
                                                                   currentTime,
                                                                   logChannels);
                        dataManager.addToSamplingCollections(newChannelConfig.channel, currentTime);
                    } else {
                        newChannelConfig.channel = new ChannelImpl(dataManager,
                                                                   newChannelConfig,
                                                                   channelState,
                                                                   flag,
                                                                   currentTime,
                                                                   logChannels);
                    }
                } else {
                    newChannelConfig.channel = new ChannelImpl(dataManager,
                                                               newChannelConfig,
                                                               channelState,
                                                               flag,
                                                               currentTime,
                                                               logChannels);
                }
            } else {
                newChannelConfig.channel = oldChannelConfig.channel;
                newChannelConfig.channel.config = newChannelConfig;
                newChannelConfig.channel.setNewDeviceState(channelState, flag);
                if (!newChannelConfigEntry.getValue().isDisabled()
                    && (newChannelConfigEntry.getValue().loggingInterval > 0)) {
                    dataManager.addToLoggingCollections(newChannelConfig.channel, currentTime);
                    logChannels.add(newChannelConfigEntry.getValue());
                }
            }
        }

    }

    private void setStates(DeviceState DeviceState, ChannelState channelState, Flag flag) {
        state = DeviceState;
        for (ChannelConfigImpl channelConfig : deviceConfig.channelConfigsById.values()) {
            if (channelConfig.state != ChannelState.DISABLED) {
                channelConfig.state = channelState;
                if (channelConfig.channel.currentSample.getFlag()
                    != Flag.SAMPLING_AND_LISTENING_DISABLED) {
                    channelConfig.channel.setFlag(flag);
                }
            }
        }
    }

    void driverRegisteredSignal() {

        if (state == DeviceState.DRIVER_UNAVAILABLE) {
            setStates(DeviceState.CONNECTING, ChannelState.CONNECTING, Flag.CONNECTING);
            connect();
        } else if (state == DeviceState.DISCONNECTING) {
            eventList.add(DeviceEvent.DRIVER_REGISTERED);
        }
    }

    void driverDeregisteredSignal() {

        if (state == DeviceState.DISABLED) {
            dataManager.activeDeviceCountDown--;
            if (dataManager.activeDeviceCountDown == 0) {
                dataManager.driverRemovedSignal.countDown();
            }
        } else if (state == DeviceState.CONNECTED) {

            eventList.add(0, DeviceEvent.DRIVER_DEREGISTERED);

            disableSampling();
            removeAllTasksOfThisDevice();
            setStates(DeviceState.DISCONNECTING, ChannelState.DISCONNECTING, Flag.DISCONNECTING);
            disconnect();
        } else if (state == DeviceState.WAITING_FOR_CONNECTION_RETRY) {
            disableConnectionRetry();
            setStates(DeviceState.DRIVER_UNAVAILABLE,
                      ChannelState.DRIVER_UNAVAILABLE,
                      Flag.DRIVER_UNAVAILABLE);
            dataManager.activeDeviceCountDown--;
            if (dataManager.activeDeviceCountDown == 0) {
                dataManager.driverRemovedSignal.countDown();
            }
        } else {
            // add driver deregistered event always to the front of the queue
            eventList.add(0, DeviceEvent.DRIVER_DEREGISTERED);
        }
    }

    public void deleteSignal() {
        if (state == DeviceState.DRIVER_UNAVAILABLE || state == DeviceState.DISABLED) {
            setDeleted();
        } else if (state == DeviceState.WAITING_FOR_CONNECTION_RETRY) {
            disableConnectionRetry();
            setDeleted();
        } else if (state == DeviceState.CONNECTED) {
            eventList.add(DeviceEvent.DELETED);
            setStates(DeviceState.DISCONNECTING, ChannelState.DISCONNECTING, Flag.DISCONNECTING);
            disconnect();
        } else {
            eventList.add(DeviceEvent.DELETED);
        }
    }

    void connectedSignal(long currentTime) {

        taskList.remove(0);

        if (eventList.size() == 0) {
            setConnected(currentTime);
            executeNextTask();
        } else {
            handleEventQueueWhenConnected();
        }
    }

    void connectFailureSignal() {
        taskList.remove(0);
        if (eventList.size() == 0) {
            setStates(DeviceState.WAITING_FOR_CONNECTION_RETRY,
                      ChannelState.WAITING_FOR_CONNECTION_RETRY,
                      Flag.WAITING_FOR_CONNECTION_RETRY);
            long startTimestamp = System.currentTimeMillis()
                                  + deviceConfig.getConnectRetryInterval();
            dataManager.addReconnectDeviceToActions(this, startTimestamp);
            removeAllTasksOfThisDevice();
        } else {
            handleEventQueueWhenDisconnected();
        }
    }

    // TODO is this function thread save?
    public void disconnectedSignal() {
        // TODO in rare cases where the RecordsReceivedListener causes the disconnectSignal while a SamplingTask is
        // still sampling this could cause problems
        synchronized (this) {
            removeAllTasksOfThisDevice();
            if (eventList.size() == 0) {
                setStates(DeviceState.CONNECTING, ChannelState.CONNECTING, Flag.CONNECTING);
                connect();
            } else {
                handleEventQueueWhenDisconnected();
            }
        }
    }

    public void connectRetrySignal() {
        setStates(DeviceState.CONNECTING, ChannelState.CONNECTING, Flag.CONNECTING);
        connect();
    }

    private void disableConnectionRetry() {
        dataManager.removeFromConnectionRetry(this);
    }

    private void setDeleted() {
        for (ChannelConfigImpl channelConfig : deviceConfig.channelConfigsById.values()) {
            channelConfig.state = ChannelState.DELETED;
            channelConfig.channel.setFlag(Flag.CHANNEL_DELETED);
            channelConfig.channel.handle = null;
        }
        state = DeviceState.DELETED;
    }

    private void disableSampling() {
        for (ChannelConfigImpl channelConfig : deviceConfig.channelConfigsById.values()) {
            if (channelConfig.state != ChannelState.DISABLED) {
                if (channelConfig.state == ChannelState.SAMPLING) {
                    dataManager.removeFromSamplingCollections(channelConfig.channel);
                }
            }
        }
    }

    private void handleEventQueueWhenConnected() {

        removeAllTasksOfThisDevice();
        setStates(DeviceState.DISCONNECTING, ChannelState.DISCONNECTING, Flag.DISCONNECTING);
        disconnect();

    }

    private void removeAllTasksOfThisDevice() {
        Iterator<DeviceTask> i = taskList.iterator();
        while (i.hasNext()) {
            DeviceTask deviceTask = i.next();
            if (deviceTask.device == this) {
                i.remove();
            }
        }

        if (taskList.size() > 0) {
            if (!taskList.get(0).isAlive()) {
                taskList.get(0).device.executeNextTask();
            }
        }
    }

    private void handleEventQueueWhenDisconnected() {

        // DeviceEvent.DRIVER_DEREGISTERED will always be put at position 0
        if (eventList.get(0) == DeviceEvent.DRIVER_DEREGISTERED) {

            synchronized (dataManager.driverRemovedSignal) {
                dataManager.activeDeviceCountDown--;
                if (dataManager.activeDeviceCountDown == 0) {
                    dataManager.driverRemovedSignal.countDown();
                }
            }
        }

        DeviceEvent lastEvent = eventList.get(eventList.size() - 1);

        if (lastEvent == DeviceEvent.DRIVER_DEREGISTERED) {
            setStates(DeviceState.DRIVER_UNAVAILABLE,
                      ChannelState.DRIVER_UNAVAILABLE,
                      Flag.DRIVER_UNAVAILABLE);
        } else if (lastEvent == DeviceEvent.DISABLED) {
            setStates(DeviceState.DISABLED, ChannelState.DISABLED, Flag.DISABLED);
        } else if (lastEvent == DeviceEvent.DELETED) {
            setDeleted();
        }
        // TODO handle DeviceEvent.DRIVER_REGISTERED?

        eventList.clear();

    }

    private void setConnected(long currentTime) {

        List<ChannelRecordContainerImpl> listeningChannels = null;
        for (ChannelConfigImpl channelConfig : deviceConfig.channelConfigsById.values()) {
            if (channelConfig.state != ChannelState.DISABLED) {
                if (channelConfig.listening == true) {
                    if (listeningChannels == null) {
                        listeningChannels = new LinkedList<ChannelRecordContainerImpl>();
                    }
                    listeningChannels.add(channelConfig.channel.createChannelRecordContainer());
                    channelConfig.state = ChannelState.LISTENING;
                    channelConfig.channel.setFlag(Flag.NO_VALUE_RECEIVED_YET);
                } else if (channelConfig.samplingInterval
                           != ChannelConfig.SAMPLING_INTERVAL_DEFAULT) {
                    if (currentTime == 0) {
                        currentTime = System.currentTimeMillis();
                    }
                    dataManager.addToSamplingCollections(channelConfig.channel, currentTime);
                    channelConfig.state = ChannelState.SAMPLING;
                    channelConfig.channel.setFlag(Flag.NO_VALUE_RECEIVED_YET);
                } else {
                    channelConfig.state = ChannelState.CONNECTED;
                }
            }
        }

        if (listeningChannels != null) {
            taskList.add(new StartListeningTask(dataManager, this, listeningChannels));
            state = DeviceState.STARTING_TO_LISTEN;
        } else {
            state = DeviceState.CONNECTED;
        }

    }

    private void connect() {

        ConnectTask connectTask = new ConnectTask(deviceConfig.driverParent.activeDriver,
                                                  deviceConfig.device,
                                                  dataManager);
        taskList.add(connectTask);
        if (taskList.size() == 1) {
            dataManager.executor.execute(connectTask);
        }
    }

    private void disconnect() {
        DisconnectTask disconnectTask = new DisconnectTask(deviceConfig.driverParent.activeDriver,
                                                           deviceConfig.device,
                                                           dataManager);
        taskList.add(disconnectTask);
        if (taskList.size() == 1) {
            dataManager.executor.execute(disconnectTask);
        }
    }

    // only called by main thread
    public boolean addSamplingTask(SamplingTask samplingTask, int samplingInterval) {
        if (isConnected()) {

            // new
            // if (deviceConfig.readTimeout == 0 || deviceConfig.readTimeout > samplingInterval) {
            // if (taskList.size() > 0) {
            // if (taskList.get(0).getType() == DeviceTaskType.READ) {
            // ((GenReadTask) taskList.get(0)).startedLate = true;
            // }
            // }
            // }
            // new

            taskList.add(samplingTask);
            if (taskList.size() == 1) {
                samplingTask.running = true;
                state = DeviceState.READING;
                dataManager.executor.execute(samplingTask);
            }
            return true;
        } else {
            samplingTask.deviceNotConnected();
            // TODO in the future change this to true
            return true;
        }
    }

    public void addReadTask(ReadTask readTask) {
        if (isConnected()) {
            taskList.add(readTask);
            if (taskList.size() == 1) {
                readTask.running = true;
                state = DeviceState.READING;
                dataManager.executor.execute(readTask);
            }
        } else {
            readTask.deviceNotConnected();
        }
    }

    public void taskFinished() {
        taskList.remove(0);
        if (eventList.size() == 0) {
            executeNextTask();
        } else {
            handleEventQueueWhenConnected();
        }

    }

    private void executeNextTask() {
        if (taskList.size() != 0) {
            if (taskList.get(0).getType() == DeviceTaskType.SAMPLE) {
                ((SamplingTask) taskList.get(0)).startedLate = true;
            }
            taskList.get(0).setDeviceState();
            if (taskList.get(0).device != this) {
                state = DeviceState.CONNECTED;
            }
            dataManager.executor.execute(taskList.get(0));
        } else {
            state = DeviceState.CONNECTED;
        }
    }

    public void removeTask(SamplingTask samplingTask) {
        taskList.remove(samplingTask);
    }

    public void addWriteTask(WriteTask writeTask) {
        if (isConnected()) {
            taskList.add(writeTask);
            if (taskList.size() == 1) {
                state = DeviceState.WRITING;
                dataManager.executor.execute(writeTask);
            }
        } else {
            writeTask.deviceNotConnected();
        }
    }

    public void addStartListeningTask(StartListeningTask startListenTask) {
        if (isConnected()) {
            taskList.add(startListenTask);
            if (taskList.size() == 1) {
                state = DeviceState.WRITING;
                dataManager.executor.execute(startListenTask);
            }
        }
    }

    public boolean isConnected() {
        return state == DeviceState.CONNECTED
               || state == DeviceState.READING
               || state == DeviceState.SCANNING_FOR_CHANNELS
               || state == DeviceState.STARTING_TO_LISTEN
               || state == DeviceState.WRITING;
    }
}
