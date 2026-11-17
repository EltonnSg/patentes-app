package cl.patentes.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int OK = 1, WARN = 2, BAD = 3;
    private static final int MAX_LISTA = 30;

    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbEx = Executors.newFixedThreadPool(2);
    private TextRecognizer recognizer;
    private Store store;
    private final List<Ingreso> records = new ArrayList<>();

    // Estado del formulario
    private long curTs = 0;
    private String curHora = "";
    private final String[] fotoPath = new String[3];   // posiciones 1 y 2
    private final int[] runs = new int[3];
    private int pendingSlot = 1;
    private File pendingFile;

    // Vistas
    private TextView txtHora, st1, st2, st3, txtVeredicto, txtConteo, txtNota;
    private ImageView prev1, prev2;
    private EditText edPatente, edPatente2, edCodigo, edServicio, edConductor, edNivel, edDesp;
    private RadioButton rbBat, rbCon;
    private CheckBox cbMant, cbVoucher;
    private View tilDesp, boxVoucher;
    private Button btnGuardar, btnExcel;
    private LinearLayout lista;

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok != null && ok && pendingFile != null && pendingFile.length() > 0) {
                    onPhoto(pendingSlot, pendingFile);
                }
            });

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) importFromGallery(pendingSlot, uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtHora = findViewById(R.id.txtHora);
        st1 = findViewById(R.id.st1);
        st2 = findViewById(R.id.st2);
        st3 = findViewById(R.id.st3);
        txtVeredicto = findViewById(R.id.txtVeredicto);
        txtConteo = findViewById(R.id.txtConteo);
        txtNota = findViewById(R.id.txtNota);
        prev1 = findViewById(R.id.prev1);
        prev2 = findViewById(R.id.prev2);
        edPatente = findViewById(R.id.edPatente);
        edPatente2 = findViewById(R.id.edPatente2);
        edCodigo = findViewById(R.id.edCodigo);
        edServicio = findViewById(R.id.edServicio);
        edConductor = findViewById(R.id.edConductor);
        edNivel = findViewById(R.id.edNivel);
        edDesp = findViewById(R.id.edDesp);
        rbBat = findViewById(R.id.rbBat);
        rbCon = findViewById(R.id.rbCon);
        cbMant = findViewById(R.id.cbMant);
        cbVoucher = findViewById(R.id.cbVoucher);
        boxVoucher = findViewById(R.id.boxVoucher);
        tilDesp = findViewById(R.id.tilDesp);
        btnGuardar = findViewById(R.id.btnGuardar);
        btnExcel = findViewById(R.id.btnExcel);
        lista = findViewById(R.id.lista);
        txtVeredicto.setBackgroundResource(R.drawable.bg_verdict);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        store = new Store(this);

        findViewById(R.id.btnCam1).setOnClickListener(v -> launchCamera(1));
        findViewById(R.id.btnCam2).setOnClickListener(v -> launchCamera(2));
        findViewById(R.id.btnGal1).setOnClickListener(v -> launchGallery(1));
        findViewById(R.id.btnGal2).setOnClickListener(v -> launchGallery(2));
        btnGuardar.setOnClickListener(v -> guardar());
        btnExcel.setOnClickListener(v -> exportar());

        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateVerdict(); }
        };
        edPatente.addTextChangedListener(w);
        edPatente2.addTextChangedListener(w);

        cbVoucher.setOnCheckedChangeListener((b, checked) -> {
            boxVoucher.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) {
                runs[2]++;
                deleteQuiet(fotoPath[2]);
                fotoPath[2] = null;
                prev2.setImageDrawable(null);
                prev2.setVisibility(View.GONE);
                edCodigo.setText("");
                edConductor.setText("");
                edServicio.setText("");
                edPatente2.setText("");
                st2.setText("");
            }
            updateVerdict();
        });

        ((RadioGroup) findViewById(R.id.rgDesp)).setOnCheckedChangeListener(
                (g, id) -> tilDesp.setVisibility(id == R.id.rbCon ? View.VISIBLE : View.GONE));

        if (savedInstanceState != null) restoreState(savedInstanceState);
        updateVerdict();

        ex.execute(() -> {
            List<Ingreso> loaded = store.load();
            runOnUiThread(() -> {
                records.clear();
                records.addAll(loaded);
                renderList();
            });
        });
    }

    // ---------- estado ----------
    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pendingSlot", pendingSlot);
        out.putString("pendingFile", pendingFile == null ? null : pendingFile.getPath());
        out.putLong("curTs", curTs);
        out.putString("curHora", curHora);
        out.putString("foto1", fotoPath[1]);
        out.putString("foto2", fotoPath[2]);
        out.putBoolean("voucher", cbVoucher.isChecked());
    }

    private void restoreState(Bundle b) {
        pendingSlot = b.getInt("pendingSlot", 1);
        String pf = b.getString("pendingFile");
        pendingFile = pf == null ? null : new File(pf);
        curTs = b.getLong("curTs", 0);
        curHora = b.getString("curHora", "");
        if (curTs != 0) txtHora.setText(curHora);
        for (int slot = 1; slot <= 2; slot++) {
            final String p = b.getString("foto" + slot);
            if (p == null || !new File(p).exists()) continue;
            fotoPath[slot] = p;
            final int s = slot;
            thumbEx.execute(() -> {
                try {
                    final Bitmap bm = ImageUtil.decode(new File(p), 900);
                    runOnUiThread(() -> showPreview(s, bm));
                } catch (Exception ignored) {
                }
            });
        }
        updateSave();
    }

    // ---------- fotos ----------
    private File tmpDir() {
        File d = new File(getFilesDir(), "tmp");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private File fotosDir() {
        File d = new File(getFilesDir(), "fotos");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private void launchCamera(int slot) {
        try {
            pendingSlot = slot;
            pendingFile = File.createTempFile("cap", ".jpg", tmpDir());
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingFile);
            takePicture.launch(uri);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_LONG).show();
        }
    }

    private void launchGallery(int slot) {
        pendingSlot = slot;
        pickImage.launch("image/*");
    }

    private void importFromGallery(final int slot, final Uri uri) {
        ex.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("sin datos");
                final File f = File.createTempFile("gal", ".jpg", tmpDir());
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                }
                runOnUiThread(() -> onPhoto(slot, f));
            } catch (Exception e) {
                runOnUiThread(() -> setSt(slot, "No se pudo abrir la foto de la galería.", BAD));
            }
        });
    }

    private void onPhoto(final int slot, final File src) {
        if (curTs == 0) {
            curTs = System.currentTimeMillis();
            curHora = fmt(curTs);
            txtHora.setText(curHora);
        }
        final long ts = curTs;
        final int my = ++runs[slot];
        setSt(slot, "Procesando foto…", 0);

        ex.execute(() -> {
            Bitmap bmp;
            try {
                bmp = ImageUtil.decode(src, 2400);
                final File dst = new File(fotosDir(), "f" + ts + "_" + slot + "_" + my + ".jpg");
                ImageUtil.saveJpeg(ImageUtil.scaleDown(bmp, 1600), dst, 85);
                //noinspection ResultOfMethodCallIgnored
                src.delete();
                final Bitmap preview = ImageUtil.scaleDown(bmp, 900);
                runOnUiThread(() -> {
                    if (runs[slot] != my) {
                        //noinspection ResultOfMethodCallIgnored
                        dst.delete();
                        return;
                    }
                    deleteQuiet(fotoPath[slot]);
                    fotoPath[slot] = dst.getPath();
                    showPreview(slot, preview);
                    setSt(slot, slot == 1 ? "Leyendo patente…" : "Leyendo comprobante…", 0);
                    updateSave();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> setSt(slot, "No se pudo abrir la foto. Toma otra.", BAD));
                return;
            }

            try {
                if (slot == 1) {
                    final String plate = readPlate(bmp);
                    runOnUiThread(() -> {
                        if (runs[slot] != my) return;
                        if (plate.isEmpty()) {
                            setSt(1, "No se pudo leer la patente. Escríbela a mano o toma otra foto más cerca.", WARN);
                        } else {
                            edPatente.setText(plate);
                            setSt(1, Lectura.looksValid(plate)
                                    ? "Patente leída. Revisa que coincida con la foto."
                                    : "Formato poco habitual. Revisa los caracteres.", Lectura.looksValid(plate) ? OK : WARN);
                        }
                        updateVerdict();
                    });
                } else {
                    final Lectura.Ticket t = readTicket(bmp);
                    runOnUiThread(() -> {
                        if (runs[slot] != my) return;
                        edCodigo.setText(t.codigoConductor);
                        edConductor.setText(t.conductor);
                        edServicio.setText(t.servicio);
                        edPatente2.setText(t.patenteBus);
                        setSt(2, t.complete()
                                ? "Comprobante leído. Revisa los datos contra la foto."
                                : "Faltan datos por leer. Complétalos a mano o toma otra foto.", t.complete() ? OK : WARN);
                        updateVerdict();
                    });
                }
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    if (runs[slot] == my) setSt(slot, "No se pudo leer automáticamente. Escribe los datos a mano.", WARN);
                });
            }
        });
    }

    private void showPreview(int slot, Bitmap bmp) {
        ImageView iv = slot == 1 ? prev1 : prev2;
        iv.setImageBitmap(bmp);
        iv.setVisibility(View.VISIBLE);
    }

    private static void deleteQuiet(String path) {
        if (path != null && !path.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
    }

    // ---------- lectura con ML Kit (sin conexión) ----------
    private Text recognize(Bitmap b) throws Exception {
        return Tasks.await(recognizer.process(InputImage.fromBitmap(b, 0)));
    }

    /** Lee la patente por zonas de la foto y se queda con la que parece más clara y más grande. */
    private String readPlate(Bitmap bmp) throws Exception {
        Bitmap[] passes = {
                bmp,
                ImageUtil.crop(bmp, 0f, 0.3f, 1f, 1f),
                ImageUtil.crop(bmp, 0.08f, 0.2f, 0.92f, 0.8f)
        };
        Lectura.Fix best = null;
        double bestScore = 1e9;
        for (Bitmap b : passes) {
            Text t = recognize(b);
            for (Text.TextBlock blk : t.getTextBlocks()) {
                for (Text.Line ln : blk.getLines()) {
                    Lectura.Fix f = Lectura.findPlateFix(ln.getText());
                    if (f == null) continue;
                    Rect r = ln.getBoundingBox();
                    double h = r == null ? 0 : (double) r.height() / b.getHeight();
                    double sc = f.score - Math.min(2.0, h * 20.0);
                    if (sc < bestScore) {
                        bestScore = sc;
                        best = f;
                    }
                }
            }
            if (best == null) {
                Lectura.Fix f = Lectura.findPlateFix(t.getText());
                if (f != null) best = f;
            }
            if (best != null && Lectura.looksValid(best.plate)) break;
        }
        return best == null ? "" : best.plate;
    }

    /** Lee el comprobante completo y, si faltan datos, lo recorre por franjas. */
    private Lectura.Ticket readTicket(Bitmap bmp) throws Exception {
        Lectura.Ticket out = new Lectura.Ticket();
        Bitmap[] passes = {
                bmp,
                ImageUtil.crop(bmp, 0.05f, 0f, 0.95f, 0.55f),
                ImageUtil.crop(bmp, 0.05f, 0.25f, 0.95f, 0.8f),
                ImageUtil.crop(bmp, 0.05f, 0.45f, 0.95f, 1f)
        };
        for (Bitmap b : passes) {
            Text t = recognize(b);
            out.mergeMissing(Lectura.parseTicket(t.getText()));
            if (out.complete()) break;
        }
        return out;
    }

    // ---------- formulario ----------
    private static String fmt(long ts) {
        SimpleDateFormat f = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", new Locale("es", "CL"));
        f.setTimeZone(TimeZone.getTimeZone("America/Santiago"));
        return f.format(new Date(ts));
    }

    private void setSt(int slot, String msg, int kind) {
        TextView tv = slot == 1 ? st1 : (slot == 2 ? st2 : st3);
        tv.setText(msg);
        int color;
        switch (kind) {
            case OK: color = Color.parseColor("#2E9E5B"); break;
            case WARN: color = Color.parseColor("#D98300"); break;
            case BAD: color = Color.parseColor("#E0463B"); break;
            default: color = tv.getTextColors().getDefaultColor(); break;
        }
        tv.setTextColor(kind == 0 ? defaultTextColor() : color);
    }

    private int defaultTextColor() {
        return txtHora.getTextColors().getDefaultColor();
    }

    private void updateVerdict() {
        String v = Lectura.compare(edPatente.getText().toString(), edPatente2.getText().toString());
        String t = "Validación: faltan las dos patentes.";
        int color = defaultTextColor();
        if (!cbVoucher.isChecked()) {
            t = "Sin voucher: no se puede validar la patente.";
            color = Color.parseColor("#D98300");
        } else if (v.equals("coinciden")) {
            t = "Coinciden: la patente de la foto es la misma del comprobante.";
            color = Color.parseColor("#2E9E5B");
        } else if (v.equals("no")) {
            t = "No coinciden: " + Lectura.clean(edPatente.getText().toString()) + " en la foto y "
                    + Lectura.clean(edPatente2.getText().toString()) + " en el comprobante.";
            color = Color.parseColor("#E0463B");
        } else if (v.equals("dudosa")) {
            t = "No se puede validar: hay caracteres dudosos (?). Corrígelos.";
            color = Color.parseColor("#D98300");
        }
        txtVeredicto.setText(t);
        txtVeredicto.setTextColor(color);
        updateSave();
    }

    private void updateSave() {
        boolean voucher = cbVoucher.isChecked();
        btnGuardar.setEnabled(fotoPath[1] != null
                && !Lectura.clean(edPatente.getText().toString()).isEmpty()
                && (!voucher || (fotoPath[2] != null && !Lectura.clean(edPatente2.getText().toString()).isEmpty())));
    }

    private void guardar() {
        int nivel;
        try {
            nivel = Integer.parseInt(edNivel.getText().toString().trim());
        } catch (NumberFormatException e) {
            nivel = -1;
        }
        if (nivel < 0 || nivel > 100) {
            setSt(3, "Escribe el porcentaje de batería o combustible (0 a 100).", WARN);
            edNivel.requestFocus();
            return;
        }
        boolean con = rbCon.isChecked();
        String desp = edDesp.getText().toString().trim();
        if (con && desp.isEmpty()) {
            setSt(3, "Describe el desperfecto antes de guardar.", WARN);
            edDesp.requestFocus();
            return;
        }
        boolean voucher = cbVoucher.isChecked();
        String p1 = Lectura.clean(edPatente.getText().toString());
        String p2 = voucher ? Lectura.clean(edPatente2.getText().toString()) : "";
        String v = voucher ? Lectura.compare(p1, p2) : "sinvoucher";

        Ingreso r = new Ingreso();
        r.id = "r" + System.currentTimeMillis();
        r.ts = curTs;
        r.hora = curHora;
        r.patente = p1;
        r.patenteComprobante = p2;
        r.traeVoucher = voucher;
        r.codConductor = voucher ? edCodigo.getText().toString().trim() : "";
        r.conductor = voucher ? edConductor.getText().toString().trim() : "";
        r.servicio = voucher ? edServicio.getText().toString().trim() : "";
        r.coincide = v.equals("coinciden") ? "Sí" : (v.equals("no") ? "No" : (v.equals("sinvoucher") ? "Sin voucher" : "Sin validar"));
        r.energia = rbBat.isChecked() ? "Batería" : "Combustible";
        r.nivel = nivel;
        r.conDesperfectos = con;
        r.desperfectos = con ? desp : "Sin desperfectos";
        r.mantencion = cbMant.isChecked();
        r.foto1 = fotoPath[1];
        r.foto2 = voucher ? fotoPath[2] : "";

        records.add(r);
        final List<Ingreso> snapshot = new ArrayList<>(records);
        ex.execute(() -> {
            final boolean ok = store.save(snapshot);
            if (!ok) runOnUiThread(() -> setSt(3, "No se pudo guardar en el teléfono. Exporta el Excel ahora.", BAD));
        });

        resetForm();
        renderList();
        setSt(3, r.coincide.equals("No") ? "Ingreso guardado. Ojo: las patentes no coinciden." : "Ingreso guardado.",
                r.coincide.equals("No") ? WARN : OK);
    }

    private void resetForm() {
        curTs = 0;
        curHora = "";
        runs[1]++;
        runs[2]++;
        fotoPath[1] = null;
        fotoPath[2] = null;
        txtHora.setText("Con la primera foto");
        edPatente.setText("");
        edPatente2.setText("");
        edCodigo.setText("");
        edConductor.setText("");
        edServicio.setText("");
        edNivel.setText("");
        edDesp.setText("");
        cbMant.setChecked(false);
        cbVoucher.setChecked(true);
        ((RadioButton) findViewById(R.id.rbComb)).setChecked(true);
        ((RadioButton) findViewById(R.id.rbSin)).setChecked(true);
        prev1.setImageDrawable(null);
        prev2.setImageDrawable(null);
        prev1.setVisibility(View.GONE);
        prev2.setVisibility(View.GONE);
        st1.setText("");
        st2.setText("");
        updateVerdict();
    }

    // ---------- lista ----------
    private void renderList() {
        List<Ingreso> sorted = new ArrayList<>(records);
        Collections.sort(sorted, new Comparator<Ingreso>() {
            @Override public int compare(Ingreso a, Ingreso b) { return Long.compare(b.ts, a.ts); }
        });
        txtConteo.setText(sorted.size() + (sorted.size() == 1 ? " registro" : " registros"));
        btnExcel.setEnabled(!sorted.isEmpty());
        lista.removeAllViews();
        if (sorted.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Todavía no hay ingresos. Toma las dos fotos para registrar el primero.");
            lista.addView(t);
            return;
        }
        LayoutInflater inf = LayoutInflater.from(this);
        int shown = Math.min(sorted.size(), MAX_LISTA);
        for (int i = 0; i < shown; i++) {
            final Ingreso r = sorted.get(i);
            View v = inf.inflate(R.layout.item_registro, lista, false);
            ((TextView) v.findViewById(R.id.itPatente)).setText(r.patente);
            TextView co = v.findViewById(R.id.itCoincide);
            if (r.coincide.equals("Sí")) {
                co.setText("Patentes coinciden");
                co.setTextColor(Color.parseColor("#2E9E5B"));
            } else if (r.coincide.equals("No")) {
                co.setText("No coinciden (" + r.patenteComprobante + ")");
                co.setTextColor(Color.parseColor("#E0463B"));
            } else {
                co.setText(r.traeVoucher ? "Sin validar" : "Sin voucher");
                co.setTextColor(Color.parseColor("#D98300"));
            }
            StringBuilder est = new StringBuilder(r.energia + " " + r.nivel + "%");
            if (r.conDesperfectos) est.append(" · Desperfecto: ").append(r.desperfectos);
            if (r.mantencion) est.append(" · Solicitada por mantención");
            ((TextView) v.findViewById(R.id.itEstado)).setText(est.toString());
            if (!r.traeVoucher) {
                ((TextView) v.findViewById(R.id.itConductor)).setText("Sin voucher");
            } else ((TextView) v.findViewById(R.id.itConductor)).setText("Conductor " + (r.codConductor.isEmpty() ? "—" : r.codConductor)
                    + (r.conductor.isEmpty() ? "" : " · " + r.conductor)
                    + "\nServicio " + (r.servicio.isEmpty() ? "—" : r.servicio));
            ((TextView) v.findViewById(R.id.itHora)).setText(r.hora);
            loadThumb((ImageView) v.findViewById(R.id.imgF1), r.foto1);
            ImageView im2 = v.findViewById(R.id.imgF2);
            if (r.foto2 == null || r.foto2.isEmpty()) im2.setVisibility(View.GONE);
            else loadThumb(im2, r.foto2);
            v.findViewById(R.id.itQuitar).setOnClickListener(b -> confirmarQuitar(r));
            lista.addView(v);
        }
        if (sorted.size() > shown) {
            TextView t = new TextView(this);
            t.setText("Se muestran los últimos " + shown + ". El Excel incluye todos.");
            lista.addView(t);
        }
    }

    private void loadThumb(final ImageView iv, final String path) {
        if (path == null || path.isEmpty()) return;
        thumbEx.execute(() -> {
            try {
                final Bitmap b = ImageUtil.decode(new File(path), 240);
                runOnUiThread(() -> iv.setImageBitmap(b));
            } catch (Exception ignored) {
            }
        });
    }

    private void confirmarQuitar(final Ingreso r) {
        new AlertDialog.Builder(this)
                .setTitle("Quitar ingreso")
                .setMessage("¿Quitar el ingreso de " + r.patente + "? No se puede deshacer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Quitar", (d, w) -> {
                    records.remove(r);
                    deleteQuiet(r.foto1);
                    deleteQuiet(r.foto2);
                    final List<Ingreso> snapshot = new ArrayList<>(records);
                    ex.execute(() -> store.save(snapshot));
                    renderList();
                })
                .show();
    }

    // ---------- Excel ----------
    private void exportar() {
        btnExcel.setEnabled(false);
        txtNota.setText("Generando Excel…");
        final List<Ingreso> snap = new ArrayList<>(records);
        ex.execute(() -> {
            try {
                Collections.sort(snap, new Comparator<Ingreso>() {
                    @Override public int compare(Ingreso a, Ingreso b) { return Long.compare(a.ts, b.ts); }
                });
                String[] header = {"Hora de ingreso", "Patente", "Foto patente", "Código conductor", "Conductor", "Servicio",
                        "Patente en comprobante", "Foto comprobante", "¿Trae voucher?", "¿Coinciden?", "Tipo de energía", "Nivel (%)",
                        "Desperfectos", "Solicitada por mantención"};
                double[] widths = {21, 12, 18, 17, 28, 12, 16, 18, 13, 13, 15, 10, 34, 18};
                List<XlsxWriter.Row> rows = new ArrayList<>();
                for (Ingreso r : snap) {
                    XlsxWriter.Row row = new XlsxWriter.Row();
                    row.heightPt = 66;
                    row.cells.add(new XlsxWriter.Cell(r.hora, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(r.patente, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(null, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(r.codConductor, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(r.conductor, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(r.servicio, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(r.patenteComprobante, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(null, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(r.traeVoucher ? "Sí" : "No", r.traeVoucher ? XlsxWriter.ST_NORMAL : XlsxWriter.ST_ORANGE));
                    row.cells.add(new XlsxWriter.Cell(r.coincide, r.coincide.equals("Sí") ? XlsxWriter.ST_GREEN
                            : (r.coincide.equals("No") ? XlsxWriter.ST_RED : XlsxWriter.ST_ORANGE)));
                    row.cells.add(new XlsxWriter.Cell(r.energia, XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(Integer.valueOf(r.nivel), XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(r.desperfectos, r.conDesperfectos ? XlsxWriter.ST_RED : XlsxWriter.ST_NORMAL));
                    row.cells.add(new XlsxWriter.Cell(r.mantencion ? "Sí" : "No", r.mantencion ? XlsxWriter.ST_ORANGE : XlsxWriter.ST_NORMAL));
                    addImage(row, 2, r.foto1);
                    addImage(row, 7, r.foto2);
                    rows.add(row);
                }
                File dir = new File(getCacheDir(), "export");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                String stamp = fmt(System.currentTimeMillis()).substring(0, 10);
                final File out = new File(dir, "ingresos_patentes_" + stamp + ".xlsx");
                try (FileOutputStream fo = new FileOutputStream(out)) {
                    XlsxWriter.write(fo, "Ingresos", header, widths, rows);
                }
                runOnUiThread(() -> {
                    txtNota.setText("Excel listo. Elige dónde guardarlo o enviarlo.");
                    btnExcel.setEnabled(true);
                    compartir(out);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    txtNota.setText("No se pudo generar el Excel. Intenta de nuevo.");
                    btnExcel.setEnabled(true);
                });
            }
        });
    }

    private void addImage(XlsxWriter.Row row, int col, String path) {
        if (path == null || path.isEmpty() || !new File(path).exists()) return;
        try {
            Bitmap b = ImageUtil.decode(new File(path), 360);
            int w = 112, h = Math.round(112f * b.getHeight() / b.getWidth());
            if (h > 82) {
                h = 82;
                w = Math.round(82f * b.getWidth() / b.getHeight());
            }
            row.images.add(new XlsxWriter.Image(col, ImageUtil.jpegBytes(b, 70), Math.max(1, w), Math.max(1, h)));
        } catch (Exception ignored) {
        }
    }

    private void compartir(File f) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Guardar o enviar el Excel"));
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir el menú para compartir", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recognizer.close();
        ex.shutdown();
        thumbEx.shutdown();
    }
}
