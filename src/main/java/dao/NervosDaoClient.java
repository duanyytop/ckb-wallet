package dao;

import org.nervos.ckb.address.Network;
import org.nervos.ckb.service.Api;
import org.nervos.ckb.type.OutPoint;
import org.nervos.ckb.type.transaction.Transaction;
import org.nervos.ckb.type.transaction.TransactionWithStatus;
import org.nervos.ckb.utils.Utils;

/**
 * Copyright © 2020 Nervos Foundation. All rights reserved.
 */
public class NervosDaoClient {
    private static final String NODE_URL = "http://localhost:8114";

    private static NervosDaoOperator nervosDaoOperator;
    private static Api api;

    public static void main(String[] args) throws Exception {

        init();

        String depositTxHash = deposit();

        String withdrawTxHash = startWithdraw(depositTxHash);

        withdraw(withdrawTxHash);
    }

    private static void init() {
        api = new Api(NODE_URL, false);
        String daoPrivateKey = "08730a367dfabcadb805d69e0e613558d5160eb8bab9d6e326980c2c46a05db2";
        nervosDaoOperator = new NervosDaoOperator(api, daoPrivateKey, Network.MAINNET);
    }

    private static String deposit() throws Exception {
        System.out.println("Before depositing, balance: " + nervosDaoOperator.getBalance() + " CKB");
        Transaction transaction = nervosDaoOperator.generateDepositingToDaoTx(Utils.ckbToShannon(1000));
        String txHash = api.sendTransaction(transaction);
        System.out.println("Nervos DAO deposit tx hash: " + txHash);
        // Waiting some time to make tx into blockchain
        System.out.println("After depositing, balance: " + nervosDaoOperator.getBalance() + " CKB");
        return txHash;
    }

    private static String startWithdraw(String depositTxHash) throws Exception {
        // Nervos DAO withdraw phase1 must be after 4 epoch of depositing transaction
        OutPoint depositOutPoint = new OutPoint(depositTxHash, "0x0");
        Transaction transaction = nervosDaoOperator.generateWithdrawingFromDaoTx(depositOutPoint);
        String txHash = api.sendTransaction(transaction);
        System.out.println("Nervos DAO withdraw phase1 tx hash: " + txHash);
        return txHash;
    }

    private static void withdraw(String withdrawTxHash) throws Exception {
        // Nervos DAO withdraw phase2 must be after 180 epoch of depositing transaction
        TransactionWithStatus withdrawTx = api.getTransaction(withdrawTxHash);
        OutPoint depositOutPoint = withdrawTx.transaction.inputs.get(0).previousOutput;
        OutPoint withdrawOutPoint = new OutPoint(withdrawTxHash, "0x0");
        Transaction transaction =
                nervosDaoOperator.generateClaimingFromDaoTx(depositOutPoint, withdrawOutPoint, Utils.ckbToShannon(0.01));
        String txHash = api.sendTransaction(transaction);
        System.out.println("Nervos DAO withdraw phase2 tx hash: " + txHash);
        // Waiting some time to make tx into blockchain
        System.out.println("After withdrawing, balance: " + nervosDaoOperator.getBalance() + " CKB");
    }

}
