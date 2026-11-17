package cl.patentes.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Un ingreso de bus registrado. */
public final class Ingreso {
    public String id = "";
    public long ts;
    public String hora = "";
    public String patente = "";
    public String patenteComprobante = "";
    public String codConductor = "";
    public String conductor = "";
    public String servicio = "";
    public String coincide = "Sin validar";
    public String energia = "Combustible";
    public int nivel;
    public String desperfectos = "Sin desperfectos";
    public boolean conDesperfectos;
    public boolean mantencion;
    public boolean traeVoucher = true;
    public boolean enviado;
    public String foto1 = "";
    public String foto2 = "";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("ts", ts);
        o.put("hora", hora);
        o.put("patente", patente);
        o.put("patenteComprobante", patenteComprobante);
        o.put("codConductor", codConductor);
        o.put("conductor", conductor);
        o.put("servicio", servicio);
        o.put("coincide", coincide);
        o.put("energia", energia);
        o.put("nivel", nivel);
        o.put("desperfectos", desperfectos);
        o.put("conDesperfectos", conDesperfectos);
        o.put("mantencion", mantencion);
        o.put("traeVoucher", traeVoucher);
        o.put("enviado", enviado);
        o.put("foto1", foto1);
        o.put("foto2", foto2);
        return o;
    }

    public static Ingreso fromJson(JSONObject o) {
        Ingreso r = new Ingreso();
        r.id = o.optString("id");
        r.ts = o.optLong("ts");
        r.hora = o.optString("hora");
        r.patente = o.optString("patente");
        r.patenteComprobante = o.optString("patenteComprobante");
        r.codConductor = o.optString("codConductor");
        r.conductor = o.optString("conductor");
        r.servicio = o.optString("servicio");
        r.coincide = o.optString("coincide", "Sin validar");
        r.energia = o.optString("energia", "Combustible");
        r.nivel = o.optInt("nivel");
        r.desperfectos = o.optString("desperfectos", "Sin desperfectos");
        r.conDesperfectos = o.optBoolean("conDesperfectos");
        r.mantencion = o.optBoolean("mantencion");
        r.traeVoucher = o.optBoolean("traeVoucher", true);
        r.enviado = o.optBoolean("enviado", false);
        r.foto1 = o.optString("foto1");
        r.foto2 = o.optString("foto2");
        return r;
    }
}
