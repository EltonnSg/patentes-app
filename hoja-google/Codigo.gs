// Recibe los ingresos de la app y los agrega a la hoja "Ingreso de buses".
// Columnas A a G = las tuyas. H en adelante = datos extra.
var NOMBRE_CARPETA = 'Fotos ingreso de buses';
var EXTRA = ['Energía', 'Nivel %', 'Desperfectos', '¿Patentes coinciden?', 'Patente en voucher', 'Código conductor', 'Foto patente', 'Foto voucher', 'ID'];

function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(30000);
    var d = JSON.parse(e.postData.contents);
    var hoja = SpreadsheetApp.getActiveSpreadsheet().getSheets()[0];

    // Cabeceras extra (se crean una sola vez)
    if (hoja.getRange(1, 8).getValue() === '') {
      hoja.getRange(1, 8, 1, EXTRA.length).setValues([EXTRA]);
    }
    var colId = 7 + EXTRA.length;

    // Si el ingreso ya llegó antes (reintento), no se duplica
    var ultima = hoja.getLastRow();
    if (ultima > 1) {
      var ids = hoja.getRange(2, colId, ultima - 1, 1).getValues();
      for (var i = 0; i < ids.length; i++) {
        if (ids[i][0] === d.id) return salida({ ok: true, duplicado: true });
      }
    }

    var carpeta = obtenerCarpeta();
    var l1 = guardarFoto(carpeta, d.foto1, d.bus + '_patente_' + d.id);
    var l2 = guardarFoto(carpeta, d.foto2, d.bus + '_voucher_' + d.id);

    var fila = [
      "'" + d.fecha, "'" + d.hora, d.bus, d.conductor, d.servicio, d.voucher, d.mantencion,
      d.energia, d.nivel, d.desperfectos, d.coincide, d.patenteComprobante, d.codConductor,
      l1, l2, d.id
    ];
    hoja.appendRow(fila);
    return salida({ ok: true });
  } catch (err) {
    return salida({ ok: false, error: String(err) });
  } finally {
    try { lock.releaseLock(); } catch (x) {}
  }
}

function doGet() {
  return salida({ ok: true, mensaje: 'La hoja está conectada.' });
}

function salida(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

function obtenerCarpeta() {
  var it = DriveApp.getFoldersByName(NOMBRE_CARPETA);
  if (it.hasNext()) return it.next();
  var c = DriveApp.createFolder(NOMBRE_CARPETA);
  c.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  return c;
}

function guardarFoto(carpeta, b64, nombre) {
  if (!b64) return '';
  var blob = Utilities.newBlob(Utilities.base64Decode(b64), 'image/jpeg', nombre + '.jpg');
  return carpeta.createFile(blob).getUrl();
}
