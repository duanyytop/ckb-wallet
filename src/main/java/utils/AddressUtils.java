package utils;

import org.nervos.ckb.address.Network;
import org.nervos.ckb.transaction.LockUtils;
import org.nervos.ckb.type.Script;
import org.nervos.ckb.utils.address.AddressGenerator;

public class AddressUtils {

    public static String fromPrivateKey(String privateKey, Network network) {
       Script lockScript = LockUtils.generateLockScriptWithPrivateKey(privateKey, Constant.SECP_BLAKE160_CODE_HASH);
       return AddressGenerator.generate(network, lockScript);
    }
}
