package io.proximax.test;

import io.proximax.sdk.BlockchainApi;
import io.proximax.sdk.FeeCalculationStrategy;
import io.proximax.sdk.model.account.Account;
import io.proximax.utils.GeneralUtils;
import io.proximax.utils.TransactionHelper;
import java.math.BigInteger;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Administrator
 */
public class MainRunner implements Runnable {

    /**
     * logger
     */
    private static final Logger logger = LoggerFactory.getLogger(MainRunner.class);
    private boolean isDone = false;
    private int numTxns = 0;
    private BlockchainApi api = null;
    private Account topSender = null;
    private FeeCalculationStrategy fee = FeeCalculationStrategy.HIGH;
    private StringBuffer msg;
    private BigInteger amount = BigInteger.ZERO;
    private int nRunners = 0;
    private long txnDelay;
    private BigInteger maxFee = BigInteger.ZERO;
    private String backupNodes = "";

    public MainRunner(int nRunners, int numTxns, long txnDelay, BlockchainApi api, Account topSender, StringBuffer msg, BigInteger amount, FeeCalculationStrategy fee, String backupNodes) {
        this.txnDelay = txnDelay;
        this.api = api;
        this.topSender = topSender;
        this.msg = msg;
        this.amount = amount;
        this.nRunners = nRunners;
        this.fee = fee;
        this.numTxns = numTxns;
        this.backupNodes = backupNodes;
    }
    private long nFailed = 0;
    private long nTxnFee = 0;

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        long nDelay = 8 * txnDelay;
        BigInteger xpxStart = TransactionHelper.getXPX(api, topSender.getAddress());
        if (xpxStart.compareTo(BigInteger.ZERO) == 1) {
            RunnerTxn r = new RunnerTxn(1, api, topSender, amount, fee, msg, numTxns, txnDelay, backupNodes);
            new Thread(r).start();
            long ltime = System.currentTimeMillis();
            while (true) {
                nFailed = r.getTxnFailed();
                nTxnFee = r.getTxnFee();
                GeneralUtils.sleep(2000);
                if (System.currentTimeMillis() - ltime > 600000) { // 10*60*1000 
                    ltime = System.currentTimeMillis();
                    logger.info("***************************************************************************************");
                    logger.info("Runtime: {}s - Txns: Fail {} Success {} - Fee: {}xpx/{}txns", (System.currentTimeMillis() - startTime) / 1000, nFailed, nTxnFee, GeneralUtils.mXPX2XPXStr(r.getMaxFee().multiply(BigInteger.valueOf(nTxnFee))), nTxnFee);
                    logger.info("***************************************************************************************");
                }
                if (r.isDone()) {
                    break;
                }
                if (bKill) {
                    r.killProcess();
                }
            }
            maxFee = r.getMaxFee();
        } else {
            nFailed = numTxns;
        }
        isDone = true;
    }
    private boolean bKill = false;

    public void killAll() {
        bKill = true;
    }

    public BigInteger getMaxFee() {
        return maxFee;
    }

    public boolean isDone() {
        return isDone;
    }

    public long getFailed() {
        return nFailed;
    }

    public long getTxnFee() {
        return nTxnFee;
    }
}
