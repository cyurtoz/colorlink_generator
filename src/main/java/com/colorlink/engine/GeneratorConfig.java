package com.colorlink.engine;

/**
 * Immutable configuration for the Colorlink puzzle generator.
 * Use Builder to construct instances.
 */
public class GeneratorConfig {

    public final int    rows;
    public final int    cols;
    public final int    colorCount;
    public final long   randomSeed;
    public final int    maxAttempts;
    public final int    minPathLength;
    public final double maxShortPathRatio;  // fraction of colors allowed to have short paths
    public final int    minEndpointDistance;
    public final boolean validationEnabled;

    private GeneratorConfig(Builder b) {
        rows               = b.rows;
        cols               = b.cols;
        colorCount         = b.colorCount;
        randomSeed         = b.randomSeed;
        maxAttempts        = b.maxAttempts;
        minPathLength      = b.minPathLength;
        maxShortPathRatio  = b.maxShortPathRatio;
        minEndpointDistance = b.minEndpointDistance;
        validationEnabled  = b.validationEnabled;
    }

    /** Convenience factory using spec-recommended defaults for a given grid size. */
    public static GeneratorConfig forSize(int rows, int cols, int colorCount) {
        return new Builder(rows, cols, colorCount).build();
    }

    // -----------------------------------------------------------------------

    public static class Builder {
        int    rows, cols, colorCount;
        long   randomSeed         = System.currentTimeMillis();
        int    maxAttempts        = 800;
        int    minPathLength;
        double maxShortPathRatio  = 0.25;
        int    minEndpointDistance = 2;
        boolean validationEnabled = true;

        public Builder(int rows, int cols, int colorCount) {
            this.rows       = rows;
            this.cols       = cols;
            this.colorCount = colorCount;
            // Spec: 5x5-6x6 → min 3, 7x7-10x10 → min 4
            this.minPathLength = (rows <= 6) ? 3 : 4;
        }

        public Builder seed(long s)           { randomSeed         = s;    return this; }
        public Builder attempts(int n)        { maxAttempts        = n;    return this; }
        public Builder minPathLen(int n)      { minPathLength      = n;    return this; }
        public Builder shortRatio(double d)   { maxShortPathRatio  = d;    return this; }
        public Builder minEpDist(int n)       { minEndpointDistance = n;   return this; }
        public Builder validate(boolean v)    { validationEnabled  = v;    return this; }

        public GeneratorConfig build() { return new GeneratorConfig(this); }
    }

    @Override
    public String toString() {
        return String.format("Config[%dx%d, %d colors, seed=%d, maxAttempts=%d, minLen=%d]",
            rows, cols, colorCount, randomSeed, maxAttempts, minPathLength);
    }
}
