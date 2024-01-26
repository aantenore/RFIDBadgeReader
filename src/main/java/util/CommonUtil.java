package util;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CommonUtil {
    public static String getAbsolutePath(String filePath) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return filePath;
        } else {
            return Paths.get(System.getProperty("user.dir")).resolve(filePath).normalize().toString();
        }
    }
}
