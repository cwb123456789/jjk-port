package com.jujutsuenhancement.bfn;

/** The execution context of a command: executor, position, rotation, anchor, dimension. Immutable; the execute sub-commands derive new ones. */
public record Ctx(Ent self, double x, double y, double z, float yaw, float pitch, boolean eyes, int dim) {
    public Ctx withSelf(Ent e) {
        return new Ctx(e, x, y, z, yaw, pitch, eyes, dim);
    }

    public Ctx at(Ent e) {
        return new Ctx(self, e.x(), e.y(), e.z(), e.yaw(), e.pitch(), false, e.dim());
    }

    public Ctx pos(double nx, double ny, double nz) {
        return new Ctx(self, nx, ny, nz, yaw, pitch, eyes, dim);
    }

    public Ctx rot(float nyaw, float npitch) {
        return new Ctx(self, x, y, z, nyaw, npitch, eyes, dim);
    }

    public Ctx anchor(boolean e) {
        return new Ctx(self, x, y, z, yaw, pitch, e, dim);
    }

    /** the origin with the anchor applied (eyes: raised by the executor's eye height) */
    public double ox() {
        return x;
    }

    public double oy() {
        return eyes && self != null ? y + self.eyeHeight() : y;
    }

    public double oz() {
        return z;
    }
}
