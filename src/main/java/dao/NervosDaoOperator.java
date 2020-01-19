package dao;

import org.nervos.ckb.address.Network;
import org.nervos.ckb.crypto.secp256k1.Sign;
import org.nervos.ckb.service.Api;
import org.nervos.ckb.system.SystemContract;
import org.nervos.ckb.system.type.SystemScriptCell;
import org.nervos.ckb.transaction.*;
import org.nervos.ckb.type.Block;
import org.nervos.ckb.type.OutPoint;
import org.nervos.ckb.type.Script;
import org.nervos.ckb.type.Witness;
import org.nervos.ckb.type.cell.CellDep;
import org.nervos.ckb.type.cell.CellInput;
import org.nervos.ckb.type.cell.CellOutput;
import org.nervos.ckb.type.cell.CellWithStatus;
import org.nervos.ckb.type.fixed.UInt64;
import org.nervos.ckb.type.transaction.Transaction;
import org.nervos.ckb.type.transaction.TransactionWithStatus;
import org.nervos.ckb.utils.EpochParser;
import org.nervos.ckb.utils.Numeric;
import org.nervos.ckb.utils.address.AddressGenerator;
import transaction.CollectUtils;
import transaction.NumberUtils;
import transaction.Receiver;
import transaction.ScriptGroupWithPrivateKeys;
import utils.Constant;

import java.io.Console;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Copyright © 2019 Nervos Foundation. All rights reserved. */
public class NervosDaoOperator {
  private static final String NERVOS_DAO_DATA = "0x0000000000000000";
  private static final BigInteger UnitCKB = new BigInteger("100000000");
  private static final int DAO_LOCK_PERIOD_EPOCHS = 180;


  private Api api;
  private String daoPrivateKey;
  private String daoAddress;

  NervosDaoOperator(Api api, String daoPrivateKey, Network network) {
    this.api = api;
    this.daoPrivateKey = daoPrivateKey;
    this.daoAddress = AddressGenerator.generate(network, LockUtils.generateLockScriptWithPrivateKey(daoPrivateKey, Constant.SECP_BLAKE160_CODE_HASH));
  }

  public String getBalance() {
    return new CollectUtils(api).getCapacityWithAddress(daoAddress).divide(UnitCKB).toString(10);
  }

  public Transaction generateDepositingToDaoTx(BigInteger capacity) throws IOException {
    Script type =
        new Script(SystemContract.getSystemNervosDaoCell(api).cellHash, "0x", Script.TYPE);

    CollectUtils txUtils = new CollectUtils(api);

    List<CellOutput> cellOutputs =
        txUtils.generateOutputs(
            Collections.singletonList(new Receiver(daoAddress, capacity)), daoAddress);
    cellOutputs.get(0).type = type;

    List<String> cellOutputsData = Arrays.asList(NERVOS_DAO_DATA, "0x");

    List<ScriptGroupWithPrivateKeys> scriptGroupWithPrivateKeysList = new ArrayList<>();
    TransactionBuilder txBuilder = new TransactionBuilder(api);
    txBuilder.addOutputs(cellOutputs);
    txBuilder.setOutputsData(cellOutputsData);
    txBuilder.addCellDep(
        new CellDep(SystemContract.getSystemNervosDaoCell(api).outPoint, CellDep.CODE));

    // You can get fee rate by rpc or set a simple number
    // BigInteger feeRate = Numeric.toBigInt(api.estimateFeeRate("5").feeRate);
    BigInteger feeRate = BigInteger.valueOf(1024);
    CollectUtils collectUtils = new CollectUtils(api, true);
    CollectResult collectResult =
        collectUtils.collectInputs(
            Collections.singletonList(daoAddress),
            txBuilder.buildTx(),
            feeRate,
            Sign.SIGN_LENGTH * 2);

    // update change output capacity after collecting cells
    cellOutputs.get(cellOutputs.size() - 1).capacity = collectResult.changeCapacity;
    txBuilder.setOutputs(cellOutputs);

    int startIndex = 0;
    for (CellsWithAddress cellsWithAddress : collectResult.cellsWithAddresses) {
      txBuilder.addInputs(cellsWithAddress.inputs);
      for (int i = 0; i < cellsWithAddress.inputs.size(); i++) {
        txBuilder.addWitness(i == 0 ? new Witness(Witness.SIGNATURE_PLACEHOLDER) : "0x");
      }
      scriptGroupWithPrivateKeysList.add(
          new ScriptGroupWithPrivateKeys(
              new ScriptGroup(NumberUtils.regionToList(startIndex, cellsWithAddress.inputs.size())),
              Collections.singletonList(daoPrivateKey)));
      startIndex += cellsWithAddress.inputs.size();
    }

    Secp256k1SighashAllBuilder signBuilder = new Secp256k1SighashAllBuilder(txBuilder.buildTx());

    for (ScriptGroupWithPrivateKeys scriptGroupWithPrivateKeys : scriptGroupWithPrivateKeysList) {
      signBuilder.sign(
          scriptGroupWithPrivateKeys.scriptGroup, scriptGroupWithPrivateKeys.privateKeys.get(0));
    }
    return signBuilder.buildTx();
  }

  public Transaction generateWithdrawingFromDaoTx(OutPoint depositOutPoint)
      throws IOException {
    CellWithStatus cellWithStatus = api.getLiveCell(depositOutPoint, true);
    if (!CellWithStatus.Status.LIVE.getValue().equals(cellWithStatus.status)) {
      throw new IOException("Cell is not yet live!");
    }
    TransactionWithStatus transactionWithStatus = api.getTransaction(depositOutPoint.txHash);
    if (!TransactionWithStatus.Status.COMMITTED
        .getValue()
        .equals(transactionWithStatus.txStatus.status)) {
      throw new IOException("Transaction is not committed yet!");
    }
    Block depositBlock = api.getBlock(transactionWithStatus.txStatus.blockHash);
    BigInteger depositBlockNumber = Numeric.toBigInt(depositBlock.header.number);
    CellOutput cellOutput = cellWithStatus.cell.output;

    String outputData = Numeric.toHexString(new UInt64(depositBlockNumber).toBytes());

    Script lock = LockUtils.generateLockScriptWithAddress(daoAddress);
    CellOutput changeOutput = new CellOutput("0x0", lock);

    List<CellOutput> cellOutputs = Arrays.asList(cellOutput, changeOutput);
    List<String> cellOutputsData = Arrays.asList(outputData, "0x");
    List<String> headerDeps = Collections.singletonList(depositBlock.header.hash);

    List<ScriptGroupWithPrivateKeys> scriptGroupWithPrivateKeysList = new ArrayList<>();
    TransactionBuilder txBuilder = new TransactionBuilder(api);
    txBuilder.addCellDep(
        new CellDep(SystemContract.getSystemNervosDaoCell(api).outPoint, CellDep.CODE));
    txBuilder.setOutputsData(cellOutputsData);
    txBuilder.setHeaderDeps(headerDeps);
    txBuilder.addOutputs(cellOutputs);
    txBuilder.addInput(new CellInput(depositOutPoint, "0x0"));

    // You can get fee rate by rpc or set a simple number
    // BigInteger feeRate = Numeric.toBigInt(api.estimateFeeRate("5").feeRate);
    BigInteger feeRate = BigInteger.valueOf(1024);
    CollectUtils collectUtils = new CollectUtils(api, true);
    CollectResult collectResult =
        collectUtils.collectInputs(
            Collections.singletonList(daoAddress),
            txBuilder.buildTx(),
            feeRate,
            Sign.SIGN_LENGTH * 2);

    // update change output capacity after collecting cells
    cellOutputs.get(cellOutputs.size() - 1).capacity = collectResult.changeCapacity;
    txBuilder.setOutputs(cellOutputs);

    CellsWithAddress cellsWithAddress = collectResult.cellsWithAddresses.get(0);
    txBuilder.setInputs(cellsWithAddress.inputs);
    for (int i = 0; i < cellsWithAddress.inputs.size(); i++) {
      if (i == 0) {
        txBuilder.addWitness(new Witness(Witness.SIGNATURE_PLACEHOLDER));
      } else {
        txBuilder.addWitness("0x");
      }
    }
    ScriptGroup scriptGroup =
        new ScriptGroup(NumberUtils.regionToList(0, cellsWithAddress.inputs.size()));
    scriptGroupWithPrivateKeysList.add(
        new ScriptGroupWithPrivateKeys(scriptGroup, Collections.singletonList(daoPrivateKey)));

    Secp256k1SighashAllBuilder signBuilder = new Secp256k1SighashAllBuilder(txBuilder.buildTx());

    for (ScriptGroupWithPrivateKeys scriptGroupWithPrivateKeys : scriptGroupWithPrivateKeysList) {
      signBuilder.sign(
          scriptGroupWithPrivateKeys.scriptGroup, scriptGroupWithPrivateKeys.privateKeys.get(0));
    }
    return signBuilder.buildTx();
  }

  public Transaction generateClaimingFromDaoTx(
          OutPoint depositOutPoint, OutPoint withdrawingOutPoint, BigInteger fee) throws IOException {
    Script lock = LockUtils.generateLockScriptWithAddress(daoAddress);
    CellWithStatus cellWithStatus = api.getLiveCell(withdrawingOutPoint, true);
    if (!CellWithStatus.Status.LIVE.getValue().equals(cellWithStatus.status)) {
      throw new IOException("Cell is not yet live!");
    }
    TransactionWithStatus transactionWithStatus = api.getTransaction(withdrawingOutPoint.txHash);
    if (!TransactionWithStatus.Status.COMMITTED
        .getValue()
        .equals(transactionWithStatus.txStatus.status)) {
      throw new IOException("Transaction is not committed yet!");
    }

    BigInteger depositBlockNumber =
        new UInt64(Numeric.hexStringToByteArray(cellWithStatus.cell.data.content)).getValue();
    Block depositBlock = api.getBlockByNumber(Numeric.toHexStringWithPrefix(depositBlockNumber));
    EpochParser.EpochParams depositEpoch = EpochParser.parse(depositBlock.header.epoch);

    Block withdrawBlock = api.getBlock(transactionWithStatus.txStatus.blockHash);
    EpochParser.EpochParams withdrawEpoch = EpochParser.parse(withdrawBlock.header.epoch);

    long withdrawFraction = withdrawEpoch.index * depositEpoch.length;
    long depositFraction = depositEpoch.index * withdrawEpoch.length;
    long depositedEpochs = withdrawEpoch.number - depositEpoch.number;
    if (withdrawFraction > depositFraction) {
      depositedEpochs += 1;
    }
    long lockEpochs =
        (depositedEpochs + (DAO_LOCK_PERIOD_EPOCHS - 1))
            / DAO_LOCK_PERIOD_EPOCHS
            * DAO_LOCK_PERIOD_EPOCHS;
    long minimalSinceEpochNumber = depositEpoch.number + lockEpochs;
    long minimalSinceEpochIndex = depositEpoch.index;
    long minimalSinceEpochLength = depositEpoch.length;

    String minimalSince =
        EpochParser.parse(minimalSinceEpochLength, minimalSinceEpochIndex, minimalSinceEpochNumber);
    String outputCapacity =
        api.calculateDaoMaximumWithdraw(depositOutPoint, withdrawBlock.header.hash);

    CellOutput cellOutput =
        new CellOutput(
            Numeric.toHexStringWithPrefix(Numeric.toBigInt(outputCapacity).subtract(fee)), lock);

    SystemScriptCell secpCell = SystemContract.getSystemSecpCell(api);
    SystemScriptCell nervosDaoCell = SystemContract.getSystemNervosDaoCell(api);

    Transaction tx =
        new Transaction(
            "0x0",
            Arrays.asList(
                new CellDep(secpCell.outPoint, CellDep.DEP_GROUP),
                new CellDep(nervosDaoCell.outPoint)),
            Arrays.asList(depositBlock.header.hash, withdrawBlock.header.hash),
            Collections.singletonList(new CellInput(withdrawingOutPoint, minimalSince)),
            Collections.singletonList(cellOutput),
            Collections.singletonList("0x"),
            Collections.singletonList(new Witness("", NERVOS_DAO_DATA, "")));

    return tx.sign(Numeric.toBigInt(daoPrivateKey));
  }
}
