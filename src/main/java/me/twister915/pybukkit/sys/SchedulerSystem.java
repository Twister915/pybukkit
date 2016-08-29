package me.twister915.pybukkit.sys;

import me.twister915.pybukkit.PyBukkit;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;

import java.util.concurrent.TimeUnit;

public final class SchedulerSystem implements SysType {
    public final Scheduler.Worker async, sync;

    public SchedulerSystem(PyBukkit plugin) {
        async = plugin.getAsyncScheduler().createWorker();
        sync = plugin.getSyncScheduler().createWorker();
    }

    public Subscription repeatAsync(Action0 action, String repeat) {
        return async.schedulePeriodically(action, 0, parse(repeat), TimeUnit.MILLISECONDS);
    }

    public Subscription repeatSync(Action0 action, String repeat) {
        return sync.schedulePeriodically(action, 0, parse(repeat), TimeUnit.MILLISECONDS);
    }

    public Subscription scheduleSync(Action0 action, String timeDelay) {
        return sync.schedule(action, parse(timeDelay), TimeUnit.MILLISECONDS);
    }

    public Subscription scheduleAsync(Action0 action, String timeDelay) {
        return async.schedule(action, parse(timeDelay), TimeUnit.MILLISECONDS);
    }

    private static long parse(String timeDelay) {
        String[] split = timeDelay.split(" ");
        if (split.length != 2)
            throw new IllegalArgumentException("You must specify time in this format: '2 seconds' '5 hours' '1 tick'");

        long l = Long.parseLong(split[0]);

        String unit = split[1].trim().toUpperCase();
        if (!unit.endsWith("S"))
            unit += "S";

        if (unit.equals("TICKS"))
            return l * 50;

        return TimeUnit.valueOf(unit).toMillis(l);
    }

    @Override
    public void disable() {
        async.unsubscribe();
        sync.unsubscribe();
    }
}
