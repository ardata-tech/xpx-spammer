package io.proximax.utils;

import io.proximax.sdk.model.account.Account;
import io.proximax.sdk.model.account.Address;
import io.proximax.sdk.model.mosaic.NetworkCurrencyMosaic;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

/**
 *
 * @author Administrator
 */
public class GeneralUtils {

    public static long parseLong(String value, long defaultValue) {
        long l;
        try {
            l = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            l = defaultValue;
        }
        return l;
    }
    
    public static String mXPX2XPXStr(BigInteger mXPX) {
        return String.format("%.06f", mXPX2XPX(mXPX));
    }
    
    public static double mXPX2XPX(BigInteger mXPX) {
        return (1.0 * mXPX.longValue() / 1000000.0);        
    }
    
    public static int parseInt(String value, int defaultValue) {
        int l;
        try {
            l = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            l = defaultValue;
        }
        return l;
    }
    
    public static long parseTime(String value, long defaultValue) {
        long l;
        long multi = 1;
        if (value.endsWith("ms")) {
            multi = 1;
            value = value.substring(0, value.length()-2);
        } else if (value.endsWith("s")) {
            multi = 1000;
            value = value.substring(0, value.length()-1);
        } else if (value.endsWith("m")) {
            multi = 60 * 1000;
            value = value.substring(0, value.length()-1);
        } else if (value.endsWith("h")) {
            multi = 60 * 60 * 1000;
            value = value.substring(0, value.length()-1);
        }
        try {            
            l = multi * Long.parseLong(value);
        } catch (NumberFormatException ex) {
            l = defaultValue;
        }
        
        return l;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    public static void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
        }
    }

    public static String getAccount(Account acc) {
        return String.format("%s,%s,%s", acc.getPrivateKey(), acc.getPublicKey(), acc.getAddress().plain());
    }

    /**
     * Check if a address is valid
     *
     * @param address A address
     *
     * @return True if valid, false otherwise
     */
    public static boolean isAddressValid(String address) {
        try {
            if (address.length() == 40) {
                Address addr = Address.createFromRawAddress(address);
                return addr != null;
            }
        } catch (Exception ex) {
        }
        return false;
    }

    public static int getRandomNumber(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

}
