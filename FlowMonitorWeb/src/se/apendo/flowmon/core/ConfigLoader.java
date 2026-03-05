package se.apendo.flowmon.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import se.apendo.flowmon.core.Models.FlowConfig;

/**
 * Laddar flödeskonfiguration från JSON utan externa bibliotek.
 *
 * Förväntat format:
 * {
 *   "flows": [
 *     {
 *       "id":"F1",
 *       "sender":"A",
 *       "receiver":"B",
 *       "msgType":"X",
 *       "expectations": { ... }
 *     }
 *   ]
 * }
 */
public final class ConfigLoader {

    private final List<FlowConfig> flows = new ArrayList<FlowConfig>();

    public List<FlowConfig> getFlows() { return flows; }

    /** Läs från classpath, t.ex. "/flows.json" (lägg filen i WEB-INF/classes) */
    public void loadFromClasspath(String resourcePath) throws Exception {
        InputStream in = null;
        try {
            in = ConfigLoader.class.getResourceAsStream(resourcePath);
            if (in == null) throw new IllegalArgumentException("Config resource not found: " + resourcePath);

            String json = slurpUtf8(in);
            loadFromString(json);

        } finally {
            try { if (in != null) in.close(); } catch (Exception ignore) {}
        }
    }

    public void loadFromString(String json) throws Exception {
        flows.clear();

        Object rootObj = SimpleJson.parse(json);
        Map<String, Object> root = SimpleJson.asObject(rootObj);

        List<Object> arr = SimpleJson.asArray(root.get("flows"));
        for (int i = 0; i < arr.size(); i++) {
            Map<String, Object> fo = SimpleJson.asObject(arr.get(i));

            FlowConfig fc = new FlowConfig();
            fc.id = SimpleJson.asString(fo.get("id"), "");
            fc.sender = SimpleJson.asString(fo.get("sender"), "");
            fc.receiver = SimpleJson.asString(fo.get("receiver"), "");
            fc.msgType = SimpleJson.asString(fo.get("msgType"), "");

            Map<String, Object> exp = SimpleJson.asObject(fo.get("expectations"));
            fc.expectations = (exp != null) ? exp : new HashMap<String, Object>();

            if (fc.id != null && fc.id.trim().length() > 0) flows.add(fc);
        }
    }

    private static String slurpUtf8(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }
}

