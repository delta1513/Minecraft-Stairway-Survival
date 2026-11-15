package io.github.delta1513.stairway.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Material;

/**
 * Region-wide find-and-replace (PLAN.md §10 implementation hint), in three stages:
 * <ol>
 *   <li>on the first {@link #process} call (main thread) snapshot every chunk the region touches;</li>
 *   <li>scan the snapshots on {@code scanExecutor} (off the main thread) collecting matches;</li>
 *   <li>apply the replacements on the main thread, one write per match, within the tick budget.</li>
 * </ol>
 * Only writes are charged against the budget; reads happen on the snapshots off-thread. While the
 * scan is pending {@link #process} returns 0 so the queue keeps the job at its head.
 */
public final class ScanReplaceJob implements BlockJob {

    private final Region region;
    private final SnapshotProvider snapshots;
    private final Executor scanExecutor;
    private final BlockMatcher matcher;
    private final BlockEditor editor;
    private final Material replacement;
    private final PositionConsumer onReplaced;
    private final BlockMatcher reported;

    private CompletableFuture<PackedPositions> scan;
    private PackedPositions matches;
    private int applied;
    private boolean cancelled;

    /**
     * @param onReplaced called on the main thread after each block is replaced; may be null
     */
    public ScanReplaceJob(Region region, SnapshotProvider snapshots, Executor scanExecutor, BlockMatcher matcher,
            BlockEditor editor, Material replacement, PositionConsumer onReplaced) {
        this(region, snapshots, scanExecutor, matcher, editor, replacement, onReplaced, null);
    }

    /**
     * @param onReplaced called on the main thread after a block is replaced; may be null
     * @param reported   evaluated off-thread on each match (against the snapshot) to decide whether
     *                   {@code onReplaced} is called for it; null reports every match. The block is
     *                   replaced either way.
     */
    public ScanReplaceJob(Region region, SnapshotProvider snapshots, Executor scanExecutor, BlockMatcher matcher,
            BlockEditor editor, Material replacement, PositionConsumer onReplaced, BlockMatcher reported) {
        this.reported = reported;
        this.region = region;
        this.snapshots = snapshots;
        this.scanExecutor = scanExecutor;
        this.matcher = matcher;
        this.editor = editor;
        this.replacement = replacement;
        this.onReplaced = onReplaced;
    }

    @Override
    public int process(int budget) {
        if (region.isEmpty() || cancelled) {
            matches = PackedPositions.EMPTY;
            return 0;
        }
        if (scan == null) {
            startScan();
        }
        if (matches == null) {
            if (!scan.isDone()) {
                return 0;
            }
            matches = scan.join(); // throws CompletionException when the scan failed
        }
        int done = 0;
        while (done < budget && applied < matches.size()) {
            int x = matches.x(applied);
            int y = matches.y(applied);
            int z = matches.z(applied);
            boolean report = matches.reported(applied);
            applied++;
            editor.set(x, y, z, replacement);
            if (onReplaced != null && report) {
                onReplaced.accept(x, y, z);
            }
            done++;
        }
        return done;
    }

    private void startScan() {
        List<Capture> captured = new ArrayList<>();
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                captured.add(new Capture(cx, cz, snapshots.capture(cx, cz)));
            }
        }
        scan = new CompletableFuture<>();
        try {
            scanExecutor.execute(() -> {
                try {
                    scan.complete(scanAll(captured));
                } catch (Throwable t) {
                    scan.completeExceptionally(t);
                }
            });
        } catch (RuntimeException e) {
            scan.completeExceptionally(e);
        }
    }

    private PackedPositions scanAll(List<Capture> captured) {
        PackedPositions.Builder out = new PackedPositions.Builder();
        for (Capture c : captured) {
            int x0 = Math.max(region.minX(), c.cx << 4);
            int x1 = Math.min(region.maxX(), (c.cx << 4) + 15);
            int z0 = Math.max(region.minZ(), c.cz << 4);
            int z1 = Math.min(region.maxZ(), (c.cz << 4) + 15);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = region.minY(); y <= region.maxY(); y++) {
                        Material type = c.snapshot.typeAt(x, y, z);
                        if (matcher.matches(type, c.snapshot, x, y, z)) {
                            out.add(x, y, z, reported == null || reported.matches(type, c.snapshot, x, y, z));
                        }
                    }
                }
            }
        }
        return out.build();
    }

    @Override
    public boolean isDone() {
        return matches != null && applied >= matches.size();
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    private record Capture(int cx, int cz, BlockSnapshot snapshot) {}

    /** Compact growable list of block positions packed into longs (26 bits x, 26 bits z, 11 bits y, 1 report flag). */
    static final class PackedPositions {
        static final PackedPositions EMPTY = new PackedPositions(new long[0], 0);

        private static final int XZ_BITS = 26;
        private static final int Y_BITS = 11;
        private static final long XZ_MASK = (1L << XZ_BITS) - 1;
        private static final long Y_MASK = (1L << Y_BITS) - 1;
        private static final int Y_OFFSET = 1 << (Y_BITS - 1); // supports -1024..1023

        private final long[] data;
        private final int size;

        private PackedPositions(long[] data, int size) {
            this.data = data;
            this.size = size;
        }

        int size() {
            return size;
        }

        int x(int i) {
            return (int) (data[i] << (64 - XZ_BITS) >> (64 - XZ_BITS));
        }

        int z(int i) {
            return (int) ((data[i] >> XZ_BITS) << (64 - XZ_BITS) >> (64 - XZ_BITS));
        }

        boolean reported(int i) {
            return data[i] < 0;
        }

        int y(int i) {
            return (int) ((data[i] >>> (2 * XZ_BITS)) & Y_MASK) - Y_OFFSET;
        }

        static final class Builder {
            private long[] data = new long[1024];
            private int size;

            void add(int x, int y, int z, boolean reported) {
                if (size == data.length) {
                    data = Arrays.copyOf(data, size * 2);
                }
                data[size++] = (x & XZ_MASK) | ((z & XZ_MASK) << XZ_BITS) | (((long) (y + Y_OFFSET) & Y_MASK) << (2 * XZ_BITS))
                        | (reported ? Long.MIN_VALUE : 0L);
            }

            PackedPositions build() {
                return new PackedPositions(data, size);
            }
        }
    }
}
