# ckb-wallet
ckb-wallet is a ckb wallet demo which support creating new HD wallet, importing HD wallet from mnemonic, 
fetching balance and sending capacity to others.

ckb-wallet is just an open source ckb wallet demo, *NOT A PRODUCTION*. You can create your wallet(
including master private key, child private keys and mnemonic) and also import wallet from mnemonic.

ckb-wallet generates child key and address with path(m/44'/309'/0'/0 /0) whose coin type of ckb is 309, 
and address account number is 0, and change is 0 and address index is 0. You can get more information about 
HD wallet path from [BIP44](https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki).

## Done

- Creating HD wallet
- Importing HD wallet from mnemonic
- Fetching balance with address
- Sending capacity to mainnet or testnet addresses
- Nervos DAO deposit and withdraw

