package me.twister915.pybukkit.sys;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyException;
import org.python.core.PyObject;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public final class DataSystem extends PyObject implements SysType {
    private final Map<String, DataIndex> dataMap = new HashMap<>();
    private final Gson gson;
    private final JsonParser parser = new JsonParser();

    public DataSystem(Gson gson) {
        this.gson = gson;
    }

    private DataIndex getData(String name) throws PyException {
        File saveLocation;
        if (name.equals("global")) {
            saveLocation = new File(Bukkit.getWorldContainer(), "py_data");
            if (!saveLocation.exists() && !saveLocation.mkdirs())
                throw Py.IOError("could not create directory to save global data!");
        }
        else {
            World world = Bukkit.getWorld(name);
            if (world == null)
                throw Py.AttributeError("No such world " + name + " (hint: use 'global' to save data for entire server, or look at mongo for saving different sorts of data)!");
            saveLocation = world.getWorldFolder();
        }

        String s = name.toLowerCase();
        DataIndex dataIndex = dataMap.get(s);
        if (dataIndex == null) {
            dataIndex = new DataIndex(saveLocation);
            dataMap.put(s, dataIndex);
        }
        return dataIndex;
    }

    @Override
    public PyObject __getitem__(PyObject key) {
        return getData(key.asString());
    }

    @Override
    public PyObject __findattr_ex__(String name) {
        return getData(name);
    }

    private PyObject getfor(File saveLocation, String name) throws IOException {
        File file = new File(saveLocation, (name.trim() + ".json"));
        if (!file.exists())
            return new PyDictionary();

        try (Reader reader = new FileReader(file)) {
            JsonReader jsonReader = new JsonReader(reader);
            JsonElement raw = parser.parse(jsonReader);
            if (raw.isJsonNull())
                return new PyDictionary();

            JsonObject parse = raw.getAsJsonObject();

            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<String, JsonElement> stringJsonElementEntry : parse.entrySet()) {
                DataEntry dataEntry = gson.fromJson(stringJsonElementEntry.getValue(), DataEntry.class);
                try {
                    data.put(stringJsonElementEntry.getKey(), gson.fromJson(dataEntry.instance, Class.forName(dataEntry.type)));
                } catch (ClassNotFoundException ignored) {}
            }

            return Py.java2py(data);
        }
    }

    public final class DataIndex extends PyObject {
        private final Map<String, PyObject> data = new HashMap<>();
        private final File saveLocation;

        public DataIndex(File saveLocation) {
            this.saveLocation = saveLocation;
        }

        @Override
        public PyObject __findattr_ex__(String name) {
            try {
                PyObject pyObject = super.__findattr_ex__(name);
                if (pyObject != null)
                    return pyObject;
            } catch (Throwable t) {}
            PyObject obj = data.get(name);
            if (obj == null)
                try {
                    obj = getfor(saveLocation, name);
                    data.put(name, obj); //le cache
                } catch (IOException e) {
                    throw Py.IOError(e);
                }
            return obj;
        }

        @SuppressWarnings("unchecked")
        public void save() {
            for (Map.Entry<String, PyObject> entry : data.entrySet()) {
                Map<String, Object> map = (Map<String, Object>) entry.getValue().__tojava__(Map.class);
                Map<String, DataEntry> dataEntryMap = new HashMap<>();
                for (Map.Entry<String, Object> stringObjectEntry : map.entrySet())
                    dataEntryMap.put(stringObjectEntry.getKey(), new DataEntry(stringObjectEntry.getValue()));
                File file = new File(saveLocation, entry.getKey() + ".json");
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(gson.toJson(dataEntryMap));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final class DataEntry {
        private String type;
        private JsonElement instance;

        public DataEntry(Object value) {
            type = value.getClass().getName();
            instance = gson.toJsonTree(value);
        }

        public DataEntry() {}
    }

    @Override
    public void disable() {
        dataMap.values().forEach(DataIndex::save);
    }
}
