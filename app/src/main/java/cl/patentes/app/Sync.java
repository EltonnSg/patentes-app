package cl.patentes.app;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Envía un ingreso a la hoja de Google (Apps Script). */
public final class Sync {
    private Sync() {}

    /** Devuelve true solo si la hoja confirmó que guardó el ingreso. */
    public static boolean enviar(String url, Ingreso r, byte[] foto1, byte[] foto2) {
        HttpURLConnection c = null;
        try {
            String fecha = r.hora, hora = "";
            int sp = r.hora.indexOf(' ');
            if (sp > 0) {
                fecha = r.hora.substring(0, sp);
                hora = r.hora.substring(sp + 1);
            }
            JSONObject o = new JSONObject();
            o.put("id", r.id);
            o.put("fecha", fecha);
            o.put("hora", hora);
            o.put("bus", r.patente);
            o.put("conductor", r.conductor);
            o.put("codConductor", r.codConductor);
            o.put("servicio", r.servicio);
            o.put("voucher", r.traeVoucher ? "Sí" : "No");
            o.put("mantencion", r.mantencion ? "Sí" : "No");
            o.put("energia", r.energia);
            o.put("nivel", r.nivel);
            o.put("desperfectos", r.desperfectos);
            o.put("coincide", r.coincide);
            o.put("patenteComprobante", r.patenteComprobante);
            if (foto1 != null) o.put("foto1", Base64.encodeToString(foto1, Base64.NO_WRAP));
            if (foto2 != null) o.put("foto2", Base64.encodeToString(foto2, Base64.NO_WRAP));
            byte[] body = o.toString().getBytes(StandardCharsets.UTF_8);

            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setDoOutput(true);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body);
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) return false;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String resp = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            return code == 200 && resp.contains("\"ok\":true");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
