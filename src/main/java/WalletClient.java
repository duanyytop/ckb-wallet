import org.nervos.ckb.address.Network;
import org.nervos.ckb.service.Api;
import org.nervos.ckb.transaction.LockUtils;
import org.nervos.ckb.type.Script;
import org.nervos.ckb.utils.Numeric;
import org.nervos.ckb.utils.address.AddressGenerator;
import utils.Constant;
import wallet.Change;
import wallet.WalletElement;
import wallet.WalletUtils;

import java.io.IOException;

/**
 * Copyright © 2019 Nervos Foundation. All rights reserved.
 */
public class WalletClient {
    private static Api api;

    static {
        api = new Api("http://localhost:8114", false);
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Tip block number: " + api.getTipBlockNumber().toString());
        createNewWallet();
        importWalletFromMnemonic();
    }

    private static void createNewWallet() {
        WalletElement walletElement = WalletUtils.generateBip44Wallet();
        Script lockScript = LockUtils.generateLockScriptWithPrivateKey(
                walletElement.masterPrivateKey, Constant.SECP_BLAKE160_CODE_HASH);
        String address = AddressGenerator.generate(Network.MAINNET, lockScript);
        System.out.println("The address of new wallet with master private key is: " + address);
    }

    private static void importWalletFromMnemonic() {
        String mnemonic = "wood marble short lunch library category sand income naive tattoo vivid catch";
        WalletElement walletElement = WalletUtils.importBip44FromMnemonic(mnemonic, Change.EXTERNAL, 0);
        Script lockScript = LockUtils.generateLockScriptWithPrivateKey(
                walletElement.privateKey, Constant.SECP_BLAKE160_CODE_HASH);
        String address = AddressGenerator.generate(Network.MAINNET, lockScript);
        System.out.println("The address of the exist wallet imported from mnemonic is: " + address);
    }

}
