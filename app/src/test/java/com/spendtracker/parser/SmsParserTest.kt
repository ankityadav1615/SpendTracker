package com.spendtracker.parser

import com.spendtracker.model.Category
import com.spendtracker.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    private fun parse(body: String, sender: String = "VM-HDFCBK-S"): ParsedSms {
        val parsed = SmsParser.parse(sender, body)
        assertNotNull("Expected a transaction for: $body", parsed)
        return parsed!!
    }

    @Test
    fun hdfcUpiSent() {
        val sms = parse(
            "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 20/10/24\nRef 434212345678\nNot You?\n" +
                "Call 18002586161/SMS BLOCK UPI to 7308080808",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(500_00L, sms.amountPaise)
        assertEquals("Swiggy", sms.counterparty)
        assertEquals("1234", sms.accountTail)
        assertEquals("HDFC Bank", sms.bank)
        assertEquals("434212345678", sms.refNo)
        assertNull(sms.balancePaise)
    }

    @Test
    fun hdfcUpiReceivedFromVpa() {
        val sms = parse(
            "Money Received - INR 1,500.00 in your HDFC Bank A/c xx1234 on 21-10-24 by VPA rahul.sharma@okaxis " +
                "(UPI Ref No 429512345678)",
        )
        assertEquals(TransactionType.CREDIT, sms.type)
        assertEquals(1_500_00L, sms.amountPaise)
        assertEquals("rahul.sharma@okaxis", sms.vpa)
        assertNull("Only the VPA is known", sms.counterparty)
        assertEquals("1234", sms.accountTail)
        assertEquals("429512345678", sms.refNo)
    }

    @Test
    fun sbiUpiDebitWithoutCurrencySymbol() {
        val sms = parse(
            "Dear UPI user A/C X1234 debited by 250.0 on date 20Oct24 trf to RAMESH KUMAR Refno 429312345678. " +
                "If not u? call 1800111109. -SBI",
            sender = "AD-SBIUPI",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(250_00L, sms.amountPaise)
        assertEquals("Ramesh Kumar", sms.counterparty)
        assertEquals("1234", sms.accountTail)
        assertEquals("SBI", sms.bank)
        assertEquals("429312345678", sms.refNo)
    }

    @Test
    fun iciciUpiDebitNamesPayeeBeforeCredited() {
        val sms = parse(
            "ICICI Bank Acct XX123 debited for Rs 1,250.00 on 21-Oct-24; DMART AVENUE credited. UPI:429612345678. " +
                "Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
            sender = "VM-ICICIB-S",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(1_250_00L, sms.amountPaise)
        assertEquals("Dmart Avenue", sms.counterparty)
        assertEquals("123", sms.accountTail)
        assertEquals("ICICI Bank", sms.bank)
        assertEquals("429612345678", sms.refNo)
    }

    @Test
    fun axisUpiPathGivesPayee() {
        val sms = parse(
            "INR 320.00 debited\nA/c no. XX4321\n20-10-24, 18:22:11\nUPI/P2M/429812345678/ZOMATO LTD\n" +
                "Not you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
            sender = "JD-AXISBK-S",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(320_00L, sms.amountPaise)
        assertEquals("Zomato LTD", sms.counterparty)
        assertEquals("4321", sms.accountTail)
        assertEquals("Axis Bank", sms.bank)
        assertEquals("429812345678", sms.refNo)
    }

    @Test
    fun kotakSentToVpa() {
        val sms = parse(
            "Sent Rs.199.00 from Kotak Bank AC X5678 to netflix@hdfcbank on 22-10-24.UPI Ref 429912345678. " +
                "Not you, https://kotak.com/KBANKT/Fraud",
            sender = "VM-KOTAKB-S",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(199_00L, sms.amountPaise)
        assertEquals("netflix@hdfcbank", sms.vpa)
        assertNull(sms.counterparty)
        assertEquals("5678", sms.accountTail)
        assertEquals("Kotak Bank", sms.bank)
        assertEquals("429912345678", sms.refNo)
    }

    @Test
    fun creditCardSpendIsDebitNotCredit() {
        val sms = parse(
            "Thank you for using your HDFC Bank Credit Card ending 4321 for Rs 2,499.00 at AMAZON PAY INDIA on " +
                "2024-10-22:10:15:22. Authorization code:- 123456",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(2_499_00L, sms.amountPaise)
        assertEquals("Amazon Pay India", sms.counterparty)
        assertEquals("4321", sms.accountTail)
    }

    @Test
    fun cardSpendStripsGatewayPrefixAndIgnoresLimit() {
        val sms = parse(
            "INR 1,234.50 spent using ICICI Bank Card XX9876 on 23-Oct-24 on IND*FLIPKART. Avl Limit: INR 98,765.50. " +
                "If not you, call 1800 2662/SMS BLOCK 9876 to 9215676766",
            sender = "VM-ICICIB",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(1_234_50L, sms.amountPaise)
        assertEquals("Flipkart", sms.counterparty)
        assertEquals("9876", sms.accountTail)
        assertNull("Card limit is not an account balance", sms.balancePaise)
    }

    @Test
    fun neftSalaryCreditWithBalance() {
        val sms = parse(
            "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 01-OCT-24 for NEFT Cr-CITI0000001-" +
                "ACME TECHNOLOGIES PVT LTD-RAHUL SHARMA-CITIN24123456789.Avl bal INR 1,02,345.67. " +
                "Cheque deposits in A/C are subject to clearing",
        )
        assertEquals(TransactionType.CREDIT, sms.type)
        assertEquals(85_000_00L, sms.amountPaise)
        assertEquals("Acme Technologies PVT LTD", sms.counterparty)
        assertEquals(1_02_345_67L, sms.balancePaise)
        assertEquals("1234", sms.accountTail)
    }

    @Test
    fun sbiCreditSkipsAmountWhenLookingForPayer() {
        val sms = parse(
            "Dear Customer, Your A/c XXXXXXXX1234 is credited by Rs.75,000.00 on 30-09-24 by SALARY SEP24 ACME CORP. " +
                "Avl Bal Rs.80,123.45 -SBI",
            sender = "VM-SBIINB",
        )
        assertEquals(TransactionType.CREDIT, sms.type)
        assertEquals(75_000_00L, sms.amountPaise)
        assertEquals("Salary SEP24 Acme Corp", sms.counterparty)
        assertEquals(80_123_45L, sms.balancePaise)
        assertEquals("SBI", sms.bank)
    }

    @Test
    fun nachEmiDebitStripsRailPrefix() {
        val sms = parse(
            "Your A/c XX1234 has been debited with INR 12,345.00 on 05-10-2024 towards NACH-DR-BAJAJ FINANCE LTD. " +
                "Avl Bal INR 45,678.90",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(12_345_00L, sms.amountPaise)
        assertEquals("Bajaj Finance LTD", sms.counterparty)
        assertEquals(45_678_90L, sms.balancePaise)
    }

    @Test
    fun atmWithdrawal() {
        val sms = parse("Rs.5000.00 withdrawn from A/c XX1234 on 06-10-24 at ATM S1ANBG01. Avl Bal Rs.40678.90", "AX-SBIINB")
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(5_000_00L, sms.amountPaise)
        assertEquals("ATM S1ANBG01", sms.counterparty)
        assertEquals(40_678_90L, sms.balancePaise)
    }

    @Test
    fun amountSkipsLeadingBalance() {
        val sms = parse("Avl bal Rs 10,000.00. Rs 500.00 debited from a/c XX1234 on 21-10-24 to Ramesh", "VM-KOTAKB")
        assertEquals(500_00L, sms.amountPaise)
        assertEquals(10_000_00L, sms.balancePaise)
        assertEquals("Ramesh", sms.counterparty)
    }

    @Test
    fun rupeeSymbolAndMixedCaseName() {
        val sms = parse(
            "₹250.00 paid to Uber India from your IDFC FIRST Bank a/c XX1234 via UPI. Ref 429612340000",
            sender = "BZ-IDFCFB",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(250_00L, sms.amountPaise)
        assertEquals("Uber India", sms.counterparty)
        assertEquals("IDFC FIRST Bank", sms.bank)
        assertEquals("429612340000", sms.refNo)
    }

    @Test
    fun upiToVpaWithRefNo() {
        val sms = parse("Rs.845.00 debited from a/c **1234 on 13-10-24 to VPA blinkit.payu@hdfcbank (UPI Ref No. 428712345678).")
        assertEquals(84_500L, sms.amountPaise)
        assertEquals("blinkit.payu@hdfcbank", sms.vpa)
        assertEquals("1234", sms.accountTail)
        assertEquals("428712345678", sms.refNo)
    }

    @Test
    fun hyphenSeparatedUpiNarrationDebit() {
        val sms = parse(
            "Rs.350.00 debited from A/c XX1234 on 20-10-24 Info: UPI-SWIGGY-swiggy@icici-ICIC0DC0099-429512345678-Payment. " +
                "Avl bal:INR 5,000.00",
        )
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(350_00L, sms.amountPaise)
        assertEquals("swiggy@icici", sms.vpa)
        assertEquals("Swiggy", sms.counterparty)
        assertEquals("429512345678", sms.refNo)
        assertEquals(5_000_00L, sms.balancePaise)
    }

    @Test
    fun hyphenSeparatedUpiNarrationCredit() {
        val sms = parse(
            "INR 2,000.00 credited to A/c XX1234 on 21-10-24 for UPI-RAHUL SHARMA-rahul@okaxis-UTIB0000123-429512345678-NA. " +
                "Avl Bal INR 7,000.00",
        )
        assertEquals(TransactionType.CREDIT, sms.type)
        assertEquals("rahul@okaxis", sms.vpa)
        assertEquals("Rahul Sharma", sms.counterparty)
        assertEquals("429512345678", sms.refNo)
    }

    @Test
    fun merchantVpaWithHyphen() {
        val sms = parse(
            "Sent Rs.120.00 from Kotak Bank AC X5678 to gpay-11223344556@okbizaxis on 22-10-24.UPI Ref 429912345679.",
            sender = "VM-KOTAKB-S",
        )
        assertEquals("gpay-11223344556@okbizaxis", sms.vpa)
        assertNull(sms.counterparty)
        assertEquals("429912345679", sms.refNo)
    }

    @Test
    fun reversalOfFailedPaymentIsACredit() {
        val sms = parse("Rs 500.00 credited to your A/c XX1234 on 21-10-24 as reversal of failed UPI transaction 429512345678")
        assertEquals(TransactionType.CREDIT, sms.type)
        assertEquals(500_00L, sms.amountPaise)
        assertEquals("429512345678", sms.refNo)
    }

    @Test
    fun ignoresMessagesThatAreNotCompletedTransactions() {
        val notTransactions = listOf(
            "123456 is your OTP for txn of Rs 500.00 at AMAZON on HDFC Bank card ending 1234. Valid for 5 mins. Do not share.",
            "ABC has requested money from you on Google Pay. On approving, Rs 500 will be debited from your account",
            "Your EMI of Rs 12,345 for loan a/c XX1234 will be debited on 05-11-2024. Please maintain sufficient balance.",
            "Your HDFC Bank Credit Card XX5678 statement: Total amount due Rs 15,000, minimum due Rs 750, payment due by 15-11-24",
            "Your UPI transaction of Rs 500.00 to SWIGGY has failed. Any amount debited will be refunded within 3 days.",
            "Congratulations! You are eligible for a pre-approved personal loan of Rs 5,00,000. Apply now",
            "Payment of Rs 15,000.00 received towards your HDFC Bank Credit Card ending 5678 on 10-10-24. Thank you.",
            "Your HDFC Bank account statement for October is ready",
            "Festive special! Vishal Mega Mart 300 bonus points = Rs 150 credited to your loyalty account valid till tomorrow. Redeemable on minimum shopping Rs 1500.",
        )
        notTransactions.forEach { body -> assertNull(body, SmsParser.parse("VM-HDFCBK", body)) }
    }

    @Test
    fun ignoresPersonalSenders() {
        assertNull(SmsParser.parse("+919876543210", "Sent Rs.500.00 From HDFC Bank A/C *1234 To ABC On 20/10/24"))
    }

    @Test
    fun onboardingExampleGivesTheEntryItShows() {
        val body = "ICICI Bank Acct XX3485 debited for Rs 450.00 on 20-Oct-26; SWIGGY credited. UPI:629318840412."
        val sms = parse(body, sender = "VM-ICICIB-S")
        assertEquals(TransactionType.DEBIT, sms.type)
        assertEquals(450_00L, sms.amountPaise)
        assertEquals("Swiggy", sms.counterparty)
        assertEquals("ICICI Bank", sms.bank)
        assertEquals("3485", sms.accountTail)
        assertEquals(Category.FOOD, Categorizer.categorize(sms, body))
    }

    @Test
    fun ignoresPromotionalRouteSenders() {
        // DLT marks promotional messages with a -P suffix; banks send alerts on -S/-T routes.
        assertNull(SmsParser.parse("JD-VMEGAM-P", "Rs 150 credited to your account. Shop now!"))
        assertNull(SmsParser.parse("VM-HDFCBK-P", "Rs 500 debited from A/c XX1234 to AMAZON. UPI Ref 123456789012"))
        assertNotNull(SmsParser.parse("VM-HDFCBK-S", "Rs 500 debited from A/c XX1234 to AMAZON. UPI Ref 123456789012"))
    }

    @Test
    fun needsSomeSignOfARealAccount() {
        // A store's promo has an amount and "credited" but no account, UPI id, reference or bank.
        assertNull(SmsParser.parse("AD-VMEGAM", "Rs 150 has been credited for you. Visit the nearest store."))
        assertNull(SmsParser.parse("AX-MYNTRA", "Rs 200 credited to your Myntra wallet."))
        // The same words from a bank count.
        assertNotNull(SmsParser.parse("AD-SBIUPI", "Rs 150 has been credited to your account."))
    }

    @Test
    fun ignoresMoreOffersAndRewards() {
        val offers = listOf(
            "Congrats! You've won Rs 500 in the lucky draw. Claim from A/c XX1234 now.",
            "Use code FEST100 to get Rs 100 credited to A/c XX1234 on your next order.",
            "Flat 20% off! Rs 300 cashback will be credited to your A/c XX1234 on shopping above Rs 1500.",
            "Get cashback up to Rs 250 credited to A/c XX1234 when you pay with UPI.",
            "Rs 50 will be credited to your wallet after the order is delivered. A/c XX1234",
        )
        offers.forEach { body -> assertNull(body, SmsParser.parse("VM-HDFCBK", body)) }
    }

    @Test
    fun senderKeyDropsOperatorAndRoute() {
        assertEquals("HDFCBK", SmsParser.senderKey("VM-HDFCBK-S"))
        assertEquals("HDFCBK", SmsParser.senderKey("ad-hdfcbk"))
        assertEquals("VMEGAM", SmsParser.senderKey("JD-VMEGAM-P"))
        assertEquals("VMEGAM", SmsParser.senderKey(" VMEGAM "))
        assertEquals("+919876543210", SmsParser.senderKey("+91 98765 43210"))
        assertEquals("", SmsParser.senderKey("  "))
    }

    @Test
    fun recognisesBankSenders() {
        assertEquals(true, SmsParser.isBankSender("VM-HDFCBK-S"))
        assertEquals(true, SmsParser.isBankSender("AD-SBIUPI"))
        assertEquals(true, SmsParser.isBankSender("VM-GRNBNK"))
        assertEquals(false, SmsParser.isBankSender("JD-VMEGAM-P"))
        assertEquals(false, SmsParser.isBankSender("+919876543210"))
    }
}
