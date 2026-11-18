package cn.shiguang.qwenpocket;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public final class ImageProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    public String getType(Uri uri) { return "image/jpeg"; }
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"/capture.jpg".equals(uri.getPath())) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), "capture.jpg"),
            ParcelFileDescriptor.parseMode(mode));
    }
    public Cursor query(Uri uri, String[] p, String s, String[] a, String sort) { return null; }
    public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    public int delete(Uri uri, String s, String[] a) { return 0; }
    public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
