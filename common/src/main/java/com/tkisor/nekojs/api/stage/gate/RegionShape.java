package com.tkisor.nekojs.api.stage.gate;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.NekoId;

/**
 * Immutable spatial region in a target dimension, modeled as either an axis-aligned bounding box
 * or a sphere.
 *
 * @param dimension target dimension identifier
 * @param mode      spatial geometry mode ({@link Mode#BOX} or {@link Mode#SPHERE})
 * @param minX      minimum X coordinate (normalized with {@link Math#min(double, double)})
 * @param minY      minimum Y coordinate (normalized with {@link Math#min(double, double)})
 * @param minZ      minimum Z coordinate (normalized with {@link Math#min(double, double)})
 * @param maxX      maximum X coordinate (normalized with {@link Math#max(double, double)})
 * @param maxY      maximum Y coordinate (normalized with {@link Math#max(double, double)})
 * @param maxZ      maximum Z coordinate (normalized with {@link Math#max(double, double)})
 * @param centerX   sphere or box center X coordinate
 * @param centerY   sphere or box center Y coordinate
 * @param centerZ   sphere or box center Z coordinate
 * @param radius    sphere radius (strictly positive for {@link Mode#SPHERE}; {@code 0.0} for {@link Mode#BOX})
 */
@Doc("Immutable spatial region in a target dimension, modeled as either an axis-aligned box or a sphere.")
public record RegionShape(
        NekoId dimension,
        Mode mode,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double centerX,
        double centerY,
        double centerZ,
        double radius
) {
    /**
     * Spatial geometry mode for a {@link RegionShape}.
     */
    @Doc("Spatial geometry mode for a RegionShape.")
    public enum Mode {
        BOX,
        SPHERE
    }

    public RegionShape {
        if (dimension == null) {
            throw new IllegalArgumentException("[NEKO-4071] Region shape dimension cannot be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("[NEKO-4071] Region shape mode cannot be null");
        }
        if (mode == Mode.BOX) {
            requireFinite(minX, "minX");
            requireFinite(minY, "minY");
            requireFinite(minZ, "minZ");
            requireFinite(maxX, "maxX");
            requireFinite(maxY, "maxY");
            requireFinite(maxZ, "maxZ");
            double loX = Math.min(minX, maxX);
            double hiX = Math.max(minX, maxX);
            double loY = Math.min(minY, maxY);
            double hiY = Math.max(minY, maxY);
            double loZ = Math.min(minZ, maxZ);
            double hiZ = Math.max(minZ, maxZ);
            minX = loX;
            maxX = hiX;
            minY = loY;
            maxY = hiY;
            minZ = loZ;
            maxZ = hiZ;
            centerX = (loX + hiX) * 0.5;
            centerY = (loY + hiY) * 0.5;
            centerZ = (loZ + hiZ) * 0.5;
            radius = 0.0;
        } else {
            requireFinite(centerX, "centerX");
            requireFinite(centerY, "centerY");
            requireFinite(centerZ, "centerZ");
            requireFinite(radius, "radius");
            if (radius <= 0.0) {
                throw new IllegalArgumentException(
                        "[NEKO-4071] Sphere region radius must be > 0, got: " + radius);
            }
            minX = centerX - radius;
            maxX = centerX + radius;
            minY = centerY - radius;
            maxY = centerY + radius;
            minZ = centerZ - radius;
            maxZ = centerZ + radius;
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Region shape coordinate '" + name + "' must be finite, got: " + value);
        }
    }

    /**
     * Creates an axis-aligned box region with normalized minimum and maximum coordinates.
     */
    @Doc("Creates an axis-aligned box region with normalized minimum and maximum coordinates.")
    @Param(name = "dimension", value = "Target dimension id.")
    @Param(name = "x1", value = "First corner X coordinate.")
    @Param(name = "y1", value = "First corner Y coordinate.")
    @Param(name = "z1", value = "First corner Z coordinate.")
    @Param(name = "x2", value = "Opposite corner X coordinate.")
    @Param(name = "y2", value = "Opposite corner Y coordinate.")
    @Param(name = "z2", value = "Opposite corner Z coordinate.")
    @Return("Normalized box RegionShape.")
    public static RegionShape box(
            NekoId dimension,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2
    ) {
        return new RegionShape(
                dimension,
                Mode.BOX,
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2),
                0.0,
                0.0,
                0.0,
                0.0
        );
    }

    /**
     * Creates a spherical region centered at {@code (centerX, centerY, centerZ)} with positive {@code radius}.
     */
    @Doc("Creates a spherical region with the given center coordinates and positive radius.")
    @Param(name = "dimension", value = "Target dimension id.")
    @Param(name = "centerX", value = "Sphere center X coordinate.")
    @Param(name = "centerY", value = "Sphere center Y coordinate.")
    @Param(name = "centerZ", value = "Sphere center Z coordinate.")
    @Param(name = "radius", value = "Sphere radius (must be > 0).")
    @Return("Spherical RegionShape.")
    public static RegionShape sphere(
            NekoId dimension,
            double centerX,
            double centerY,
            double centerZ,
            double radius
    ) {
        return new RegionShape(
                dimension,
                Mode.SPHERE,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                centerX,
                centerY,
                centerZ,
                radius
        );
    }

    /**
     * Checks whether the given dimension and 3D point fall within (or on the boundary of) this region.
     */
    @Doc("Returns true when the given dimension and 3D coordinates lie within this region.")
    @Param(name = "dim", value = "Dimension id to test.")
    @Param(name = "x", value = "X coordinate to test.")
    @Param(name = "y", value = "Y coordinate to test.")
    @Param(name = "z", value = "Z coordinate to test.")
    @Return("True if the point is inside or on the boundary of this region.")
    public boolean contains(NekoId dim, double x, double y, double z) {
        if (dim == null || !this.dimension.equals(dim)) {
            return false;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return false;
        }
        return switch (this.mode) {
            case BOX -> x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
            case SPHERE -> {
                if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                    yield false;
                }
                double dx = x - centerX;
                double dy = y - centerY;
                double dz = z - centerZ;
                yield (dx * dx + dy * dy + dz * dz) <= (radius * radius);
            }
        };
    }
}
