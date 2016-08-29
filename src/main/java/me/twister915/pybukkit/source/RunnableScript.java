package me.twister915.pybukkit.source;

import lombok.Data;

import java.io.InputStream;

@Data public final class RunnableScript {
    private final InputStream script;
}
