/*
 * Copyright 2016 - 2022 Anton Tananaev (anton@traccar.org)
 * Copyright 2016 Andrey Kunitsyn (andrey@traccar.org)
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
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.model.Position;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.DeviceReportSection;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RouteReportProvider {

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;

    private final Map<String, Integer> namesCount = new HashMap<>();

    @Inject
    public RouteReportProvider(Config config, ReportUtils reportUtils, Storage storage) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    public Collection<Position> getObjects(DeviceGroupQuery deviceGroupQuery) throws StorageException {
        reportUtils.checkPeriodLimit(deviceGroupQuery.getFrom(), deviceGroupQuery.getTo());

        ArrayList<Position> result = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, deviceGroupQuery.getUserId(), deviceGroupQuery.getDeviceIds(), deviceGroupQuery.getGroupIds())) {
            result.addAll(PositionUtil.getPositions(storage, device.getId(), deviceGroupQuery.getFrom(), deviceGroupQuery.getTo()));
        }
        return result;
    }


    private String getUniqueSheetName(String key) {
        namesCount.compute(key, (k, value) -> value == null ? 1 : (value + 1));
        return namesCount.get(key) > 1 ? key + '-' + namesCount.get(key) : key;
    }

    public void getExcel(OutputStream outputStream,
                         DeviceGroupQuery deviceGroupQuery) throws StorageException, IOException {
        reportUtils.checkPeriodLimit(deviceGroupQuery.getFrom(), deviceGroupQuery.getTo());

        ArrayList<DeviceReportSection> devicesRoutes = new ArrayList<>();
        ArrayList<String> sheetNames = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, deviceGroupQuery.getUserId(), deviceGroupQuery.getDeviceIds(), deviceGroupQuery.getGroupIds())) {
            var positions = PositionUtil.getPositions(storage, device.getId(), deviceGroupQuery.getFrom(), deviceGroupQuery.getTo());
            DeviceReportSection deviceRoutes = new DeviceReportSection();
            deviceRoutes.setDeviceName(device.getName());
            sheetNames.add(WorkbookUtil.createSafeSheetName(getUniqueSheetName(deviceRoutes.getDeviceName())));
            if (device.getGroupId() > 0) {
                Group group = storage.getObject(Group.class, new Request(
                        new Columns.All(), new Condition.Equals("id", device.getGroupId())));
                if (group != null) {
                    deviceRoutes.setGroupName(group.getName());
                }
            }
            deviceRoutes.setObjects(positions);
            devicesRoutes.add(deviceRoutes);
        }

        File file = Paths.get(config.getString(Keys.TEMPLATES_ROOT), "export", "route.xlsx").toFile();
        try (InputStream inputStream = new FileInputStream(file)) {
            var context = reportUtils.initializeContext(deviceGroupQuery.getUserId());
            context.putVar("devices", devicesRoutes);
            context.putVar("sheetNames", sheetNames);
            context.putVar("from", deviceGroupQuery.getFrom());
            context.putVar("to", deviceGroupQuery.getTo());
            reportUtils.processTemplateWithSheets(inputStream, outputStream, context);
        }
    }
}
