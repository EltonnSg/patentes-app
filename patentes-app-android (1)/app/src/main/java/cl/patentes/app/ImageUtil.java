package cl.patentes.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Utilidades para abrir, girar, recortar y guardar fotos. */
public final class ImageUtil {
    private ImageUtil() {}

    /** Abre una foto reducida a un lado máximo y la gira según su orientación EXIF. */
    public static Bitmap decode(File f, int maxSide) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("Imagen no válida");
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        if (bmp == null) throw new IOException("No se pudo abrir la imagen");
        bmp = rotate(bmp, orientationOf(f));
        return scaleDown(bmp, maxSide);
    }

    private static int orientationOf(File f) {
        try {
            ExifInterface ex = new ExifInterface(f.getAbsolutePath());
            return ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap rotate(Bitmap b, int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
            default: return b;
        }
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b) b.recycle();
        return r;
    }

    public static Bitmap scaleDown(Bitmap b, int maxSide) {
        int big = Math.max(b.getWidth(), b.getHeight());
        if (big <= maxSide) return b;
        float k = (float) maxSide / big;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)), Math.max(1, Math.round(b.getHeight() * k)), true);
    }

    /** Recorta una zona usando fracciones del ancho y alto (0 a 1). */
    public static Bitmap crop(Bitmap b, float x0, float y0, float x1, float y1) {
        int x = Math.round(b.getWidth() * x0), y = Math.round(b.getHeight() * y0);
        int w = Math.max(1, Math.round(b.getWidth() * (x1 - x0))), h = Math.max(1, Math.round(b.getHeight() * (y1 - y0)));
        if (x + w > b.getWidth()) w = b.getWidth() - x;
        if (y + h > b.getHeight()) h = b.getHeight() - y;
        return Bitmap.createBitmap(b, x, y, Math.max(1, w), Math.max(1, h));
    }

    public static void saveJpeg(Bitmap b, File f, int quality) throws IOException {
        try (FileOutputStream fo = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, quality, fo);
        }
    }

    public static byte[] jpegBytes(Bitmap b, int quality) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, quality, bo);
        return bo.toByteArray();
    }
}
