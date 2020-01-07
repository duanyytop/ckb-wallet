package wallet;

import crypto.Bip32ECKeyPair;
import crypto.MnemonicUtils;
import org.nervos.ckb.utils.Numeric;

import java.security.SecureRandom;

public class WalletUtils {

    private static final String PASSPHRASE = "";
    private static final int HARDENED_BIT = 0x80000000;
    private static final SecureRandom secureRandom = new SecureRandom();

    public static WalletElement generateBip44Wallet() {
        return generateBip44Wallet(Change.EXTERNAL, 0);
    }

    public static WalletElement generateBip44Wallet(Change change, int addressIndex) {
        byte[] initialEntropy = new byte[16];
        secureRandom.nextBytes(initialEntropy);

        String mnemonic = MnemonicUtils.generateMnemonic(initialEntropy);
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, PASSPHRASE);

        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        String masterPrivateKey = Numeric.toHexStringNoPrefix(masterKeypair.getPrivateKey());
        Bip32ECKeyPair bip44Keypair = generateBip44KeyPair(masterKeypair, change, addressIndex);
        String privateKey = Numeric.toHexStringNoPrefix(bip44Keypair.getPrivateKey());

        return new WalletElement(masterPrivateKey, privateKey, mnemonic);
    }

    public static WalletElement importBip44FromMnemonic(String mnemonic) {
        return importBip44FromMnemonic(mnemonic, Change.EXTERNAL, 0);
    }

    public static WalletElement importBip44FromMnemonic(String mnemonic, Change change, int addressIndex) {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, PASSPHRASE);
        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);
        String masterPrivateKey = Numeric.toHexStringNoPrefix(masterKeypair.getPrivateKey());
        Bip32ECKeyPair bip44Keypair = generateBip44KeyPair(masterKeypair, change, addressIndex);
        String privateKey = Numeric.toHexStringNoPrefix(bip44Keypair.getPrivateKey());
        return new WalletElement(masterPrivateKey, privateKey, mnemonic);
    }

    //  m / purpose' / coin_type' / account' / change / address_index
    // For Nervos CKB: m/44'/309'/0'/change /address_index
    private static Bip32ECKeyPair generateBip44KeyPair(Bip32ECKeyPair master, Change change, int addressIndex) {
        int changeInt = change == Change.EXTERNAL ? 0 : 1;
        final int[] path = {44 | HARDENED_BIT, 309 | HARDENED_BIT, HARDENED_BIT, changeInt, addressIndex};
        return Bip32ECKeyPair.deriveKeyPair(master, path);
    }

}
