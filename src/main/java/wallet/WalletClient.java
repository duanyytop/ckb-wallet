package wallet;

import org.nervos.ckb.address.Network;
import org.nervos.ckb.crypto.secp256k1.Sign;
import org.nervos.ckb.service.Api;
import org.nervos.ckb.transaction.*;
import org.nervos.ckb.type.Script;
import org.nervos.ckb.type.Witness;
import org.nervos.ckb.type.cell.CellOutput;
import org.nervos.ckb.utils.Utils;
import org.nervos.ckb.utils.address.AddressGenerator;
import transaction.*;
import utils.AddressUtils;
import utils.Constant;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Copyright © 2019 Nervos Foundation. All rights reserved.
 */
public class WalletClient {
    private static Api api;
    private static Network network;

    static {
        api = new Api("http://localhost:8114", false);
        network = Network.TESTNET;
    }

    public static void main(String[] args) throws IOException {
        String tipBlockNumber = api.getTipBlockNumber().toString();
        System.out.println("Tip block number: " + tipBlockNumber);

        createNewWallet();

        importWalletFromMnemonic();

        List<String> privateKeys = Collections.singletonList("e79f3207ea4980b7fed79956d5934249ceac4751a4fae01a0f7c4a96884bc4e3");
        List<Receiver> receivers =
                Arrays.asList(
                        new Receiver("ckt1qyqxgp7za7dajm5wzjkye52asc8fxvvqy9eqlhp82g", Utils.ckbToShannon(800)),
                        new Receiver("ckt1qyqtnz38fht9nvmrfdeunrhdtp29n0gagkps4duhek", Utils.ckbToShannon(900)),
                        new Receiver("ckt1qyqxvnycu7tdtyuejn3mmcnl4y09muxz8c3s2ewjd4", Utils.ckbToShannon(1000)));
        String hash = sendCapacity(privateKeys, receivers);
        System.out.println("Tx hash: " + hash);
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

    private static String sendCapacity(
            List<String> privateKeys, List<Receiver> receivers)
            throws IOException {
        return sendCapacity(privateKeys, receivers, AddressUtils.fromPrivateKey(privateKeys.get(0), network));
    }

    private static String sendCapacity(
            List<String> privateKeys, List<Receiver> receivers, String changeAddress)
            throws IOException {
        List<ScriptGroupWithPrivateKeys> scriptGroupWithPrivateKeysList = new ArrayList<>();
        TransactionBuilder txBuilder = new TransactionBuilder(api);
        CollectUtils txUtils = new CollectUtils(api);

        List<CellOutput> cellOutputs = txUtils.generateOutputs(receivers, changeAddress);
        txBuilder.addOutputs(cellOutputs);

        // You can get fee rate by rpc or set a simple number
        // BigInteger feeRate = Numeric.toBigInt(api.estimateFeeRate("5").feeRate);
        BigInteger feeRate = BigInteger.valueOf(1024);

        List<String> senderAddresses = privateKeys.stream().map(privateKey -> AddressUtils.fromPrivateKey(privateKey, network)).collect(Collectors.toList());
        // initial_length = 2 * secp256k1_signature_byte.length
        CollectResult collectResult =
                txUtils.collectInputs(senderAddresses, txBuilder.buildTx(), feeRate, Sign.SIGN_LENGTH * 2);

        // update change output capacity after collecting cells
        cellOutputs.get(cellOutputs.size() - 1).capacity = collectResult.changeCapacity;
        txBuilder.setOutputs(cellOutputs);

        int startIndex = 0;
        for (CellsWithAddress cellsWithAddress : collectResult.cellsWithAddresses) {
            txBuilder.addInputs(cellsWithAddress.inputs);
            for (int i = 0; i < cellsWithAddress.inputs.size(); i++) {
                txBuilder.addWitness(i == 0 ? new Witness(Witness.SIGNATURE_PLACEHOLDER) : "0x");
            }
            if (cellsWithAddress.inputs.size() > 0) {
                scriptGroupWithPrivateKeysList.add(
                        new ScriptGroupWithPrivateKeys(
                                new ScriptGroup(
                                        NumberUtils.regionToList(startIndex, cellsWithAddress.inputs.size())),
                                privateKeys.stream()
                                        .filter(privateKey -> AddressUtils.fromPrivateKey(privateKey, network).equals(cellsWithAddress.address))
                                        .collect(Collectors.toList())));
                startIndex += cellsWithAddress.inputs.size();
            }
        }

        Secp256k1SighashAllBuilder signBuilder = new Secp256k1SighashAllBuilder(txBuilder.buildTx());

        for (ScriptGroupWithPrivateKeys scriptGroupWithPrivateKeys : scriptGroupWithPrivateKeysList) {
            signBuilder.sign(
                    scriptGroupWithPrivateKeys.scriptGroup, scriptGroupWithPrivateKeys.privateKeys.get(0));
        }

        return api.sendTransaction(signBuilder.buildTx());
    }

}
