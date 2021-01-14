package io.proximax.test;

import io.proximax.sdk.BlockchainApi;
import io.proximax.sdk.FeeCalculationStrategy;
import io.proximax.sdk.model.account.Account;
import io.proximax.sdk.model.blockchain.NetworkType;
import io.proximax.sdk.model.mosaic.Mosaic;
import io.proximax.sdk.model.mosaic.NetworkCurrencyMosaic;
import io.proximax.utils.GeneralUtils;
import io.proximax.utils.TransactionHelper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Administrator
 */
public class MainTxn {

    /**
     * logger
     */
    private static final Logger logger = LoggerFactory.getLogger(MainTxn.class);
    private Properties prop = new Properties();
    private Account sender;
    private FeeCalculationStrategy fee = FeeCalculationStrategy.ZERO;
    private Mosaic xpx = NetworkCurrencyMosaic.ONE;
    private StringBuffer msg;
    private BigInteger xpxStart = BigInteger.ZERO;
    protected BlockchainApi api;
    private long txnDelay = 1;
    private int numTxns;
    private String backupNodes = "";

    public MainTxn(int numTxns) throws Exception {
        System.out.println("Load configure: " + System.getProperty("user.dir") + File.separator + "proximax.properties");
        InputStream input = new FileInputStream("proximax.properties");
        // load a properties file
        prop.load(input);
        api = new BlockchainApi(new URL(getApiUrl()), getNetworkType());
        sender = Account.createFromPrivateKey(getSender(), api.getNetworkType());
        msg = getMsg();
        fee = getFee();
        xpx = getXPX();
        txnDelay = getTxnDelay();
        backupNodes = prop.getProperty("BackupNodes", "");
        this.numTxns = numTxns;
        if (numTxns > 0 && numTxns % 4 != 0) {
            this.numTxns = 4 * (numTxns / 4 + 1);
        }
        logger.info("BlockchainApi  : {} | {}", api.getNetworkType(), api.getUrl());
        logger.info("Sender         : {}", GeneralUtils.getAccount(sender));
        logger.info("Fee            : {}", fee);
        logger.info("Msg lenghth    : {}", msg.length());
        logger.info("Send XPX       : {}", GeneralUtils.mXPX2XPXStr(xpx.getAmount()));
        logger.info("Num Txns       : {}", this.numTxns);
        logger.info("Txn Delay      : {}", prop.getProperty("txndelay", "1"));
        xpxStart = TransactionHelper.getXPXAmount(api, sender.getAddress());
        logger.info("Wallet XPX     : {}\n", GeneralUtils.mXPX2XPXStr(xpxStart));
    }

    private NetworkType getNetworkType() {
        return NetworkType.valueOf(prop.getProperty("network", "TEST_NET"));
    }

    private String getApiUrl() {
        return prop.getProperty("node", "");
    }

    private String getSender() {
        return prop.getProperty("sender", "");
    }

    private long getTxnDelay() {
        return GeneralUtils.parseTime(prop.getProperty("txndelay", "1"), 1);
    }

    private StringBuffer getMsg() {
        return new StringBuffer(prop.getProperty("msg", "Welcome Msg Txn"));
    }

    private Mosaic getXPX() {
        return NetworkCurrencyMosaic.createRelative(BigDecimal.valueOf(Long.parseLong(prop.getProperty("xpx", "1"))));
    }

    private FeeCalculationStrategy getFee() {
        String fee = prop.getProperty("FeeCalculationStrategy", "high");
        if (fee.compareToIgnoreCase("high") == 0) {
            return FeeCalculationStrategy.HIGH;
        } else if (fee.compareToIgnoreCase("medium") == 0) {
            return FeeCalculationStrategy.MEDIUM;
        } else if (fee.compareToIgnoreCase("low") == 0) {
            return FeeCalculationStrategy.LOW;
        }
        return FeeCalculationStrategy.ZERO;
    }

    private void runProcess() throws Exception {
        if (numTxns > 0) {
            long startTime = System.currentTimeMillis();
            long nFailed = 0;
            long nTxnFee = 0;
            MainRunner r = new MainRunner(1, numTxns, txnDelay, api, sender, msg, xpx.getAmount(), fee, backupNodes);
            new Thread(r).start();
            while (!r.isDone()) {
                GeneralUtils.sleep(5000L);
            }
            nFailed = r.getFailed();
            nTxnFee = r.getTxnFee();
            BigInteger walletXPX = TransactionHelper.getXPX(api, sender.getAddress());
            logger.info("================================================");
            logger.info("Total time: {}s - Fail {} Success {} txns", (System.currentTimeMillis() - startTime) / 1000, nFailed, nTxnFee);
            logger.info("Start XPX : {}", GeneralUtils.mXPX2XPXStr(xpxStart));
            logger.info("End   XPX : {}", GeneralUtils.mXPX2XPXStr(walletXPX));
            logger.info("Lost  XPX : {}", GeneralUtils.mXPX2XPXStr(xpxStart.subtract(walletXPX)));
            logger.info("Fee       : {}xpx/{}txns", GeneralUtils.mXPX2XPXStr(r.getMaxFee().multiply(BigInteger.valueOf(nTxnFee))), nTxnFee);
            logger.info("================================================");
        }
    }

    private void runProcess(int nRunners) throws Exception {
        if (numTxns > 0 && nRunners > 0) {

            long startTime = System.currentTimeMillis();
            long nFailed = 0;
            long nTxnFee = 0;
            MainRunner r = new MainRunner(nRunners, numTxns, txnDelay, api, sender, msg, xpx.getAmount(), fee, backupNodes);
            new Thread(r).start();
            while (!r.isDone()) {
                GeneralUtils.sleep(5000L);
            }
            nFailed = r.getFailed();
            nTxnFee = r.getTxnFee();
            BigInteger walletXPX = TransactionHelper.getXPX(api, sender.getAddress());
            logger.info("================================================");
            logger.info("Total time: {}s - Fail {} Success {} txns", (System.currentTimeMillis() - startTime) / 1000, nFailed, nTxnFee);
            logger.info("Start XPX : {}", GeneralUtils.mXPX2XPXStr(xpxStart));
            logger.info("End   XPX : {}", GeneralUtils.mXPX2XPXStr(walletXPX));
            logger.info("Lost  XPX : {}", GeneralUtils.mXPX2XPXStr(xpxStart.subtract(walletXPX)));
            logger.info("Fee       : {}xpx/{}txns", GeneralUtils.mXPX2XPXStr(r.getMaxFee().multiply(BigInteger.valueOf(nTxnFee))), nTxnFee);
            logger.info("================================================");

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        Thread.sleep(200);
                        logger.info("Shutting down ...");
                        r.killAll();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                }
            });

        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Main <numoftxn>");
            System.out.println(" <numoftxn>:  number of txn");
            return;
        }
        int numTxns = GeneralUtils.parseInt(args[0], 0);
        new MainTxn(numTxns).runProcess();
    }
}
