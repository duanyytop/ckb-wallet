package transaction;

public class Sender {

    public String privateKey;
    public String address;

    public Sender(String privateKey, String address) {
        this.privateKey = privateKey;
        this.address = address;
    }
}
