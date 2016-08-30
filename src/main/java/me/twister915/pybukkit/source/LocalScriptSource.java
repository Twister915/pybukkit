package me.twister915.pybukkit.source;

import lombok.Getter;
import me.twister915.pybukkit.PyBukkit;
import rx.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

public final class LocalScriptSource implements ScriptSource {
    @Getter private final File root;
    private final Scheduler.Worker async;

    public LocalScriptSource(PyBukkit plugin) throws IOException {
        this.async = plugin.getAsyncScheduler().createWorker();

        String string = plugin.getConfig().getString("root-path");
        if (string == null) root = new File(plugin.getDataFolder(), "scripts");
        else if (string.startsWith("/")) root = new File(string);
        else root = new File(plugin.getDataFolder(), string);

        if (!root.exists() && !root.mkdirs())
            throw new IOException("Could not write directory " + root);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Observable<Void> watchUpdate() {
        return Observable.create(sub -> {
            async.schedule(() -> {
                try {
                    new WatchDirectory().poll(sub);
                } catch (IOException e) {
                    sub.onError(e);
                }
            });
        });
    }

    private class WatchDirectory {
        private final WatchService watcher;
        private final Map<WatchKey,Path> keys = new HashMap<>();

        private WatchDirectory() throws IOException {
            watcher = FileSystems.getDefault().newWatchService();
            registerAll(getRoot().toPath());
        }

        private void registerAll(Path path) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    keys.put(dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        @SuppressWarnings("unchecked")
        public void poll(Subscriber<? super Void> subscriber) throws IOException {
            try {
                while (!subscriber.isUnsubscribed()) {
                    try {
                        WatchKey key = watcher.take();
                        for (WatchEvent<?> watchEvent : key.pollEvents()) {
                            if (subscriber.isUnsubscribed())
                                return;

                            WatchEvent.Kind<?> kind = watchEvent.kind();
                            if (kind == ENTRY_MODIFY || kind == ENTRY_DELETE || kind == ENTRY_CREATE) {
                                subscriber.onNext(null);
                            }
                        }
                        boolean valid = key.reset();
                        if (!valid) {
                            keys.remove(key);
                            if (keys.isEmpty())
                                break;
                        }
                    } catch (Exception e) {
                        subscriber.onError(e);
                        return;
                    }
                }
            } finally {
                watcher.close();
            }
        }
    }
}
