package cn.shiguang.qwenpocket;
/** All saved profile IDs migrate to the current CPU quantized model pair. */
final class SpeedProfiles {
    final int id=1;
    final String label="CPU",modelLabel="Qwen3.5-0.8B · W4/W8";
    final String languageFile="model-allint-w4w8.gguf",visionFile="vision-allint-q8.gguf";
    final boolean quantized=true;
    private static final SpeedProfiles CPU=new SpeedProfiles();
    static SpeedProfiles get(int ignored){return CPU;}
    String description(){return modelLabel+"  ·  CPU";}
    boolean contains(String filename){return languageFile.equals(filename)||visionFile.equals(filename);}
}
