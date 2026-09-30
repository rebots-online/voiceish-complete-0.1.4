import java.util.Base64;
import com.voiceish.sim.Crypto;

// Calls the actual Android byte-level crypto implementation on the JVM.
// No phone, network, or SMS access.
public final class CryptoInterop {
    public static void main(String[] args) throws Exception {
        byte[] key = Base64.getDecoder().decode(args[1]);
        byte[] iv = Base64.getDecoder().decode(args[2]);
        boolean encrypt = args[0].equals("encrypt");
        String direction = args[3].replace("voiceish-sim:v1:", "");
        byte[] input = Base64.getDecoder().decode(args[4]);
        byte[] result = encrypt ? Crypto.encrypt(key, iv, input, direction) : Crypto.decrypt(key, iv, input, direction);
        System.out.print(Base64.getEncoder().encodeToString(result));
    }
}
