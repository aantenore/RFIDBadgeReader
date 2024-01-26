package component;

public interface WriterManager<WHAT> {
    Void write(WHAT what) throws Exception;
}
