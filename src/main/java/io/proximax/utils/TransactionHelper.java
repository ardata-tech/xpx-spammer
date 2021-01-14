package io.proximax.utils;

import io.proximax.sdk.BlockchainApi;
import io.proximax.sdk.FeeCalculationStrategy;
import io.proximax.sdk.TransactionRepository;
import io.proximax.sdk.infrastructure.AccountHttp;
import io.proximax.sdk.infrastructure.MosaicHttp;
import io.proximax.sdk.infrastructure.TransactionHttp;
import io.proximax.sdk.model.account.Account;
import io.proximax.sdk.model.account.AccountInfo;
import io.proximax.sdk.model.account.Address;
import io.proximax.sdk.model.blockchain.NetworkType;
import io.proximax.sdk.model.mosaic.Mosaic;
import io.proximax.sdk.model.mosaic.MosaicId;
import io.proximax.sdk.model.mosaic.MosaicNames;
import io.proximax.sdk.model.mosaic.NetworkCurrencyMosaic;
import io.proximax.sdk.model.transaction.Deadline;
import io.proximax.sdk.model.transaction.PlainMessage;
import io.proximax.sdk.model.transaction.SignedTransaction;
import io.proximax.sdk.model.transaction.TransferTransaction;
import io.proximax.sdk.model.transaction.Message;
import io.proximax.sdk.model.transaction.SecureMessage;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Administrator
 */
public class TransactionHelper {

    public static Mosaic ZERO = NetworkCurrencyMosaic.createAbsolute(BigInteger.ZERO);

    public static String sendMessage(NetworkType networkType, String apiUrl, Account sender, Address recipientAddress, Message message, Mosaic amount, FeeCalculationStrategy fee) throws Exception {
        BlockchainApi blockchainApi = new BlockchainApi(new URL(apiUrl), networkType);
        return sendMessage(blockchainApi, sender, recipientAddress, message, amount, fee);
    }

    public static String sendMessage(BlockchainApi blockchainApi, Account sender, Address recipientAddress, Message message, Mosaic amount, FeeCalculationStrategy fee) throws Exception {
        final TransferTransaction transferTransaction = blockchainApi.transact().transfer()
                .deadline(Deadline.create(1, ChronoUnit.HOURS))
                .to(recipientAddress)
                .mosaics(amount)
                .message(message)
                .feeCalculationStrategy(fee)
                .networkType(blockchainApi.getNetworkType())
                .build();
        final SignedTransaction signedTransaction = blockchainApi.sign(transferTransaction, sender);
        final TransactionRepository transactionHttp = blockchainApi.createTransactionRepository();
        transactionHttp.announce(signedTransaction).toFuture().get();
        //System.out.println("Thread " + Thread.currentThread().getId() + ": " + signedTransaction.getHash());
        return signedTransaction.getHash();
    }

    public static String sendSecureMessage(NetworkType networkType, String apiUrl, Account sender, Account recipient, StringBuffer message) throws Exception {
        return sendMessage(networkType, apiUrl, sender, recipient.getAddress(), SecureMessage.create(sender.getKeyPair().getPrivateKey(),
                recipient.getKeyPair().getPublicKey(), message.toString()), ZERO, FeeCalculationStrategy.ZERO);
    }

    public static String sendPlainMessage(NetworkType networkType, String apiUrl, String senderPrivateKey, Address recipientAddress, String message) throws Exception {
        return sendMessage(networkType, apiUrl, Account.createFromPrivateKey(senderPrivateKey, networkType), recipientAddress, PlainMessage.create(message), ZERO, FeeCalculationStrategy.ZERO);
    }

    public static String sendPlainMessageFee(NetworkType networkType, String apiUrl, Account sender, Account recipient, StringBuffer message, FeeCalculationStrategy fee) throws Exception {
        return sendMessage(networkType, apiUrl, sender, recipient.getAddress(), PlainMessage.create(message.toString()), ZERO, fee);
    }

    public static boolean checkAccount(NetworkType networkType, String apiUrl, String address) {
        System.out.println("API Url: " + apiUrl);
        System.out.println("Address: " + address);
        try {
            BlockchainApi blockchainApi = new BlockchainApi(new URL(apiUrl), networkType);
            BigInteger xpxAmount = getXPXAmount(blockchainApi, Address.createFromRawAddress(address));
            if (xpxAmount.compareTo(BigInteger.ZERO) != 1) {
                System.out.println("XPX Amount: " + xpxAmount);
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public static BigInteger getXPX(BlockchainApi blockchainApi, Address address) {
        try {
            return getXPXAmount(blockchainApi, address);
        } catch (Exception ex) {
        }
        return BigInteger.ZERO;
    }

    public static BigInteger getXPXAmount(BlockchainApi blockchainApi, Address address) throws Exception {
        AccountHttp accountHttp = (AccountHttp) blockchainApi.createAccountRepository();
        AccountInfo accountInfo = accountHttp.getAccountInfo(address).toFuture().get();
        List<MosaicId> mosaicIds = new ArrayList<>();
        List<Mosaic> mosaics = accountInfo.getMosaics();
        for (Mosaic mo : mosaics) {
            mosaicIds.add(new MosaicId(mo.getId().getId()));
        }
        MosaicHttp mosaicHttp = (MosaicHttp) blockchainApi.createMosaicRepository();
        List<MosaicNames> l = mosaicHttp.getMosaicNames(mosaicIds).toFuture().get();
        for (int i = 0; i < l.size(); i++) {
            MosaicNames mo = l.get(i);
            if (mo.getNames().contains(NetworkCurrencyMosaic.MOSAIC_NAMESPACE)) {
                //return mosaics.get(i).getAmount().divide(BigDecimal.valueOf(Math.pow(10, NetworkCurrencyMosaic.DIVISIBILITY)).toBigInteger()).longValue();
                return mosaics.get(i).getAmount();
            }
        }
        return BigInteger.ZERO;
    }

    public static void printMosaics(NetworkType networkType, String apiUrl, String senderPrivateKey) throws Exception {
        System.out.println("API Url: " + apiUrl);
        System.out.println("Private: " + senderPrivateKey);
        final Account account = Account.createFromPrivateKey(senderPrivateKey, networkType);
        BlockchainApi blockchainApi = new BlockchainApi(new URL(apiUrl), networkType);
        AccountHttp accountHttp = (AccountHttp) blockchainApi.createAccountRepository();
        AccountInfo accountInfo = accountHttp.getAccountInfo(account.getAddress()).toFuture().get();
        for (Mosaic mo : accountInfo.getMosaics()) {
            System.out.println("Mosaic: " + mo + " - HEX: " + mo.getIdAsHex());
        }
    }
}
