package cl.patentes.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Guarda los ingresos en un archivo JSON dentro del teléfono. */
public final class Store {
    private final File file;

    public Store(Context c) {
        file = new File(c.getFilesDir(), "ingresos.json");
    }

    public synchronized List<Ingreso> load() {
        List<Ingreso> out = new ArrayList<>();
        if (!file.exists()) return out;
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONArray arr = new JSONArray(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(Ingreso.fromJson(o));
            }
        } catch (Exception e) {
            // Si el archivo está dañado se parte de cero sin cerrar la app.
        }
        return out;
    }

    public synchronized boolean save(List<Ingreso> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Ingreso r : list) arr.put(r.toJson());
            File tmp = new File(file.getParentFile(), "ingresos.json.tmp");
            try (FileOutputStream fo = new FileOutputStream(tmp)) {
                fo.write(arr.toString().getBytes(StandardCharsets.UTF_8));
                fo.getFD().sync();
            }
            if (file.exists() && !file.delete()) return false;
            return tmp.renameTo(file);
        } catch (Exception e) {
            return false;
        }
    }
}
