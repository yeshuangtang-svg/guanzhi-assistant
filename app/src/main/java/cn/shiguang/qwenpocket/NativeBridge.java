package cn.shiguang.qwenpocket;

public final class NativeBridge {
    static { System.loadLibrary("qwenpocket"); }
    public interface Listener { void onToken(byte[] utf8); }
    public static native String load(String model, String vision, int threads, boolean reserved, int imageTokens);
    public static native String generate(String[] roles, String[] texts, String[] images, String webContext, int maxTokens, Listener listener);
    public static native void cancel();
    public static native void resetCancel();
    public static native void setRepetitionProtection(boolean enabled);
    public static native int finishReason();
    public static native int keepOutputBytes();
    public static native void unload();
    public static native String stats();
    public static native String phase();
    public static native String backend();
    public static native void setDiagnosticPath(String path);
    public static native String diagnostics();
}
