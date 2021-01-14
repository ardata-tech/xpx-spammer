package io.proximax.test;

import io.proximax.sdk.BlockchainApi;
import io.proximax.sdk.FeeCalculationStrategy;
import io.proximax.sdk.ListenerRepository;
import io.proximax.sdk.TransactionRepository;
import io.proximax.sdk.infrastructure.AccountHttp;
import io.proximax.sdk.infrastructure.MosaicHttp;
import io.proximax.sdk.model.account.Account;
import io.proximax.sdk.model.account.AccountInfo;
import io.proximax.sdk.model.account.Address;
import io.proximax.sdk.model.mosaic.Mosaic;
import io.proximax.sdk.model.mosaic.MosaicId;
import io.proximax.sdk.model.mosaic.MosaicNames;
import io.proximax.sdk.model.mosaic.NetworkCurrencyMosaic;
import io.proximax.sdk.model.transaction.Deadline;
import io.proximax.sdk.model.transaction.Message;
import io.proximax.sdk.model.transaction.PlainMessage;
import io.proximax.sdk.model.transaction.SecureMessage;
import io.proximax.sdk.model.transaction.SignedTransaction;
import io.proximax.sdk.model.transaction.TransferTransaction;
import io.proximax.utils.GeneralUtils;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.LinkedList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 *
 * @author Administrator
 */
public class RunnerTxn implements Runnable {

    public static Mosaic MOSAIC_ZERO = NetworkCurrencyMosaic.createAbsolute(BigInteger.ZERO);
    /**
     * logger
     */
    private static final Logger logger = LoggerFactory.getLogger(RunnerTxn.class);
    public static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");
    private Account topSender = null;
    private final BigInteger xpxAmount;
    private final FeeCalculationStrategy fee;
    private String processId = "Process 000";
    private StringBuffer msg;
    BlockchainApi api = null;
    protected ListenerRepository listener;
    protected TransactionRepository transactionHttp = null;
    protected Collection<Disposable> disposables = new LinkedList<>();
    protected BigInteger maxFee = BigInteger.ZERO;
    protected long txnDelay = 30000;
    protected long numTxns = 1440;
    protected List<String> backupNodes;
    protected List<String> badNodes = new ArrayList<>();

    public RunnerTxn(int id, BlockchainApi api, Account topSender, BigInteger xpxAmount, FeeCalculationStrategy fee, StringBuffer msg, int numTxns, long txnDelay, String backupNodes) {
        this.api = api;
        this.xpxAmount = xpxAmount;
        this.msg = msg;
        this.topSender = topSender;
        this.fee = fee;
        this.numTxns = numTxns;
        this.txnDelay = txnDelay;
        this.processId = String.format("Process %03d", id);
        this.backupNodes = Arrays.asList(backupNodes.split(";"));
    }

    private void logAccount(Account recipient) {
        try {
            String str = GeneralUtils.getAccount(recipient) + "\n";
            FileUtils.writeStringToFile(new File(String.format("accounts_%s_%s.cvs", api.getNetworkType().toString(), SDF.format(new Date()))), str, "utf-8", true);
        } catch (IOException ex) {
        }
    }

    private BlockchainApi getNextApi() {
        do {
            int len = backupNodes.size();
            if (len > 0) {
                int rand = (int) (len * Math.random());
                String strApi = backupNodes.get(rand);
                while (strApi.equals(api.getUrl().toString())) {
                    rand = (int) (len * Math.random());
                    strApi = backupNodes.get(rand);
                }
                try {
                    return new BlockchainApi(new URL(strApi), api.getNetworkType());
                } catch (Exception ex) {
                    logger.error("{} -{}", processId, ex.getMessage());
                    backupNodes.remove(strApi);
                }
            } else {
                break;
            }
        } while (true);
        return null;
    }

    private BlockchainApi getNextApi(String oldNode) {
        backupNodes.remove(oldNode);
        if (!badNodes.contains(oldNode)) {
            badNodes.add(oldNode);
        }
        int len = backupNodes.size();
        if (len > 0) {
            try {
                int rand = (int) (len * Math.random());
                String strApi = backupNodes.get(rand);
                return new BlockchainApi(new URL(strApi), api.getNetworkType());
            } catch (Exception ex) {
            }
        }
        return null;
    }

    private void logFailedAccount(Account recipient) {
        try {
            String str = GeneralUtils.getAccount(recipient) + "\n";
            FileUtils.writeStringToFile(new File(String.format("failed_accounts_%s_%s.cvs", api.getNetworkType().toString(), SDF.format(new Date()))), str, "utf-8", true);
        } catch (IOException ex) {
        }
    }

    public BigInteger getMaxFee() {
        return maxFee;
    }

    public String getSender() {
        return topSender.getPrivateKey();
    }

    private long totalTxnFail = 0;
    private long totalTxnFee = 0;

    public long getTxnSuccess() {
        return getTxnFee();
    }

    public long getTxnFailed() {
        return totalTxnFail;
    }

    public long getTxnFee() {
        return totalTxnFee;
    }

    public String sendRandomMessage(Account sender, Address recipient) throws Exception {
        String message = RandomStringUtils.randomAlphabetic(20);
        return sendMessage(sender, recipient, PlainMessage.create(message), 5);
    }

    public void init() throws Exception {
        transactionHttp = api.createTransactionRepository();
        // prepare listener1
        listener = api.createListener();
        listener.open().get();
    }

    public String sendXPX(Account sender, Account recipient, BigInteger xpx) throws Exception {
        Message message = SecureMessage.create(sender.getKeyPair().getPrivateKey(), recipient.getKeyPair().getPublicKey(), msg.toString());
        BigInteger senderXPX = getNetworkCurrencyMosaic(sender.getAddress()).getAmount();
        if (senderXPX.compareTo(xpx) != 1) {
            throw new Exception("Failure_Core_Insufficient_Balance");
        }
        return sendMessage(sender, recipient.getAddress(), message, NetworkCurrencyMosaic.createAbsolute(xpx), fee, 5);
    }

    public final static long TXN_TIMEOUT = 30000L;

    private int nErrors = 0;

    protected int runProcess() {
        int txnFee = 0;
        Account sender = null, recipient = null;
        try {
            sender = Account.generateNewAccount(api.getNetworkType());
            logger.info("{} -Create SENDER account... {}", processId, GeneralUtils.getAccount(sender));
            signup(sender.getAddress());
            maxFee = getMaxFeeCalculation(topSender, sender);
            logger.info("{} -Calculation Max Fee: {}", processId, GeneralUtils.mXPX2XPXStr(maxFee));
            logger.info("Txn {} -{} send {} xpx to {}...", getTxnFee(), topSender.getAddress().plain(), GeneralUtils.mXPX2XPXStr(xpxAmount.add(maxFee.multiply(BigInteger.valueOf(3)))), sender.getAddress().plain());
            sendXPX(topSender, sender, xpxAmount.add(maxFee.multiply(BigInteger.valueOf(3))));
            txnFee = 1;
            this.totalTxnFee += 1;
            long sec1 = System.currentTimeMillis();
            logAccount(sender);
            while (!checkAccount(sender.getAddress())) {
                GeneralUtils.sleep(5000L);
            }
            recipient = Account.generateNewAccount(api.getNetworkType());
            logger.info("{} -Create RECIPIENT account... {}", processId, GeneralUtils.getAccount(recipient));
            sleepForAWhile(sec1, txnDelay);
            long sec2 = System.currentTimeMillis();
            logger.info("Txn {} -{} send {} xpx to {}... {}ms", getTxnFee(), sender.getAddress().plain(), GeneralUtils.mXPX2XPXStr(xpxAmount.add(maxFee)), recipient.getAddress().plain(), sec2 - sec1);
            sendXPX(sender, recipient, xpxAmount.add(maxFee));
            txnFee = 2;
            this.totalTxnFee += 1;
            sec1 = System.currentTimeMillis();
            logAccount(recipient);
            while (!checkAccount(recipient.getAddress())) {
                GeneralUtils.sleep(5000L);
            }
            sleepForAWhile(sec1, txnDelay);
            sec2 = System.currentTimeMillis();
            logger.info("Txn {} -{} send back {} xpx to {}...{} ms", getTxnFee(), recipient.getAddress().plain(), GeneralUtils.mXPX2XPXStr(xpxAmount), sender.getAddress().plain(), sec2 - sec1);
            sendXPX(recipient, sender, xpxAmount);
            txnFee = 3;
            this.totalTxnFee += 1;
            sec1 = System.currentTimeMillis();
            while (!checkAccount(sender.getAddress())) {
                GeneralUtils.sleep(5000L);
            }
            sleepForAWhile(sec1, txnDelay);
            sec2 = System.currentTimeMillis();
            logger.info("Txn {} -{} send back {} xpx to {}...{} ms", getTxnFee(), sender.getAddress().plain(), GeneralUtils.mXPX2XPXStr(xpxAmount), topSender.getAddress().plain(), sec2 - sec1);
            sendXPX(sender, topSender, xpxAmount);
            txnFee = 4;
            this.totalTxnFee += 1;
            nErrors = 0;
        } catch (Exception ex) {
            nErrors++;
            if (txnFee == 1 || txnFee == 3) {
                logFailedAccount(sender);
            } else if (txnFee == 2) {
                logFailedAccount(sender);
                logFailedAccount(recipient);
            }
            logger.error("Txn {} -{}", getTxnFee(), ex.getMessage());
            BlockchainApi api = getNextApi();
            if (api != null) {
                nErrors = 0;
                this.api = api;
                logger.info("================================================");
                logger.info("Change New BlockchainApi  : {} | {}", api.getNetworkType(), api.getUrl());
                logger.info("================================================");
            }
        }
        this.totalTxnFail += (4 - txnFee);
        //this.totalTxnFee += txnFee;
        return (4 - txnFee);
    }

    @Override
    public void run() {
        try {
            init();
            int nProcess = (int) numTxns / 4;
            if (numTxns % 4 != 0) {
                nProcess += 1;
                numTxns = 4 * nProcess;
            }
            for (int i = 0; i < nProcess; i++) {
                runProcess();
                if (nErrors == 3 || bKill) {
                    break;
                }
            }
        } catch (Exception ex) {
        }
        cleanup();
        isDone = true;
    }

    private boolean isDone = false;
    private boolean bKill = false;

    public void killProcess() {
        bKill = true;
    }

    public boolean isDone() {
        return isDone;
    }

    /**
     * convenience sleep needed to work around server listener1 synchronization
     * issues
     *
     * @param startTime
     * @param duration
     */
    protected void sleepForAWhile(long startTime, long duration) {
        long delay = (duration * 84) / 100;
        while (true) {
            GeneralUtils.sleep(1000);
            if (System.currentTimeMillis() - startTime >= delay) {
                break;
            }
        }
    }

    public String sendMessage(Account sender, Address recipient, Message message, long timeout) throws Exception {
        final TransferTransaction transferTransaction = api.transact().transfer()
                .deadline(Deadline.create(1, ChronoUnit.HOURS))
                .to(recipient)
                .message(message)
                .networkType(api.getNetworkType())
                .build();
        final SignedTransaction signedTransaction = api.sign(transferTransaction, sender);
        logger.info("{} -Transfer announced. {} - Hash: {}", processId, transactionHttp.announce(signedTransaction).toFuture().get(), signedTransaction.getHash());
        //logger.info("{} -Transfer announced. {}", transactionHttp.announce(signedTransaction).blockingFirst());
        logger.info("{} -Transfer done. {}", processId, listener.confirmed(recipient).timeout(timeout, TimeUnit.MINUTES).blockingFirst());
        return signedTransaction.getHash();
    }

    public String sendMessage(Account sender, Address recipient, Message message, FeeCalculationStrategy fee, long timeout) throws Exception {
        final TransferTransaction transferTransaction = api.transact().transfer()
                .deadline(Deadline.create(1, ChronoUnit.HOURS))
                .to(recipient)
                .message(message)
                .feeCalculationStrategy(fee)
                .networkType(api.getNetworkType())
                .build();
        final SignedTransaction signedTransaction = api.sign(transferTransaction, sender);
        logger.info("{} -Transfer announced. {} - Hash: {}", processId, transactionHttp.announce(signedTransaction).toFuture().get(), signedTransaction.getHash());
        //logger.info("{} -Transfer announced. {}", transactionHttp.announce(signedTransaction).blockingFirst());
        logger.info("{} -Transfer done. {}", processId, listener.confirmed(recipient).timeout(timeout, TimeUnit.MINUTES).blockingFirst());
        return signedTransaction.getHash();
    }

    public String sendMessage(Account sender, Address recipient, Message message, Mosaic amount, long timeout) throws Exception {
        final TransferTransaction transferTransaction = api.transact().transfer()
                .deadline(Deadline.create(1, ChronoUnit.HOURS))
                .to(recipient)
                .mosaics(amount)
                .message(message)
                .networkType(api.getNetworkType())
                .build();
        final SignedTransaction signedTransaction = api.sign(transferTransaction, sender);
        logger.info("{} -Transfer announced. {} - Hash: {}", processId, transactionHttp.announce(signedTransaction).toFuture().get(), signedTransaction.getHash());
        //logger.info("{} -Transfer announced. {}", transactionHttp.announce(signedTransaction).blockingFirst());
        logger.info("{} -Transfer done. {}", processId, listener.confirmed(recipient).timeout(timeout, TimeUnit.MINUTES).blockingFirst());
        return signedTransaction.getHash();
    }

    public BigInteger getMaxFeeCalculation(Account sender, Account recipient) throws Exception {
        Message message = SecureMessage.create(sender.getKeyPair().getPrivateKey(), recipient.getKeyPair().getPublicKey(), msg.toString());
        return getMaxFeeCalculation(recipient.getAddress(), message, NetworkCurrencyMosaic.createAbsolute(xpxAmount), fee);
    }

    public BigInteger getMaxFeeCalculation(Address recipient, Message message, Mosaic amount, FeeCalculationStrategy fee) throws Exception {
        TransferTransaction transferTransaction = api.transact().transfer()
                .deadline(Deadline.create(1, ChronoUnit.HOURS))
                .to(recipient)
                .mosaics(amount)
                .message(message)
                .feeCalculationStrategy(fee)
                .networkType(api.getNetworkType())
                .build();
        return transferTransaction.getMaxFee();
    }

    public String sendMessage(Account sender, Address recipient, Message message, Mosaic amount, FeeCalculationStrategy fee, long timeout) throws Exception {
        TransferTransaction transferTransaction = api.transact().transfer()
                .deadline(Deadline.create(1, ChronoUnit.HOURS))
                .to(recipient)
                .mosaics(amount)
                .message(message)
                .feeCalculationStrategy(fee)
                .networkType(api.getNetworkType())
                .build();
        final SignedTransaction signedTransaction = api.sign(transferTransaction, sender);
        //logger.info("Txn {} -Transfer announced. {} - Hash: {}", totalTxnFee, transactionHttp.announce(signedTransaction).toFuture().get(), signedTransaction.getHash());
        //logger.info("{} -Transfer announced. {} - Hash: {}", processId, transactionHttp.announce(signedTransaction).blockingFirst(), signedTransaction.getHash());                
        //logger.info("Txn {} -Transfer done. {}", totalTxnFee, listener.confirmed(recipient).timeout(timeout, TimeUnit.MINUTES).blockingFirst());
        logger.info("Txn {} -Transfer announced. {} - Hash: {}", getTxnFee(), transactionHttp.announce(signedTransaction).blockingFirst().getMessage(), signedTransaction.getHash());
        logger.info("Txn {} -Transfer done. - Hash: {}", getTxnFee(), listener.confirmed(recipient).timeout(timeout, TimeUnit.MINUTES).blockingFirst().getTransactionInfo().get().getHash().get());
        return signedTransaction.getHash();
    }

    public boolean checkAccount(Address address) throws Exception {
        Mosaic m = getNetworkCurrencyMosaic(address);
        logger.info("{} -Address: {} - XPX Amount: {}", processId, address.plain(), GeneralUtils.mXPX2XPXStr(m.getAmount()));
        return (m.getAmount().compareTo(BigInteger.ZERO) == 1);
    }

    public long getXPXAmount(Address address) throws Exception {
        AccountHttp accountHttp = (AccountHttp) api.createAccountRepository();
        AccountInfo accountInfo = accountHttp.getAccountInfo(address).toFuture().get();
        List<MosaicId> mosaicIds = new ArrayList<>();
        List<Mosaic> mosaics = accountInfo.getMosaics();
        for (Mosaic mo : mosaics) {
            mosaicIds.add(new MosaicId(mo.getId().getId()));
        }
        MosaicHttp mosaicHttp = (MosaicHttp) api.createMosaicRepository();
        List<MosaicNames> l = mosaicHttp.getMosaicNames(mosaicIds).toFuture().get();
        for (int i = 0; i < l.size(); i++) {
            MosaicNames mo = l.get(i);
            if (mo.getNames().contains(NetworkCurrencyMosaic.MOSAIC_NAMESPACE)) {
                return mosaics.get(i).getAmount().divide(BigDecimal.valueOf(Math.pow(10, NetworkCurrencyMosaic.DIVISIBILITY)).toBigInteger()).longValue();
            }
        }
        return 0;
    }

    public Mosaic getNetworkCurrencyMosaic(Address address) throws Exception {
        AccountHttp accountHttp = (AccountHttp) api.createAccountRepository();
        AccountInfo accountInfo = accountHttp.getAccountInfo(address).toFuture().get();
        List<MosaicId> mosaicIds = new ArrayList<>();
        List<Mosaic> mosaics = accountInfo.getMosaics();
        for (Mosaic mo : mosaics) {
            mosaicIds.add(new MosaicId(mo.getId().getId()));
        }
        MosaicHttp mosaicHttp = (MosaicHttp) api.createMosaicRepository();
        List<MosaicNames> l = mosaicHttp.getMosaicNames(mosaicIds).toFuture().get();
        for (int i = 0; i < l.size(); i++) {
            MosaicNames mo = l.get(i);
            if (mo.getNames().contains(NetworkCurrencyMosaic.MOSAIC_NAMESPACE)) {
                return mosaics.get(i);
            }
        }
        return NetworkCurrencyMosaic.createAbsolute(BigInteger.ZERO);
    }

    /**
     * subscribe to all channels for the address
     *
     * @param listener
     * @param addr
     */
    protected void signup(Address addr) {
        // output nothing by default
        Consumer<? super Object> logAll = (obj) -> logger.debug("{} -Listener fired: {}", processId, obj);
        Consumer<? super Object> logStatus = (obj) -> logger.warn("{} -Status fired: {}", processId, obj);
        disposables.add(listener.status(addr).subscribe(logStatus, logStatus));
        disposables.add(listener.unconfirmedAdded(addr).subscribe(logAll, logAll));
        disposables.add(listener.unconfirmedRemoved(addr).subscribe(logAll, logAll));
        disposables.add(listener.aggregateBondedAdded(addr).subscribe(logAll, logAll));
        disposables.add(listener.aggregateBondedRemoved(addr).subscribe(logAll, logAll));
        disposables.add(listener.cosignatureAdded(addr).subscribe(logAll, logAll));
        disposables.add(listener.confirmed(addr).subscribe(logAll, logAll));
    }

    void cleanup() {
        logger.info("{} -Cleaning up", processId);
        listener.close();
        disposables.stream().forEach(Disposable::dispose);
        disposables.clear();
    }

}
