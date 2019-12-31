package org.jahia.modules.healcheckmodulesstate.probes;

import org.jahia.modules.healthcheck.interfaces.Probe;
import org.jahia.osgi.BundleUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import org.jahia.osgi.BundleState;
import org.jahia.osgi.FrameworkService;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.modulemanager.BundleInfo;
import org.jahia.services.modulemanager.Constants;
import org.jahia.services.modulemanager.spi.BundleService;
import org.jahia.services.modulemanager.spi.BundleService.BundleInformation;
import org.jahia.settings.SettingsBean;
import org.osgi.framework.Bundle;
import org.springframework.beans.factory.annotation.Autowired;

@Component(service = Probe.class, immediate = true)
public class ModulesStateProbe implements Probe {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModulesStateProbe.class);
    private static final String CLUSTERED_SERVICE_FILTER = "(" + Constants.BUNDLE_SERVICE_PROPERTY_CLUSTERED + "=true)";
    private JSONArray data;

    @Autowired
    BundleService bundleService;

    @Override
    public String getStatus() {
        data = new JSONArray();
        final boolean status = doCheck();

        if (status) {
            return "GREEN";
        } else {
            return "RED";
        }
    }

    @Override
    public JSONObject getData() {
        if (data.length() == 0) {
            return null;
        }
        final JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("invalid_modules", data);
        } catch (JSONException ex) {
            LOGGER.error("Impossible to put the data in the JSONObject", ex);
        }

        return jsonObject;
    }

    @Override
    public String getName() {
        return "ModuleStateProbe";
    }

    private boolean doCheck() {
        final BundleService bundleService;
        final boolean clusterActivated = SettingsBean.getInstance().isClusterActivated();
        if (clusterActivated) {
            bundleService = BundleUtils.getOsgiService(BundleService.class, CLUSTERED_SERVICE_FILTER);
        } else {
            bundleService = (BundleService) SpringContextSingleton.getBean("org.jahia.services.modulemanager.spi.impl.DefaultBundleService");
        }

        final JSONArray jsonArray = new JSONArray();
        boolean status = true;
        for (Bundle bundle : FrameworkService.getBundleContext().getBundles()) {
            if (!BundleUtils.isFragment(bundle)) {
                final Map<String, BundleService.BundleInformation> infos = bundleService.getInfo(BundleInfo.fromBundle(bundle), null);
                for (Map.Entry<String, BundleInformation> entry : infos.entrySet()) {
                    final BundleInformation info = entry.getValue();
                    if (info.getOsgiState() != BundleState.ACTIVE) {
                        final String node = entry.getKey();
                        final String name = bundle.getSymbolicName();
                        status = false;
                        jsonArray.put(node + ":" + name);
                    }
                }
            }
        }
        data.put(jsonArray);
        return status;
    }
}
