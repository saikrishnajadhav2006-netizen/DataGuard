import java.io.File;
public class TestFile {
    public static void main(String[] args) throws Exception {
        File parent = new File("C:\\Temp");
        File child = new File(parent, "/etc/shadow");
        System.out.println("Absolute Path: " + child.getAbsolutePath());
        System.out.println("Canonical Path: " + child.getCanonicalPath());
        System.out.println("Is Absolute: " + new File("/etc/shadow").isAbsolute());
    }
}
