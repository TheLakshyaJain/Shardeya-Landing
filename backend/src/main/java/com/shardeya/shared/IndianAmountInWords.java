package com.shardeya.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * B-11 §7: payment receipts must show the amount in figures AND Indian
 * words ("Two Lakh Rupees Only") -- legally expected on Indian receipts,
 * and specifically the Indian lakh/crore grouping, never the
 * million/billion convention CLAUDE.md rule #2 already forbids for
 * display generally.
 */
public final class IndianAmountInWords {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private IndianAmountInWords() {
    }

    /** e.g. 200000.00 -> "Two Lakh Rupees Only"; 200000.50 -> "Two Lakh Rupees and Fifty Paise Only". */
    public static String convert(BigDecimal amount) {
        BigDecimal abs = amount.abs();
        long rupees = abs.setScale(0, RoundingMode.DOWN).longValueExact();
        int paise = abs.subtract(BigDecimal.valueOf(rupees)).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();

        StringBuilder sb = new StringBuilder();
        if (rupees == 0) {
            sb.append("Zero Rupees");
        } else {
            sb.append(convertWholeNumber(rupees)).append(" Rupees");
        }
        if (paise > 0) {
            sb.append(" and ").append(convertBelowThousand(paise)).append(" Paise");
        }
        sb.append(" Only");
        return sb.toString();
    }

    // Indian grouping: crore (1,00,00,000) / lakh (1,00,000) / thousand / hundred,
    // never the million/billion split CLAUDE.md rule #2 forbids for display.
    private static String convertWholeNumber(long number) {
        if (number == 0) return "Zero";
        StringBuilder sb = new StringBuilder();
        long crore = number / 10000000;
        number %= 10000000;
        long lakh = number / 100000;
        number %= 100000;
        long thousand = number / 1000;
        number %= 1000;
        long hundredsPart = number;

        if (crore > 0) sb.append(convertBelowThousand(crore)).append(" Crore ");
        if (lakh > 0) sb.append(convertBelowThousand(lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(convertBelowThousand(thousand)).append(" Thousand ");
        if (hundredsPart > 0) sb.append(convertBelowThousand(hundredsPart));
        return sb.toString().trim();
    }

    private static String convertBelowThousand(long number) {
        StringBuilder sb = new StringBuilder();
        if (number >= 100) {
            sb.append(ONES[(int) (number / 100)]).append(" Hundred ");
            number %= 100;
        }
        if (number >= 20) {
            sb.append(TENS[(int) (number / 10)]).append(" ");
            number %= 10;
            if (number > 0) sb.append(ONES[(int) number]).append(" ");
        } else if (number > 0) {
            sb.append(ONES[(int) number]).append(" ");
        }
        return sb.toString().trim();
    }
}
