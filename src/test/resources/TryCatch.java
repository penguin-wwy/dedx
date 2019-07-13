import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TryCatch {
    public int run(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            if (NextLineSize(reader) > 0) {
                return NextLineSize(reader);
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        } finally {
            System.out.println("Read Line Length.");
        }
        return 0;
    }

    protected static int NextLineSize(BufferedReader reader) throws IOException {
        String line = reader.readLine();
	System.out.println(line.getBytes());
        return line.length();
    }
}
