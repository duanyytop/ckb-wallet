package wallet;

public class WalletElement {
    public String masterPrivateKey;
    public String privateKey;
    public String mnemonic;

    public WalletElement(String masterPrivateKey, String privateKey, String mnemonic) {
        this.masterPrivateKey = masterPrivateKey;
        this.privateKey = privateKey;
        this.mnemonic = mnemonic;
    }
}
