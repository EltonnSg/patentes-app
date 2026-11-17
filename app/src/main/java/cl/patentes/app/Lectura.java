package cl.patentes.app;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Limpieza de patentes y extracción de datos desde el texto que entrega el lector. Sin dependencias de Android. */
public final class Lectura {
    private Lectura() {}

    private static final String CONS = "BCDFGHJKLPRSTVWXYZ";
    private static final Map<Character, Character> D2L = new HashMap<>();
    private static final Map<Character, Character> L2D = new HashMap<>();

    static {
        D2L.put('0', 'O'); D2L.put('1', 'I'); D2L.put('2', 'Z'); D2L.put('4', 'A');
        D2L.put('5', 'S'); D2L.put('6', 'G'); D2L.put('8', 'B');
        L2D.put('O', '0'); L2D.put('D', '0'); L2D.put('Q', '0'); L2D.put('I', '1'); L2D.put('L', '1');
        L2D.put('Z', '2'); L2D.put('A', '4'); L2D.put('S', '5'); L2D.put('G', '6'); L2D.put('B', '8');
    }

    public static final class Fix {
        public final String plate;
        public final double score;
        Fix(String plate, double score) { this.plate = plate; this.score = score; }
    }

    public static final class Ticket {
        public String codigoConductor = "";
        public String conductor = "";
        public String patenteBus = "";
        public String servicio = "";

        public boolean complete() {
            return !codigoConductor.isEmpty() && !conductor.isEmpty() && !patenteBus.isEmpty() && !servicio.isEmpty();
        }

        /** Copia solo los datos que todavía faltan. */
        public void mergeMissing(Ticket o) {
            if (codigoConductor.isEmpty()) codigoConductor = o.codigoConductor;
            if (conductor.isEmpty()) conductor = o.conductor;
            if (patenteBus.isEmpty()) patenteBus = o.patenteBus;
            if (servicio.isEmpty()) servicio = o.servicio;
        }
    }

    public static String clean(String s) {
        if (s == null) return "";
        return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9?]", "");
    }

    private static Fix tryPattern(String tok, int nL, int nD) {
        StringBuilder out = new StringBuilder();
        int fixes = 0;
        for (int i = 0; i < nL; i++) {
            char c = tok.charAt(i);
            if (c >= '0' && c <= '9') {
                Character m = D2L.get(c);
                if (m == null) return null;
                c = m; fixes++;
            }
            out.append(c);
        }
        for (int i = nL; i < nL + nD; i++) {
            char c = tok.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                Character m = L2D.get(c);
                if (m == null) return null;
                c = m; fixes++;
            }
            out.append(c);
        }
        double bonus = 0;
        if (nL == 4) {
            int vow = 0;
            boolean allCons = true;
            for (int i = 0; i < 4; i++) {
                char c = out.charAt(i);
                if ("AEIOU".indexOf(c) >= 0) vow++;
                if (CONS.indexOf(c) < 0) allCons = false;
            }
            bonus += vow * 0.6;
            if (allCons) bonus -= 1;
        }
        return new Fix(out.toString(), fixes + bonus);
    }

    /** Corrige confusiones típicas (0/O, 1/I, 8/B...) según el formato de patente chilena. */
    public static Fix fixPlate(String raw) {
        String tok = clean(raw);
        Fix best = null;
        Fix[] opts;
        if (tok.length() == 6) opts = new Fix[]{tryPattern(tok, 4, 2), tryPattern(tok, 2, 4)};
        else if (tok.length() == 5) opts = new Fix[]{tryPattern(tok, 3, 2)};
        else return null;
        for (Fix f : opts) {
            if (f == null || f.score > 2) continue;
            if (best == null || f.score < best.score) best = f;
        }
        return best;
    }

    /** Busca la mejor patente dentro de un texto libre uniendo hasta 3 trozos vecinos (SF WB 77). */
    public static Fix findPlateFix(String text) {
        if (text == null) return null;
        String[] toks = text.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String t : toks) if (!t.isEmpty()) list.add(t);
        Fix best = null;
        for (int i = 0; i < list.size(); i++) {
            StringBuilder cand = new StringBuilder();
            for (int j = i; j < Math.min(i + 3, list.size()); j++) {
                cand.append(list.get(j));
                if (cand.length() > 6) break;
                if (cand.length() >= 5) {
                    Fix f = fixPlate(cand.toString());
                    if (f != null) {
                        double sc = f.score + (j > i ? 0.5 : 0);
                        if (best == null || sc < best.score) best = new Fix(f.plate, sc);
                    }
                }
            }
        }
        return best;
    }

    public static String findPlate(String text) {
        Fix f = findPlateFix(text);
        return f == null ? "" : f.plate;
    }

    public static boolean looksValid(String p) {
        return p != null && p.matches("^[A-Z]{4}[0-9]{2}$|^[A-Z]{2}[0-9]{4}$|^[A-Z]{3}[0-9]{2}$");
    }

    private static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static final Pattern P_COND = Pattern.compile("Conductor\\s*[:\\-]?\\s*([0-9OIl]{3,7})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_BUS = Pattern.compile("\\bBus\\s+([A-Z0-9]{5,7})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_NAME = Pattern.compile("([A-Z]{2,}(?:\\s+[A-Z]{2,}){1,4})");
    private static final Pattern P_NAME_LONG = Pattern.compile("([A-Z]{3,}(?:\\s+[A-Z]{3,}){1,4})");
    private static final Pattern P_LABEL = Pattern.compile("Cod[a-z]{2,4}\\s+Usua[a-z]{1,3}", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SERV = Pattern.compile("^[^A-Za-z0-9]*([A-Z]{0,2}[0-9]{1,4}[A-Z]{0,2}(?:\\s[A-Z]{1,2})?)(?![0-9A-Za-z])");
    private static final Pattern P_PLATE4 = Pattern.compile("\\b([A-Z]{4}[0-9]{2})\\b");
    private static final Pattern BAD_NAME = Pattern.compile("C[OE]DIGO|USUARI|VALIDAC|CONDUCTOR");
    private static final Pattern BAD_NAME2 = Pattern.compile("C[OE]DIGO|USUARI|VALIDAC|CONDUCTOR|ESTACION|REPRESENT|SANTA|PRESENTANTE");

    /** Extrae los datos del comprobante del conductor a partir del texto leído. */
    public static Ticket parseTicket(String text) {
        Ticket out = new Ticket();
        String raw = fold(text);
        String[] rawLines = raw.split("\\r?\\n");
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String l : rawLines) { String t = l.trim(); if (!t.isEmpty()) lines.add(t); }

        Matcher m = P_COND.matcher(raw);
        if (m.find()) out.codigoConductor = m.group(1).replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1');

        int ci = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).toLowerCase(Locale.ROOT).contains("conductor")) { ci = i; break; }
        if (ci >= 0) {
            for (int i = ci + 1; i < Math.min(ci + 4, lines.size()); i++) {
                Matcher mm = P_NAME.matcher(lines.get(i).toUpperCase(Locale.ROOT));
                String best = "";
                while (mm.find()) if (mm.group(1).length() > best.length()) best = mm.group(1);
                if (best.split("\\s+").length >= 2 && best.length() >= 8
                        && !best.matches("^(BUS|CODIGO|VALIDACION).*") && !BAD_NAME.matcher(best).find()) {
                    out.conductor = best;
                    break;
                }
            }
        }

        Matcher mb = P_BUS.matcher(raw);
        if (mb.find()) {
            Fix f = fixPlate(mb.group(1));
            out.patenteBus = f != null ? f.plate : clean(mb.group(1));
        }

        for (int i = 0; i < lines.size(); i++) {
            if (P_LABEL.matcher(lines.get(i)).find()) {
                for (int k = i; k < Math.min(i + 3, lines.size()); k++) {
                    String seg = lines.get(k);
                    if (k == i) {
                        Matcher lm = P_LABEL.matcher(seg);
                        seg = lm.find() ? seg.substring(lm.end()) : seg;
                    }
                    Matcher sm = P_SERV.matcher(seg.trim().toUpperCase(Locale.ROOT));
                    if (sm.find() && sm.group(1).matches(".*[0-9].*") && sm.group(1).length() >= 2) {
                        out.servicio = sm.group(1).replaceAll("\\s+", " ");
                        break;
                    }
                }
                break;
            }
        }

        if (out.patenteBus.isEmpty()) {
            Matcher tm = P_PLATE4.matcher(raw.toUpperCase(Locale.ROOT));
            if (tm.find()) {
                Fix ff = fixPlate(tm.group(1));
                if (ff != null) out.patenteBus = ff.plate;
            }
        }

        if (out.conductor.isEmpty()) {
            String bestN = "";
            for (String ln : lines) {
                Matcher m2 = P_NAME_LONG.matcher(ln.toUpperCase(Locale.ROOT));
                while (m2.find()) {
                    String cand = m2.group(1);
                    if (cand.length() >= 12 && cand.length() > bestN.length() && !BAD_NAME2.matcher(cand).find()) bestN = cand;
                }
            }
            out.conductor = bestN;
        }
        return out;
    }

    /** Compara dos patentes: "coinciden", "no", "dudosa" o "pendiente". */
    public static String compare(String a, String b) {
        a = clean(a); b = clean(b);
        if (a.isEmpty() || b.isEmpty()) return "pendiente";
        if (a.contains("?") || b.contains("?")) return "dudosa";
        return a.equals(b) ? "coinciden" : "no";
    }
}
