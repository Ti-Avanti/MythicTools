package gg.fotia.mythictools.leveling;

import java.util.List;

/** 已准备好的水平几何形状，距离查询不访问文件或区块。 */
public interface HorizontalShape {
    double lowerBoundSquared(double x, double z);

    double distanceSquared(double x, double z);

    record Point(double x, double z) implements HorizontalShape {
        @Override
        public double lowerBoundSquared(double px, double pz) {
            return distanceSquared(px, pz);
        }

        @Override
        public double distanceSquared(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return dx * dx + dz * dz;
        }
    }

    record Box(double minX, double minZ, double maxX, double maxZ) implements HorizontalShape {
        @Override
        public double lowerBoundSquared(double x, double z) {
            return distanceSquared(x, z);
        }

        @Override
        public double distanceSquared(double x, double z) {
            double dx = Math.max(Math.max(minX - x, x - maxX), 0.0);
            double dz = Math.max(Math.max(minZ - z, z - maxZ), 0.0);
            return dx * dx + dz * dz;
        }
    }

    /** 多边形按实际边计算最短距离，包围盒仅用于剪枝。 */
    final class Polygon implements HorizontalShape {
        private final List<Point> vertices;
        private final Box bounds;

        public Polygon(List<Point> vertices) {
            if (vertices.size() < 3) {
                throw new IllegalArgumentException("区域多边形至少需要三个顶点");
            }
            this.vertices = List.copyOf(vertices);
            bounds = new Box(vertices.stream().mapToDouble(Point::x).min().orElseThrow(),
                    vertices.stream().mapToDouble(Point::z).min().orElseThrow(),
                    vertices.stream().mapToDouble(Point::x).max().orElseThrow() + 1.0,
                    vertices.stream().mapToDouble(Point::z).max().orElseThrow() + 1.0);
        }

        @Override
        public double lowerBoundSquared(double x, double z) {
            return bounds.distanceSquared(x, z);
        }

        @Override
        public double distanceSquared(double x, double z) {
            // WorldGuard 包含选中的边界方块，内部的任意位置都按距离零处理。
            if (contains(Math.floor(x), Math.floor(z))) {
                return 0.0;
            }
            double closest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < vertices.size(); i++) {
                closest = Math.min(closest, segmentSquared(x, z, vertices.get(i),
                        vertices.get((i + 1) % vertices.size())));
            }
            return closest;
        }

        private boolean contains(double x, double z) {
            boolean inside = false;
            for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
                Point a = vertices.get(j);
                Point b = vertices.get(i);
                if (segmentSquared(x, z, a, b) < 1.0e-12) {
                    return true;
                }
                if ((a.z() > z) != (b.z() > z)
                        && x < (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) {
                    inside = !inside;
                }
            }
            return inside;
        }

        private static double segmentSquared(double x, double z, Point a, Point b) {
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double length = dx * dx + dz * dz;
            double t = length == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0,
                    ((x - a.x()) * dx + (z - a.z()) * dz) / length));
            double px = x - a.x() - t * dx;
            double pz = z - a.z() - t * dz;
            return px * px + pz * pz;
        }
    }
}
