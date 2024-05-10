/*
 * Copyright 2016 - 2022 Anton Tananaev (anton@traccar.org)
 * Copyright 2016 - 2018 Andrey Kunitsyn (andrey@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.reports;

import jakarta.inject.Inject;
import org.apache.poi.ss.util.WorkbookUtil;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.model.*;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.DeviceReportSection;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

public class EventsReportProvider {

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;

    @Inject
    public EventsReportProvider(Config config, ReportUtils reportUtils, Storage storage) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    private List<Event> getEvents(long deviceId, Date from, Date to) throws StorageException {
        return storage.getObjects(Event.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("deviceId", deviceId),
                        new Condition.Between("eventTime", "from", from, "to", to)),
                new Order("eventTime")));
    }

    public Collection<Event> getObjects(
            Collection<String> types, DeviceGroupQuery deviceGroupQuery) throws StorageException {
        reportUtils.checkPeriodLimit(deviceGroupQuery.getFrom(), deviceGroupQuery.getTo());

        ArrayList<Event> result = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, deviceGroupQuery.getUserId(), deviceGroupQuery.getDeviceIds(), deviceGroupQuery.getGroupIds())) {
            Collection<Event> events = getEvents(device.getId(), deviceGroupQuery.getFrom(), deviceGroupQuery.getTo());
            boolean all = types.isEmpty() || types.contains(Event.ALL_EVENTS);
            for (Event event : events) {
                if (all || types.contains(event.getType())) {
                    long geofenceId = event.getGeofenceId();
                    long maintenanceId = event.getMaintenanceId();
                    if ((geofenceId == 0 || reportUtils.getObject(deviceGroupQuery.getUserId(), Geofence.class, geofenceId) != null)
                            && (maintenanceId == 0
                            || reportUtils.getObject(deviceGroupQuery.getUserId(), Maintenance.class, maintenanceId) != null)) {
                        result.add(event);
                    }
                }
            }
        }
        return result;
    }

    public void getExcel(
            OutputStream outputStream,
            Collection<String> types, DeviceGroupQuery deviceGroupQuery) throws StorageException, IOException {
        reportUtils.checkPeriodLimit(deviceGroupQuery.getFrom(), deviceGroupQuery.getTo());

        ArrayList<DeviceReportSection> devicesEvents = new ArrayList<>();
        ArrayList<String> sheetNames = new ArrayList<>();
        HashMap<Long, String> geofenceNames = new HashMap<>();
        HashMap<Long, String> maintenanceNames = new HashMap<>();
        HashMap<Long, Position> positions = new HashMap<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, deviceGroupQuery.getUserId(), deviceGroupQuery.getDeviceIds(), deviceGroupQuery.getGroupIds())) {
            Collection<Event> events = getEvents(device.getId(), deviceGroupQuery.getFrom(), deviceGroupQuery.getTo());
            boolean all = types.isEmpty() || types.contains(Event.ALL_EVENTS);
            for (Iterator<Event> iterator = events.iterator(); iterator.hasNext(); ) {
                Event event = iterator.next();
                if (all || types.contains(event.getType())) {
                    long geofenceId = event.getGeofenceId();
                    long maintenanceId = event.getMaintenanceId();
                    if (geofenceId != 0) {
                        Geofence geofence = reportUtils.getObject(deviceGroupQuery.getUserId(), Geofence.class, geofenceId);
                        if (geofence != null) {
                            geofenceNames.put(geofenceId, geofence.getName());
                        } else {
                            iterator.remove();
                        }
                    } else if (maintenanceId != 0) {
                        Maintenance maintenance = reportUtils.getObject(deviceGroupQuery.getUserId(), Maintenance.class, maintenanceId);
                        if (maintenance != null) {
                            maintenanceNames.put(maintenanceId, maintenance.getName());
                        } else {
                            iterator.remove();
                        }
                    }
                } else {
                    iterator.remove();
                }
            }
            for (Event event : events) {
                long positionId = event.getPositionId();
                if (positionId > 0) {
                    Position position = storage.getObject(Position.class, new Request(
                            new Columns.All(), new Condition.Equals("id", positionId)));
                    positions.put(positionId, position);
                }
            }
            DeviceReportSection deviceEvents = new DeviceReportSection();
            deviceEvents.setDeviceName(device.getName());
            sheetNames.add(WorkbookUtil.createSafeSheetName(deviceEvents.getDeviceName()));
            if (device.getGroupId() > 0) {
                Group group = storage.getObject(Group.class, new Request(
                        new Columns.All(), new Condition.Equals("id", device.getGroupId())));
                if (group != null) {
                    deviceEvents.setGroupName(group.getName());
                }
            }
            deviceEvents.setObjects(events);
            devicesEvents.add(deviceEvents);
        }

        File file = Paths.get(config.getString(Keys.TEMPLATES_ROOT), "export", "events.xlsx").toFile();
        try (InputStream inputStream = new FileInputStream(file)) {
            var context = reportUtils.initializeContext(deviceGroupQuery.getUserId());
            context.putVar("devices", devicesEvents);
            context.putVar("sheetNames", sheetNames);
            context.putVar("geofenceNames", geofenceNames);
            context.putVar("maintenanceNames", maintenanceNames);
            context.putVar("positions", positions);
            context.putVar("from", deviceGroupQuery.getFrom());
            context.putVar("to", deviceGroupQuery.getTo());
            reportUtils.processTemplateWithSheets(inputStream, outputStream, context);
        }
    }
}
