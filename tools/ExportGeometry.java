import com.piotrek.trimaranyacht.geometry.TrimaranGeometry;

/** Emits the exact Java model tree for deterministic texture generation and optional OBJ export. */
public final class ExportGeometry {
    private static void part(StringBuilder out, TrimaranGeometry.Part p) {
        out.append("{\"name\":\"").append(p.name).append("\",\"pose\":[")
            .append(p.x).append(',').append(p.y).append(',').append(p.z).append(',')
            .append(p.rx).append(',').append(p.ry).append(',').append(p.rz).append("],\"boxes\":[");
        for (int i = 0; i < p.boxes.size(); i++) {
            if (i > 0) out.append(',');
            var b = p.boxes.get(i);
            out.append('[').append(b.x()).append(',').append(b.y()).append(',').append(b.z()).append(',')
                .append(b.w()).append(',').append(b.h()).append(',').append(b.d()).append(',')
                .append(b.u()).append(',').append(b.v()).append(',').append(b.material()).append(']');
        }
        out.append("],\"children\":[");
        for (int i = 0; i < p.children.size(); i++) {
            if (i > 0) out.append(',');
            part(out, p.children.get(i));
        }
        out.append("]}");
    }
    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        part(out, TrimaranGeometry.body());
        System.out.println(out);
    }
}
