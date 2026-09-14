import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCrypt;

/** Local provisioning helper. Caller supplies the password over stdin and captures hashes privately. */
class DemoPasswordHasher {
    public static void main(String[] args) throws Exception {
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String password = input.readLine();
        int count = Integer.parseInt(input.readLine());
        if (password == null
                || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || count < 1
                || count > 100) {
            throw new IllegalArgumentException("Invalid local seed input");
        }
        for (int i = 0; i < count; i++) {
            System.out.println(BCrypt.hashpw(password, BCrypt.gensalt(10)));
        }
    }
}
