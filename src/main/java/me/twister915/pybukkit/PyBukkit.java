package me.twister915.pybukkit;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import lombok.AccessLevel;
import lombok.Getter;
import me.twister915.pybukkit.cmd.PyBukkitCommand;
import me.twister915.pybukkit.script.ScriptManager;
import me.twister915.pybukkit.source.LocalScriptSource;
import org.bukkit.configuration.file.FileConfiguration;
import tech.rayline.core.GsonBridge;
import tech.rayline.core.inject.Inject;
import tech.rayline.core.library.IgnoreLibraries;
import tech.rayline.core.parse.ReadOnlyResource;
import tech.rayline.core.parse.ResourceFile;
import tech.rayline.core.plugin.RedemptivePlugin;
import tech.rayline.core.plugin.YAMLConfigurationFile;

import java.util.Collections;

@Getter
@IgnoreLibraries
public final class PyBukkit extends RedemptivePlugin {
    @ResourceFile(filename = "mongo.yml", raw = true) @ReadOnlyResource @Getter(AccessLevel.NONE)
    private YAMLConfigurationFile databaseConfig;

    private MongoClient mongoClient;
    private MongoDatabase mongoDB;
    private ScriptManager scriptManager;
    @Inject private GsonBridge gsonBridge;

    private MongoDatabase createDatabase() {
        FileConfiguration config = databaseConfig.getConfig();
        String host = config.getString("host", "localhost"), database = config.getString("database", "pybukkit"),
                username = config.getString("username"), password = config.getString("password");
        int port = config.getInt("port", 27017);

        if (username != null && password != null) {
            mongoClient = new MongoClient(new ServerAddress(host, port),
                    Collections.singletonList(MongoCredential.createCredential(username, database, password.toCharArray()))
            );
        } else {
            mongoClient = new MongoClient(host, port);
        }

        return mongoClient.getDatabase(database);
    }

    @Override
    protected void onModuleEnable() throws Exception {
        mongoDB = createDatabase();
        registerCommand(new PyBukkitCommand());
        PyBukkitLoadEvent pyBukkitLoadEvent = new PyBukkitLoadEvent(this);
        getServer().getPluginManager().callEvent(pyBukkitLoadEvent);
        scriptManager = new ScriptManager(pyBukkitLoadEvent);
        scriptManager.invokeIndex();
    }

    @Override
    protected void onModuleDisable() throws Exception {
        scriptManager.onDisable();
        mongoClient.close();
    }
}
