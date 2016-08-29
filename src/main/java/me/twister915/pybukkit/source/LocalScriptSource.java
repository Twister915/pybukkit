package me.twister915.pybukkit.source;

import me.twister915.pybukkit.PyBukkit;
import me.twister915.pybukkit.script.ScriptPath;
import rx.Scheduler;
import rx.Single;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import static java.nio.file.StandardWatchEventKinds.*;

public final class LocalScriptSource implements ScriptSource {
    private final File rootDir;
    private final Scheduler.Worker async, sync;

    public LocalScriptSource(PyBukkit plugin) throws IOException {
        this.async = plugin.getAsyncScheduler().createWorker();
        this.sync = plugin.getSyncScheduler().createWorker();

        String string = plugin.getConfig().getString("root-path");
        if (string == null) rootDir = new File(plugin.getDataFolder(), "scripts");
        else if (string.startsWith("/")) rootDir = new File(string);
        else rootDir = new File(plugin.getDataFolder(), string);

        if (!rootDir.exists() && !rootDir.mkdirs())
            throw new IOException("Could not write directory " + rootDir);
    }

    private File toFile(ScriptPath path) {
        return new File(rootDir, path.join('/') + ".py");
    }

    @Override
    public RunnableScript loadScript(ScriptPath path) throws IOException {
        return new RunnableScript(new FileInputStream(toFile(path)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Single<Void> watchUpdates(ScriptPath path) {
        Path filesystemPath = toFile(path).toPath();
        return Single.create(sub -> {
            async.schedule(() -> {
                try {
                    WatchService watchService = FileSystems.getDefault().newWatchService();
                    filesystemPath.getParent().register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                    while (true) {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> watchEvent : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = watchEvent.kind();
                            Path context = ((WatchEvent<Path>) watchEvent).context();
                            if (!context.equals(filesystemPath.getFileName()))
                                continue;

                            if (kind == ENTRY_MODIFY || kind == ENTRY_DELETE)
                                sync.schedule(() -> sub.onSuccess(null));

                            watchService.close();
                            return;
                        }
                        boolean valid = key.reset();
                        if (!valid)
                            throw new IllegalStateException("The watcher is no longer valid for " + filesystemPath + "!");
                    }
                } catch (Exception e) {
                    sub.onError(e);
                }
            });
        });
    }

    @Override
    public boolean exists(ScriptPath path) throws IOException {
        return toFile(path).exists();
    }
}
